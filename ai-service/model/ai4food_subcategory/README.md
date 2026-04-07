# AI4Food Subcategory Bundle

把 AI4Food `EfficientNetV2` 的 `subcategory (73 classes)` 预训练模型文件放到这个目录下，
就可以直接复用当前项目的 Python 推理服务和 Java 食物库匹配链路。
当前接入默认会按 `metadata.json` 里的 `classifier_adapter=ai4food_official`
去贴近 AI4Food 官方推理流程。

建议目录结构：

```text
ai4food_subcategory/
  metadata.json
  model.keras            # 或 model.h5 / SavedModel 目录
  labels.json
  retrieval_bank.json    # 可选但强烈建议，便于映射到项目食物库
  category_manifest.json # 可选
```

注意：

- `metadata.json` 可以从同目录下的 `metadata.template.json` 复制一份后再改。
- `labels.json` 建议直接写模型输出标签顺序。
- 如果模型标签是英文，建议同步准备 `retrieval_bank.json`，把英文标签、中文名、别名、搜索关键词都配进去。
- 当前项目返回的是 `top-k` 候选，不是多框检测。
