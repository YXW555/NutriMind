from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np

from build_retrieval_bank import build_retrieval_bank_from_manifest

try:
    import open_clip
    import torch
except ImportError as exc:  # pragma: no cover - optional runtime dependency
    raise SystemExit(
        "Missing dependency for CLIP text export. Please run `pip install -r requirements-clip.txt` first."
    ) from exc


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export CLIP text embedding bank for NutriMind concepts")
    parser.add_argument("--manifest", type=Path, required=True, help="Path to category_manifest.json")
    parser.add_argument("--bank-json", type=Path, default=None, help="Optional existing retrieval_bank.json")
    parser.add_argument("--output", type=Path, required=True, help="Output .npz path for text embeddings")
    parser.add_argument("--metadata-output", type=Path, default=None, help="Optional output path for metadata json")
    parser.add_argument("--model-name", type=str, default="ViT-B-32")
    parser.add_argument("--pretrained", type=str, default="openai")
    parser.add_argument("--language", choices=["zh", "en", "both"], default="both")
    parser.add_argument("--device", type=str, default="")
    parser.add_argument("--batch-size", type=int, default=32)
    return parser.parse_args()


def load_bank(args: argparse.Namespace) -> dict:
    if args.bank_json and args.bank_json.exists():
        return json.loads(args.bank_json.read_text(encoding="utf-8"))
    return build_retrieval_bank_from_manifest(args.manifest)


def choose_prompts(concept: dict, language: str) -> list[str]:
    zh_prompts = [str(item).strip() for item in concept.get("clip_prompts_zh", []) if str(item).strip()]
    en_prompts = [str(item).strip() for item in concept.get("clip_prompts_en", []) if str(item).strip()]
    if language == "zh":
        return zh_prompts
    if language == "en":
        return en_prompts
    return zh_prompts + en_prompts


def encode_texts(model, tokenizer, prompts: list[str], device: torch.device, batch_size: int) -> np.ndarray:
    outputs: list[np.ndarray] = []
    with torch.no_grad():
        for start in range(0, len(prompts), batch_size):
            batch = prompts[start:start + batch_size]
            tokens = tokenizer(batch).to(device)
            features = model.encode_text(tokens)
            features = features / features.norm(dim=-1, keepdim=True).clamp_min(1e-12)
            outputs.append(features.cpu().numpy())
    return np.concatenate(outputs, axis=0)


def main() -> None:
    args = parse_args()
    bank = load_bank(args)
    concepts = bank.get("concepts", [])
    if not concepts:
        raise SystemExit("retrieval bank is empty")

    device = torch.device(args.device) if args.device else torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model, _, _ = open_clip.create_model_and_transforms(args.model_name, pretrained=args.pretrained, device=device)
    tokenizer = open_clip.get_tokenizer(args.model_name)
    model.eval()

    concept_ids: list[str] = []
    labels: list[str] = []
    prompt_counts: list[int] = []
    embeddings: list[np.ndarray] = []

    for concept in concepts:
        prompts = choose_prompts(concept, args.language)
        if not prompts:
            continue

        prompt_embeddings = encode_texts(model, tokenizer, prompts, device, args.batch_size)
        pooled = prompt_embeddings.mean(axis=0)
        norm = np.linalg.norm(pooled)
        if norm > 0:
            pooled = pooled / norm

        concept_ids.append(str(concept.get("concept_id", "")).strip())
        labels.append(str(concept.get("canonical_label", "")).strip())
        prompt_counts.append(len(prompts))
        embeddings.append(pooled.astype("float32"))

    if not embeddings:
        raise SystemExit("no valid prompt embeddings were generated")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    np.savez(
        args.output,
        embeddings=np.stack(embeddings, axis=0),
        concept_ids=np.asarray(concept_ids),
        labels=np.asarray(labels),
        prompt_counts=np.asarray(prompt_counts, dtype=np.int32),
    )

    metadata_output = args.metadata_output or args.output.with_suffix(".meta.json")
    metadata = {
        "version": "clip-text-bank-v1",
        "model_name": args.model_name,
        "pretrained": args.pretrained,
        "language": args.language,
        "concept_count": len(concept_ids),
        "source_manifest": str(args.manifest.name),
        "source_bank": str(args.bank_json.name) if args.bank_json else "",
        "output_file": args.output.name,
    }
    metadata_output.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Saved CLIP text bank to {args.output}")
    print(f"Saved metadata to {metadata_output}")
    print(f"Concepts: {len(concept_ids)}")


if __name__ == "__main__":
    main()
