from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field, field_validator


class PredictionItem(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    label: str = Field(..., min_length=1, description="Display label returned to Java")
    canonical_label: str | None = Field(default=None, description="Canonical label used for catalog matching")
    confidence: float = Field(..., ge=0.0, le=1.0, description="Normalized confidence in [0, 1]")
    source: str | None = Field(default=None, description="Backend or evidence source")
    match_reason: str | None = Field(default=None, description="Short explanation for the selected match")
    aliases: list[str] = Field(default_factory=list, description="Known aliases for the predicted concept")
    search_keywords: list[str] = Field(default_factory=list, description="Keywords consumed by the Java matching layer")

    @field_validator("aliases", "search_keywords", mode="before")
    @classmethod
    def ensure_string_list(cls, value: object) -> list[str]:
        if value is None:
            return []
        if isinstance(value, list):
            return [str(item).strip() for item in value if str(item).strip()]
        if isinstance(value, str):
            text = value.strip()
            return [text] if text else []
        return [str(value).strip()] if str(value).strip() else []


class PredictionResponse(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    mode: str = Field(..., description="Resolved inference backend mode")
    model_version: str = Field(..., description="Model version exposed to the Java layer")
    predictions: list[PredictionItem] = Field(default_factory=list, description="Compatibility-safe prediction list")
