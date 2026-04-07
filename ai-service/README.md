# NutriMind AI Service

`ai-service` 现在已经从“固定 32 类分类 demo”升级成了更适合继续扩展的识别架构。

## 当前架构

1. Java 服务层
负责鉴权、接收前端图片、调用识别引擎、把识别候选映射到食物库中的真实食物。

2. Python 推理层
负责输出“候选食物概念”，而不是只给一个单标签分类结果。

3. 训练与检索标签库层
负责准备数据集、构建 `retrieval_bank.json`、导出分类模型和后续的 CLIP 文本向量库。

## 当前支持的识别模式

### 1. `manifest_retrieval`

当本地还没有 ONNX 模型，或者没有安装 `onnxruntime` 时，系统会走“类别清单候选召回”模式。

它会结合：

- 文件名提示
- 简单视觉颜色启发
- `category_manifest.json` 中的别名、关键词、优先级

先返回一组候选，再由 Java 服务映射到食物库。

### 2. `onnx_retrieval`

当本地存在 `food_classifier.onnx` 时，系统先跑分类模型，再把预测标签补充成“可检索候选”输出。

这意味着即使底层还是分类模型，上层接口也已经是检索式架构了，后面替换成 `CLIP / SigLIP` 时不用重做前后端接口。

### 3. `clip_retrieval`

当本地已经准备好：

- `clip_text_bank.npz`
- `clip_text_bank.meta.json`
- `open-clip-torch`

Python 推理层就可以直接把图片编码成向量，再和文本向量库做相似度检索，返回开放类别候选。

## 新增的 CLIP-ready 基础设施

这次我又往前推进了一步，补上了接 CLIP 最关键的准备层：

- [category_manifest.json](d:/workspace/NutriMind/ai-service/model/category_manifest.json)
现在不只是类别名，还包含：
中文名、英文名、别名、搜索关键词、中文 prompt、英文 prompt。

- [build_retrieval_bank.py](d:/workspace/NutriMind/ai-service/training/build_retrieval_bank.py)
把食物概念清单整理成 [retrieval_bank.json](d:/workspace/NutriMind/ai-service/model/retrieval_bank.json)，供候选召回和后续文本编码使用。

- [export_clip_text_bank.py](d:/workspace/NutriMind/ai-service/training/export_clip_text_bank.py)
可选地把文本提示词编码成 `CLIP` 向量库，后续就能直接做图片向量和文本向量的相似度检索。

- [requirements-clip.txt](d:/workspace/NutriMind/ai-service/training/requirements-clip.txt)
提供可选的 CLIP 导出依赖入口。

## 为什么现在就做这层

因为只做闭集分类器会很快碰到三个问题：

1. 类别上限太明显
现实食物远远不止 32 类。

2. 对真实拍照场景鲁棒性不够
用户拍的是外卖、家常菜、拼盘、包装食品，不是标准数据集样张。

3. 后续接论文模型成本高
如果前期接口只围绕“单标签分类”，后面接开放类别检索、分割、估重都会很痛苦。

现在这套结构已经变成：

- 前端拿到候选列表
- Java 侧做食物库映射
- Python 侧可以先用 manifest 召回，再逐步替换成 ONNX、CLIP、SigLIP

## 关键文件

- [VisionRecognitionService.java](d:/workspace/NutriMind/ai-service/src/main/java/com/yxw/ai/service/VisionRecognitionService.java)
主识别流程入口

- [FoodCatalogMatchService.java](d:/workspace/NutriMind/ai-service/src/main/java/com/yxw/ai/service/FoodCatalogMatchService.java)
把识别候选映射到食物库的核心匹配逻辑

- [main.py](d:/workspace/NutriMind/ai-service/inference/python/app/main.py)
Python 推理服务入口

- [inference.py](d:/workspace/NutriMind/ai-service/inference/python/app/inference.py)
候选召回与分类后处理逻辑

- [model_registry.py](d:/workspace/NutriMind/ai-service/inference/python/app/model_registry.py)
模型、标签、概念清单注册中心

- [training/README.md](d:/workspace/NutriMind/ai-service/training/README.md)
训练和 CLIP-ready 流程说明

## 如何切到 Python 推理模式

Java `ai-service` 配置：

```properties
app.vision.engine=python
app.vision.python.base-url=http://localhost:8090
app.vision.python.predict-path=/predict
```

也可以通过环境变量：

```powershell
$env:APP_VISION_ENGINE="python"
$env:APP_VISION_PYTHON_BASE_URL="http://localhost:8090"
```

Python 推理层后端选择通过 `VISION_BACKEND` 控制，支持：

- `auto`
- `classifier`
- `clip`
- `manifest`

例如：

```powershell
$env:VISION_BACKEND="clip"
```

## Python 推理服务启动

```powershell
cd ai-service/inference/python
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8090 --reload
```

如果是国内网络环境，首次拉取 Hugging Face 模型前可以先设置：

```powershell
$env:HF_ENDPOINT="https://hf-mirror.com"
```

如果要直接启动当前已经接好的 `food101_seed` 模型包，可以在同一目录运行：

```powershell
.\start-food101-seed.ps1 -Reload
```

这个脚本会自动读取 [metadata.json](d:/workspace/NutriMind/ai-service/model/food101_seed/metadata.json)，并设置：

```powershell
$env:VISION_MODEL_BUNDLE="food101_seed"
$env:VISION_BACKEND="auto"
```

这样就不需要再手动填写 ONNX 路径、标签路径和输入尺寸了。

如果要启用 CLIP 检索后端，再额外安装：

```powershell
pip install -r requirements-clip.txt
```

如果下载 CLIP 预训练权重时在国内网络环境里超时，可以先设置：

```powershell
$env:HF_ENDPOINT="https://hf-mirror.com"
```

## 构建检索标签库

```powershell
cd ai-service/training
python build_retrieval_bank.py --manifest ../model/category_manifest.json --output ../model/retrieval_bank.json
```

## 接入公开数据集

```powershell
cd ai-service/training
python prepare_public_datasets.py --registry ./public_datasets.json --output-root ./datasets/public
```

这一步会先把 `Food-101 / FoodX-251 / ChineseFoodNet` 的工作目录、说明文件和映射模板建好。
其中 `Food-101` 还支持后续自动下载官方压缩包。

## 可选导出 CLIP 文本向量库

```powershell
cd ai-service/training
pip install -r requirements-clip.txt
$env:HF_ENDPOINT="https://hf-mirror.com"
python export_clip_text_bank.py --manifest ../model/category_manifest.json --bank-json ../model/retrieval_bank.json --output ../model/clip_text_bank.npz --language both
```

## 推荐路线

我建议后面继续按这条路线推进：

1. 先把“候选召回 + 人工确认 + 入库”链路做稳。
2. 用公开数据集做第一阶段训练。
3. 再用你自己手机拍摄的数据做微调。
4. 再升级到 `CLIP / SigLIP` 这类开放词表检索。
5. 最后做分割、估重和营养计算。

## 是否一定要先下载公开数据集

不一定，取决于你现在要推进的是哪一步：

1. 如果你现在要训练图像分类器或微调视觉模型。
要下载到本地，或者放到训练脚本能访问的存储里。

2. 如果你现在只是先做 CLIP 文本向量库和开放类别召回演示。
可以先不下载大规模图片数据，直接先用概念清单和 prompt 跑起来。

3. 如果你后面要把效果做得更像真实项目。
还是建议把公开数据集下载到本地，再加你自己的手机实拍数据继续训练。
