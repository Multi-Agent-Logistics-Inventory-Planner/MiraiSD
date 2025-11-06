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
    # Input columns (writer adds id)
    input_cols = [
        "item_id",
        "computed_at",
        "horizon_days",
        "avg_daily_delta",
        "days_to_stockout",
        "suggested_reorder_qty",
        "suggested_order_date",
        "confidence",
        "features",
    ]
    df_in = pd.DataFrame(
        [
            [
                "SKU-1",  # item_id
                "2025-11-05T00:00:00Z",  # computed_at
                21,  # horizon_days
                3.5,  # avg_daily_delta
                10.0,  # days_to_stockout
                45,  # suggested_reorder_qty
                "2025-11-05",  # suggested_order_date
                0.95,  # confidence
                "{\"example\":true}",  # features
            ]
        ],
        columns=input_cols,
    )
    out_path = repo.write_forecasts(df_in)
    assert out_path == tmp_path / "forecast_predictions.csv"
    assert out_path.exists()
    loaded = pd.read_csv(out_path)
    output_cols = [
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
    ]
    assert list(loaded.columns) == output_cols


def test_write_forecasts_accepts_id_and_orders_columns(tmp_path: Path, monkeypatch):
    monkeypatch.setattr(config, "OUTPUT_DIR", tmp_path)
    # Provide input with an id; writer should preserve order and id
    output_cols_with_id = [
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
    ]
    df_in = pd.DataFrame(
        [
            [
                "row-1",
                "SKU-9",
                "2025-11-06T00:00:00Z",
                14,
                2.0,
                7.0,
                20,
                "",
                0.9,
                "{}",
            ]
        ],
        columns=output_cols_with_id,
    )
    out_path = repo.write_forecasts(df_in)
    loaded = pd.read_csv(out_path)
    assert list(loaded.columns) == output_cols_with_id
    assert loaded.iloc[0]["id"] == "row-1"
