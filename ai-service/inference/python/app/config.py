from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path

from pydantic import PrivateAttr
from pydantic_settings import BaseSettings, SettingsConfigDict

AI_SERVICE_ROOT = Path(__file__).resolve().parents[3]
MODEL_ROOT = AI_SERVICE_ROOT / "model"
LEGACY_MODEL_ROOT = Path(__file__).resolve().parents[4] / "model"


class Settings(BaseSettings):
    model_bundle: str = ""
    model_path: Path = MODEL_ROOT / "food_classifier.onnx"
    labels_path: Path = MODEL_ROOT / "labels.json"
    model_metadata_path: Path = MODEL_ROOT / "metadata.json"
    manifest_path: Path = MODEL_ROOT / "category_manifest.json"
    retrieval_bank_path: Path = MODEL_ROOT / "retrieval_bank.json"
    clip_text_bank_path: Path = MODEL_ROOT / "clip_text_bank.npz"
    clip_text_bank_meta_path: Path = MODEL_ROOT / "clip_text_bank.meta.json"

    model_version: str = "food-cls-v1"
    model_runtime: str = "auto"
    classifier_adapter: str = "default"
    backend: str = "auto"
    top_k_default: int = 4
    image_size: int = 224
    retrieval_pool_size: int = 12
    hybrid_internal_top_k: int = 5
    hybrid_classifier_accept_confidence: float = 0.88
    hybrid_classifier_margin_threshold: float = 0.20
    hybrid_clip_support_top_k: int = 3
    classifier_input_layout: str = "nchw"
    classifier_preprocess: str = "imagenet"

    clip_model_name: str = "ViT-B-32"
    clip_pretrained: str = "openai"
    clip_language: str = "both"
    clip_device: str = ""

    mock_mode_enabled: bool = True

    _resolved_model_bundle: str = PrivateAttr(default="")
    _classifier_preferred: bool = PrivateAttr(default=False)

    model_config = SettingsConfigDict(
        env_prefix="VISION_",
        env_file=".env",
        env_file_encoding="utf-8-sig",
        extra="ignore",
    )

    def model_post_init(self, __context) -> None:
        provided_fields = set(self.model_fields_set)
        bundle_dir = self._resolve_bundle_dir(provided_fields)

        self._classifier_preferred = bool(self.model_bundle.strip()) or "model_path" in provided_fields
        if bundle_dir is None:
            if self.model_runtime == "auto":
                self.model_runtime = self._infer_runtime_from_model_path(self.model_path)
            return

        metadata = self._load_bundle_metadata(bundle_dir)
        self._resolved_model_bundle = bundle_dir.name
        self._classifier_preferred = True

        metadata_path = bundle_dir / "metadata.json"
        if "model_metadata_path" not in provided_fields and metadata_path.exists():
            self.model_metadata_path = metadata_path

        if "model_path" not in provided_fields:
            self.model_path = self._resolve_bundle_model_path(bundle_dir, metadata)
        if "labels_path" not in provided_fields:
            self.labels_path = self._resolve_bundle_labels_path(bundle_dir, metadata)
        if "manifest_path" not in provided_fields:
            manifest_path = self._resolve_optional_bundle_path(
                bundle_dir,
                metadata,
                "category_manifest_file",
            )
            if manifest_path is not None:
                self.manifest_path = manifest_path
        if "retrieval_bank_path" not in provided_fields:
            retrieval_bank_path = self._resolve_optional_bundle_path(
                bundle_dir,
                metadata,
                "retrieval_bank_file",
            )
            if retrieval_bank_path is not None:
                self.retrieval_bank_path = retrieval_bank_path
        if "clip_text_bank_path" not in provided_fields:
            clip_text_bank_path = self._resolve_optional_bundle_path(
                bundle_dir,
                metadata,
                "clip_text_bank_file",
            )
            if clip_text_bank_path is not None:
                self.clip_text_bank_path = clip_text_bank_path
        if "clip_text_bank_meta_path" not in provided_fields:
            clip_text_bank_meta_path = self._resolve_optional_bundle_path(
                bundle_dir,
                metadata,
                "clip_text_bank_meta_file",
            )
            if clip_text_bank_meta_path is not None:
                self.clip_text_bank_meta_path = clip_text_bank_meta_path
        if "image_size" not in provided_fields:
            metadata_image_size = metadata.get("image_size")
            if metadata_image_size:
                self.image_size = int(metadata_image_size)
        if "model_version" not in provided_fields:
            metadata_model_version = str(metadata.get("model_version", "")).strip()
            if metadata_model_version:
                self.model_version = metadata_model_version
        if "model_runtime" not in provided_fields:
            self.model_runtime = self._resolve_runtime(metadata)
        if "classifier_adapter" not in provided_fields:
            adapter = str(metadata.get("classifier_adapter") or metadata.get("adapter") or "").strip()
            if adapter:
                self.classifier_adapter = adapter
        if "classifier_input_layout" not in provided_fields:
            input_layout = str(metadata.get("input_layout") or "").strip()
            if input_layout:
                self.classifier_input_layout = input_layout
        if "classifier_preprocess" not in provided_fields:
            preprocess = str(metadata.get("preprocess") or "").strip()
            if preprocess:
                self.classifier_preprocess = preprocess

        if self.model_runtime == "auto":
            self.model_runtime = self._infer_runtime_from_model_path(self.model_path)

    @property
    def resolved_model_bundle(self) -> str:
        return self._resolved_model_bundle

    @property
    def prefers_classifier(self) -> bool:
        return self._classifier_preferred

    def _resolve_bundle_dir(self, provided_fields: set[str]) -> Path | None:
        bundle_name = self.model_bundle.strip()
        if bundle_name:
            bundle_dir = MODEL_ROOT / bundle_name
            legacy_bundle_dir = LEGACY_MODEL_ROOT / bundle_name
            if not bundle_dir.exists() or not self._bundle_has_supported_model(bundle_dir):
                if legacy_bundle_dir.exists() and self._bundle_has_supported_model(legacy_bundle_dir):
                    bundle_dir = legacy_bundle_dir
            if not bundle_dir.exists():
                raise FileNotFoundError(f"configured model bundle does not exist: {bundle_dir}")
            if not bundle_dir.is_dir():
                raise NotADirectoryError(f"configured model bundle is not a directory: {bundle_dir}")
            return bundle_dir

        if "model_path" in provided_fields or self.model_path.exists():
            return None

        bundle_dirs = [
            path for path in MODEL_ROOT.iterdir()
            if path.is_dir() and self._bundle_has_supported_model(path)
        ]
        if LEGACY_MODEL_ROOT.exists():
            bundle_dirs.extend(
                path for path in LEGACY_MODEL_ROOT.iterdir()
                if path.is_dir() and self._bundle_has_supported_model(path)
            )
        if len(bundle_dirs) == 1:
            return bundle_dirs[0]
        return None

    def _load_bundle_metadata(self, bundle_dir: Path) -> dict:
        metadata_path = bundle_dir / "metadata.json"
        if not metadata_path.exists():
            return {}
        data = json.loads(metadata_path.read_text(encoding="utf-8-sig"))
        return data if isinstance(data, dict) else {}

    def _resolve_bundle_model_path(self, bundle_dir: Path, metadata: dict) -> Path:
        configured_name = str(
            metadata.get("model_file")
            or metadata.get("onnx_file")
            or metadata.get("keras_file")
            or metadata.get("saved_model_dir")
            or ""
        ).strip()
        if configured_name:
            candidate = (bundle_dir / configured_name).resolve()
            if candidate.exists():
                return candidate

        for relative_path in (
            "food_classifier.onnx",
            "model.onnx",
            "model.keras",
            "model.h5",
            "model.hdf5",
            "saved_model",
        ):
            candidate = bundle_dir / relative_path
            if candidate.exists():
                return candidate.resolve()

        for pattern in ("*.onnx", "*.keras", "*.h5", "*.hdf5"):
            matches = sorted(bundle_dir.rglob(pattern))
            if matches:
                return matches[0].resolve()

        saved_model_markers = sorted(bundle_dir.rglob("saved_model.pb"))
        if saved_model_markers:
            return saved_model_markers[0].parent.resolve()

        raise FileNotFoundError(f"no supported model found under bundle: {bundle_dir}")

    def _resolve_bundle_labels_path(self, bundle_dir: Path, metadata: dict) -> Path:
        configured_name = str(metadata.get("labels_file") or "").strip()
        if configured_name:
            candidate = (bundle_dir / configured_name).resolve()
            if candidate.exists():
                return candidate

        for relative_path in ("labels.json", "checkpoints/labels.json"):
            candidate = bundle_dir / relative_path
            if candidate.exists():
                return candidate.resolve()

        matches = sorted(bundle_dir.rglob("labels.json"))
        if not matches:
            raise FileNotFoundError(f"no labels.json found under bundle: {bundle_dir}")
        return matches[0].resolve()

    def _resolve_optional_bundle_path(self, bundle_dir: Path, metadata: dict, key: str) -> Path | None:
        configured_name = str(metadata.get(key) or "").strip()
        if not configured_name:
            return None

        candidate = (bundle_dir / configured_name).resolve()
        if candidate.exists():
            return candidate
        return None

    def _resolve_runtime(self, metadata: dict) -> str:
        runtime = str(
            metadata.get("runtime")
            or metadata.get("framework")
            or metadata.get("export_format")
            or ""
        ).strip().lower()
        if runtime in {"onnx"}:
            return "onnx"
        if runtime in {"tensorflow", "keras", "savedmodel", "saved_model"}:
            return "tensorflow"
        return "auto"

    def _infer_runtime_from_model_path(self, model_path: Path) -> str:
        if model_path.is_dir():
            return "tensorflow"

        suffix = model_path.suffix.lower()
        if suffix == ".onnx":
            return "onnx"
        if suffix in {".keras", ".h5", ".hdf5"}:
            return "tensorflow"
        return "auto"

    def _bundle_has_supported_model(self, bundle_dir: Path) -> bool:
        supported_patterns = ("*.onnx", "*.keras", "*.h5", "*.hdf5")
        for pattern in supported_patterns:
            if any(bundle_dir.rglob(pattern)):
                return True
        return any(bundle_dir.rglob("saved_model.pb"))


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
