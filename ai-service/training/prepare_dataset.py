from __future__ import annotations

import argparse
import json
import random
import shutil
from pathlib import Path


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Split raw NutriMind food images into train/val/test")
    parser.add_argument("--raw-root", type=Path, required=True, help="Raw image root, for example datasets/food_cls/raw")
    parser.add_argument("--output-root", type=Path, required=True, help="Output dataset root containing train/ val/ test/")
    parser.add_argument("--manifest", type=Path, required=True, help="Path to category_manifest.json")
    parser.add_argument("--train-ratio", type=float, default=0.7)
    parser.add_argument("--val-ratio", type=float, default=0.2)
    parser.add_argument("--test-ratio", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--mode", choices=["copy", "move"], default="copy")
    parser.add_argument("--min-images", type=int, default=5, help="Warn if a class has fewer than this number of images")
    return parser.parse_args()


def load_labels(manifest_path: Path) -> list[str]:
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("category manifest must be a JSON array")
    labels = [str(item.get("label", "")).strip() for item in data if isinstance(item, dict)]
    labels = [label for label in labels if label]
    if not labels:
        raise ValueError("category manifest does not contain any valid labels")
    return labels


def list_images(class_dir: Path) -> list[Path]:
    return [
        path for path in class_dir.iterdir()
        if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS
    ]


def split_counts(total: int, train_ratio: float, val_ratio: float, test_ratio: float) -> tuple[int, int, int]:
    if total <= 0:
        return 0, 0, 0

    train_count = int(total * train_ratio)
    val_count = int(total * val_ratio)
    test_count = total - train_count - val_count

    if total >= 3:
        if train_count == 0:
            train_count = 1
        if val_count == 0:
            val_count = 1
        test_count = total - train_count - val_count
        if test_count <= 0:
            test_count = 1
            if train_count > val_count:
                train_count -= 1
            else:
                val_count -= 1

    return train_count, val_count, test_count


def transfer_file(source: Path, target: Path, mode: str) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    if mode == "copy":
        shutil.copy2(source, target)
    else:
        shutil.move(str(source), str(target))


def main() -> None:
    args = parse_args()
    ratio_sum = round(args.train_ratio + args.val_ratio + args.test_ratio, 6)
    if abs(ratio_sum - 1.0) > 0.0001:
        raise ValueError("train/val/test ratios must sum to 1.0")

    labels = load_labels(args.manifest)
    random.seed(args.seed)

    summary: dict[str, dict[str, int]] = {}
    warnings: list[str] = []

    for split in ("train", "val", "test"):
        (args.output_root / split).mkdir(parents=True, exist_ok=True)

    for label in labels:
        class_dir = args.raw_root / label
        if not class_dir.exists():
            warnings.append(f"Missing raw class directory: {class_dir}")
            summary[label] = {"train": 0, "val": 0, "test": 0, "total": 0}
            continue

        images = list_images(class_dir)
        random.shuffle(images)
        total = len(images)

        if total < args.min_images:
            warnings.append(f"Class {label} only has {total} images")

        train_count, val_count, test_count = split_counts(
            total,
            args.train_ratio,
            args.val_ratio,
            args.test_ratio,
        )

        train_images = images[:train_count]
        val_images = images[train_count:train_count + val_count]
        test_images = images[train_count + val_count:train_count + val_count + test_count]

        for split_name, split_images in (
            ("train", train_images),
            ("val", val_images),
            ("test", test_images),
        ):
            for image_path in split_images:
                target_path = args.output_root / split_name / label / image_path.name
                transfer_file(image_path, target_path, args.mode)

        summary[label] = {
            "train": len(train_images),
            "val": len(val_images),
            "test": len(test_images),
            "total": total,
        }

    labels_output = args.output_root / "labels.json"
    labels_output.write_text(json.dumps(labels, ensure_ascii=False, indent=2), encoding="utf-8")

    split_summary = {
        "raw_root": str(args.raw_root.resolve()),
        "output_root": str(args.output_root.resolve()),
        "mode": args.mode,
        "seed": args.seed,
        "ratios": {
            "train": args.train_ratio,
            "val": args.val_ratio,
            "test": args.test_ratio,
        },
        "classes": summary,
        "warnings": warnings,
    }
    (args.output_root / "split_summary.json").write_text(
        json.dumps(split_summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"Prepared dataset at {args.output_root}")
    print(f"Labels saved to {labels_output}")
    if warnings:
        print("Warnings:")
        for item in warnings:
            print(f"- {item}")


if __name__ == "__main__":
    main()
