from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from typing import Any

import numpy as np
from PIL import Image

try:
    import tensorflow as tf
except ImportError:  # pragma: no cover - optional dependency
    tf = None


@dataclass
class AI4FoodOfficialRuntime:
    model: Any
    class_names: list[str]


def load_ai4food_runtime(model_path: Path, labels_path: Path) -> AI4FoodOfficialRuntime | None:
    if tf is None or not model_path.exists() or not labels_path.exists():
        return None

    model = tf.keras.models.load_model(
        model_path,
        custom_objects={"DepthwiseConv2D": DepthwiseConv2DCompat},
        compile=False,
    )
    class_names = _load_class_names(labels_path)
    return AI4FoodOfficialRuntime(model=model, class_names=class_names)


class DepthwiseConv2DCompat(tf.keras.layers.DepthwiseConv2D if tf is not None else object):
    @classmethod
    def from_config(cls, config: dict[str, Any]) -> Any:
        # Older exported AI4Food HDF5 models may carry `groups=1`,
        # which newer Keras versions no longer accept for DepthwiseConv2D.
        config = dict(config)
        config.pop("groups", None)
        return cls(**config)


def predict_top_k(
    runtime: AI4FoodOfficialRuntime,
    image_bytes: bytes,
    image_size: int,
    top_k: int,
    model_variant: str = "",
) -> list[tuple[str, float]]:
    image_array = _preprocess_image(image_bytes, image_size)
    predictions = runtime.model.predict(image_array, verbose=0)
    scores = np.asarray(predictions, dtype="float32").reshape(-1)
    if scores.size == 0:
        return []

    scores = _normalize_scores(scores)
    pairs = list(enumerate(scores))
    pairs.sort(key=lambda item: float(item[1]), reverse=True)

    results: list[tuple[str, float]] = []
    for index, score in pairs[:top_k]:
        if index >= len(runtime.class_names):
            continue
        label = runtime.class_names[index]
        if label:
            results.append((_normalize_ai4food_label(label, model_variant), float(score)))
    return results


def _preprocess_image(image_bytes: bytes, image_size: int) -> np.ndarray:
    image = Image.open(BytesIO(image_bytes)).convert("RGB")
    image = image.resize((image_size, image_size))
    image_array = np.asarray(image).astype("float32") / 255.0
    return np.expand_dims(image_array, axis=0)


def _load_class_names(labels_path: Path) -> list[str]:
    if labels_path.suffix.lower() == ".json":
        import json

        data = json.loads(labels_path.read_text(encoding="utf-8"))
        if not isinstance(data, list):
            raise ValueError("AI4Food labels.json must be a JSON array")
        return [str(item).strip() for item in data if str(item).strip()]

    lines = labels_path.read_text(encoding="utf-8").splitlines()
    return [line.strip() for line in lines if line.strip()]


def _normalize_scores(scores: np.ndarray) -> np.ndarray:
    if np.all(scores >= 0.0) and np.all(scores <= 1.0):
        total = float(scores.sum())
        if 0.98 <= total <= 1.02:
            return scores

    shifted = scores - np.max(scores)
    exp = np.exp(shifted)
    return exp / np.sum(exp)


def _normalize_ai4food_label(label: str, model_variant: str) -> str:
    normalized = label.strip()
    if model_variant == "category" and normalized == "alcohol":
        return "drinks"
    if model_variant == "category" and normalized == "nuts":
        return "fruits"
    return normalized
