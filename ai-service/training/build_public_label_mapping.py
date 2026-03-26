from __future__ import annotations

import argparse
import difflib
import json
import re
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build mapping suggestions from a public dataset to NutriMind concepts")
    parser.add_argument("--manifest", type=Path, required=True, help="Path to category_manifest.json")
    parser.add_argument("--dataset-id", type=str, required=True, help="Dataset id, for example food101")
    parser.add_argument("--source-root", type=Path, required=True, help="Directory containing source class folders")
    parser.add_argument("--output", type=Path, required=True, help="Output mapping json path")
    parser.add_argument("--min-confidence", type=float, default=0.55, help="Minimum confidence to keep a suggestion")
    return parser.parse_args()


def normalize_text(value: str) -> str:
    return "".join(ch for ch in value.strip().lower() if ch.isalnum() or "\u4e00" <= ch <= "\u9fff")


def tokenize_label(value: str) -> list[str]:
    parts = re.split(r"[\s_\-./]+", value.strip().lower())
    return [part for part in parts if part]


def load_manifest(path: Path) -> list[dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("category manifest must be a JSON array")
    return [item for item in data if isinstance(item, dict)]


def collect_concept_terms(item: dict) -> list[str]:
    values = [
        str(item.get("label", "")).strip(),
        str(item.get("english_name", "")).strip(),
        *[str(v).strip() for v in item.get("aliases", []) if str(v).strip()],
        *[str(v).strip() for v in item.get("search_keywords", []) if str(v).strip()],
    ]
    unique_values: list[str] = []
    for value in values:
        if value and value not in unique_values:
            unique_values.append(value)
    return unique_values


def score_match(source_label: str, target_terms: list[str]) -> tuple[float, str]:
    normalized_source = normalize_text(source_label)
    source_tokens = set(tokenize_label(source_label))
    best_score = 0.0
    best_reason = ""

    for term in target_terms:
        normalized_term = normalize_text(term)
        if not normalized_term:
            continue

        if normalized_source == normalized_term:
            return 1.0, f"exact normalized match with '{term}'"

        if normalized_source in normalized_term or normalized_term in normalized_source:
            score = 0.86
            if score > best_score:
                best_score = score
                best_reason = f"substring normalized match with '{term}'"

        ratio = difflib.SequenceMatcher(None, normalized_source, normalized_term).ratio()
        if ratio >= 0.68 and ratio > best_score:
            best_score = round(ratio, 4)
            best_reason = f"string similarity match with '{term}'"

        term_tokens = set(tokenize_label(term))
        if source_tokens and term_tokens:
            overlap = len(source_tokens & term_tokens) / max(len(source_tokens | term_tokens), 1)
            if overlap >= 0.5:
                score = round(0.60 + overlap * 0.25, 4)
                if score > best_score:
                    best_score = score
                    best_reason = f"token overlap match with '{term}'"

    return best_score, best_reason


def choose_best_concept(source_label: str, concepts: list[dict]) -> tuple[dict | None, float, str, list[dict]]:
    scored: list[dict] = []
    for concept in concepts:
        terms = collect_concept_terms(concept)
        score, reason = score_match(source_label, terms)
        if score <= 0:
            continue
        scored.append(
            {
                "concept_id": str(concept.get("id", "")).strip(),
                "label": str(concept.get("label", "")).strip(),
                "confidence": round(score, 4),
                "reason": reason,
            }
        )

    scored.sort(key=lambda item: (-item["confidence"], item["label"]))
    if not scored:
        return None, 0.0, "", []
    best = scored[0]
    return best, float(best["confidence"]), str(best["reason"]), scored[:3]


def find_class_directories(source_root: Path) -> list[Path]:
    if not source_root.exists():
        raise FileNotFoundError(f"source root does not exist: {source_root}")

    child_dirs = [path for path in source_root.iterdir() if path.is_dir()]
    if not child_dirs:
        return []

    nested_candidates: list[Path] = []
    if len(child_dirs) == 1:
        nested_candidates = [path for path in child_dirs[0].iterdir() if path.is_dir()]

    if len(nested_candidates) >= len(child_dirs):
        return sorted(nested_candidates, key=lambda path: path.name)
    return sorted(child_dirs, key=lambda path: path.name)


def main() -> None:
    args = parse_args()
    concepts = load_manifest(args.manifest)
    class_dirs = find_class_directories(args.source_root)

    mappings: list[dict] = []
    for class_dir in class_dirs:
        source_label = class_dir.name
        best, confidence, reason, candidates = choose_best_concept(source_label, concepts)
        if best is None or confidence < args.min_confidence:
            mappings.append(
                {
                    "source_label": source_label,
                    "source_dir": str(class_dir.resolve()),
                    "status": "unmatched",
                    "matched_concept_id": "",
                    "matched_label": "",
                    "match_confidence": round(confidence, 4),
                    "match_reason": reason,
                    "candidate_targets": candidates,
                }
            )
            continue

        status = "exact_match" if confidence >= 0.99 else "suggested_match"
        mappings.append(
            {
                "source_label": source_label,
                "source_dir": str(class_dir.resolve()),
                "status": status,
                "matched_concept_id": best["concept_id"],
                "matched_label": best["label"],
                "match_confidence": round(confidence, 4),
                "match_reason": reason,
                "candidate_targets": candidates,
            }
        )

    summary = {
        "dataset_id": args.dataset_id,
        "manifest_file": str(args.manifest.resolve()),
        "source_root": str(args.source_root.resolve()),
        "class_count": len(mappings),
        "matched_count": sum(1 for item in mappings if item["matched_concept_id"]),
        "unmatched_count": sum(1 for item in mappings if not item["matched_concept_id"]),
        "mappings": mappings,
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Saved mapping suggestions to {args.output}")
    print(f"Classes: {summary['class_count']}, matched: {summary['matched_count']}, unmatched: {summary['unmatched_count']}")


if __name__ == "__main__":
    main()
