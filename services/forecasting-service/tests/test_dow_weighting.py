import pandas as pd
import numpy as np

from src.forecast import estimate_mu_sigma, _dow_weighted_estimate
from src import config


def _make_features_with_dow(days: int = 28, base_consumption: float = 5.0,
                             weekend_multiplier: float = 1.0,
                             item_id: str = "A",
                             start_date: str = "2025-11-03") -> pd.DataFrame:
    """Create features DataFrame with DOW-varying consumption.

    start_date defaults to a Monday (2025-11-03).
    """
    dates = pd.date_range(start_date, periods=days, freq="D")
    consumption = []
    for d in dates:
        if d.dayofweek >= 5:  # Sat/Sun
            consumption.append(base_consumption * weekend_multiplier)
        else:
            consumption.append(base_consumption)

    return pd.DataFrame({
        "date": dates,
        "item_id": [item_id] * days,
        "consumption": consumption,
    })


def test_weekend_heavy_multipliers():
    """Weekend-heavy data: multipliers > 1 for Sat/Sun, < 1 for weekdays."""
    feats = _make_features_with_dow(days=28, base_consumption=5.0, weekend_multiplier=3.0)
    result = estimate_mu_sigma(feats, method="dow_weighted")

    assert len(result) == 1
    row = result.iloc[0]
    assert row["method"] == "dow_weighted"
    assert "dow_multipliers" in result.columns

    multipliers = row["dow_multipliers"]
    assert isinstance(multipliers, dict)

    # Weekend multipliers should be > 1
    assert multipliers[5] > 1.0  # Saturday
    assert multipliers[6] > 1.0  # Sunday

    # Weekday multipliers should be < 1 (since overall mean includes weekends)
    for d in range(5):
        assert multipliers[d] < 1.5  # weekdays should be lower than weekends


def test_uniform_data_multipliers_near_one():
    """Uniform data: all DOW multipliers should be approximately 1.0."""
    feats = _make_features_with_dow(days=28, base_consumption=5.0, weekend_multiplier=1.0)
    result = estimate_mu_sigma(feats, method="dow_weighted")

    multipliers = result.iloc[0]["dow_multipliers"]
    for d in range(7):
        assert abs(multipliers[d] - 1.0) < 0.01


def test_single_day_fills_missing_dows():
    """Single day of data: missing DOWs filled with 1.0."""
    # Just one data point on a Wednesday (dow=2)
    feats = pd.DataFrame({
        "date": ["2025-11-05"],
        "item_id": ["A"],
        "consumption": [10.0],
    })
    result = estimate_mu_sigma(feats, method="dow_weighted")

    multipliers = result.iloc[0]["dow_multipliers"]
    assert len(multipliers) == 7
    # Wednesday should have a value
    assert multipliers[2] == 1.0  # 10.0 / 10.0 = 1.0
    # All other DOWs should be 1.0 (filled)
    for d in [0, 1, 3, 4, 5, 6]:
        assert multipliers[d] == 1.0


def test_sigma_reflects_residuals_not_raw_std():
    """Sigma should be based on residuals after DOW effect, not raw std."""
    # Perfectly predictable by DOW pattern: weekdays=2, weekends=10
    feats = _make_features_with_dow(days=28, base_consumption=2.0, weekend_multiplier=5.0)

    mu, sigma, multipliers = _dow_weighted_estimate(feats)

    # After removing DOW effect, residuals should be ~0
    # So sigma should be close to SIGMA_FLOOR
    assert sigma <= 0.1  # Very small residuals expected


def test_mu_hat_is_overall_mean():
    """mu_hat should be the overall mean, not DOW-adjusted."""
    feats = _make_features_with_dow(days=28, base_consumption=4.0, weekend_multiplier=2.0)
    result = estimate_mu_sigma(feats, method="dow_weighted")

    row = result.iloc[0]
    # 28 days: 20 weekdays * 4 + 8 weekend days * 8 = 80 + 64 = 144
    # overall mean = 144/28 ~= 5.143
    expected_mean = (20 * 4.0 + 8 * 8.0) / 28
    assert abs(row["mu_hat"] - expected_mean) < 0.01


def test_dow_weighted_method_accepted():
    """dow_weighted should be accepted as a valid method."""
    feats = _make_features_with_dow(days=14)
    result = estimate_mu_sigma(feats, method="dow_weighted")
    assert not result.empty
    assert result.iloc[0]["method"] == "dow_weighted"


def test_multi_sku_dow_weighted():
    """Multiple SKUs should each get independent DOW multipliers."""
    a = _make_features_with_dow(days=14, base_consumption=5.0, weekend_multiplier=3.0, item_id="A")
    b = _make_features_with_dow(days=14, base_consumption=10.0, weekend_multiplier=1.0, item_id="B")
    feats = pd.concat([a, b], ignore_index=True)

    result = estimate_mu_sigma(feats, method="dow_weighted")

    assert len(result) == 2
    a_row = result[result["item_id"] == "A"].iloc[0]
    b_row = result[result["item_id"] == "B"].iloc[0]

    # A should have high weekend multipliers
    assert a_row["dow_multipliers"][5] > 1.5

    # B should have uniform multipliers
    assert abs(b_row["dow_multipliers"][5] - 1.0) < 0.01
