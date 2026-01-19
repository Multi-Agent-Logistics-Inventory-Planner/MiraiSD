from pathlib import Path
import json

import pandas as pd

from src import config
from src.forecast_job import run_batch


def _write_csv(p: Path, rows: list[dict]) -> None:
    df = pd.DataFrame(rows)
    df.to_csv(p, index=False)


def _write_events_ndjson(p: Path, rows: list[dict]) -> None:
    with p.open("w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r) + "\n")


def test_run_batch_writes_db_aligned_output(tmp_path: Path, monkeypatch):
    # Point config paths to temp dirs
    data_dir = tmp_path / "data"
    events_dir = tmp_path / "events"
    out_dir = tmp_path / "out"
    data_dir.mkdir(parents=True, exist_ok=True)
    events_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)

    monkeypatch.setattr(config, "DATA_DIR", data_dir)
    monkeypatch.setattr(config, "EVENTS_DIR", events_dir)
    monkeypatch.setattr(config, "OUTPUT_DIR", out_dir)

    # Seed items and inventories
    _write_csv(
        data_dir / "items.csv",
        [
            {
                "item_id": "A",
                "name": "Item A",
                "category": "Cat",
                "lead_time_days": 7,
                "safety_stock_days": 0,
                "service_level": 0.95,
                "lead_time_std_days": 0.0,
            },
            {
                "item_id": "B",
                "name": "Item B",
                "category": "Cat",
                "lead_time_days": 5,
                "safety_stock_days": 0,
                # omit optional columns to test defaults
            },
        ],
    )

    _write_csv(
        data_dir / "inventories.csv",
        [
            {"item_id": "A", "as_of_ts": "2025-11-05T09:00:00Z", "current_qty": 60},
            {"item_id": "B", "as_of_ts": "2025-11-05T09:00:00Z", "current_qty": 30},
        ],
    )

    # Seed simple events: A has one sale, B none
    _write_events_ndjson(
        events_dir / "inventory-changes.ndjson",
        [
            {
                "event_id": "e1",
                "payload": {
                    "item_id": "A",
                    "quantity_change": -5,
                    "reason": "sale",
                    "at": "2025-11-05T10:00:00Z",
                },
            }
        ],
    )

    out_path = run_batch(from_ts="2025-11-05T00:00:00Z", to_ts="2025-11-05T23:59:59Z", method="ma14", target_days=21)
    assert out_path.exists()
    df = pd.read_csv(out_path)

    expected_cols = [
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
    assert list(df.columns) == expected_cols
    # 1 row per item
    assert set(df["item_id"]) == {"A", "B"}
    # Basic sanity checks
    assert (df["horizon_days"] == 21).all()
    assert df["suggested_reorder_qty"].ge(0).all()
    assert df["avg_daily_delta"].ge(0).all()


def test_workflow_event_to_prediction(tmp_path: Path, monkeypatch):
    """Test the full workflow: Events → Features → Forecast → Policy → Output."""
    data_dir = tmp_path / "data"
    events_dir = tmp_path / "events"
    out_dir = tmp_path / "out"
    data_dir.mkdir(parents=True, exist_ok=True)
    events_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)

    monkeypatch.setattr(config, "DATA_DIR", data_dir)
    monkeypatch.setattr(config, "EVENTS_DIR", events_dir)
    monkeypatch.setattr(config, "OUTPUT_DIR", out_dir)

    # Item: 7-day lead time, 95% service level
    _write_csv(
        data_dir / "items.csv",
        [
            {
                "item_id": "TEST-001",
                "name": "Test Item",
                "category": "Test",
                "lead_time_days": 7,
                "safety_stock_days": 0,
                "service_level": 0.95,
                "lead_time_std_days": 0.0,
            }
        ],
    )

    # Current inventory: 50 units
    _write_csv(
        data_dir / "inventories.csv",
        [
            {"item_id": "TEST-001", "as_of_ts": "2025-11-05T09:00:00Z", "current_qty": 50}
        ],
    )

    # Events: 3 sales over 2 days (day 1: 5 units, day 2: 4 units)
    _write_events_ndjson(
        events_dir / "inventory-changes.ndjson",
        [
            {
                "event_id": "e1",
                "payload": {
                    "item_id": "TEST-001",
                    "quantity_change": -3,
                    "reason": "sale",
                    "at": "2025-11-04T10:00:00Z",
                },
            },
            {
                "event_id": "e2",
                "payload": {
                    "item_id": "TEST-001",
                    "quantity_change": -2,
                    "reason": "sale",
                    "at": "2025-11-04T14:00:00Z",
                },
            },
            {
                "event_id": "e3",
                "payload": {
                    "item_id": "TEST-001",
                    "quantity_change": -4,
                    "reason": "sale",
                    "at": "2025-11-05T11:00:00Z",
                },
            },
            {
                "event_id": "e4",
                "payload": {
                    "item_id": "TEST-001",
                    "quantity_change": 10,
                    "reason": "shipment",
                    "at": "2025-11-05T12:00:00Z",
                },
            },
        ],
    )

    print("\n" + "="*80)
    print("WORKFLOW TEST: Event Changes → Forecast Predictions")
    print("="*80)

    # Run the job
    out_path = run_batch(
        from_ts="2025-11-04T00:00:00Z",
        to_ts="2025-11-05T23:59:59Z",
        method="ma14",
        target_days=21,
    )

    assert out_path.exists()
    df = pd.read_csv(out_path)

    print("\nINPUTS:")
    print(f"  Item: TEST-001, Lead time: 7 days, Service level: 95%")
    print(f"  Current inventory: 50 units")
    print(f"  Events: 3 sales (day 1: 5 units, day 2: 4 units), 1 shipment (ignored)")

    print("\nOUTPUT (forecast_predictions.csv):")
    print(df.to_string(index=False))

    # Verify item appears
    row = df[df["item_id"] == "TEST-001"].iloc[0]

    print("\nVERIFICATIONS:")
    print(f"  ✓ avg_daily_delta: {row['avg_daily_delta']:.2f} (should reflect consumption)")
    print(f"  ✓ days_to_stockout: {row['days_to_stockout']:.1f} days")
    print(f"  ✓ suggested_reorder_qty: {row['suggested_reorder_qty']} units")
    print(f"  ✓ confidence: {row['confidence']:.2%}")

    # Features JSON should contain policy details
    features = json.loads(row["features"])
    print(f"\nFEATURES (from JSON):")
    print(f"  safety_stock: {features.get('safety_stock', 0):.2f}")
    print(f"  rop: {features.get('rop', 0):.2f}")
    print(f"  current_qty: {features.get('current_qty', 0)}")
    print(f"  method: {features.get('method', 'unknown')}")

    # Sanity checks
    assert float(row["avg_daily_delta"]) >= 0.1  # Floor applied
    assert float(row["days_to_stockout"]) > 0
    assert int(row["suggested_reorder_qty"]) >= 0
    assert 0.0 < float(row["confidence"]) <= 1.0
    assert features.get("method") == "ma14"

    print("\n" + "="*80)
    print("✓ Workflow test passed!")
    print("="*80 + "\n")


def test_stock_below_rop_triggers_order_date(tmp_path: Path, monkeypatch):
    """Test that when current_qty <= ROP, suggested_order_date is populated."""
    data_dir = tmp_path / "data"
    events_dir = tmp_path / "events"
    out_dir = tmp_path / "out"
    data_dir.mkdir(parents=True, exist_ok=True)
    events_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)

    monkeypatch.setattr(config, "DATA_DIR", data_dir)
    monkeypatch.setattr(config, "EVENTS_DIR", events_dir)
    monkeypatch.setattr(config, "OUTPUT_DIR", out_dir)

    # Item with known policy: mu=5/day, L=7, alpha=0.95 → ROP ≈ 43.7
    _write_csv(
        data_dir / "items.csv",
        [
            {
                "item_id": "LOW-STOCK",
                "name": "Low Stock Item",
                "category": "Test",
                "lead_time_days": 7,
                "safety_stock_days": 0,
                "service_level": 0.95,
                "lead_time_std_days": 0.0,
            }
        ],
    )

    # Current inventory: 15 units (below expected ROP of ~43.7)
    _write_csv(
        data_dir / "inventories.csv",
        [
            {"item_id": "LOW-STOCK", "as_of_ts": "2025-11-05T09:00:00Z", "current_qty": 15}
        ],
    )

    # Create consistent sales history to establish mu ≈ 5
    events_data = []
    for day in range(5):
        date_str = f"2025-11-{day+1:02d}T10:00:00Z"
        events_data.append({
            "event_id": f"e{day+1}",
            "payload": {
                "item_id": "LOW-STOCK",
                "quantity_change": -5,
                "reason": "sale",
                "at": date_str,
            },
        })

    _write_events_ndjson(events_dir / "inventory-changes.ndjson", events_data)

    print("\n" + "="*80)
    print("TEST: Stock Below ROP → suggested_order_date populated")
    print("="*80)

    out_path = run_batch(
        from_ts="2025-11-01T00:00:00Z",
        to_ts="2025-11-05T23:59:59Z",
        method="ma14",
        target_days=21,
    )

    df = pd.read_csv(out_path)
    row = df[df["item_id"] == "LOW-STOCK"].iloc[0]

    print(f"\nCurrent stock: 15 units")
    features = json.loads(row["features"])
    rop = features.get("rop", 0)
    print(f"ROP: {rop:.2f} units")

    print(f"\nOUTPUT:")
    print(f"  suggested_order_date: '{row['suggested_order_date']}'")
    print(f"  suggested_reorder_qty: {row['suggested_reorder_qty']}")

    # Verify order date is populated when stock <= ROP
    assert pd.notna(row["suggested_order_date"]) and str(row["suggested_order_date"]).strip() != ""
    current_qty = features.get("current_qty", 0)
    assert float(current_qty) <= rop, f"Stock ({current_qty}) should be <= ROP ({rop})"
    print("\n✓ suggested_order_date populated because stock <= ROP")
    print("="*80 + "\n")


def test_forecast_changes_with_different_date_ranges(tmp_path: Path, monkeypatch):
    """Test how forecasts change with different historical windows."""
    data_dir = tmp_path / "data"
    events_dir = tmp_path / "events"
    out_dir = tmp_path / "out"
    data_dir.mkdir(parents=True, exist_ok=True)
    events_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)

    monkeypatch.setattr(config, "DATA_DIR", data_dir)
    monkeypatch.setattr(config, "EVENTS_DIR", events_dir)
    monkeypatch.setattr(config, "OUTPUT_DIR", out_dir)

    _write_csv(
        data_dir / "items.csv",
        [
            {
                "item_id": "VOLATILE",
                "name": "Volatile Item",
                "category": "Test",
                "lead_time_days": 7,
                "safety_stock_days": 0,
                "service_level": 0.95,
                "lead_time_std_days": 0.0,
            }
        ],
    )

    _write_csv(
        data_dir / "inventories.csv",
        [
            {"item_id": "VOLATILE", "as_of_ts": "2025-11-15T09:00:00Z", "current_qty": 50}
        ],
    )

    # Create 14 days of sales: first week (3 units/day), second week (7 units/day)
    events_data = []
    for day in range(14):
        date = f"2025-11-{day+1:02d}T10:00:00Z"
        qty = 3 if day < 7 else 7  # Low early, high later
        events_data.append({
            "event_id": f"e{day+1}",
            "payload": {
                "item_id": "VOLATILE",
                "quantity_change": -qty,
                "reason": "sale",
                "at": date,
            },
        })

    _write_events_ndjson(events_dir / "inventory-changes.ndjson", events_data)

    print("\n" + "="*80)
    print("TEST: Forecast Changes with Different Date Ranges")
    print("="*80)

    # Test 1: Short window (last 3 days - should see higher demand)
    out_path_short = run_batch(
        from_ts="2025-11-13T00:00:00Z",
        to_ts="2025-11-15T23:59:59Z",
        method="ma14",
        target_days=21,
    )
    df_short = pd.read_csv(out_path_short)
    mu_short = float(df_short[df_short["item_id"] == "VOLATILE"].iloc[0]["avg_daily_delta"])

    # Test 2: Long window (all 14 days - should average lower)
    out_path_long = run_batch(
        from_ts="2025-11-01T00:00:00Z",
        to_ts="2025-11-15T23:59:59Z",
        method="ma14",
        target_days=21,
    )
    df_long = pd.read_csv(out_path_long)
    mu_long = float(df_long[df_long["item_id"] == "VOLATILE"].iloc[0]["avg_daily_delta"])

    print(f"\nSales pattern: Days 1-7: 3 units/day, Days 8-14: 7 units/day")
    print(f"\nShort window (last 3 days):")
    print(f"  avg_daily_delta: {mu_short:.2f} units/day")
    print(f"\nLong window (all 14 days):")
    print(f"  avg_daily_delta: {mu_long:.2f} units/day")

    # Short window should reflect recent higher demand better
    assert mu_short >= mu_long, "Short window should capture recent trend better"
    print("\n✓ Forecasts adjust based on date range")
    print("="*80 + "\n")


def test_multiple_forecast_methods_comparison(tmp_path: Path, monkeypatch):
    """Test how different forecast methods (ma7, ma14, exp_smooth) produce different results."""
    data_dir = tmp_path / "data"
    events_dir = tmp_path / "events"
    out_dir = tmp_path / "out"
    data_dir.mkdir(parents=True, exist_ok=True)
    events_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)

    monkeypatch.setattr(config, "DATA_DIR", data_dir)
    monkeypatch.setattr(config, "EVENTS_DIR", events_dir)
    monkeypatch.setattr(config, "OUTPUT_DIR", out_dir)

    _write_csv(
        data_dir / "items.csv",
        [
            {
                "item_id": "METHOD-TEST",
                "name": "Method Test Item",
                "category": "Test",
                "lead_time_days": 7,
                "safety_stock_days": 0,
                "service_level": 0.95,
                "lead_time_std_days": 0.0,
            }
        ],
    )

    _write_csv(
        data_dir / "inventories.csv",
        [
            {"item_id": "METHOD-TEST", "as_of_ts": "2025-11-10T09:00:00Z", "current_qty": 40}
        ],
    )

    # Create 10 days of sales: increasing trend (1, 2, 3, ..., 10)
    events_data = []
    for day in range(10):
        date = f"2025-11-{day+1:02d}T10:00:00Z"
        events_data.append({
            "event_id": f"e{day+1}",
            "payload": {
                "item_id": "METHOD-TEST",
                "quantity_change": -(day + 1),
                "reason": "sale",
                "at": date,
            },
        })

    _write_events_ndjson(events_dir / "inventory-changes.ndjson", events_data)

    print("\n" + "="*80)
    print("TEST: Forecast Method Comparison")
    print("="*80)
    print("\nSales pattern: Increasing trend (1, 2, 3, ..., 10 units/day)")

    results = {}
    for method in ["ma7", "ma14", "exp_smooth"]:
        out_path = run_batch(
            from_ts="2025-11-01T00:00:00Z",
            to_ts="2025-11-10T23:59:59Z",
            method=method,
            target_days=21,
        )
        df = pd.read_csv(out_path)
        row = df[df["item_id"] == "METHOD-TEST"].iloc[0]
        results[method] = float(row["avg_daily_delta"])
        print(f"\n{method}:")
        print(f"  avg_daily_delta: {results[method]:.2f} units/day")

    # Exp smooth should be most responsive to recent trend
    # MA7 should be more responsive than MA14
    assert results["exp_smooth"] >= results["ma7"], "Exp smooth should capture recent trend"
    assert results["ma7"] >= results["ma14"], "MA7 should be more responsive than MA14"
    print("\n✓ Methods produce different forecasts based on responsiveness")
    print("="*80 + "\n")


def test_high_demand_scenario(tmp_path: Path, monkeypatch):
    """Test scenario with high demand and stock running low."""
    data_dir = tmp_path / "data"
    events_dir = tmp_path / "events"
    out_dir = tmp_path / "out"
    data_dir.mkdir(parents=True, exist_ok=True)
    events_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)

    monkeypatch.setattr(config, "DATA_DIR", data_dir)
    monkeypatch.setattr(config, "EVENTS_DIR", events_dir)
    monkeypatch.setattr(config, "OUTPUT_DIR", out_dir)

    _write_csv(
        data_dir / "items.csv",
        [
            {
                "item_id": "HIGH-DEMAND",
                "name": "High Demand Item",
                "category": "Test",
                "lead_time_days": 7,
                "safety_stock_days": 0,
                "service_level": 0.95,
                "lead_time_std_days": 0.0,
            }
        ],
    )

    # Very low stock: 10 units
    _write_csv(
        data_dir / "inventories.csv",
        [
            {"item_id": "HIGH-DEMAND", "as_of_ts": "2025-11-10T09:00:00Z", "current_qty": 10}
        ],
    )

    # High consistent demand: 8 units/day for 7 days
    events_data = []
    for day in range(7):
        date = f"2025-11-{day+3:02d}T10:00:00Z"
        events_data.append({
            "event_id": f"e{day+1}",
            "payload": {
                "item_id": "HIGH-DEMAND",
                "quantity_change": -8,
                "reason": "sale",
                "at": date,
            },
        })

    _write_events_ndjson(events_dir / "inventory-changes.ndjson", events_data)

    print("\n" + "="*80)
    print("TEST: High Demand + Low Stock Scenario")
    print("="*80)

    out_path = run_batch(
        from_ts="2025-11-03T00:00:00Z",
        to_ts="2025-11-10T23:59:59Z",
        method="ma14",
        target_days=21,
    )

    df = pd.read_csv(out_path)
    row = df[df["item_id"] == "HIGH-DEMAND"].iloc[0]
    features = json.loads(row["features"])

    print(f"\nINPUTS:")
    print(f"  Current stock: 10 units")
    print(f"  Demand: 8 units/day (consistent)")
    print(f"  Lead time: 7 days")

    print(f"\nOUTPUT:")
    print(f"  avg_daily_delta: {row['avg_daily_delta']:.2f} units/day")
    print(f"  days_to_stockout: {row['days_to_stockout']:.1f} days")
    print(f"  suggested_reorder_qty: {row['suggested_reorder_qty']} units")
    print(f"  suggested_order_date: '{row['suggested_order_date']}'")
    print(f"  ROP: {features.get('rop', 0):.2f} units")

    # Verify urgent reorder scenario
    assert float(row["days_to_stockout"]) < 2, "Should run out quickly"
    assert int(row["suggested_reorder_qty"]) > 0, "Should suggest large order"
    assert pd.notna(row["suggested_order_date"]) and str(row["suggested_order_date"]).strip() != "", "Should trigger order date"
    print("\n✓ High demand scenario correctly triggers urgent reorder")
    print("="*80 + "\n")

