from collections.abc import Iterable
from pathlib import Path

import pandas as pd

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

FORECAST_OUTPUT_COLUMNS: Iterable[str] = (
    "run_id",
    "item_id",
    "computed_at",
    "avg_daily_demand",
    "days_to_stockout",
    "reorder_point",
    "lead_time_days",
    "safety_stock",
    "suggested_order_date",
    "suggested_qty",
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


def write_forecasts(forecasts: pd.DataFrame) -> Path:
    _ensure_columns(forecasts, FORECAST_OUTPUT_COLUMNS)
    out_dir = config.OUTPUT_DIR
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / "forecast_predictions.csv"
    forecasts.to_csv(out_path, index=False)
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
