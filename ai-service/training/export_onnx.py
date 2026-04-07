from __future__ import annotations

import argparse
import json
from pathlib import Path

import torch

from model_utils import build_resnet18


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export NutriMind classifier to ONNX")
    parser.add_argument("--checkpoint", type=Path, required=True, help="Path to best.pth")
    parser.add_argument("--labels", type=Path, required=True, help="Path to labels.json")
    parser.add_argument("--output", type=Path, required=True, help="Output ONNX file path")
    parser.add_argument("--image-size", type=int, default=224)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    class_names = json.loads(args.labels.read_text(encoding="utf-8"))
    if not isinstance(class_names, list) or not class_names:
        raise ValueError("labels.json must contain a non-empty list")

    checkpoint = torch.load(args.checkpoint, map_location="cpu")
    model = build_resnet18(num_classes=len(class_names), pretrained=False)
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    dummy_input = torch.randn(1, 3, args.image_size, args.image_size)
    torch.onnx.export(
        model,
        dummy_input,
        args.output,
        export_params=True,
        opset_version=17,
        do_constant_folding=True,
        input_names=["input"],
        output_names=["logits"],
        dynamic_axes={"input": {0: "batch_size"}, "logits": {0: "batch_size"}},
    )

    print(f"Exported ONNX model to {args.output}")


if __name__ == "__main__":
    main()
