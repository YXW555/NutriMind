from __future__ import annotations

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from .config import get_settings
from .inference import PredictionService
from .model_registry import ModelRegistry
from .schemas import PredictionResponse

settings = get_settings()
registry = ModelRegistry(settings)
prediction_service = PredictionService(settings, registry)

app = FastAPI(title="NutriMind Vision Inference", version="0.1.0")


@app.get("/health")
def health() -> dict:
    return prediction_service.health_payload()


@app.post("/predict", response_model=PredictionResponse)
async def predict(
    file: UploadFile = File(...),
    top_k: int = Form(default=settings.top_k_default),
) -> PredictionResponse:
    if file.content_type and not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="uploaded file must be an image")

    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="no image selected")

    return prediction_service.predict(image_bytes, file.filename or "uploaded-image.jpg", top_k)
