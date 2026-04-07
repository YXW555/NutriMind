from __future__ import annotations

import hashlib
from collections import OrderedDict
from io import BytesIO
from pathlib import Path

import numpy as np
from PIL import Image

from .ai4food_official import predict_top_k
from .config import Settings
from .model_registry import ClipRuntime, FoodConcept, ModelRegistry
from .schemas import PredictionItem, PredictionResponse

try:
    import torch
except ImportError:  # pragma: no cover - optional dependency for CLIP mode
    torch = None


FILENAME_HINTS = {
    "rice": "米饭",
    "egg": "鸡蛋",
    "milk": "牛奶",
    "bread": "面包",
    "banana": "香蕉",
    "apple": "苹果",
    "orange": "橙子",
    "salad": "沙拉",
    "chicken": "鸡胸肉",
    "beef": "牛肉",
    "fish": "鱼",
    "shrimp": "虾",
    "broccoli": "西兰花",
    "yogurt": "酸奶",
    "noodle": "面条",
    "sandwich": "三明治",
    "tomato": "番茄",
    "potato": "土豆",
}


class PredictionService:
    def __init__(self, settings: Settings, registry: ModelRegistry) -> None:
        self.settings = settings
        self.registry = registry

    def health_payload(self) -> dict:
        return {
            "status": "ok",
            "backend_preference": self.settings.backend,
            "active_backend": self.registry.resolve_backend(),
            "model_bundle": self.settings.resolved_model_bundle,
            "model_version": self.settings.model_version,
            "model_runtime": self.settings.model_runtime,
            "classifier_adapter": self.settings.classifier_adapter,
            "model_path": str(self.settings.model_path),
            "labels_path": str(self.settings.labels_path),
            "model_metadata_path": str(self.settings.model_metadata_path),
            "retrieval_bank_path": str(self.settings.retrieval_bank_path),
            "clip_text_bank_path": str(self.settings.clip_text_bank_path),
            "image_size": self.settings.image_size,
            "label_count": len(self.registry.labels),
            "classifier_preferred": self.settings.prefers_classifier,
            "classifier_available": self.registry.classifier_available(),
            "classifier_runtime": self.registry.classifier_runtime_name() or "none",
            "clip_available": self.registry.clip_available(),
            "concept_count": self.registry.concept_count(),
        }

    def predict(self, image_bytes: bytes, filename: str, top_k: int) -> PredictionResponse:
        top_k = max(1, min(top_k or self.settings.top_k_default, 5))
        backend = self.registry.resolve_backend()

        if backend == "hybrid_retrieval":
            predictions = self._hybrid_predict(image_bytes, top_k)
        elif backend == "clip_retrieval":
            predictions = self._clip_retrieval_predict(image_bytes, top_k)
        elif backend in {"onnx_retrieval", "tensorflow_retrieval"}:
            predictions = self._classifier_predict(image_bytes, top_k)
        else:
            predictions = self._manifest_retrieval_predict(filename, image_bytes, top_k)

        return PredictionResponse(
            mode=backend,
            model_version=self.settings.model_version,
            predictions=predictions,
        )

    def _hybrid_predict(self, image_bytes: bytes, top_k: int) -> list[PredictionItem]:
        internal_top_k = max(top_k, self.settings.hybrid_internal_top_k)
        classifier_predictions = self._classifier_predict(image_bytes, internal_top_k)
        clip_predictions = self._clip_retrieval_predict(image_bytes, internal_top_k)

        if not classifier_predictions:
            return clip_predictions[:top_k]
        if not clip_predictions:
            return classifier_predictions[:top_k]

        if self._should_prioritize_classifier(classifier_predictions, clip_predictions):
            primary = self._annotate_predictions(
                self._select_classifier_predictions(classifier_predictions, clip_predictions),
                "分类模型与 CLIP 检索结果一致，优先采用分类结果",
            )
            secondary = clip_predictions
        else:
            primary = self._annotate_predictions(
                clip_predictions,
                "分类模型与 CLIP 检索结果不一致，已回退到 CLIP 检索",
            )
            secondary = self._filter_supported_classifier_predictions(classifier_predictions, clip_predictions)

        return self._merge_predictions(primary, secondary, top_k)

    def _classifier_predict(self, image_bytes: bytes, top_k: int) -> list[PredictionItem]:
        runtime = self.registry.classifier_runtime_name()
        if runtime == "onnx":
            probs = self._onnx_classifier_predict(image_bytes)
        elif runtime == "tensorflow":
            probs = self._tensorflow_classifier_predict(image_bytes)
        else:
            return self._manifest_retrieval_predict("uploaded-image.jpg", image_bytes, top_k)

        if probs.size == 0:
            return self._manifest_retrieval_predict("uploaded-image.jpg", image_bytes, top_k)

        indices = np.argsort(probs)[::-1][:top_k]

        predictions: list[PredictionItem] = []
        for index in indices:
            if int(index) >= len(self.registry.labels):
                continue

            label = self.registry.labels[int(index)]
            predictions.append(
                self._build_prediction_item(
                    label=label,
                    confidence=float(round(float(probs[index]), 4)),
                    source=f"{runtime}_classifier",
                    match_reason=self._classifier_match_reason(runtime),
                )
            )
        return predictions

    def _onnx_classifier_predict(self, image_bytes: bytes) -> np.ndarray:
        session = self.registry.get_classifier_session()
        if session is None:
            return np.asarray([], dtype="float32")

        image_tensor = self._preprocess_classifier(image_bytes)
        input_name = session.get_inputs()[0].name
        outputs = session.run(None, {input_name: image_tensor})
        return self._normalize_classifier_scores(outputs[0])

    def _tensorflow_classifier_predict(self, image_bytes: bytes) -> np.ndarray:
        adapter = (self.settings.classifier_adapter or "default").strip().lower()
        if adapter == "ai4food_official":
            return self._tensorflow_ai4food_predict(image_bytes)

        runtime = self.registry.get_tensorflow_runtime()
        if runtime is None:
            return np.asarray([], dtype="float32")

        image_tensor = self._preprocess_classifier(image_bytes)
        outputs = runtime.model.predict(image_tensor, verbose=0)
        return self._normalize_classifier_scores(outputs)

    def _tensorflow_ai4food_predict(self, image_bytes: bytes) -> np.ndarray:
        runtime = self.registry.get_ai4food_runtime()
        if runtime is None:
            return np.asarray([], dtype="float32")

        results = predict_top_k(
            runtime=runtime,
            image_bytes=image_bytes,
            image_size=self.settings.image_size,
            top_k=len(self.registry.labels),
            model_variant=self._ai4food_model_variant(),
        )
        if not results:
            return np.asarray([], dtype="float32")

        score_map = {label: score for label, score in results}
        return np.asarray([float(score_map.get(label, 0.0)) for label in self.registry.labels], dtype="float32")

    def _should_prioritize_classifier(
        self,
        classifier_predictions: list[PredictionItem],
        clip_predictions: list[PredictionItem],
    ) -> bool:
        top1 = classifier_predictions[0]
        top2_confidence = classifier_predictions[1].confidence if len(classifier_predictions) > 1 else 0.0
        margin = top1.confidence - top2_confidence
        clip_rank = self._find_prediction_rank(top1, clip_predictions)

        return (
            top1.confidence >= self.settings.hybrid_classifier_accept_confidence
            and margin >= self.settings.hybrid_classifier_margin_threshold
            and clip_rank is not None
            and clip_rank < self.settings.hybrid_clip_support_top_k
        )

    def _clip_retrieval_predict(self, image_bytes: bytes, top_k: int) -> list[PredictionItem]:
        runtime = self.registry.get_clip_runtime()
        if runtime is None or torch is None:
            return self._manifest_retrieval_predict("uploaded-image.jpg", image_bytes, top_k)

        image = Image.open(BytesIO(image_bytes)).convert("RGB")
        image_tensor = runtime.preprocess(image).unsqueeze(0).to(runtime.device)

        with torch.no_grad():
            image_features = runtime.model.encode_image(image_tensor)
            image_features = image_features / image_features.norm(dim=-1, keepdim=True).clamp_min(1e-12)

        image_vector = image_features.detach().cpu().numpy()[0].astype("float32")
        scores = runtime.embeddings @ image_vector
        probs = self._softmax(scores)
        indices = np.argsort(probs)[::-1][:top_k]

        predictions: list[PredictionItem] = []
        for index in indices:
            concept_id = runtime.concept_ids[int(index)] if int(index) < len(runtime.concept_ids) else ""
            label = runtime.labels[int(index)] if int(index) < len(runtime.labels) else ""
            concept = self.registry.find_concept_by_id(concept_id) or self.registry.find_concept(label)
            reason = self._build_clip_reason(runtime, concept)
            predictions.append(
                self._build_prediction_item(
                    label=label,
                    confidence=float(round(float(probs[index]), 4)),
                    source="clip_text_bank",
                    match_reason=reason,
                    concept=concept,
                )
            )
        return predictions

    def _manifest_retrieval_predict(self, filename: str, image_bytes: bytes, top_k: int) -> list[PredictionItem]:
        normalized_name = Path(filename or "uploaded-image").name.lower()
        candidates: OrderedDict[str, PredictionItem] = OrderedDict()

        for keyword, label in FILENAME_HINTS.items():
            if keyword in normalized_name:
                self._put_candidate(
                    candidates,
                    label=label,
                    confidence=0.94 - 0.05 * len(candidates),
                    source="filename_hint",
                    match_reason=f"文件名包含关键词 {keyword}",
                )
            if len(candidates) >= top_k:
                break

        if len(candidates) < top_k:
            for label, reason in self._visual_hint_labels(image_bytes):
                self._put_candidate(
                    candidates,
                    label=label,
                    confidence=0.80 - 0.05 * len(candidates),
                    source="visual_heuristic",
                    match_reason=reason,
                )
                if len(candidates) >= top_k:
                    break

        if len(candidates) < top_k:
            digest = hashlib.sha256(image_bytes).hexdigest()
            concept_pool = self.registry.ordered_concepts()[: max(self.settings.retrieval_pool_size, top_k)]
            if concept_pool:
                offset = int(digest[:8], 16) % len(concept_pool)
                for index in range(len(concept_pool)):
                    concept = concept_pool[(offset + index) % len(concept_pool)]
                    self._put_candidate(
                        candidates,
                        label=concept.label,
                        confidence=max(0.38, 0.68 - 0.04 * len(candidates)),
                        source="manifest_bank",
                        match_reason="类别清单兜底召回",
                    )
                    if len(candidates) >= top_k:
                        break

        return list(candidates.values())[:top_k]

    def _preprocess_classifier(self, image_bytes: bytes) -> np.ndarray:
        image = Image.open(BytesIO(image_bytes)).convert("RGB")
        image = image.resize((self.settings.image_size, self.settings.image_size))
        image_array = np.asarray(image).astype("float32")

        preprocess_mode = (self.settings.classifier_preprocess or "imagenet").strip().lower()
        if preprocess_mode == "imagenet":
            image_array = image_array / 255.0
            mean = np.array([0.485, 0.456, 0.406], dtype="float32")
            std = np.array([0.229, 0.224, 0.225], dtype="float32")
            image_array = (image_array - mean) / std
        elif preprocess_mode == "zero_one":
            image_array = image_array / 255.0
        elif preprocess_mode in {"identity", "none", "keras_efficientnet_v2"}:
            pass
        else:
            raise ValueError(f"unsupported classifier preprocess mode: {self.settings.classifier_preprocess}")

        layout = (self.settings.classifier_input_layout or "nchw").strip().lower()
        if layout == "nchw":
            image_array = np.transpose(image_array, (2, 0, 1))
        elif layout != "nhwc":
            raise ValueError(f"unsupported classifier input layout: {self.settings.classifier_input_layout}")

        return np.expand_dims(image_array, axis=0)

    def _visual_hint_labels(self, image_bytes: bytes) -> list[tuple[str, str]]:
        image = Image.open(BytesIO(image_bytes)).convert("RGB")
        image.thumbnail((96, 96))
        image_array = np.asarray(image).astype("float32") / 255.0
        mean_rgb = image_array.mean(axis=(0, 1))
        red, green, blue = mean_rgb.tolist()
        brightness = float(mean_rgb.mean())

        labels: list[tuple[str, str]] = []
        if green > red + 0.04 and green > blue + 0.04:
            labels.extend([
                ("西兰花", "画面整体偏绿色，优先召回蔬菜类食物"),
                ("清炒时蔬", "颜色分布偏绿，像轻加工蔬菜"),
                ("黄瓜", "绿色区域明显，补充召回黄瓜类候选"),
            ])
        if red > green + 0.04 and red > blue + 0.04:
            labels.extend([
                ("番茄", "画面整体偏红，补充召回番茄类食物"),
                ("西红柿炒蛋", "高红色占比，补充召回常见番茄菜品"),
            ])
        if brightness > 0.72 and abs(red - green) < 0.08 and blue < 0.62:
            labels.extend([
                ("米饭", "整体较亮且颜色均匀，补充召回主食类候选"),
                ("面包", "高亮暖色区域较多，补充召回烘焙主食"),
                ("馒头", "颜色较浅，补充召回蒸制主食"),
            ])
        if red > 0.60 and green > 0.55 and blue < 0.42:
            labels.extend([
                ("香蕉", "黄绿色占比较高，补充召回香蕉候选"),
                ("玉米", "暖黄色区域明显，补充召回玉米候选"),
            ])
        if brightness < 0.46 and red > blue + 0.05:
            labels.extend([
                ("牛肉", "低亮度暖色区域较多，补充召回肉类候选"),
                ("鸡胸肉", "纹理偏暖且亮度较低，补充召回鸡肉候选"),
            ])
        if not labels:
            labels.extend([
                ("沙拉", "默认加入轻食类候选，方便人工确认"),
                ("米饭", "默认加入主食类候选，方便人工确认"),
            ])
        return labels

    def _put_candidate(
        self,
        candidates: OrderedDict[str, PredictionItem],
        label: str,
        confidence: float,
        source: str,
        match_reason: str,
    ) -> None:
        item = self._build_prediction_item(
            label=label,
            confidence=confidence,
            source=source,
            match_reason=match_reason,
        )
        key = item.canonical_label or item.label
        existing = candidates.get(key)
        if existing is None or existing.confidence < item.confidence:
            candidates[key] = item

    def _merge_predictions(
        self,
        primary: list[PredictionItem],
        secondary: list[PredictionItem],
        top_k: int,
    ) -> list[PredictionItem]:
        merged: OrderedDict[str, PredictionItem] = OrderedDict()

        for item in [*primary, *secondary]:
            key = self._prediction_key(item)
            current = merged.get(key)
            if current is None or current.confidence < item.confidence:
                merged[key] = item

        return list(merged.values())[:top_k]

    def _annotate_predictions(self, predictions: list[PredictionItem], reason_suffix: str) -> list[PredictionItem]:
        annotated: list[PredictionItem] = []
        for item in predictions:
            reason = item.match_reason or ""
            combined_reason = f"{reason}；{reason_suffix}" if reason else reason_suffix
            annotated.append(item.model_copy(update={"match_reason": combined_reason}))
        return annotated

    def _filter_supported_classifier_predictions(
        self,
        classifier_predictions: list[PredictionItem],
        clip_predictions: list[PredictionItem],
    ) -> list[PredictionItem]:
        supported: list[PredictionItem] = []
        for item in classifier_predictions:
            clip_rank = self._find_prediction_rank(item, clip_predictions)
            if clip_rank is None or clip_rank >= self.settings.hybrid_clip_support_top_k:
                continue

            supported.append(
                item.model_copy(
                    update={
                        "match_reason": (
                            f"{item.match_reason}；分类模型结果被 CLIP 检索支持，作为补充候选保留"
                            if item.match_reason
                            else "分类模型结果被 CLIP 检索支持，作为补充候选保留"
                        )
                    }
                )
            )
        return supported

    def _select_classifier_predictions(
        self,
        classifier_predictions: list[PredictionItem],
        clip_predictions: list[PredictionItem],
    ) -> list[PredictionItem]:
        if not classifier_predictions:
            return []

        selected = [classifier_predictions[0]]
        for item in classifier_predictions[1:]:
            clip_rank = self._find_prediction_rank(item, clip_predictions)
            if clip_rank is None or clip_rank >= self.settings.hybrid_clip_support_top_k:
                continue
            selected.append(item)
        return selected

    def _find_prediction_rank(self, target: PredictionItem, predictions: list[PredictionItem]) -> int | None:
        target_key = self._prediction_key(target)
        for index, item in enumerate(predictions):
            if self._prediction_key(item) == target_key:
                return index
        return None

    def _prediction_key(self, item: PredictionItem) -> str:
        return str(item.canonical_label or item.label).strip().lower()

    def _build_prediction_item(
        self,
        label: str,
        confidence: float,
        source: str,
        match_reason: str,
        concept: FoodConcept | None = None,
    ) -> PredictionItem:
        resolved_concept = concept or self.registry.find_concept(label)
        canonical_label = resolved_concept.label if resolved_concept else label.strip()
        aliases = list(resolved_concept.aliases) if resolved_concept else []
        search_keywords = list(resolved_concept.search_keywords) if resolved_concept else []
        return PredictionItem(
            label=canonical_label,
            canonical_label=canonical_label,
            confidence=float(round(max(0.0, min(confidence, 1.0)), 4)),
            source=source,
            match_reason=match_reason,
            aliases=aliases,
            search_keywords=search_keywords,
        )

    def _build_clip_reason(self, runtime: ClipRuntime, concept: FoodConcept | None) -> str:
        model_name = str(runtime.metadata.get("model_name") or self.settings.clip_model_name).strip()
        if concept and concept.group_zh:
            return f"CLIP 图文向量相似度召回，命中{concept.group_zh}候选（{model_name}）"
        return f"CLIP 图文向量相似度召回（{model_name}）"

    @staticmethod
    def _softmax(logits: np.ndarray) -> np.ndarray:
        shifted = logits - np.max(logits)
        exp = np.exp(shifted)
        return exp / np.sum(exp)

    def _normalize_classifier_scores(self, outputs: object) -> np.ndarray:
        scores = self._extract_classifier_scores(outputs)
        if scores.size == 0:
            return scores

        if np.all(scores >= 0.0) and np.all(scores <= 1.0):
            score_sum = float(scores.sum())
            if 0.98 <= score_sum <= 1.02:
                return scores

        return self._softmax(scores)

    @staticmethod
    def _extract_classifier_scores(outputs: object) -> np.ndarray:
        value = outputs
        if isinstance(value, dict):
            value = next(iter(value.values()), [])
        if isinstance(value, (list, tuple)):
            value = value[0] if value else []

        scores = np.asarray(value, dtype="float32")
        if scores.ndim == 0:
            return np.asarray([], dtype="float32")
        if scores.ndim == 1:
            return scores
        return scores.reshape(scores.shape[0], -1)[0]

    @staticmethod
    def _classifier_match_reason(runtime: str) -> str:
        if runtime == "tensorflow":
            return "AI4Food 官方推理流程命中高置信候选"
        return "分类模型命中高置信候选"

    def _ai4food_model_variant(self) -> str:
        bundle_name = (self.settings.resolved_model_bundle or "").strip().lower()
        if "category" in bundle_name:
            return "category"
        if "subcategory" in bundle_name:
            return "subcategory"
        if "product" in bundle_name:
            return "product"
        return ""
