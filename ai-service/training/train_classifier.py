from __future__ import annotations

import argparse
import json
from pathlib import Path

import torch
from torch import nn
from torch.optim import AdamW
from torch.utils.data import DataLoader
from torchvision import datasets, transforms
from tqdm import tqdm

from model_utils import build_resnet18, save_checkpoint


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train NutriMind food classifier")
    parser.add_argument("--dataset-root", type=Path, required=True, help="ImageFolder root, containing train/ and val/")
    parser.add_argument("--output-dir", type=Path, required=True, help="Directory to save checkpoints")
    parser.add_argument("--epochs", type=int, default=10)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--learning-rate", type=float, default=1e-4)
    parser.add_argument("--image-size", type=int, default=224)
    parser.add_argument("--num-workers", type=int, default=4)
    parser.add_argument("--allow-empty", action="store_true", help="Allow empty class folders in ImageFolder roots")
    parser.add_argument(
        "--no-pretrained",
        action="store_true",
        help="Disable ImageNet pretrained weights and train from random initialization",
    )
    return parser.parse_args()


def build_transforms(image_size: int):
    train_transform = transforms.Compose(
        [
            transforms.Resize((image_size, image_size)),
            transforms.RandomHorizontalFlip(),
            transforms.RandomRotation(10),
            transforms.ColorJitter(brightness=0.15, contrast=0.15, saturation=0.15),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
        ]
    )
    eval_transform = transforms.Compose(
        [
            transforms.Resize((image_size, image_size)),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
        ]
    )
    return train_transform, eval_transform


def evaluate(model: nn.Module, loader: DataLoader, criterion: nn.Module, device: torch.device):
    model.eval()
    total_loss = 0.0
    total_correct = 0
    total_samples = 0

    with torch.no_grad():
        for images, targets in loader:
            images = images.to(device)
            targets = targets.to(device)

            logits = model(images)
            loss = criterion(logits, targets)

            total_loss += loss.item() * images.size(0)
            total_correct += (logits.argmax(dim=1) == targets).sum().item()
            total_samples += images.size(0)

    avg_loss = total_loss / max(total_samples, 1)
    avg_acc = total_correct / max(total_samples, 1)
    return avg_loss, avg_acc


def main() -> None:
    args = parse_args()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    train_dir = args.dataset_root / "train"
    val_dir = args.dataset_root / "val"
    if not train_dir.exists() or not val_dir.exists():
        raise FileNotFoundError("dataset root must contain train/ and val/ directories")

    train_transform, eval_transform = build_transforms(args.image_size)
    train_dataset = datasets.ImageFolder(
        train_dir,
        transform=train_transform,
        allow_empty=args.allow_empty,
    )
    val_dataset = datasets.ImageFolder(
        val_dir,
        transform=eval_transform,
        allow_empty=args.allow_empty,
    )

    train_loader = DataLoader(
        train_dataset,
        batch_size=args.batch_size,
        shuffle=True,
        num_workers=args.num_workers,
        pin_memory=torch.cuda.is_available(),
    )
    val_loader = DataLoader(
        val_dataset,
        batch_size=args.batch_size,
        shuffle=False,
        num_workers=args.num_workers,
        pin_memory=torch.cuda.is_available(),
    )

    model = build_resnet18(
        num_classes=len(train_dataset.classes),
        pretrained=not args.no_pretrained,
    ).to(device)
    criterion = nn.CrossEntropyLoss()
    optimizer = AdamW(model.parameters(), lr=args.learning_rate)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    labels_path = args.output_dir / "labels.json"
    labels_path.write_text(json.dumps(train_dataset.classes, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Detected classes: {len(train_dataset.classes)}")
    print(f"Train samples: {len(train_dataset.samples)}, Val samples: {len(val_dataset.samples)}")

    best_acc = 0.0
    for epoch in range(1, args.epochs + 1):
        model.train()
        running_loss = 0.0
        running_correct = 0
        running_samples = 0

        progress = tqdm(train_loader, desc=f"Epoch {epoch}/{args.epochs}", unit="batch")
        for images, targets in progress:
            images = images.to(device)
            targets = targets.to(device)

            optimizer.zero_grad()
            logits = model(images)
            loss = criterion(logits, targets)
            loss.backward()
            optimizer.step()

            running_loss += loss.item() * images.size(0)
            running_correct += (logits.argmax(dim=1) == targets).sum().item()
            running_samples += images.size(0)

            progress.set_postfix(
                loss=f"{running_loss / max(running_samples, 1):.4f}",
                acc=f"{running_correct / max(running_samples, 1):.4f}",
            )

        val_loss, val_acc = evaluate(model, val_loader, criterion, device)
        print(f"Validation loss={val_loss:.4f}, acc={val_acc:.4f}")

        save_checkpoint(args.output_dir / "last.pth", model, train_dataset.classes, epoch, best_acc)
        if val_acc >= best_acc:
            best_acc = val_acc
            save_checkpoint(args.output_dir / "best.pth", model, train_dataset.classes, epoch, best_acc)

    print(f"Training finished. Best val acc: {best_acc:.4f}")


if __name__ == "__main__":
    main()
