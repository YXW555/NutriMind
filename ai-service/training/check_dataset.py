from __future__ import annotations

import argparse
import json
from pathlib import Path


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate NutriMind dataset splits")
    parser.add_argument("--dataset-root", type=Path, required=True, help="Root containing train/ val/ test/")
    parser.add_argument("--manifest", type=Path, required=True, help="Path to category_manifest.json")
    parser.add_argument("--min-train", type=int, default=10)
    parser.add_argument("--min-val", type=int, default=2)
    parser.add_argument("--min-test", type=int, default=2)
    return parser.parse_args()


def load_labels(manifest_path: Path) -> list[str]:
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    return [
        str(item.get("label", "")).strip()
        for item in data
        if isinstance(item, dict) and str(item.get("label", "")).strip()
    ]


def count_images(path: Path) -> int:
    if not path.exists():
        return 0
    return sum(1 for file in path.iterdir() if file.is_file() and file.suffix.lower() in IMAGE_EXTENSIONS)


def main() -> None:
    args = parse_args()
    labels = load_labels(args.manifest)
    report: dict[str, dict[str, int]] = {}
    problems: list[str] = []

    for label in labels:
        counts = {
            "train": count_images(args.dataset_root / "train" / label),
            "val": count_images(args.dataset_root / "val" / label),
            "test": count_images(args.dataset_root / "test" / label),
        }
        report[label] = counts

        if counts["train"] < args.min_train:
            problems.append(f"{label}: train images too few ({counts['train']})")
        if counts["val"] < args.min_val:
            problems.append(f"{label}: val images too few ({counts['val']})")
        if counts["test"] < args.min_test:
            problems.append(f"{label}: test images too few ({counts['test']})")

    output = {
        "dataset_root": str(args.dataset_root.resolve()),
        "class_count": len(labels),
        "classes": report,
        "problems": problems,
    }
    print(json.dumps(output, ensure_ascii=False, indent=2))

    if problems:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
