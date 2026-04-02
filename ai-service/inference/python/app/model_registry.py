from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any

import numpy as np

from .config import Settings

try:
    import onnxruntime as ort
except ImportError:  # pragma: no cover - optional dependency
    ort = None

try:
    import open_clip
    import torch
except ImportError:  # pragma: no cover - optional dependency
    open_clip = None
    torch = None


DEFAULT_LABELS = [
    "米饭",
    "面条",
    "鸡蛋",
    "牛奶",
    "酸奶",
    "面包",
    "三明治",
    "馒头",
    "包子",
    "饺子",
    "燕麦",
    "香蕉",
    "苹果",
    "橙子",
    "西兰花",
    "黄瓜",
    "番茄",
    "土豆",
    "玉米",
    "豆腐",
    "鸡胸肉",
    "牛肉",
    "鱼",
    "虾",
    "沙拉",
    "炒饭",
    "炒面",
    "粥",
    "酸辣土豆丝",
    "西红柿炒蛋",
    "宫保鸡丁",
    "清炒时蔬",
]


@dataclass(frozen=True)
class FoodConcept:
    concept_id: str
    label: str
    english_name: str = ""
    group: str = "other"
    group_zh: str = "其他食物"
    aliases: tuple[str, ...] = ()
    search_keywords: tuple[str, ...] = ()
    clip_prompts_zh: tuple[str, ...] = ()
    clip_prompts_en: tuple[str, ...] = ()
    priority: int = 999
    notes: str = ""

    def all_terms(self) -> tuple[str, ...]:
        values: list[str] = []
        for item in (
            self.label,
            self.english_name,
            *self.aliases,
            *self.search_keywords,
            *self.clip_prompts_zh,
            *self.clip_prompts_en,
        ):
            text = item.strip()
            if text and text not in values:
                values.append(text)
        return tuple(values)


@dataclass
class ClipRuntime:
    model: Any
    preprocess: Any
    device: Any
    embeddings: np.ndarray
    concept_ids: tuple[str, ...]
    labels: tuple[str, ...]
    metadata: dict[str, Any]


class ModelRegistry:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self._classifier_session = None
        self._clip_runtime = None
        self._classifier_init_attempted = False
        self._clip_init_attempted = False

        self._concepts = self._load_concepts()
        self._concept_lookup = self._build_concept_lookup(self._concepts)
        self._concept_id_lookup = {concept.concept_id: concept for concept in self._concepts}
        self._labels = self._load_labels()

    @property
    def labels(self) -> list[str]:
        return self._labels

    @property
    def concepts(self) -> list[FoodConcept]:
        return self._concepts

    def concept_count(self) -> int:
        return len(self._concepts)

    def ordered_concepts(self) -> list[FoodConcept]:
        return list(self._concepts)

    def find_concept(self, value: str | None) -> FoodConcept | None:
        normalized = self._normalize(value)
        if not normalized:
            return None
        return self._concept_lookup.get(normalized)

    def find_concept_by_id(self, concept_id: str | None) -> FoodConcept | None:
        if not concept_id:
            return None
        return self._concept_id_lookup.get(concept_id.strip())

    def get_classifier_session(self):
        if self._classifier_init_attempted:
            return self._classifier_session

        self._classifier_init_attempted = True
        model_path = self.settings.model_path
        if ort is None or not model_path.exists():
            self._classifier_session = None
            return None

        self._classifier_session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
        return self._classifier_session

    def classifier_available(self) -> bool:
        return self.get_classifier_session() is not None

    def get_clip_runtime(self) -> ClipRuntime | None:
        if self._clip_init_attempted:
            return self._clip_runtime

        self._clip_init_attempted = True
        text_bank_path = self.settings.clip_text_bank_path
        if open_clip is None or torch is None or not text_bank_path.exists():
            self._clip_runtime = None
            return None

        metadata = self._load_clip_metadata()
        model_name = str(metadata.get("model_name") or self.settings.clip_model_name).strip()
        pretrained = str(metadata.get("pretrained") or self.settings.clip_pretrained).strip()
        device_name = self.settings.clip_device.strip() or ("cuda" if torch.cuda.is_available() else "cpu")
        device = torch.device(device_name)

        bank = np.load(text_bank_path, allow_pickle=False)
        embeddings = bank["embeddings"].astype("float32")
        concept_ids = tuple(str(item).strip() for item in bank["concept_ids"].tolist())
        labels = tuple(str(item).strip() for item in bank["labels"].tolist())

        model, _, preprocess = open_clip.create_model_and_transforms(model_name, pretrained=pretrained)
        model = model.to(device)
        model.eval()

        self._clip_runtime = ClipRuntime(
            model=model,
            preprocess=preprocess,
            device=device,
            embeddings=embeddings,
            concept_ids=concept_ids,
            labels=labels,
            metadata=metadata,
        )
        return self._clip_runtime

    def clip_available(self) -> bool:
        return self.get_clip_runtime() is not None

    def resolve_backend(self) -> str:
        preference = (self.settings.backend or "auto").strip().lower()

        if preference == "manifest":
            return "manifest_retrieval"
        if preference == "clip":
            if self.clip_available():
                return "clip_retrieval"
            if self.classifier_available():
                return "onnx_retrieval"
            return "manifest_retrieval"
        if preference == "classifier":
            if self.classifier_available():
                return "onnx_retrieval"
            if self.clip_available():
                return "clip_retrieval"
            return "manifest_retrieval"
        if preference == "hybrid":
            if self.classifier_available() and self.clip_available():
                return "hybrid_retrieval"
            if self.clip_available():
                return "clip_retrieval"
            if self.classifier_available():
                return "onnx_retrieval"
            return "manifest_retrieval"

        if self.classifier_available() and self.clip_available():
            return "hybrid_retrieval"
        if self.settings.prefers_classifier and self.classifier_available():
            return "onnx_retrieval"
        if self.clip_available():
            return "clip_retrieval"
        if self.classifier_available():
            return "onnx_retrieval"
        return "manifest_retrieval"

    def _load_concepts(self) -> list[FoodConcept]:
        retrieval_bank_path: Path = self.settings.retrieval_bank_path
        if retrieval_bank_path.exists():
            return self._load_concepts_from_retrieval_bank(retrieval_bank_path)

        manifest_path: Path = self.settings.manifest_path
        if manifest_path.exists():
            concepts = self._load_concepts_from_manifest(manifest_path)
            if concepts:
                return concepts

        return [
            FoodConcept(
                concept_id=f"default_{index:03d}",
                label=label,
                priority=index,
            )
            for index, label in enumerate(DEFAULT_LABELS, start=1)
        ]

    def _load_concepts_from_retrieval_bank(self, retrieval_bank_path: Path) -> list[FoodConcept]:
        data = json.loads(retrieval_bank_path.read_text(encoding="utf-8"))
        items = data.get("concepts", [])
        if not isinstance(items, list):
            return []

        concepts: list[FoodConcept] = []
        for index, item in enumerate(items, start=1):
            if not isinstance(item, dict):
                continue

            label = self._clean_text(item.get("canonical_label"))
            if not label:
                continue

            concepts.append(
                FoodConcept(
                    concept_id=self._clean_text(item.get("concept_id")) or f"concept_{index:03d}",
                    label=label,
                    english_name=self._clean_text(item.get("english_name")),
                    group=self._clean_text(item.get("group")) or "other",
                    group_zh=self._clean_text(item.get("group_zh")) or "其他食物",
                    aliases=tuple(self._clean_list(item.get("aliases"))),
                    search_keywords=tuple(self._clean_list(item.get("search_keywords"))),
                    clip_prompts_zh=tuple(self._clean_list(item.get("clip_prompts_zh"))),
                    clip_prompts_en=tuple(self._clean_list(item.get("clip_prompts_en"))),
                    priority=self._parse_priority(item.get("priority"), default=index),
                    notes=self._clean_text(item.get("notes")),
                )
            )

        return sorted(concepts, key=lambda concept: (concept.priority, concept.label))

    def _load_concepts_from_manifest(self, manifest_path: Path) -> list[FoodConcept]:
        data = json.loads(manifest_path.read_text(encoding="utf-8"))
        if not isinstance(data, list):
            return []

        concepts: list[FoodConcept] = []
        for index, item in enumerate(data, start=1):
            if not isinstance(item, dict):
                continue

            label = self._clean_text(item.get("label"))
            if not label:
                continue

            concepts.append(
                FoodConcept(
                    concept_id=self._clean_text(item.get("id")) or f"concept_{index:03d}",
                    label=label,
                    english_name=self._clean_text(item.get("english_name")),
                    group=self._clean_text(item.get("group")) or "other",
                    group_zh=self._clean_text(item.get("group_zh")) or "其他食物",
                    aliases=tuple(self._clean_list(item.get("aliases"))),
                    search_keywords=tuple(self._clean_list(item.get("search_keywords"))),
                    clip_prompts_zh=tuple(self._clean_list(item.get("clip_prompts_zh"))),
                    clip_prompts_en=tuple(self._clean_list(item.get("clip_prompts_en"))),
                    priority=self._parse_priority(item.get("priority"), default=index),
                    notes=self._clean_text(item.get("notes")),
                )
            )

        return sorted(concepts, key=lambda concept: (concept.priority, concept.label))

    def _load_labels(self) -> list[str]:
        labels_path: Path = self.settings.labels_path
        if not labels_path.exists():
            return [concept.label for concept in self._concepts]

        with labels_path.open("r", encoding="utf-8") as fp:
            data = json.load(fp)

        if isinstance(data, list):
            return [str(item).strip() for item in data if str(item).strip()]

        raise ValueError("labels.json must be a JSON array")

    def _build_concept_lookup(self, concepts: list[FoodConcept]) -> dict[str, FoodConcept]:
        lookup: dict[str, FoodConcept] = {}
        for concept in concepts:
            for term in concept.all_terms():
                normalized = self._normalize(term)
                if normalized and normalized not in lookup:
                    lookup[normalized] = concept
        return lookup

    def _load_clip_metadata(self) -> dict[str, Any]:
        metadata_path = self.settings.clip_text_bank_meta_path
        if not metadata_path.exists():
            return {}
        return json.loads(metadata_path.read_text(encoding="utf-8"))

    def _clean_text(self, value: object) -> str:
        return str(value or "").strip()

    def _clean_list(self, values: object) -> list[str]:
        if not isinstance(values, list):
            return []

        cleaned: list[str] = []
        for item in values:
            text = self._clean_text(item)
            if text and text not in cleaned:
                cleaned.append(text)
        return cleaned

    def _parse_priority(self, value: object, default: int) -> int:
        try:
            return int(value)
        except (TypeError, ValueError):
            return default

    def _normalize(self, value: str | None) -> str:
        if not value:
            return ""
        return "".join(ch for ch in value.strip().lower() if ch.isalnum() or "\u4e00" <= ch <= "\u9fff")
