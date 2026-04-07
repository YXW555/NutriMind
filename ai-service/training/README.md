# Training

这里放的是 `ai-service` 的训练、数据准备和检索标签库构建脚本。

## 当前已经支持的两条路线

1. 分类器路线
先训练高频食物分类器，导出 `ONNX`，快速打通识别闭环。

2. CLIP-ready 检索路线
先构建“食物概念文本库”和“文本向量库”，后续可接 `CLIP / SigLIP / open-vocabulary retrieval`。

## 目录说明

```text
training/
  datasets/
  bootstrap_dataset_dirs.py
  prepare_public_datasets.py
  build_public_label_mapping.py
  build_subset_manifest.py
  materialize_public_dataset.py
  public_datasets.json
  prepare_dataset.py
  check_dataset.py
  build_retrieval_bank.py
  export_clip_text_bank.py
  model_utils.py
  train_classifier.py
  export_onnx.py
  requirements.txt
  requirements-clip.txt
```

## 推荐流程

### A. 数据集准备

1. 用 [category_manifest.json](d:/workspace/NutriMind/ai-service/model/category_manifest.json) 确认第一版食物概念。
2. 运行 `bootstrap_dataset_dirs.py` 创建数据目录。
3. 把图片放进 `datasets/food_cls/raw/<类别名>/`。
4. 运行 `prepare_dataset.py` 自动切分 `train / val / test`。
5. 运行 `check_dataset.py` 看哪些类别还缺图。

### A-2. 接入公开数据集

1. 运行 `prepare_public_datasets.py` 初始化公开数据集工作目录。
2. `Food-101` 可以选择自动下载官方压缩包。
3. `FoodX-251` 和 `ChineseFoodNet` 先生成目录、说明和映射模板，再按官方页面手动放入数据。
4. 后续把公开数据集类别映射到你自己的概念库，再做联合训练或迁移学习。

### A-3. 构建公开数据集到概念库的映射

1. 下载并解压公开数据集到本地之后，运行 `build_public_label_mapping.py`。
2. 这个脚本会扫描源数据集的类别目录，并根据你的 `category_manifest.json` 自动给出映射建议。
3. 你可以先看生成的映射文件，再决定哪些保留、哪些手动改、哪些忽略。

### A-4. 把公开数据集样本整理进训练目录

1. 确认映射文件后，运行 `materialize_public_dataset.py`。
2. 它会把已匹配的类别样本复制或硬链接到你的训练目录。
3. 这样公开数据集就能真正并入你自己的 `raw/<类别名>/` 训练体系里。

### A-5. 构建公开数据集子集 manifest

1. 如果你当前只确认了部分映射，可以运行 `build_subset_manifest.py`。
2. 这样就能只针对“已确认类别”训练一个 seed 模型，而不是强行使用完整 32 类 manifest。

### B. 构建检索标签库

1. 运行 `build_retrieval_bank.py`。
2. 生成 [retrieval_bank.json](d:/workspace/NutriMind/ai-service/model/retrieval_bank.json)。
3. 这个文件会整理：
- 中文名
- 英文名
- 别名
- 搜索关键词
- 中文 prompt
- 英文 prompt
- 归一化检索词

### C. 可选导出 CLIP 文本向量库

1. 安装 `requirements-clip.txt`。
2. 运行 `export_clip_text_bank.py`。
3. 生成：
- `clip_text_bank.npz`
- `clip_text_bank.meta.json`

这样后续就可以把图片编码成向量，再和文本向量库做相似度匹配。

### D. 训练闭集分类器

1. 运行 `train_classifier.py`。
2. 产出 `best.pth` 和 `labels.json`。
3. 运行 `export_onnx.py` 导出 `food_classifier.onnx`。

## 示例命令

```bash
python bootstrap_dataset_dirs.py --manifest ../model/category_manifest.json --dataset-root ./datasets/food_cls
python prepare_public_datasets.py --registry ./public_datasets.json --output-root ./datasets/public
python build_public_label_mapping.py --manifest ../model/category_manifest.json --dataset-id food101 --source-root ./datasets/public/food101/raw/food-101/images --output ./datasets/public/food101/meta/label_mapping.auto.json
python build_subset_manifest.py --manifest ../model/category_manifest.json --mapping ./datasets/public/food101/meta/label_mapping.reviewed.json --output ./datasets/public/food101/meta/food101_seed_manifest.json
python materialize_public_dataset.py --mapping ./datasets/public/food101/meta/label_mapping.reviewed.json --manifest ../model/category_manifest.json --output-root ./datasets/food101_seed/raw --include-suggested --min-confidence 0.8
python prepare_dataset.py --raw-root ./datasets/food101_seed/raw --output-root ./datasets/food101_seed --manifest ./datasets/public/food101/meta/food101_seed_manifest.json
python check_dataset.py --dataset-root ./datasets/food101_seed --manifest ./datasets/public/food101/meta/food101_seed_manifest.json
python build_retrieval_bank.py --manifest ../model/category_manifest.json --output ../model/retrieval_bank.json
python train_classifier.py --dataset-root ./datasets/food_cls --output-dir ../model/checkpoints --epochs 10
python export_onnx.py --checkpoint ../model/checkpoints/best.pth --labels ../model/checkpoints/labels.json --output ../model/food_classifier.onnx
```

当前已经跑通的一版 `Food-101 seed` 模型产物位于：

- [metadata.json](d:/workspace/NutriMind/ai-service/model/food101_seed/metadata.json)
- [food_classifier_seed.onnx](d:/workspace/NutriMind/ai-service/model/food101_seed/food_classifier_seed.onnx)
- [labels.json](d:/workspace/NutriMind/ai-service/model/food101_seed/checkpoints/labels.json)

如果你要继续复训这版模型，可以直接参考：

```bash
python train_classifier.py --dataset-root ./datasets/food101_seed --output-dir ../model/food101_seed/checkpoints --epochs 2 --batch-size 16 --num-workers 0 --image-size 160
python export_onnx.py --checkpoint ../model/food101_seed/checkpoints/best.pth --labels ../model/food101_seed/checkpoints/labels.json --output ../model/food101_seed/food_classifier_seed.onnx --image-size 160
```

导出 CLIP 文本向量库的命令示例：

```bash
pip install -r requirements-clip.txt
$env:HF_ENDPOINT="https://hf-mirror.com"
python export_clip_text_bank.py --manifest ../model/category_manifest.json --bank-json ../model/retrieval_bank.json --output ../model/clip_text_bank.npz --language both
```

## 这一步和项目有什么关系

你现在的项目最适合走“先可用，再变强”的路线：

1. 前端和后端先用候选召回 + 人工确认跑通真实产品流程。
2. 同时准备检索标签库，让系统具备扩类能力。
3. 再用公开数据集 + 手机实拍数据训练更强模型。
4. 最后升级到 CLIP / SigLIP / 分割 / 估重。

## 是否必须先把数据集下载到本地

分三种情况看：

1. 如果你要训练分类器、微调视觉模型、做监督学习。
需要。图片数据必须在你本地磁盘、移动硬盘、NAS，或者训练脚本可访问的存储位置。

2. 如果你只是先做 CLIP 文本向量库。
不需要大规模图片数据，因为这一步只是把食物文字提示词编码成文本向量。

3. 如果你想先做零样本 CLIP 演示。
一开始也可以先不下载公开数据集，直接用预训练 CLIP 和你自己的概念提示词先跑开放类别候选召回。

更务实的顺序通常是：

1. 先不下载大数据，先把候选召回和 CLIP 文本库链路跑通。
2. 再下载公开数据集做第一阶段训练。
3. 最后再补你自己的手机实拍数据做微调。
