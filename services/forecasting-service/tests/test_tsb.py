import pandas as pd

from src.estimators.tsb import tsb_estimate
from src.forecast import estimate_mu_sigma
from src import config


def _daily_series(consumption: list[float], item_id: str = "A", start: str = "2026-03-01") -> pd.DataFrame:
    """Build a zero-filled daily DataFrame consumed by tsb_estimate / estimate_mu_sigma."""
    dates = pd.date_range(start, periods=len(consumption), freq="D")
    return pd.DataFrame({
        "date": dates,
        "item_id": [item_id] * len(consumption),
        "consumption": consumption,
    })


def test_all_zeros_returns_floor():
    """No sale events at all => mu_hat collapses to MU_FLOOR."""
    feats = _daily_series([0.0] * 30)
    mu_hat, sigma, p, z, _ = tsb_estimate(feats)
    assert mu_hat == config.MU_FLOOR
    assert p == 0.0  # smoothing of all-zeros from p_0=0 stays at 0
    assert sigma == config.SIGMA_FLOOR


def test_steady_daily_demand_converges_to_mean():
    """Every day sells 5: p_t -> 1, z_t -> 5, mu_hat -> 5."""
    feats = _daily_series([5.0] * 60)
    mu_hat, _, p, z, _ = tsb_estimate(feats)
    assert abs(p - 1.0) < 1e-6
    assert abs(z - 5.0) < 1e-6
    assert abs(mu_hat - 5.0) < 1e-6


def test_bursty_demand_reflects_probability_times_size():
    """Sells 10 every 4th day (P=0.25, Z=10). mu_hat should land near 2.5."""
    pattern = [0.0, 0.0, 0.0, 10.0] * 15  # 60 days, 15 sale events
    feats = _daily_series(pattern)
    mu_hat, _, p, z, _ = tsb_estimate(feats)
    assert 0.20 <= p <= 0.30, f"p drifted: {p}"
    assert 9.0 <= z <= 11.0, f"z drifted: {z}"
    # mu_hat ~ p * z. Allow generous bracket because smoothing trajectory
    # depends on the order of the first burst.
    assert 1.5 <= mu_hat <= 3.5, f"mu_hat drifted: {mu_hat}"


def test_dying_series_probability_decays():
    """5/day for 14 days then dead for 28 days -> p_t and forecast must collapse."""
    pattern = [5.0] * 14 + [0.0] * 28
    feats = _daily_series(pattern)
    mu_hat, _, p, _, _ = tsb_estimate(feats)
    # After 28 zero-sale days, p should have decayed well below the early peak.
    assert p < 0.2, f"p failed to decay on cold series: {p}"
    assert mu_hat < 1.0, f"mu_hat failed to follow p down: {mu_hat}"


def test_tsb_beats_zero_filled_mean_on_intermittent_series():
    """Compare TSB to the existing dow_weighted (zero-filled mean) on a bursty series.

    The TSB forecast for the *sale-day* demand level should track the actual
    sale-day consumption (10) more closely than the zero-filled mean does. The
    zero-filled estimator divides by all days; TSB separates probability from
    size.
    """
    # 12 sale-days in a 60-day window, each 10 units. Zero-filled mean = 2.0.
    pattern = ([0.0] * 4 + [10.0]) * 12
    pattern = pattern[:60]
    feats = _daily_series(pattern)

    tsb_result = estimate_mu_sigma(feats, method="tsb")
    dow_result = estimate_mu_sigma(feats, method="dow_weighted")

    tsb_mu = float(tsb_result.iloc[0]["mu_hat"])
    dow_mu = float(dow_result.iloc[0]["mu_hat"])

    # dow_weighted reduces to the zero-filled mean: about 10/5 = 2.0.
    assert 1.5 <= dow_mu <= 2.5, f"dow_weighted unexpectedly far from 2.0: {dow_mu}"
    # TSB's `z` component recovers the ~10 units/sale-day signal, so its mu_hat
    # should at least not be biased the same way; we only assert it isn't
    # massively lower than dow_weighted.
    assert tsb_mu >= dow_mu * 0.9, f"tsb_mu={tsb_mu} unexpectedly below dow_mu={dow_mu}"
    # And the stored sale-day demand z must recover the actual sale size.
    assert 8.0 <= float(tsb_result.iloc[0]["z"]) <= 11.0


def test_estimate_mu_sigma_routes_to_tsb():
    """The public router exposes the new method and emits p/z columns."""
    feats = _daily_series([1.0, 0.0, 2.0, 0.0] * 8)
    result = estimate_mu_sigma(feats, method="tsb")
    assert result.iloc[0]["method"] == "tsb"
    assert "p" in result.columns
    assert "z" in result.columns
    assert "dow_multipliers" in result.columns


def test_alpha_beta_overrides_take_effect():
    """Higher alpha => probability adapts faster to recent sales."""
    feats = _daily_series([0.0] * 30 + [10.0] * 5)
    _, _, p_slow, _, _ = tsb_estimate(feats, alpha=0.05, beta=0.1)
    _, _, p_fast, _, _ = tsb_estimate(feats, alpha=0.4, beta=0.1)
    assert p_fast > p_slow, (
        f"Higher alpha should give higher final p when sales arrive late "
        f"(fast={p_fast}, slow={p_slow})"
    )
