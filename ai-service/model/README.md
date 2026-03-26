# Model Directory

这里统一存放 `ai-service` 的模型产物、标签库和元数据。

## 推荐文件结构

```text
model/
  category_manifest.json
  labels.json
  retrieval_bank.json
  food_classifier.onnx
  clip_text_bank.npz
  clip_text_bank.meta.json
  best.pth
  metadata.json
```

## 每个文件的作用

- `category_manifest.json`
食物概念主清单，包含中文名、英文名、别名、搜索关键词、CLIP 提示词。

- `labels.json`
闭集分类模型使用的标签列表。

- `retrieval_bank.json`
由训练脚本根据 `category_manifest.json` 生成的检索标签库，供候选召回和后续 CLIP 文本编码使用。

- `food_classifier.onnx`
当前分类推理模型。

- `clip_text_bank.npz`
可选。把食物提示词编码成向量后的结果，供 CLIP/SigLIP 检索推理使用。

- `clip_text_bank.meta.json`
可选。记录文本向量库对应的模型名、预训练权重、语言和概念数。

- `best.pth`
PyTorch 训练得到的最佳分类模型权重。

- `metadata.json`
记录模型版本、数据来源、训练参数、评估指标和导出说明。

## 现在这套目录为什么重要

当前项目不再只围绕“32 类分类器”设计，而是同时为两条路线做准备：

1. 闭集分类
先把高频类别做准，快速落地可用版本。

2. 开放类别检索
后续接 CLIP / SigLIP / 检索式模型时，不需要再推翻前面的数据组织方式。

## 建议保存的元数据

`metadata.json` 至少建议记录：

- `model_version`
- `task_type`
- `backbone`
- `labels_file`
- `category_manifest_file`
- `retrieval_bank_file`
- `public_datasets`
- `private_mobile_dataset_version`
- `best_val_acc`
- `clip_text_encoder`
- `clip_pretrained`

## 当前可直接使用的模型包

目前仓库里已经有一版可直接联调的模型包：

- [food101_seed](d:/workspace/NutriMind/ai-service/model/food101_seed/README.md)

这个模型包已经包含：

- ONNX 推理文件
- 标签文件
- 模型元数据
- 训练检查点

Python 推理服务现在支持通过 `VISION_MODEL_BUNDLE=food101_seed` 直接启用它。
