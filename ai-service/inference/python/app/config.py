from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path

from pydantic import PrivateAttr
from pydantic_settings import BaseSettings, SettingsConfigDict

AI_SERVICE_ROOT = Path(__file__).resolve().parents[3]
MODEL_ROOT = AI_SERVICE_ROOT / "model"


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
    backend: str = "auto"
    top_k_default: int = 4
    image_size: int = 224
    retrieval_pool_size: int = 12
    hybrid_internal_top_k: int = 5
    hybrid_classifier_accept_confidence: float = 0.88
    hybrid_classifier_margin_threshold: float = 0.20
    hybrid_clip_support_top_k: int = 3

    clip_model_name: str = "ViT-B-32"
    clip_pretrained: str = "openai"
    clip_language: str = "both"
    clip_device: str = ""

    llava_model_id: str = "llava-hf/llava-v1.6-mistral-7b-hf"
    llava_model_revision: str = "main"
    llava_model_version: str = "llava-next-v1.6-mistral-7b"
    llava_device: str = ""
    llava_torch_dtype: str = "auto"
    llava_max_new_tokens: int = 256
    llava_temperature: float = 0.0
    llava_top_p: float = 1.0
    llava_do_sample: bool = False
    llava_catalog_limit: int = 32
    llava_alias_limit: int = 3

    mock_mode_enabled: bool = True

    _resolved_model_bundle: str = PrivateAttr(default="")
    _classifier_preferred: bool = PrivateAttr(default=False)

    model_config = SettingsConfigDict(
        env_prefix="VISION_",
        env_file=".env",
        extra="ignore",
    )

    def model_post_init(self, __context) -> None:
        provided_fields = set(self.model_fields_set)
        bundle_dir = self._resolve_bundle_dir(provided_fields)

        self._classifier_preferred = bool(self.model_bundle.strip()) or "model_path" in provided_fields
        if bundle_dir is None:
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
        if "image_size" not in provided_fields:
            metadata_image_size = metadata.get("image_size")
            if metadata_image_size:
                self.image_size = int(metadata_image_size)
        if "model_version" not in provided_fields:
            metadata_model_version = str(metadata.get("model_version", "")).strip()
            if metadata_model_version:
                self.model_version = metadata_model_version

    @property
    def resolved_model_bundle(self) -> str:
        return self._resolved_model_bundle

    @property
    def prefers_classifier(self) -> bool:
        return self._classifier_preferred

    def model_version_for_backend(self, backend: str) -> str:
        if backend == "llava_next_retrieval" and self.llava_model_version.strip():
            return self.llava_model_version.strip()
        return self.model_version

    def _resolve_bundle_dir(self, provided_fields: set[str]) -> Path | None:
        bundle_name = self.model_bundle.strip()
        if bundle_name:
            bundle_dir = MODEL_ROOT / bundle_name
            if not bundle_dir.exists():
                raise FileNotFoundError(f"configured model bundle does not exist: {bundle_dir}")
            if not bundle_dir.is_dir():
                raise NotADirectoryError(f"configured model bundle is not a directory: {bundle_dir}")
            return bundle_dir

        if "model_path" in provided_fields or self.model_path.exists():
            return None

        bundle_dirs = [
            path for path in MODEL_ROOT.iterdir()
            if path.is_dir() and any(path.rglob("*.onnx"))
        ]
        if len(bundle_dirs) == 1:
            return bundle_dirs[0]
        return None

    def _load_bundle_metadata(self, bundle_dir: Path) -> dict:
        metadata_path = bundle_dir / "metadata.json"
        if not metadata_path.exists():
            return {}
        data = json.loads(metadata_path.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {}

    def _resolve_bundle_model_path(self, bundle_dir: Path, metadata: dict) -> Path:
        configured_name = str(metadata.get("onnx_file") or metadata.get("model_file") or "").strip()
        if configured_name:
            candidate = (bundle_dir / configured_name).resolve()
            if candidate.exists():
                return candidate

        for relative_path in ("food_classifier.onnx", "model.onnx"):
            candidate = bundle_dir / relative_path
            if candidate.exists():
                return candidate.resolve()

        matches = sorted(bundle_dir.rglob("*.onnx"))
        if not matches:
            raise FileNotFoundError(f"no ONNX model found under bundle: {bundle_dir}")
        return matches[0].resolve()

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


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
