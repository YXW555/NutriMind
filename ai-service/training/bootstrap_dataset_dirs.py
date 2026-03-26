from __future__ import annotations

import argparse
import json
from pathlib import Path


DEFAULT_SPLITS = ("raw", "train", "val", "test")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Create NutriMind dataset directory structure")
    parser.add_argument("--manifest", type=Path, required=True, help="Path to category_manifest.json")
    parser.add_argument("--dataset-root", type=Path, required=True, help="Output dataset root")
    parser.add_argument("--splits", nargs="+", default=list(DEFAULT_SPLITS), help="Dataset splits to create")
    return parser.parse_args()


def load_labels(manifest_path: Path) -> list[str]:
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("category manifest must be a JSON array")

    labels: list[str] = []
    for item in data:
        if not isinstance(item, dict):
            continue
        label = str(item.get("label", "")).strip()
        if label:
            labels.append(label)
    if not labels:
        raise ValueError("category manifest does not contain any valid labels")
    return labels


def main() -> None:
    args = parse_args()
    labels = load_labels(args.manifest)
    args.dataset_root.mkdir(parents=True, exist_ok=True)

    for split in args.splits:
        split_dir = args.dataset_root / split
        split_dir.mkdir(parents=True, exist_ok=True)
        for label in labels:
            (split_dir / label).mkdir(parents=True, exist_ok=True)

    summary = {
        "dataset_root": str(args.dataset_root.resolve()),
        "split_count": len(args.splits),
        "class_count": len(labels),
        "splits": list(args.splits),
        "labels": labels,
    }
    (args.dataset_root / "bootstrap_summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"Created dataset structure at {args.dataset_root}")
    print(f"Classes: {len(labels)}")


if __name__ == "__main__":
    main()
