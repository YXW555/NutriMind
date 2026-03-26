from __future__ import annotations

import argparse
import json
import tarfile
from pathlib import Path
from urllib.request import urlretrieve


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Bootstrap public food dataset workspace for NutriMind")
    parser.add_argument(
        "--registry",
        type=Path,
        default=Path("public_datasets.json"),
        help="Path to public_datasets.json",
    )
    parser.add_argument(
        "--output-root",
        type=Path,
        default=Path("datasets/public"),
        help="Where to create public dataset workspaces",
    )
    parser.add_argument(
        "--datasets",
        nargs="+",
        default=["food101", "foodx251", "chinesefoodnet"],
        help="Dataset ids to bootstrap",
    )
    parser.add_argument(
        "--download-food101",
        action="store_true",
        help="Download and extract Food-101 official archive",
    )
    parser.add_argument(
        "--force-download",
        action="store_true",
        help="Re-download archive even if it already exists",
    )
    return parser.parse_args()


def load_registry(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("public_datasets.json must be a JSON object")
    datasets = data.get("datasets")
    if not isinstance(datasets, list):
        raise ValueError("public_datasets.json must contain datasets array")
    return data


def select_datasets(registry: dict, selected_ids: list[str]) -> list[dict]:
    mapping = {
        str(item.get("id", "")).strip(): item
        for item in registry.get("datasets", [])
        if isinstance(item, dict) and str(item.get("id", "")).strip()
    }

    selected: list[dict] = []
    for dataset_id in selected_ids:
        item = mapping.get(dataset_id.strip())
        if item is None:
            raise ValueError(f"dataset id not found in registry: {dataset_id}")
        selected.append(item)
    return selected


def ensure_dataset_workspace(dataset: dict, output_root: Path) -> dict:
    dataset_id = str(dataset.get("id", "")).strip()
    dataset_root = output_root / dataset_id
    source_dir = dataset_root / "source"
    raw_dir = dataset_root / "raw"
    mapped_dir = dataset_root / "mapped"
    meta_dir = dataset_root / "meta"

    for path in (dataset_root, source_dir, raw_dir, mapped_dir, meta_dir):
        path.mkdir(parents=True, exist_ok=True)

    info_path = meta_dir / "dataset_info.json"
    info_path.write_text(json.dumps(dataset, ensure_ascii=False, indent=2), encoding="utf-8")

    readme_path = dataset_root / "README.md"
    readme_path.write_text(build_dataset_readme(dataset), encoding="utf-8")

    mapping_template_path = meta_dir / "label_mapping.template.json"
    if not mapping_template_path.exists():
        mapping_template = {
            "dataset_id": dataset_id,
            "dataset_name": dataset.get("display_name", ""),
            "mapping_notes": "把公开数据集中的类别映射到当前 NutriMind 概念库时，在这里补充对应关系。",
            "mappings": [
                {
                    "source_label": "",
                    "target_concept_id": "",
                    "target_label": "",
                    "notes": ""
                }
            ]
        }
        mapping_template_path.write_text(
            json.dumps(mapping_template, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    return {
        "dataset_id": dataset_id,
        "dataset_root": str(dataset_root.resolve()),
        "source_dir": str(source_dir.resolve()),
        "raw_dir": str(raw_dir.resolve()),
        "mapped_dir": str(mapped_dir.resolve()),
        "meta_dir": str(meta_dir.resolve()),
    }


def build_dataset_readme(dataset: dict) -> str:
    lines = [
        f"# {dataset.get('display_name', '')}",
        "",
        f"- `id`: {dataset.get('id', '')}",
        f"- `task_type`: {dataset.get('task_type', '')}",
        f"- `class_count`: {dataset.get('class_count', '')}",
        f"- `homepage`: {dataset.get('homepage', '')}",
        f"- `paper_url`: {dataset.get('paper_url', '')}",
        "",
        "## Summary",
        str(dataset.get("summary", "")).strip(),
        "",
        "## Recommended Usage",
        str(dataset.get("recommended_usage", "")).strip(),
        "",
        "## Local Layout Hint",
        str(dataset.get("local_layout_hint", "")).strip(),
        "",
        "## Notes",
    ]

    notes = dataset.get("notes", [])
    if isinstance(notes, list) and notes:
        for note in notes:
            lines.append(f"- {str(note).strip()}")
    else:
        lines.append("- No additional notes.")
    lines.append("")
    return "\n".join(lines)


def maybe_download_food101(dataset: dict, workspace: dict, force_download: bool) -> dict:
    archive_url = str(dataset.get("archive_url", "")).strip()
    if not archive_url:
        return {"downloaded": False, "extracted": False}

    source_dir = Path(workspace["source_dir"])
    raw_dir = Path(workspace["raw_dir"])
    archive_name = archive_url.rsplit("/", 1)[-1]
    archive_path = source_dir / archive_name

    if force_download or not archive_path.exists():
        print(f"Downloading Food-101 archive to {archive_path} ...")
        urlretrieve(archive_url, archive_path)
    else:
        print(f"Food-101 archive already exists: {archive_path}")

    extract_marker = raw_dir / ".food101_extracted"
    if force_download or not extract_marker.exists():
        print(f"Extracting Food-101 archive into {raw_dir} ...")
        with tarfile.open(archive_path, "r:gz") as tar:
            tar.extractall(raw_dir)
        extract_marker.write_text("ok", encoding="utf-8")
        extracted = True
    else:
        print(f"Food-101 archive already extracted: {raw_dir}")
        extracted = False

    return {
        "downloaded": True,
        "archive_path": str(archive_path.resolve()),
        "extracted": extracted,
    }


def main() -> None:
    args = parse_args()
    registry = load_registry(args.registry)
    datasets = select_datasets(registry, args.datasets)
    args.output_root.mkdir(parents=True, exist_ok=True)

    summary: list[dict] = []
    for dataset in datasets:
        workspace = ensure_dataset_workspace(dataset, args.output_root)
        dataset_summary = {
            "dataset_id": workspace["dataset_id"],
            "workspace": workspace,
            "download_supported": bool(dataset.get("download_supported", False)),
        }

        if workspace["dataset_id"] == "food101" and args.download_food101:
            dataset_summary["download"] = maybe_download_food101(
                dataset,
                workspace,
                force_download=args.force_download,
            )
        else:
            dataset_summary["download"] = {
                "downloaded": False,
                "extracted": False,
            }

        summary.append(dataset_summary)

    summary_path = args.output_root / "bootstrap_summary.json"
    summary_path.write_text(
        json.dumps(
            {
                "registry_version": registry.get("version", ""),
                "dataset_count": len(summary),
                "datasets": summary,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    print(f"Prepared public dataset workspace at {args.output_root}")
    print(f"Datasets: {len(summary)}")


if __name__ == "__main__":
    main()
