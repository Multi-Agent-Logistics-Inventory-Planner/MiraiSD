from pathlib import Path

import pandas as pd

from src import config, repo


def test_load_items_schema_and_count():
    df = repo.load_items()
    required = {"item_id", "name", "category", "lead_time_days", "safety_stock_days"}
    assert required.issubset(df.columns), "Items schema mismatch"
    assert len(df) >= 3, "Expected at least 3 items"
    assert pd.api.types.is_integer_dtype(df["lead_time_days"])
    assert pd.api.types.is_integer_dtype(df["safety_stock_days"])


def test_load_inventories_schema_and_timestamps():
    df = repo.load_inventories()
    required = {"item_id", "as_of_ts", "current_qty"}
    assert required.issubset(df.columns), "Inventories schema mismatch"
    assert pd.api.types.is_datetime64_any_dtype(df["as_of_ts"])
    assert pd.api.types.is_integer_dtype(df["current_qty"])


def test_write_forecasts_creates_expected_file(tmp_path: Path, monkeypatch):
    # Redirect output dir to a temp location to avoid polluting repo
    monkeypatch.setattr(config, "OUTPUT_DIR", tmp_path)
    out_cols = [
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
    ]
    df = pd.DataFrame(
        [
            [
                "run-001",
                "3f8f0e0b-1",
                "2025-11-05T00:00:00Z",
                3.5,
                12.0,
                50.0,
                7,
                10.5,
                "2025-11-06T00:00:00Z",
                55,
            ]
        ],
        columns=out_cols,
    )
    out_path = repo.write_forecasts(df)
    assert out_path == tmp_path / "forecast_predictions.csv"
    assert out_path.exists()
    loaded = pd.read_csv(out_path)
    assert list(loaded.columns) == out_cols
