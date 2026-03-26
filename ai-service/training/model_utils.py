from __future__ import annotations

from pathlib import Path

import torch
from torch import nn
from torchvision import models


def build_resnet18(num_classes: int, pretrained: bool = True) -> nn.Module:
    weights = models.ResNet18_Weights.DEFAULT if pretrained else None
    try:
        model = models.resnet18(weights=weights)
    except Exception as exc:
        if not pretrained:
            raise
        print(f"Warning: failed to load pretrained ResNet18 weights, falling back to random init: {exc}")
        model = models.resnet18(weights=None)
    in_features = model.fc.in_features
    model.fc = nn.Linear(in_features, num_classes)
    return model


def save_checkpoint(path: Path, model: nn.Module, class_names: list[str], epoch: int, best_acc: float) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    torch.save(
        {
            "model_state_dict": model.state_dict(),
            "class_names": class_names,
            "epoch": epoch,
            "best_acc": best_acc,
            "model_name": "resnet18",
        },
        path,
    )
