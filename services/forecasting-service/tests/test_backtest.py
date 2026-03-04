import pandas as pd
import numpy as np

from src.backtest import compute_mape


def _make_forecasts(data: list[dict]) -> pd.DataFrame:
    if not data:
        return pd.DataFrame(columns=["item_id", "computed_at", "mu_hat"])
    return pd.DataFrame(data)


def _make_usage(data: list[dict]) -> pd.DataFrame:
    if not data:
        return pd.DataFrame(columns=["item_id", "date", "consumption"])
    return pd.DataFrame(data)


def test_perfect_forecast_mape_zero():
    """Perfect forecast: MAPE = 0."""
    forecasts = _make_forecasts([
        {"item_id": "A", "computed_at": "2025-01-01", "mu_hat": 5.0},
    ])
    usage = _make_usage([
        {"item_id": "A", "date": "2025-01-01", "consumption": 5.0},
        {"item_id": "A", "date": "2025-01-02", "consumption": 5.0},
        {"item_id": "A", "date": "2025-01-03", "consumption": 5.0},
    ])

    result = compute_mape(forecasts, usage, horizon_days=14, epsilon=0.1)

    assert len(result) == 1
    row = result.iloc[0]
    assert row["mape"] == 0.0
    assert row["forecast_mu"] == 5.0
    assert row["actual_mu"] == 5.0


def test_known_error():
    """Known error: verify MAPE calculation."""
    forecasts = _make_forecasts([
        {"item_id": "A", "computed_at": "2025-01-01", "mu_hat": 10.0},
    ])
    # Actual mean = 5.0, forecast = 10.0
    # MAPE = |5 - 10| / max(5, 0.1) = 5/5 = 1.0
    usage = _make_usage([
        {"item_id": "A", "date": "2025-01-01", "consumption": 5.0},
        {"item_id": "A", "date": "2025-01-02", "consumption": 5.0},
    ])

    result = compute_mape(forecasts, usage, horizon_days=14, epsilon=0.1)

    row = result.iloc[0]
    assert abs(row["mape"] - 1.0) < 1e-9
    assert row["forecast_mu"] == 10.0
    assert row["actual_mu"] == 5.0


def test_zero_actual_uses_epsilon_floor():
    """Zero actual demand: epsilon floor prevents division by zero."""
    forecasts = _make_forecasts([
        {"item_id": "A", "computed_at": "2025-01-01", "mu_hat": 2.0},
    ])
    usage = _make_usage([
        {"item_id": "A", "date": "2025-01-01", "consumption": 0.0},
        {"item_id": "A", "date": "2025-01-02", "consumption": 0.0},
    ])

    result = compute_mape(forecasts, usage, horizon_days=14, epsilon=0.1)

    row = result.iloc[0]
    # MAPE = |0 - 2| / max(0, 0.1) = 2 / 0.1 = 20.0
    assert abs(row["mape"] - 20.0) < 1e-9


def test_no_historical_forecast_returns_empty():
    """No historical forecast data: empty result."""
    forecasts = _make_forecasts([])
    usage = _make_usage([
        {"item_id": "A", "date": "2025-01-01", "consumption": 5.0},
    ])

    result = compute_mape(forecasts, usage)

    assert result.empty
    assert "mape" in result.columns


def test_no_usage_returns_empty():
    """No usage data: empty result."""
    forecasts = _make_forecasts([
        {"item_id": "A", "computed_at": "2025-01-01", "mu_hat": 5.0},
    ])
    usage = _make_usage([])

    result = compute_mape(forecasts, usage)

    assert result.empty


def test_backtest_days_accurate():
    """Backtest days should count unique dates in usage data."""
    forecasts = _make_forecasts([
        {"item_id": "A", "computed_at": "2025-01-01", "mu_hat": 5.0},
    ])
    usage = _make_usage([
        {"item_id": "A", "date": "2025-01-01", "consumption": 4.0},
        {"item_id": "A", "date": "2025-01-02", "consumption": 6.0},
        {"item_id": "A", "date": "2025-01-03", "consumption": 5.0},
    ])

    result = compute_mape(forecasts, usage, horizon_days=14, epsilon=0.1)

    row = result.iloc[0]
    assert row["backtest_days"] == 3


def test_multi_item():
    """Multiple items computed independently."""
    forecasts = _make_forecasts([
        {"item_id": "A", "computed_at": "2025-01-01", "mu_hat": 5.0},
        {"item_id": "B", "computed_at": "2025-01-01", "mu_hat": 10.0},
    ])
    usage = _make_usage([
        {"item_id": "A", "date": "2025-01-01", "consumption": 5.0},
        {"item_id": "B", "date": "2025-01-01", "consumption": 8.0},
    ])

    result = compute_mape(forecasts, usage)

    assert len(result) == 2
    a_row = result[result["item_id"] == "A"].iloc[0]
    b_row = result[result["item_id"] == "B"].iloc[0]
    assert a_row["mape"] == 0.0
    # MAPE for B: |8 - 10| / max(8, 0.1) = 2/8 = 0.25
    assert abs(b_row["mape"] - 0.25) < 1e-9
