"""Tests for the Phase 1 ship-gate evaluator in backtest.py."""
import pandas as pd

from src.backtest import _aggregate_daily, _aggregate_lead_time, evaluate_ship_gate


def _comparison(rows: list[tuple]) -> pd.DataFrame:
    return pd.DataFrame(rows, columns=["method", "category", "scored", "units_sold", "wape", "bias"])


def test_ship_when_new_wins_overall_and_no_category_regresses():
    df = _comparison([
        ("tsb",          "Plushie",  100, 1000, 0.40, -1.0),
        ("dow_weighted", "Plushie",  100, 1000, 0.85, -5.0),
        ("tsb",          "Keychain",  50,  500, 0.55, -2.0),
        ("dow_weighted", "Keychain",  50,  500, 0.90, -8.0),
    ])
    gate = evaluate_ship_gate(df, new_method="tsb", baseline_method="dow_weighted")
    assert gate["decision"] == "SHIP"
    assert gate["regressed_categories"] == []
    assert gate["overall"]["tsb"]["wape"] < gate["overall"]["dow_weighted"]["wape"]


def test_hold_when_overall_regresses():
    df = _comparison([
        ("tsb",          "Plushie", 100, 1000, 0.95, -3.0),
        ("dow_weighted", "Plushie", 100, 1000, 0.50, -1.0),
    ])
    gate = evaluate_ship_gate(df, new_method="tsb", baseline_method="dow_weighted")
    assert gate["decision"] == "HOLD"


def test_hold_when_one_category_regresses_more_than_25_percent():
    """Overall is fine but a single category got materially worse."""
    df = _comparison([
        ("tsb",          "Plushie",  100, 1000, 0.40, -1.0),
        ("dow_weighted", "Plushie",  100, 1000, 0.85, -5.0),
        ("tsb",          "Top Toy",   50,  500, 0.62, -2.0),  # baseline 0.45 -> +37.7%
        ("dow_weighted", "Top Toy",   50,  500, 0.45, -1.0),
    ])
    gate = evaluate_ship_gate(df, new_method="tsb", baseline_method="dow_weighted")
    assert gate["decision"] == "HOLD"
    assert any(r["category"] == "Top Toy" for r in gate["regressed_categories"])


def _raw(rows: list[tuple]) -> pd.DataFrame:
    """Helper for building the raw per-day frame consumed by the aggregators."""
    return pd.DataFrame(rows, columns=[
        "method", "origin_days_ago", "item_id", "category", "date", "predicted", "actual",
    ])


def test_lt_wape_rewards_correct_rate_on_bursty_actuals():
    """A point estimate of 3/day vs lumpy actuals [0,0,0,15,0,0,6] over a 7-day window.

    daily_wape penalizes this severely (per-day error is huge on burst days)
    while lt_wape recognizes 21 predicted vs 21 actual = 0% lead-time error.
    """
    rows = []
    actuals = [0, 0, 0, 15, 0, 0, 6]  # sums to 21
    for i, a in enumerate(actuals):
        rows.append(("tsb", 7, "I1", "Plushie", f"d{i}", 3.0, a))
    df = _raw(rows)

    lt = _aggregate_lead_time(df)
    daily = _aggregate_daily(df)

    # LT: predicted_sum = 3*7 = 21, actual_sum = 21, abs_err = 0 -> WAPE = 0
    assert abs(float(lt.iloc[0]["wape"])) < 1e-9
    # Daily: per-day errors |3-0|*5 + |3-15| + |3-6| = 15+12+3 = 30, units = 21 -> WAPE > 1.0
    assert float(daily.iloc[0]["wape"]) > 1.0


def test_lt_wape_flags_real_underprediction():
    """When the rate forecast is genuinely too low, lt_wape catches it."""
    rows = []
    for i, a in enumerate([0, 0, 0, 15, 0, 0, 6]):  # 21 actual
        rows.append(("tsb", 7, "I1", "Plushie", f"d{i}", 1.0, a))  # predicts 7 over 7 days
    df = _raw(rows)
    lt = _aggregate_lead_time(df)
    # predicted_sum = 7, actual_sum = 21, abs_err = 14, WAPE = 14/21 = 0.67
    assert abs(float(lt.iloc[0]["wape"]) - (14 / 21)) < 1e-6
    # Signed error: 7 - 21 = -14 -> bias < 0
    assert float(lt.iloc[0]["bias"]) < 0


def test_lt_wape_aggregates_multiple_origins_per_item():
    """Each (origin, item) pair contributes one row to LT scoring; aggregated per category."""
    rows = [
        # Origin A: predicted 2/day over 5 days = 10, actual sums to 8
        ("tsb", 7,  "I1", "Plushie", "d1", 2.0, 0),
        ("tsb", 7,  "I1", "Plushie", "d2", 2.0, 8),
        ("tsb", 7,  "I1", "Plushie", "d3", 2.0, 0),
        ("tsb", 7,  "I1", "Plushie", "d4", 2.0, 0),
        ("tsb", 7,  "I1", "Plushie", "d5", 2.0, 0),
        # Origin B: predicted 4/day over 5 days = 20, actual sums to 22
        ("tsb", 14, "I1", "Plushie", "d6",  4.0, 22),
        ("tsb", 14, "I1", "Plushie", "d7",  4.0, 0),
        ("tsb", 14, "I1", "Plushie", "d8",  4.0, 0),
        ("tsb", 14, "I1", "Plushie", "d9",  4.0, 0),
        ("tsb", 14, "I1", "Plushie", "d10", 4.0, 0),
    ]
    lt = _aggregate_lead_time(_raw(rows))
    # scored = 2 (one row per origin), units_sold = 30
    assert int(lt.iloc[0]["scored"]) == 2
    assert int(lt.iloc[0]["units_sold"]) == 30
    # abs_err_total = |10-8| + |20-22| = 4 -> WAPE = 4 / 30
    assert abs(float(lt.iloc[0]["wape"]) - (4 / 30)) < 1e-6


def test_near_zero_baseline_does_not_count_as_regression():
    """Avoid spurious 'regression' alerts when the baseline is essentially perfect."""
    df = _comparison([
        ("tsb",          "Tiny",   10,  50, 0.05, 0.0),
        ("dow_weighted", "Tiny",   10,  50, 0.0,  0.0),
        ("tsb",          "Plushie", 100, 1000, 0.40, -1.0),
        ("dow_weighted", "Plushie", 100, 1000, 0.85, -5.0),
    ])
    gate = evaluate_ship_gate(df, new_method="tsb", baseline_method="dow_weighted")
    assert gate["decision"] == "SHIP"
    assert gate["regressed_categories"] == []
