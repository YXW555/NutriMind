# NutriMind AI Service

`ai-service` 现在已经从“固定 32 类分类 demo”升级成了更适合继续扩展的识别架构。

## 当前架构

1. Java 服务层
负责鉴权、接收前端图片、调用识别引擎、把识别候选映射到食物库中的真实食物。

2. Python 推理层
负责输出“候选食物概念”，而不是只给一个单标签分类结果。

3. 训练与检索标签库层
负责准备数据集、构建 `retrieval_bank.json`、导出分类模型和后续的 CLIP 文本向量库。

## 2026-04 更新说明

本次更新把 Python 推理层接成了：

- `LLaVA-NeXT` 作为核心多模态后端
- 独立 Python 推理服务继续对外暴露 `/predict`
- Java 侧继续沿用 `PythonInferenceResponse / PythonInferencePrediction` 契约
- Java 主干、前端调用链、候选映射链路保持不变

`/predict` 返回结构仍然保持兼容：

```json
{
  "mode": "llava_next_retrieval",
  "model_version": "llava-next-v1.6-mistral-7b",
  "predictions": [
    {
      "label": "米饭",
      "canonical_label": "米饭",
      "confidence": 0.82,
      "match_reason": "LLaVA-NeXT matched the catalog concept 米饭",
      "source": "llava_next",
      "aliases": ["白米饭", "熟米饭"],
      "search_keywords": ["米饭", "白米饭", "熟米饭"]
    }
  ]
}
```

LLaVA 的输出不会直接透传到 Java。Python 侧现在会先做：

- prompt 约束
- JSON 提取
- 字段校验
- 概念归一化
- 空结果 / 非法结果失败回退

## 当前支持的识别模式

### 1. `llava_next_retrieval`

当本地安装了 `requirements-llava.txt` 中的依赖，并且能加载 `VISION_LLAVA_MODEL_ID` 指向的模型时，Python 推理层会优先使用 LLaVA-NeXT。

它的职责不是直接生成任意自然语言，而是：

- 在受限 prompt 内只从已知概念清单中做选择
- 输出固定 JSON 结构
- 把结果归一化成 Java 已兼容的候选字段
- 如果输出不合法或解析失败，自动回退到下游检索后端

### 2. `hybrid_retrieval`

当 CLIP 和 ONNX 同时可用，但 LLaVA 不可用或 LLaVA 失败回退时，系统会使用 hybrid 策略。

### 3. `clip_retrieval`

当本地已经准备好：

- `clip_text_bank.npz`
- `clip_text_bank.meta.json`
- `open-clip-torch`

Python 推理层就可以直接把图片编码成向量，再和文本向量库做相似度检索，返回开放类别候选。

### 4. `onnx_retrieval`

当本地存在 `food_classifier.onnx` 时，系统先跑分类模型，再把预测标签补充成“可检索候选”输出。

### 5. `manifest_retrieval`

当本地还没有 ONNX 模型，或者没有安装 `onnxruntime` 时，系统会走“类别清单候选召回”模式。

它会结合：

- 文件名提示
- 简单视觉颜色启发
- `category_manifest.json` 中的别名、关键词、优先级

先返回一组候选，再由 Java 服务映射到食物库。

`auto` 模式下的实际优先级现在是：

1. `llava_next_retrieval`
2. `hybrid_retrieval`
3. `clip_retrieval`
4. `onnx_retrieval`
5. `manifest_retrieval`

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
app.vision.python.base-url=http://localhost:8091
app.vision.python.predict-path=/predict
```

也可以通过环境变量：

```powershell
$env:APP_VISION_ENGINE="python"
$env:APP_VISION_PYTHON_BASE_URL="http://localhost:8091"
```

Python 推理层后端选择通过 `VISION_BACKEND` 控制，支持：

- `auto`
- `llava`
- `hybrid`
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
uvicorn app.main:app --host 0.0.0.0 --port 8091 --reload
```

如果要启动 LLaVA-NeXT 后端，推荐这样做：

```powershell
cd ai-service/inference/python
pip install -r requirements-llava.txt
Copy-Item .env.example .env
.\start-llava-next.ps1
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
$env:VISION_BACKEND="classifier"
```

这样就不需要再手动填写 ONNX 路径、标签路径和输入尺寸了。

如果要只启用 CLIP 检索后端，再额外安装：

```powershell
pip install -r requirements-clip.txt
```

## 构建检索标签库

```powershell
cd ai-service/training
python build_retrieval_bank.py --manifest ../model/category_manifest.json --output ../model/retrieval_bank.json
```

## 队长部署步骤

### 1. 部署 Python 推理服务

```powershell
cd ai-service/inference/python
Copy-Item .env.example .env
pip install -r requirements-llava.txt
.\start-llava-next.ps1 -Port 8091
```

如果你希望只装轻量回退链路，而不装 LLaVA，可以改成：

```powershell
pip install -r requirements.txt
```

### 2. 配置 Java `ai-service`

在根目录 `.env` 或部署环境变量里设置：

```env
APP_VISION_ENGINE=python
APP_VISION_PYTHON_BASE_URL=http://localhost:8091
APP_VISION_PYTHON_PREDICT_PATH=/predict
```

### 3. 验证联通性

- 先访问 Python 服务 `GET /health`
- 再启动 Java `ai-service`
- 确认 Java 仍然消费 `mode / model_version / predictions[]`

### 4. 重要说明

- `auto` 模式已经改成 LLaVA 优先
- 如果 LLaVA 依赖缺失、模型加载失败、输出不是合法 JSON、字段不完整，Python 会自动回退
- Java 侧不需要改 DTO，也不需要改前端请求链路

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
