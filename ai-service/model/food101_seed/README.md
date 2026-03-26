# food101_seed 模型包

这是当前已经接入 NutriMind 推理链路的一版 seed 分类模型。

## 包内文件

- `food_classifier_seed.onnx`
  当前可直接用于 Python 推理服务的 ONNX 模型。
- `metadata.json`
  记录模型版本、输入尺寸、标签文件、验证集精度等元信息。
- `checkpoints/best.pth`
  训练得到的最佳 PyTorch 权重。
- `checkpoints/labels.json`
  这版模型对应的 6 类标签。

## 当前识别类别

- 三明治
- 沙拉
- 炒饭
- 面包
- 饺子
- 鸡蛋

## 直接启动方式

在 `ai-service/inference/python` 目录下运行：

```powershell
.\start-food101-seed.ps1 -Reload
```

这个脚本会自动设置：

```powershell
$env:VISION_MODEL_BUNDLE="food101_seed"
$env:VISION_BACKEND="auto"
```

然后根据 `metadata.json` 自动读取：

- ONNX 模型路径
- 标签文件路径
- 输入尺寸 `160`
- 模型版本 `food101-seed-v1`

## 手动启动方式

```powershell
$env:VISION_MODEL_BUNDLE="food101_seed"
$env:VISION_BACKEND="auto"
python -m uvicorn app.main:app --host 0.0.0.0 --port 8090 --reload
```

## 本地单图测试

如果你暂时不想启动服务，也可以直接本地跑一张图片：

```powershell
cd ai-service/inference/python
python smoke_test.py --image D:\workspace\NutriMind\ai-service\training\datasets\food101_seed\test\炒饭\1028159.jpg --top-k 3
```

## 说明

- 这版模型来自 `Food-101` 的保守审核子集，不是最终全量模型。
- 它适合先把拍照识别、后端联调和前端交互链路跑通。
- 后面继续扩类时，建议新增新的模型包目录，而不是直接覆盖当前这个包。
