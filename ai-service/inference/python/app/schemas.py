from __future__ import annotations

from pydantic import BaseModel, Field


class PredictionItem(BaseModel):
    label: str = Field(..., description="预测类别")
    canonical_label: str | None = Field(default=None, description="标准化类别名称")
    confidence: float = Field(..., ge=0.0, le=1.0, description="置信度")
    source: str | None = Field(default=None, description="候选来源")
    match_reason: str | None = Field(default=None, description="候选命中原因")
    aliases: list[str] = Field(default_factory=list, description="同义名或别名")
    search_keywords: list[str] = Field(default_factory=list, description="用于食物库搜索的关键词")


class PredictionResponse(BaseModel):
    mode: str = Field(..., description="推理模式")
    model_version: str = Field(..., description="模型版本")
    predictions: list[PredictionItem] = Field(default_factory=list, description="候选结果")
