from __future__ import annotations

import hashlib
import json
import re
from collections import OrderedDict
from io import BytesIO
from pathlib import Path

import numpy as np
from PIL import Image
from pydantic import BaseModel, ConfigDict, Field

from .config import Settings
from .model_registry import ClipRuntime, FoodConcept, LlavaRuntime, ModelRegistry
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

JSON_BLOCK_PATTERN = re.compile(r"```(?:json)?\s*(\{.*?\}|\[.*?\])\s*```", re.IGNORECASE | re.DOTALL)


class LlavaDraftPrediction(BaseModel):
    model_config = ConfigDict(extra="ignore")

    label: str | None = None
    canonical_label: str | None = None
    confidence: float | int | str | None = None
    match_reason: str | None = None
    source: str | None = None
    aliases: list[str] | str | None = None
    search_keywords: list[str] | str | None = None


class LlavaDraftEnvelope(BaseModel):
    model_config = ConfigDict(extra="ignore")

    predictions: list[LlavaDraftPrediction] = Field(default_factory=list)


class PredictionService:
    def __init__(self, settings: Settings, registry: ModelRegistry) -> None:
        self.settings = settings
        self.registry = registry

    def health_payload(self) -> dict:
        llava_runtime = self.registry.get_llava_runtime()
        return {
            "status": "ok",
            "backend_preference": self.settings.backend,
            "active_backend": self.registry.resolve_backend(),
            "model_bundle": self.settings.resolved_model_bundle,
            "model_version": self.settings.model_version,
            "llava_model_version": self.settings.llava_model_version,
            "model_path": str(self.settings.model_path),
            "labels_path": str(self.settings.labels_path),
            "model_metadata_path": str(self.settings.model_metadata_path),
            "retrieval_bank_path": str(self.settings.retrieval_bank_path),
            "clip_text_bank_path": str(self.settings.clip_text_bank_path),
            "image_size": self.settings.image_size,
            "label_count": len(self.registry.labels),
            "classifier_preferred": self.settings.prefers_classifier,
            "classifier_available": self.registry.classifier_available(),
            "clip_available": self.registry.clip_available(),
            "llava_available": llava_runtime is not None,
            "llava_model_id": "" if llava_runtime is None else llava_runtime.model_id,
            "concept_count": self.registry.concept_count(),
        }

    def predict(self, image_bytes: bytes, filename: str, top_k: int) -> PredictionResponse:
        top_k = max(1, min(top_k or self.settings.top_k_default, 5))
        preferred_backend = self.registry.resolve_backend()
        backend, predictions = self._predict_with_fallback(preferred_backend, image_bytes, filename, top_k)

        return PredictionResponse(
            mode=backend,
            model_version=self.settings.model_version_for_backend(backend),
            predictions=predictions,
        )

    def _predict_with_fallback(
        self,
        preferred_backend: str,
        image_bytes: bytes,
        filename: str,
        top_k: int,
    ) -> tuple[str, list[PredictionItem]]:
        last_error: Exception | None = None

        for backend in self._build_backend_chain(preferred_backend):
            try:
                predictions = self._dispatch_backend(backend, image_bytes, filename, top_k)
            except Exception as exc:  # pragma: no cover - runtime-only fallback protection
                last_error = exc
                self._clear_cuda_cache()
                continue

            if predictions:
                return backend, predictions[:top_k]

        if last_error is not None:
            raise last_error
        raise RuntimeError("python inference returned no predictions")

    def _build_backend_chain(self, preferred_backend: str) -> list[str]:
        chain_by_backend = {
            "llava_next_retrieval": [
                "llava_next_retrieval",
                "hybrid_retrieval",
                "clip_retrieval",
                "onnx_retrieval",
                "manifest_retrieval",
            ],
            "hybrid_retrieval": [
                "hybrid_retrieval",
                "clip_retrieval",
                "onnx_retrieval",
                "manifest_retrieval",
            ],
            "clip_retrieval": [
                "clip_retrieval",
                "onnx_retrieval",
                "manifest_retrieval",
            ],
            "onnx_retrieval": [
                "onnx_retrieval",
                "manifest_retrieval",
            ],
            "manifest_retrieval": [
                "manifest_retrieval",
            ],
        }

        ordered = chain_by_backend.get(preferred_backend, ["manifest_retrieval"])
        deduplicated: list[str] = []
        for backend in ordered:
            if backend not in deduplicated:
                deduplicated.append(backend)
        return deduplicated

    def _dispatch_backend(
        self,
        backend: str,
        image_bytes: bytes,
        filename: str,
        top_k: int,
    ) -> list[PredictionItem]:
        if backend == "llava_next_retrieval":
            return self._llava_next_predict(image_bytes, filename, top_k)
        if backend == "hybrid_retrieval":
            return self._hybrid_predict(image_bytes, top_k)
        if backend == "clip_retrieval":
            return self._clip_retrieval_predict(image_bytes, top_k)
        if backend == "onnx_retrieval":
            return self._classifier_predict(image_bytes, top_k)
        return self._manifest_retrieval_predict(filename, image_bytes, top_k)

    def _llava_next_predict(self, image_bytes: bytes, filename: str, top_k: int) -> list[PredictionItem]:
        runtime = self.registry.get_llava_runtime()
        if runtime is None:
            return []

        image = self._load_rgb_image(image_bytes)
        prompt = self._build_llava_prompt(filename, image_bytes, top_k)
        raw_output = self._run_llava_generation(runtime, image, prompt)
        predictions = self._normalize_llava_output(raw_output)
        if not predictions:
            raise ValueError("llava_next returned no valid predictions")
        return predictions[:top_k]

    def _run_llava_generation(self, runtime: LlavaRuntime, image: Image.Image, prompt: str) -> str:
        processor = runtime.processor
        conversation = [
            {
                "role": "user",
                "content": [
                    {"type": "image"},
                    {"type": "text", "text": prompt},
                ],
            }
        ]

        if hasattr(processor, "apply_chat_template"):
            rendered_prompt = processor.apply_chat_template(
                conversation,
                add_generation_prompt=True,
                tokenize=False,
            )
            model_inputs = processor(images=image, text=rendered_prompt, return_tensors="pt")
        else:
            model_inputs = processor(images=image, text=prompt, return_tensors="pt")

        prepared_inputs: dict[str, object] = {}
        for key, value in model_inputs.items():
            prepared_inputs[key] = value.to(runtime.device) if hasattr(value, "to") else value

        generate_kwargs: dict[str, object] = {
            "max_new_tokens": self.settings.llava_max_new_tokens,
            "do_sample": self.settings.llava_do_sample,
        }
        if self.settings.llava_do_sample:
            generate_kwargs["temperature"] = self.settings.llava_temperature
            generate_kwargs["top_p"] = self.settings.llava_top_p

        tokenizer = getattr(processor, "tokenizer", None)
        if tokenizer is not None and getattr(tokenizer, "eos_token_id", None) is not None:
            generate_kwargs["pad_token_id"] = tokenizer.eos_token_id

        with torch.inference_mode():
            output_ids = runtime.model.generate(**prepared_inputs, **generate_kwargs)

        prompt_length = prepared_inputs["input_ids"].shape[1] if "input_ids" in prepared_inputs else 0
        generated_ids = output_ids[:, prompt_length:] if prompt_length else output_ids
        decoded = processor.batch_decode(
            generated_ids,
            skip_special_tokens=True,
            clean_up_tokenization_spaces=True,
        )
        if not decoded:
            raise ValueError("llava_next generation returned empty text")
        return decoded[0].strip()

    def _build_llava_prompt(self, filename: str, image_bytes: bytes, top_k: int) -> str:
        catalog_limit = max(1, min(self.settings.llava_catalog_limit, self.registry.concept_count()))
        catalog = self.registry.ordered_concepts()[:catalog_limit]
        heuristic_hints = self._manifest_retrieval_predict(filename, image_bytes, min(top_k + 1, 4))
        hint_text = ", ".join(item.canonical_label for item in heuristic_hints) or "none"

        catalog_lines: list[str] = []
        for concept in catalog:
            aliases = ", ".join(concept.aliases[: self.settings.llava_alias_limit]) or "none"
            keywords = ", ".join(concept.search_keywords[: self.settings.llava_alias_limit]) or concept.label
            group_name = concept.group_zh or concept.group or "other"
            catalog_lines.append(
                f"- canonical_label: {concept.label}; aliases: {aliases}; search_keywords: {keywords}; group: {group_name}"
            )

        schema_example = json.dumps(
            {
                "predictions": [
                    {
                        "label": catalog[0].label if catalog else "米饭",
                        "canonical_label": catalog[0].label if catalog else "米饭",
                        "confidence": 0.82,
                        "match_reason": "one short sentence grounded in the image",
                        "source": "llava_next",
                        "aliases": list(catalog[0].aliases) if catalog and catalog[0].aliases else ["白米饭"],
                        "search_keywords": (
                            list(catalog[0].search_keywords)
                            if catalog and catalog[0].search_keywords
                            else ["米饭"]
                        ),
                    }
                ]
            },
            ensure_ascii=False,
        )

        return (
            "You are NutriMind's multimodal food retrieval backend.\n"
            f"Inspect the image and return at most {top_k} predictions.\n"
            "You must select concepts only from the allowed catalog below.\n"
            "Return JSON only. Do not include markdown, commentary, or code fences.\n"
            "Every prediction object must contain exactly these keys: "
            "label, canonical_label, confidence, match_reason, source, aliases, search_keywords.\n"
            "Rules:\n"
            '- label and canonical_label must exactly match one catalog canonical_label.\n'
            '- confidence must be a number between 0 and 1.\n'
            '- source must be "llava_next".\n'
            "- aliases and search_keywords must be JSON string arrays copied from the chosen catalog item.\n"
            "- If the image is uncertain, still choose the closest allowed catalog concepts instead of inventing a new one.\n"
            "- Keep match_reason to one short sentence.\n"
            f"Weak filename hint: {Path(filename or 'uploaded-image.jpg').name}\n"
            f"Weak heuristic hints from fallback retrieval: {hint_text}\n"
            "Allowed catalog:\n"
            f"{chr(10).join(catalog_lines)}\n"
            f"Return this JSON shape exactly: {schema_example}"
        )

    def _normalize_llava_output(self, raw_output: str) -> list[PredictionItem]:
        payload = self._extract_json_payload(raw_output)
        if isinstance(payload, list):
            payload = {"predictions": payload}
        elif isinstance(payload, dict) and "predictions" not in payload:
            for alternate_key in ("items", "results", "candidates"):
                if isinstance(payload.get(alternate_key), list):
                    payload = {"predictions": payload[alternate_key]}
                    break

        envelope = LlavaDraftEnvelope.model_validate(payload)
        normalized: list[PredictionItem] = []
        for index, item in enumerate(envelope.predictions):
            normalized_item = self._normalize_llava_prediction(item, index)
            if normalized_item is not None:
                normalized.append(normalized_item)

        return self._deduplicate_predictions(normalized)

    def _normalize_llava_prediction(
        self,
        prediction: LlavaDraftPrediction,
        index: int,
    ) -> PredictionItem | None:
        candidate_terms = [
            prediction.canonical_label,
            prediction.label,
            *self._coerce_string_list(prediction.aliases),
            *self._coerce_string_list(prediction.search_keywords),
        ]

        concept = None
        for term in candidate_terms:
            concept = self.registry.find_concept(term)
            if concept is not None:
                break

        if concept is None:
            return None

        confidence = self._normalize_confidence(prediction.confidence, default=max(0.45, 0.8 - index * 0.1))
        aliases = list(concept.aliases) or self._coerce_string_list(prediction.aliases)
        search_keywords = list(concept.search_keywords) or self._coerce_string_list(prediction.search_keywords)
        if not search_keywords:
            search_keywords = [concept.label]

        reason = (prediction.match_reason or "").strip()
        if not reason:
            reason = f"LLaVA-NeXT matched the catalog concept {concept.label}"

        return PredictionItem(
            label=concept.label,
            canonical_label=concept.label,
            confidence=confidence,
            source="llava_next",
            match_reason=reason,
            aliases=self._normalize_terms(aliases),
            search_keywords=self._normalize_terms(search_keywords),
        )

    def _extract_json_payload(self, raw_output: str) -> dict | list:
        cleaned = raw_output.strip()
        if not cleaned:
            raise ValueError("llava_next returned empty output")

        direct = self._try_json_loads(cleaned)
        if direct is not None:
            return direct

        fenced_match = JSON_BLOCK_PATTERN.search(cleaned)
        if fenced_match:
            fenced_payload = self._try_json_loads(fenced_match.group(1).strip())
            if fenced_payload is not None:
                return fenced_payload

        for opener, closer in (("{", "}"), ("[", "]")):
            start = cleaned.find(opener)
            end = cleaned.rfind(closer)
            if start == -1 or end == -1 or end <= start:
                continue
            candidate = cleaned[start : end + 1]
            parsed = self._try_json_loads(candidate)
            if parsed is not None:
                return parsed

        raise ValueError("failed to extract JSON from llava_next output")

    def _try_json_loads(self, value: str) -> dict | list | None:
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError:
            return None
        return parsed if isinstance(parsed, (dict, list)) else None

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
                "Classifier and CLIP agreed on the dominant concept, so the classifier result was preferred.",
            )
            secondary = clip_predictions
        else:
            primary = self._annotate_predictions(
                clip_predictions,
                "Classifier and CLIP disagreed, so the service fell back to CLIP retrieval.",
            )
            secondary = self._filter_supported_classifier_predictions(classifier_predictions, clip_predictions)

        return self._merge_predictions(primary, secondary, top_k)

    def _classifier_predict(self, image_bytes: bytes, top_k: int) -> list[PredictionItem]:
        session = self.registry.get_classifier_session()
        if session is None:
            return []

        image_tensor = self._preprocess_classifier(image_bytes)
        input_name = session.get_inputs()[0].name
        outputs = session.run(None, {input_name: image_tensor})
        logits = outputs[0][0]
        probs = self._softmax(logits)
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
                    source="onnx_classifier",
                    match_reason="Top confidence candidate from the ONNX classifier.",
                )
            )
        return predictions

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
            return []

        image = self._load_rgb_image(image_bytes)
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
        image = self._load_rgb_image(image_bytes)
        image = image.resize((self.settings.image_size, self.settings.image_size))
        image_array = np.asarray(image).astype("float32") / 255.0
        mean = np.array([0.485, 0.456, 0.406], dtype="float32")
        std = np.array([0.229, 0.224, 0.225], dtype="float32")
        image_array = (image_array - mean) / std
        image_array = np.transpose(image_array, (2, 0, 1))
        return np.expand_dims(image_array, axis=0)

    def _visual_hint_labels(self, image_bytes: bytes) -> list[tuple[str, str]]:
        image = self._load_rgb_image(image_bytes)
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
            combined_reason = f"{reason} {reason_suffix}".strip() if reason else reason_suffix
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
                            f"{item.match_reason} Confirmed by CLIP retrieval and kept as a supporting candidate."
                            if item.match_reason
                            else "Confirmed by CLIP retrieval and kept as a supporting candidate."
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
        if not search_keywords and canonical_label:
            search_keywords = [canonical_label]
        return PredictionItem(
            label=canonical_label,
            canonical_label=canonical_label,
            confidence=float(round(max(0.0, min(confidence, 1.0)), 4)),
            source=source,
            match_reason=match_reason,
            aliases=self._normalize_terms(aliases),
            search_keywords=self._normalize_terms(search_keywords),
        )

    def _build_clip_reason(self, runtime: ClipRuntime, concept: FoodConcept | None) -> str:
        model_name = str(runtime.metadata.get("model_name") or self.settings.clip_model_name).strip()
        if concept and concept.group_zh:
            return f"CLIP image-text retrieval matched the {concept.group_zh} group ({model_name})."
        return f"CLIP image-text retrieval matched this concept ({model_name})."

    def _normalize_terms(self, terms: list[str]) -> list[str]:
        normalized: list[str] = []
        for term in terms:
            text = str(term).strip()
            if text and text not in normalized:
                normalized.append(text)
        return normalized

    def _coerce_string_list(self, value: list[str] | str | None) -> list[str]:
        if value is None:
            return []
        if isinstance(value, list):
            return self._normalize_terms([str(item) for item in value])
        if isinstance(value, str):
            return self._normalize_terms([value])
        return self._normalize_terms([str(value)])

    def _normalize_confidence(self, value: float | int | str | None, default: float) -> float:
        try:
            confidence = float(value)
        except (TypeError, ValueError):
            confidence = default
        return float(round(max(0.0, min(confidence, 1.0)), 4))

    def _deduplicate_predictions(self, predictions: list[PredictionItem]) -> list[PredictionItem]:
        merged: OrderedDict[str, PredictionItem] = OrderedDict()
        for item in predictions:
            key = self._prediction_key(item)
            current = merged.get(key)
            if current is None or item.confidence > current.confidence:
                merged[key] = item
        return list(merged.values())

    def _load_rgb_image(self, image_bytes: bytes) -> Image.Image:
        return Image.open(BytesIO(image_bytes)).convert("RGB")

    def _clear_cuda_cache(self) -> None:
        if torch is not None and torch.cuda.is_available():  # pragma: no cover - runtime only
            torch.cuda.empty_cache()

    @staticmethod
    def _softmax(logits: np.ndarray) -> np.ndarray:
        shifted = logits - np.max(logits)
        exp = np.exp(shifted)
        return exp / np.sum(exp)
