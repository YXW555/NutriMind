from __future__ import annotations

import argparse
import json
import os
import shutil
from pathlib import Path


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Copy or link mapped public dataset samples into NutriMind training layout")
    parser.add_argument("--mapping", type=Path, required=True, help="Path to generated mapping json")
    parser.add_argument(
        "--manifest",
        type=Path,
        default=None,
        help="Optional category_manifest.json used to resolve labels from matched_concept_id",
    )
    parser.add_argument("--output-root", type=Path, required=True, help="Output root, for example datasets/food_cls/raw")
    parser.add_argument("--mode", choices=["copy", "hardlink"], default="copy")
    parser.add_argument("--min-confidence", type=float, default=0.75, help="Minimum mapping confidence to include")
    parser.add_argument("--include-suggested", action="store_true", help="Include suggested matches above threshold")
    parser.add_argument("--max-per-class", type=int, default=0, help="Optional max images per source class, 0 means unlimited")
    return parser.parse_args()


def list_images(path: Path) -> list[Path]:
    return sorted(
        [
            item for item in path.iterdir()
            if item.is_file() and item.suffix.lower() in IMAGE_EXTENSIONS
        ],
        key=lambda item: item.name,
    )


def transfer_file(source: Path, target: Path, mode: str) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        return

    if mode == "hardlink":
        os.link(source, target)
    else:
        shutil.copy2(source, target)


def load_manifest_lookup(path: Path | None) -> dict[str, str]:
    if path is None:
        return {}

    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("category manifest must be a JSON array")

    lookup: dict[str, str] = {}
    for item in data:
        if not isinstance(item, dict):
            continue
        concept_id = str(item.get("id", "")).strip()
        label = str(item.get("label", "")).strip()
        if concept_id and label:
            lookup[concept_id] = label
    return lookup


def should_include(item: dict, min_confidence: float, include_suggested: bool) -> bool:
    matched_label = str(item.get("matched_label", "")).strip()
    matched_concept_id = str(item.get("matched_concept_id", "")).strip()
    if not matched_label and not matched_concept_id:
        return False

    status = str(item.get("status", "")).strip()
    confidence = float(item.get("match_confidence", 0) or 0)
    if status == "exact_match":
        return True
    if include_suggested and confidence >= min_confidence:
        return status in {"suggested_match", "manual_confirmed", "exact_match"}
    return False


def resolve_target_label(item: dict, manifest_lookup: dict[str, str]) -> str:
    concept_id = str(item.get("matched_concept_id", "")).strip()
    if concept_id:
        manifest_label = manifest_lookup.get(concept_id, "").strip()
        if manifest_label:
            return manifest_label
    return str(item.get("matched_label", "")).strip()


def main() -> None:
    args = parse_args()
    data = json.loads(args.mapping.read_text(encoding="utf-8"))
    mappings = data.get("mappings", [])
    if not isinstance(mappings, list):
        raise ValueError("mapping file must contain mappings array")

    manifest_lookup = load_manifest_lookup(args.manifest)
    args.output_root.mkdir(parents=True, exist_ok=True)
    summary: dict[str, dict[str, int]] = {}

    for item in mappings:
        if not isinstance(item, dict):
            continue
        if not should_include(item, args.min_confidence, args.include_suggested):
            continue

        target_label = resolve_target_label(item, manifest_lookup)
        source_dir = Path(str(item.get("source_dir", "")).strip())
        if not target_label or not source_dir.exists():
            continue

        images = list_images(source_dir)
        if args.max_per_class > 0:
            images = images[:args.max_per_class]

        copied_count = 0
        for image_path in images:
            target_path = args.output_root / target_label / image_path.name
            transfer_file(image_path, target_path, args.mode)
            copied_count += 1

        stats = summary.setdefault(target_label, {"source_classes": 0, "image_count": 0})
        stats["source_classes"] += 1
        stats["image_count"] += copied_count

    summary_path = args.output_root / "public_materialize_summary.json"
    summary_path.write_text(
        json.dumps(
            {
                "mapping_file": str(args.mapping.resolve()),
                "manifest_file": str(args.manifest.resolve()) if args.manifest else "",
                "output_root": str(args.output_root.resolve()),
                "mode": args.mode,
                "include_suggested": args.include_suggested,
                "min_confidence": args.min_confidence,
                "max_per_class": args.max_per_class,
                "classes": summary,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    print(f"Materialized mapped dataset into {args.output_root}")
    print(f"Target classes: {len(summary)}")


if __name__ == "__main__":
    main()
