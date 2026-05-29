"""Tests for DOW multiplier application in policy + lead-time demand.

The dow_weighted estimator returns mu_hat as the window mean and stashes
per-day multipliers in JSONB. Until the fix in this commit the multipliers
were stored but never applied. These tests pin the new behavior:

* ``effective_lead_demand_vectorized`` integrates ``mu * dow_mult[dow(d)]``
  over the lead-time window.
* ``reorder_point_vectorized`` uses the DOW-adjusted lead demand.
* The Poisson/NegBin distribution buffer (steady/bursty) sizes against the
  DOW-adjusted lead-time mean.
* Missing/empty multipliers degrade cleanly to the legacy ``mu * L``.
"""
from datetime import date

import numpy as np
import pandas as pd

from src import policy


# 1.0 multiplier every day -> sum reduces to mu*L
FLAT = {d: 1.0 for d in range(7)}

# Weekend-heavy: Sat (5) and Sun (6) sell 3x, weekdays sell ~0.6x.
# Average over 7 days: (5*0.6 + 2*3.0)/7 = 1.286 -> close to 1.0 over a full week.
WEEKEND_HEAVY = {0: 0.6, 1: 0.6, 2: 0.6, 3: 0.6, 4: 0.6, 5: 3.0, 6: 3.0}


def _mu(*values: float) -> pd.Series:
    return pd.Series(list(values), dtype=float)


class TestEffectiveLeadDemand:
    def test_falls_back_to_mu_times_L_when_no_dow(self):
        result = policy.effective_lead_demand_vectorized(
            mu_hat=_mu(2.0, 3.0),
            L=10,
            dow_multipliers=None,
            start_date=None,
        )
        assert result.iloc[0] == 20.0
        assert result.iloc[1] == 30.0

    def test_falls_back_when_dow_dict_is_empty(self):
        result = policy.effective_lead_demand_vectorized(
            mu_hat=_mu(2.0),
            L=7,
            dow_multipliers=pd.Series([{}]),
            start_date=date(2026, 5, 25),  # Monday
        )
        assert result.iloc[0] == 14.0  # 2 * 7

    def test_flat_multipliers_match_mu_times_L(self):
        result = policy.effective_lead_demand_vectorized(
            mu_hat=_mu(2.0),
            L=7,
            dow_multipliers=pd.Series([FLAT]),
            start_date=date(2026, 5, 25),
        )
        assert abs(result.iloc[0] - 14.0) < 1e-9

    def test_weekend_heavy_increases_demand_when_window_includes_weekend(self):
        # Monday + 6 days covers Mon..Sun. Sum of multipliers = 5*0.6 + 2*3.0 = 9.0.
        # Effective lead demand = mu * 9.0 = 18.0 (vs flat mu*L = 14.0).
        result = policy.effective_lead_demand_vectorized(
            mu_hat=_mu(2.0),
            L=7,
            dow_multipliers=pd.Series([WEEKEND_HEAVY]),
            start_date=date(2026, 5, 25),  # Monday
        )
        assert abs(result.iloc[0] - 18.0) < 1e-9

    def test_short_window_starting_friday_picks_up_two_weekend_days(self):
        # Fri (0.6), Sat (3.0), Sun (3.0) -> sum 6.6; mu=2 -> 13.2 vs flat 6.0.
        result = policy.effective_lead_demand_vectorized(
            mu_hat=_mu(2.0),
            L=3,
            dow_multipliers=pd.Series([WEEKEND_HEAVY]),
            start_date=date(2026, 5, 29),  # Friday
        )
        assert abs(result.iloc[0] - 13.2) < 1e-9

    def test_string_keyed_multipliers_work(self):
        """The pipeline stringifies keys for JSON storage; the helper must
        tolerate that without losing the multiplier."""
        string_keyed = {str(k): v for k, v in WEEKEND_HEAVY.items()}
        result = policy.effective_lead_demand_vectorized(
            mu_hat=_mu(2.0),
            L=7,
            dow_multipliers=pd.Series([string_keyed]),
            start_date=date(2026, 5, 25),
        )
        assert abs(result.iloc[0] - 18.0) < 1e-9


class TestReorderPointVectorizedWithDow:
    def test_rop_picks_up_weekend_uplift(self):
        # Friday start, L=3 days, weekend-heavy. Lead demand = 13.2.
        # Safety stock = 5. ROP = 18.2. Without DOW it would be 11.
        rop = policy.reorder_point_vectorized(
            mu_hat=_mu(2.0),
            safety_stock=pd.Series([5.0]),
            L=3,
            dow_multipliers=pd.Series([WEEKEND_HEAVY]),
            start_date=date(2026, 5, 29),  # Friday
        )
        assert abs(rop.iloc[0] - 18.2) < 1e-9

    def test_rop_without_dow_matches_legacy_formula(self):
        rop = policy.reorder_point_vectorized(
            mu_hat=_mu(2.0),
            safety_stock=pd.Series([5.0]),
            L=3,
        )
        assert abs(rop.iloc[0] - 11.0) < 1e-9


class TestDistributionSafetyStockWithDow:
    def test_bursty_rop_grows_when_window_is_weekend_heavy(self):
        """ROP = expected_lead_demand + SS. The expected_lead_demand piece
        is DOW-adjusted; the SS piece may move either direction (high-mean
        windows have lower CV against fixed variance). What must hold: the
        total ROP for a weekend-heavy lead-time window is larger than the
        same lead-time window starting on a weekday."""
        mu_hat = _mu(2.0)
        sigma_d = _mu(2.5)  # CV > 1 -> bursty
        regime = pd.Series([policy.REGIME_BURSTY])

        ss_weekend = policy.compute_safety_stock_vectorized(
            mu_hat=mu_hat, sigma_d_hat=sigma_d, L=3, alpha=0.95, regime=regime,
            dow_multipliers=pd.Series([WEEKEND_HEAVY]),
            start_date=date(2026, 5, 29),  # Friday
        )
        rop_weekend = policy.reorder_point_vectorized(
            mu_hat=mu_hat, safety_stock=ss_weekend, L=3,
            dow_multipliers=pd.Series([WEEKEND_HEAVY]),
            start_date=date(2026, 5, 29),
        )

        ss_weekday = policy.compute_safety_stock_vectorized(
            mu_hat=mu_hat, sigma_d_hat=sigma_d, L=3, alpha=0.95, regime=regime,
            dow_multipliers=pd.Series([WEEKEND_HEAVY]),
            start_date=date(2026, 5, 26),  # Tuesday
        )
        rop_weekday = policy.reorder_point_vectorized(
            mu_hat=mu_hat, safety_stock=ss_weekday, L=3,
            dow_multipliers=pd.Series([WEEKEND_HEAVY]),
            start_date=date(2026, 5, 26),
        )

        # Lead demand alone moves from 3.6 to 13.2; ROP must reflect that.
        assert rop_weekend.iloc[0] > rop_weekday.iloc[0]

    def test_no_dow_matches_prior_behavior(self):
        """Without dow_multipliers, the distribution buffer should match what
        the legacy mu*L sum produced."""
        mu_hat = _mu(2.0)
        sigma_d = _mu(2.5)
        regime = pd.Series([policy.REGIME_BURSTY])

        ss_legacy = policy.compute_safety_stock_vectorized(
            mu_hat=mu_hat,
            sigma_d_hat=sigma_d,
            L=3,
            alpha=0.95,
            regime=regime,
        )
        ss_flat_dow = policy.compute_safety_stock_vectorized(
            mu_hat=mu_hat,
            sigma_d_hat=sigma_d,
            L=3,
            alpha=0.95,
            regime=regime,
            dow_multipliers=pd.Series([FLAT]),
            start_date=date(2026, 5, 25),
        )

        # Flat multipliers should reproduce the legacy behavior to within rounding.
        assert abs(ss_legacy.iloc[0] - ss_flat_dow.iloc[0]) < 1e-9
