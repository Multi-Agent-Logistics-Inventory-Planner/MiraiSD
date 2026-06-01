"""Tests for the censored-Poisson MLE used when stockout days are present.

Replaces the prior drop-stockout-days filter: stockout days are treated as
right-censored observations (demand was AT LEAST X), not dropped or taken
at face value.
"""
import numpy as np
import pandas as pd
import pytest

from src import config
from src.forecast import _censored_poisson_rate, estimate_mu_sigma


def test_empty_input_returns_floor():
    rate = _censored_poisson_rate(observed=np.array([]), censored=np.array([], dtype=bool))
    assert rate == config.MU_FLOOR


def test_no_censoring_matches_sample_mean():
    """No stockouts -> degenerate case; MLE returns sample mean."""
    obs = np.array([3.0, 4.0, 5.0, 6.0, 2.0])
    rate = _censored_poisson_rate(observed=obs, censored=np.zeros(5, dtype=bool))
    assert rate == pytest.approx(4.0, abs=1e-9)


def test_censored_pulls_rate_upward():
    """Half the days are censored at k=5 with true lambda=5.

    Naive averaging would underweight: it treats censored=5 as actual=5 and
    misses that demand could have been higher. The MLE accounts for the
    P(X >= 5 | lambda) survival term and recovers a rate that is at least
    as high as the naive mean.
    """
    rng = np.random.default_rng(42)
    true_lambda = 5.0
    # 30 in-stock observations from Poisson(5), 30 censored at "observed >= 5"
    in_stock = rng.poisson(true_lambda, size=30).astype(float)
    censored_obs = np.full(30, 5.0)
    obs = np.concatenate([in_stock, censored_obs])
    cens = np.array([False] * 30 + [True] * 30, dtype=bool)

    rate = _censored_poisson_rate(observed=obs, censored=cens, initial=float(obs.mean()))
    naive = float(obs.mean())

    # MLE should be at least as high as naive (censored term can only push up).
    assert rate >= naive - 1e-6
    # And should land in a reasonable neighborhood of the true lambda.
    assert 3.5 < rate < 8.0


def test_all_days_censored_still_returns_finite_rate():
    """All censored observations -> optimizer should still pick a rate that
    makes 'demand >= observed' plausible, never NaN or below MU_FLOOR."""
    obs = np.array([5.0, 5.0, 5.0, 5.0, 5.0])
    rate = _censored_poisson_rate(observed=obs, censored=np.ones(5, dtype=bool))
    assert np.isfinite(rate)
    assert rate >= config.MU_FLOOR
    # Censored at 5 with all censored -> MLE goes high (truth could be anything).
    assert rate >= 5.0


def test_rate_floored_at_mu_floor():
    """Pathological input -> MU_FLOOR floor enforced."""
    obs = np.array([0.0, 0.0, 0.0])
    rate = _censored_poisson_rate(observed=obs, censored=np.zeros(3, dtype=bool))
    assert rate >= config.MU_FLOOR


def test_pipeline_integration_uses_censored_when_flag_on(monkeypatch):
    """Item with 30 in-stock days at 2/day + 10 stockout days at observed-3/day.

    Naive average = (30*2 + 10*3)/40 = 2.25. The censored MLE should give a
    higher rate because the 10 stockout days carry "demand was at least 3"
    information."""
    monkeypatch.setattr(config, "CENSORED_DEMAND_ENABLED", True)
    monkeypatch.setattr(config, "CENSORED_DEMAND_MIN_STOCKOUT_DAYS", 1)

    dates = pd.date_range("2025-10-01", periods=40, freq="D")
    consumption = [2.0] * 30 + [3.0] * 10
    is_stockout = [False] * 30 + [True] * 10

    feats = pd.DataFrame({
        "date": dates,
        "item_id": ["A"] * 40,
        "consumption": consumption,
        "is_stockout": is_stockout,
    })

    result = estimate_mu_sigma(feats, method="dow_weighted")
    mu = result.iloc[0]["mu_hat"]
    naive = 2.25

    assert mu > naive, f"censored MLE ({mu}) should exceed naive mean ({naive})"


def test_pipeline_skips_mle_when_stockout_fraction_too_high(monkeypatch):
    """Above CENSORED_DEMAND_MAX_STOCKOUT_PCT, the MLE is unidentifiable
    (it tries to estimate 'rate-if-we-had-inventory') and blows up to the
    optimizer's upper bound. Live 2026-05-31 ablation: items at >50% stockout
    showed median 10x bump in mu_hat. Above the threshold we fall back to
    naive mean."""
    monkeypatch.setattr(config, "CENSORED_DEMAND_ENABLED", True)
    monkeypatch.setattr(config, "CENSORED_DEMAND_MIN_STOCKOUT_DAYS", 1)
    monkeypatch.setattr(config, "CENSORED_DEMAND_MAX_STOCKOUT_PCT", 0.5)

    # 70% stockout (above threshold) -> should fall back to naive.
    dates = pd.date_range("2025-10-01", periods=20, freq="D")
    consumption = [2.0] * 6 + [5.0] * 14  # 6 in-stock at 2/day, 14 stockout at 5/day
    is_stockout = [False] * 6 + [True] * 14

    feats = pd.DataFrame({
        "date": dates,
        "item_id": ["A"] * 20,
        "consumption": consumption,
        "is_stockout": is_stockout,
    })

    result = estimate_mu_sigma(feats, method="dow_weighted")
    mu = result.iloc[0]["mu_hat"]
    # Naive mean = (6*2 + 14*5)/20 = 4.1. Expect we got it (MLE skipped).
    assert mu == pytest.approx(4.1, abs=0.01), (
        f"expected naive 4.1 since stockout_pct=70% > threshold, got {mu}"
    )


def test_pipeline_skips_censored_when_flag_off(monkeypatch):
    """Same input as above but flag off -> back to naive mean."""
    monkeypatch.setattr(config, "CENSORED_DEMAND_ENABLED", False)

    dates = pd.date_range("2025-10-01", periods=40, freq="D")
    consumption = [2.0] * 30 + [3.0] * 10
    is_stockout = [False] * 30 + [True] * 10

    feats = pd.DataFrame({
        "date": dates,
        "item_id": ["A"] * 40,
        "consumption": consumption,
        "is_stockout": is_stockout,
    })

    result = estimate_mu_sigma(feats, method="dow_weighted")
    mu = result.iloc[0]["mu_hat"]

    assert mu == pytest.approx(2.25, abs=0.01)
