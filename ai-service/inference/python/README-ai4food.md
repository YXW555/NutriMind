# AI4Food 接入说明

这份说明对应当前仓库里已经补好的 AI4Food 接入点。
当前实现会优先贴近 AI4Food 官方推理脚本：

- `models.load_model(...)`
- 224x224 RGB 图像
- `/255.0` 归一化
- 分类输出按置信度排序
- 再适配成 NutriMind 现有的 `top-k` 候选格式

## 已完成的接入能力

- Python 推理服务除了 ONNX 之外，已经支持直接加载 `TensorFlow / Keras` 分类模型。
- 对 AI4Food 模型包，支持走 `classifier_adapter=ai4food_official`，尽量复用官方推理流程。
- 模型包可以通过 `metadata.json` 声明：
  - `runtime`
  - `model_file`
  - `labels_file`
  - `retrieval_bank_file`
  - `category_manifest_file`
  - `input_layout`
  - `preprocess`
- Java 侧不需要改接口，仍然走：
  - 图片识别得到 `top-k` 候选
  - 再用候选词去匹配食物库
  - 最后返回营养信息给前端

## 推荐接入方案

第一版推荐接入：

- 模型：`AI4Food EfficientNetV2`
- 粒度：`subcategory (73 classes)`

原因是这条路线和当前项目最匹配：

- 输出是整图分类 `top-k`
- 前端已经支持候选确认
- 后端已经支持把候选映射到食物库

## 你需要补的文件

把真实模型文件放到：

`ai-service/model/ai4food_subcategory/`

最少需要：

```text
metadata.json
labels.json
model.keras   # 或 model.h5 / SavedModel
```

强烈建议再补：

```text
retrieval_bank.json
```

这样英文标签、中文名、别名、搜索关键词都能一起带上，匹配你们自己的食物库会更稳。

## Python 依赖

当前仓库新增了可选依赖文件：

`ai-service/inference/python/requirements-ai4food.txt`

安装示例：

```powershell
cd ai-service/inference/python
pip install -r requirements.txt
pip install -r requirements-ai4food.txt
```

如果你当前是 Windows 原生 Python，TensorFlow 官方 `pip` 目前支持 `Python 3.10 - 3.13`。
为了少踩依赖坑，仍然建议优先用 `Python 3.12` 环境。

## 启动方式

```powershell
cd ai-service/inference/python
.\start-ai4food-subcategory.ps1 -Reload
```

它会自动设置：

```powershell
$env:VISION_MODEL_BUNDLE="ai4food_subcategory"
$env:VISION_BACKEND="classifier"
```

## Java 服务配置

让 Java 侧走 Python 推理：

```powershell
$env:APP_VISION_ENGINE="python"
$env:APP_VISION_PYTHON_BASE_URL="http://localhost:8091"
```

或者把根目录 `.env` 里的这两项改掉。
