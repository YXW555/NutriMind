from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


GROUP_NAMES_ZH = {
    "staple": "主食",
    "breakfast": "早餐",
    "protein": "蛋白质",
    "dairy": "乳制品",
    "fruit": "水果",
    "vegetable": "蔬菜",
    "mixed_meal": "复合菜品",
    "other": "其他食物",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build retrieval-ready food label bank from category manifest")
    parser.add_argument("--manifest", type=Path, required=True, help="Path to category_manifest.json")
    parser.add_argument("--output", type=Path, required=True, help="Output retrieval_bank.json")
    return parser.parse_args()


def unique_texts(values: list[str]) -> list[str]:
    results: list[str] = []
    for value in values:
        text = str(value or "").strip()
        if text and text not in results:
            results.append(text)
    return results


def normalize_term(value: str) -> str:
    lowered = value.strip().lower()
    return re.sub(r"[\s\W_]+", "", lowered, flags=re.UNICODE)


def normalize_id(value: str, fallback: str) -> str:
    base = str(value or "").strip().lower().replace(" ", "_")
    base = re.sub(r"[^a-z0-9_-]+", "", base)
    return base or fallback


def build_prompt_sets(item: dict, group_zh: str) -> tuple[list[str], list[str]]:
    label = str(item.get("label", "")).strip()
    english_name = str(item.get("english_name", "")).strip()
    aliases = unique_texts([str(value).strip() for value in item.get("aliases", []) if str(value).strip()])

    zh_prompts = unique_texts(
        [str(value).strip() for value in item.get("clip_prompts_zh", []) if str(value).strip()]
        + [f"一份{label}", f"餐桌上的{label}", f"{group_zh}里的{label}"]
        + [f"一份{alias}" for alias in aliases[:2]]
    )
    en_prompts = unique_texts(
        [str(value).strip() for value in item.get("clip_prompts_en", []) if str(value).strip()]
        + ([f"a photo of {english_name}", f"a close-up photo of {english_name}"] if english_name else [])
    )
    return zh_prompts, en_prompts


def build_retrieval_bank_from_manifest(manifest_path: Path) -> dict:
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("category manifest must be a JSON array")

    concepts: list[dict] = []
    for index, item in enumerate(data, start=1):
        if not isinstance(item, dict):
            continue

        label = str(item.get("label", "")).strip()
        if not label:
            continue

        group = str(item.get("group", "other") or "other").strip() or "other"
        group_zh = GROUP_NAMES_ZH.get(group, GROUP_NAMES_ZH["other"])
        english_name = str(item.get("english_name", "")).strip()
        aliases = unique_texts([str(value).strip() for value in item.get("aliases", []) if str(value).strip()])
        search_keywords = unique_texts(
            [str(value).strip() for value in item.get("search_keywords", []) if str(value).strip()]
        )
        zh_prompts, en_prompts = build_prompt_sets(item, group_zh)

        normalized_terms = unique_texts(
            [
                normalize_term(term)
                for term in [label, english_name, *aliases, *search_keywords]
                if normalize_term(str(term))
            ]
        )

        concept_id = normalize_id(item.get("id"), fallback=f"concept_{index:03d}")
        priority = int(item.get("priority", index))

        concepts.append(
            {
                "concept_id": concept_id,
                "canonical_label": label,
                "english_name": english_name,
                "group": group,
                "group_zh": group_zh,
                "priority": priority,
                "aliases": aliases,
                "search_keywords": search_keywords,
                "clip_prompts_zh": zh_prompts,
                "clip_prompts_en": en_prompts,
                "normalized_terms": normalized_terms,
                "notes": str(item.get("notes", "")).strip(),
            }
        )

    concepts.sort(key=lambda concept: (concept["priority"], concept["canonical_label"]))
    return {
        "version": "retrieval-bank-v1",
        "concept_count": len(concepts),
        "source_manifest": str(manifest_path.name),
        "concepts": concepts,
    }


def main() -> None:
    args = parse_args()
    bank = build_retrieval_bank_from_manifest(args.manifest)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(bank, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Saved retrieval bank to {args.output}")
    print(f"Concepts: {bank['concept_count']}")


if __name__ == "__main__":
    main()
