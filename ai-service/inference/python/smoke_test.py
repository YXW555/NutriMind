from __future__ import annotations

import argparse
import json
from pathlib import Path

from app.config import get_settings
from app.inference import PredictionService
from app.model_registry import ModelRegistry


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run a local smoke test against the current NutriMind inference config")
    parser.add_argument("--image", type=Path, required=True, help="Path to a local image file")
    parser.add_argument("--top-k", type=int, default=3, help="Number of predictions to return")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not args.image.exists():
        raise FileNotFoundError(f"image does not exist: {args.image}")

    settings = get_settings()
    registry = ModelRegistry(settings)
    service = PredictionService(settings, registry)

    with args.image.open("rb") as fp:
        result = service.predict(fp.read(), args.image.name, args.top_k)

    output = {
        "image": str(args.image.resolve()),
        "active_backend": registry.resolve_backend(),
        "classifier_runtime": registry.classifier_runtime_name(),
        "model_bundle": settings.resolved_model_bundle,
        "model_version": settings.model_version,
        "image_size": settings.image_size,
        "predictions": [item.model_dump() for item in result.predictions],
    }
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
