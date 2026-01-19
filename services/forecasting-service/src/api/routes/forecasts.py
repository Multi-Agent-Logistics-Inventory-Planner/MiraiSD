from __future__ import annotations

from fastapi import APIRouter

router = APIRouter()


@router.post("/run")
def run_forecast_job(
    from_ts: str | None = None,
    to_ts: str | None = None,
    method: str = "ma14",
    target_days: int | None = None,
) -> dict:
    """Run the batch forecast job and return the output path."""
    # Import inside handler to avoid heavy imports at API startup time.
    from ...forecast_job import run_batch

    out_path = run_batch(from_ts=from_ts, to_ts=to_ts, method=method, target_days=target_days)
    return {"out_path": str(out_path)}

