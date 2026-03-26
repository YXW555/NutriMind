from __future__ import annotations

import argparse
import json
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build a subset category manifest from reviewed mapping results")
    parser.add_argument("--manifest", type=Path, required=True, help="Path to category_manifest.json")
    parser.add_argument("--mapping", type=Path, required=True, help="Path to reviewed mapping json")
    parser.add_argument("--output", type=Path, required=True, help="Output subset manifest json")
    parser.add_argument(
        "--statuses",
        nargs="+",
        default=["exact_match", "manual_confirmed"],
        help="Mapping statuses that should be included in the subset manifest",
    )
    return parser.parse_args()


def load_manifest(path: Path) -> list[dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("category manifest must be a JSON array")
    return [item for item in data if isinstance(item, dict)]


def load_mapping(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("mapping json must be an object")
    return data


def main() -> None:
    args = parse_args()
    manifest = load_manifest(args.manifest)
    mapping = load_mapping(args.mapping)
    allowed_statuses = {status.strip() for status in args.statuses if status.strip()}

    selected_concept_ids: list[str] = []
    for item in mapping.get("mappings", []):
        if not isinstance(item, dict):
            continue
        status = str(item.get("status", "")).strip()
        concept_id = str(item.get("matched_concept_id", "")).strip()
        if status in allowed_statuses and concept_id and concept_id not in selected_concept_ids:
            selected_concept_ids.append(concept_id)

    subset = [
        item for item in manifest
        if str(item.get("id", "")).strip() in selected_concept_ids
    ]

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(subset, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Saved subset manifest to {args.output}")
    print(f"Concepts: {len(subset)}")


if __name__ == "__main__":
    main()
