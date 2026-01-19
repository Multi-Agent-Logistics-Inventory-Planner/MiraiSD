from __future__ import annotations

from fastapi import FastAPI

from .routes.forecasts import router as forecasts_router

app = FastAPI(title="forecasting-service", version="0.1.0")


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


app.include_router(forecasts_router, prefix="/forecasts", tags=["forecasts"])

