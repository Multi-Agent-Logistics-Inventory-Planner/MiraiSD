from collections.abc import Iterable
from pathlib import Path

import pandas as pd
import uuid

from . import config

REQUIRED_ITEM_COLUMNS: Iterable[str] = (
    "item_id",
    "name",
    "category",
    "lead_time_days",
    "safety_stock_days",
)

REQUIRED_INVENTORY_COLUMNS: Iterable[str] = (
    "item_id",
    "as_of_ts",
    "current_qty",
)

# Forecast schema (database contract)
FORECAST_INPUT_COLUMNS: Iterable[str] = (
    "item_id",
    "computed_at",
    "horizon_days",
    "avg_daily_delta",
    "days_to_stockout",
    "suggested_reorder_qty",
    "suggested_order_date",
    "confidence",
    "features",
)

FORECAST_OUTPUT_COLUMNS: Iterable[str] = (
    "id",
    "item_id",
    "computed_at",
    "horizon_days",
    "avg_daily_delta",
    "days_to_stockout",
    "suggested_reorder_qty",
    "suggested_order_date",
    "confidence",
    "features",
)


def _ensure_columns(df: pd.DataFrame, required: Iterable[str]) -> None:
    missing = [c for c in required if c not in df.columns]
    if missing:
        raise ValueError(f"Missing required columns: {missing}")


def load_items() -> pd.DataFrame:
    items_path = config.DATA_DIR / "items.csv"
    df = pd.read_csv(items_path)
    _ensure_columns(df, REQUIRED_ITEM_COLUMNS)
    df = df.copy()
    df["item_id"] = df["item_id"].astype(str)
    df["name"] = df["name"].astype(str)
    df["category"] = df["category"].astype(str)
    # Enforce integer types; raise on bad values
    df["lead_time_days"] = pd.to_numeric(df["lead_time_days"], errors="raise").astype(int)
    df["safety_stock_days"] = pd.to_numeric(df["safety_stock_days"], errors="raise").astype(int)
    return df


def load_inventories() -> pd.DataFrame:
    inv_path = config.DATA_DIR / "inventories.csv"
    df = pd.read_csv(inv_path)
    _ensure_columns(df, REQUIRED_INVENTORY_COLUMNS)
    df = df.copy()
    df["item_id"] = df["item_id"].astype(str)
    df["as_of_ts"] = pd.to_datetime(df["as_of_ts"], utc=True, errors="raise")
    df["current_qty"] = pd.to_numeric(df["current_qty"], errors="raise").astype(int)
    return df


def write_forecasts(forecasts_df: pd.DataFrame) -> Path:
    """Write forecast predictions (database-aligned schema) to CSV.

    Expects DataFrame with FORECAST_INPUT_COLUMNS. Adds `id` if missing and
    writes columns ordered as FORECAST_OUTPUT_COLUMNS.
    """
    _ensure_columns(forecasts_df, FORECAST_INPUT_COLUMNS)
    out_dir = config.OUTPUT_DIR
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / "forecast_predictions.csv"
    df = forecasts_df.copy()
    if "id" not in df.columns:
        df.insert(0, "id", [str(uuid.uuid4()) for _ in range(len(df))])
    df = df[[c for c in FORECAST_OUTPUT_COLUMNS]]
    df.to_csv(out_path, index=False)
    return out_path






class Repo:
    """Backwards-compatible class wrapper for existing usage."""

    def __init__(self) -> None:
        pass

    def load_items(self) -> pd.DataFrame:  # type: ignore[override]
        return load_items()

    def load_inventories(self) -> pd.DataFrame:  # type: ignore[override]
        return load_inventories()

    def write_forecasts(self, df: pd.DataFrame) -> Path:  # type: ignore[override]
        return write_forecasts(df)
