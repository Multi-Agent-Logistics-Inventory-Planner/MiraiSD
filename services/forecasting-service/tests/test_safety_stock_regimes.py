"""Tests for the CV-routed Poisson / NegBin safety stock dispatch."""
import numpy as np
import pandas as pd

from src import policy
from src.features import compute_per_sku_cv


def _series(values: list[float]) -> pd.Series:
    return pd.Series(values, dtype=float)


def test_steady_regime_uses_poisson():
    """Steady regime: SS should match poisson.ppf(alpha, mu*L) - mu*L."""
    mu = _series([4.0])
    sigma = _series([2.0])  # var < mu*L => NegBin would degenerate anyway
    regime = pd.Series(["steady"])
    ss = policy.compute_safety_stock_vectorized(
        mu_hat=mu, sigma_d_hat=sigma, L=10.0, alpha=0.95, regime=regime,
    )
    from scipy.stats import poisson
    expected = poisson.ppf(0.95, 4.0 * 10.0) - 4.0 * 10.0
    assert abs(float(ss.iloc[0]) - max(expected, 0.0)) < 1e-6


def test_bursty_regime_buffers_more_than_poisson_when_overdispersed():
    """Bursty + overdispersion: NegBin buffer should exceed the Poisson one."""
    mu = _series([4.0])
    sigma = _series([8.0])  # var = 64 > mu = 4, strong overdispersion
    bursty = pd.Series(["bursty"])
    steady = pd.Series(["steady"])

    ss_bursty = policy.compute_safety_stock_vectorized(
        mu_hat=mu, sigma_d_hat=sigma, L=10.0, alpha=0.95, regime=bursty,
    )
    ss_steady = policy.compute_safety_stock_vectorized(
        mu_hat=mu, sigma_d_hat=sigma, L=10.0, alpha=0.95, regime=steady,
    )
    assert float(ss_bursty.iloc[0]) > float(ss_steady.iloc[0]) + 5.0


def test_bursty_without_overdispersion_falls_back_to_poisson():
    """When variance <= mean*L, NegBin reduces to Poisson; SS must equal Poisson SS."""
    mu = _series([5.0])
    sigma = _series([1.0])  # var = 1 << mu = 5, no overdispersion
    ss_bursty = policy.compute_safety_stock_vectorized(
        mu_hat=mu, sigma_d_hat=sigma, L=7.0, alpha=0.95, regime=pd.Series(["bursty"]),
    )
    ss_steady = policy.compute_safety_stock_vectorized(
        mu_hat=mu, sigma_d_hat=sigma, L=7.0, alpha=0.95, regime=pd.Series(["steady"]),
    )
    assert abs(float(ss_bursty.iloc[0]) - float(ss_steady.iloc[0])) < 1e-6


def test_zero_mu_returns_zero_safety_stock():
    """Degenerate inputs do not produce NaN buffers."""
    mu = _series([0.0])
    sigma = _series([0.0])
    ss = policy.compute_safety_stock_vectorized(
        mu_hat=mu, sigma_d_hat=sigma, L=5.0, alpha=0.95, regime=pd.Series(["bursty"]),
    )
    assert float(ss.iloc[0]) == 0.0


def test_legacy_mode_unchanged_when_regime_is_none():
    """Backward compat: without regime, formula is z * sigma * sqrt(L)."""
    mu = _series([4.0])
    sigma = _series([2.0])
    ss = policy.compute_safety_stock_vectorized(
        mu_hat=mu, sigma_d_hat=sigma, L=9.0, alpha=0.95,
    )
    from statistics import NormalDist
    expected = NormalDist().inv_cdf(0.95) * 2.0 * np.sqrt(9.0)
    assert abs(float(ss.iloc[0]) - expected) < 1e-6


def test_cv_excludes_zero_days_and_uses_sale_day_signal():
    """compute_per_sku_cv: only sale days enter the std/mean ratio."""
    df = pd.DataFrame({
        "item_id": ["A"] * 10 + ["B"] * 10,
        "date": pd.date_range("2026-03-01", periods=10).tolist() * 2,
        "consumption": [0, 0, 0, 0, 0, 10, 0, 0, 0, 10] + [5, 5, 5, 5, 5, 5, 5, 5, 5, 5],
    })
    cvs = compute_per_sku_cv(df)
    # A has only constant 10s on sale days -> CV = 0 (no variation on sale days).
    assert "A" in cvs and abs(cvs["A"]) < 1e-9
    # B is constant 5 every day -> CV = 0 (no variation).
    assert "B" in cvs and abs(cvs["B"]) < 1e-9


def test_cv_reflects_burstiness_on_uneven_sale_days():
    """Uneven sale-day demand produces CV > 0; zero-day items drop out."""
    df = pd.DataFrame({
        "item_id": ["bursty"] * 10 + ["dead"] * 10,
        "date": pd.date_range("2026-03-01", periods=10).tolist() * 2,
        "consumption": [0, 1, 0, 50, 0, 0, 5, 0, 100, 0] + [0] * 10,
    })
    cvs = compute_per_sku_cv(df)
    assert cvs["bursty"] > 0.5  # high sale-day variation
    assert "dead" not in cvs    # no sale days at all
