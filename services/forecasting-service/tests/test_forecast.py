import pandas as pd
import pytest

from src.forecast import (
    _dow_weighted_estimate,
    apply_category_fallback,
    estimate_mu_sigma,
)
from src import config


def _make_constant_features(item: str, value: float, days: int = 20) -> pd.DataFrame:
    dates = pd.date_range("2025-11-01", periods=days, freq="D")
    return pd.DataFrame({
        "date": dates,
        "item_id": [item] * days,
        "consumption": [float(value)] * days,
    })


def test_constant_series_ma14_with_prints():
    feats = _make_constant_features("A", 5.0, days=20)
    print("\n" + "="*80)
    print("TEST: Constant series (A, 5.0/day) with ma14 method")
    print("="*80)
    print("\nINPUT features_df (head):")
    print(feats.head().to_string(index=False))
    print("\nINPUT features_df (tail):")
    print(feats.tail().to_string(index=False))

    out = estimate_mu_sigma(feats, method="ma14")
    print("\nOUTPUT estimate_mu_sigma (ma14):")
    print(out.to_string(index=False))
    print("="*80 + "\n")

    # mu ~ 5, sigma ~ 0 (floored to >= 0.01)
    row = out.iloc[0]
    assert row["item_id"] == "A"
    assert abs(row["mu_hat"] - 5.0) < 1e-9
    assert row["sigma_d_hat"] >= 0.01


def test_constant_series_es_with_prints():
    feats = _make_constant_features("B", 3.0, days=15)
    print("\n" + "="*80)
    print("TEST: Constant series (B, 3.0/day) with exp_smooth method")
    print("="*80)
    print("\nINPUT features_df (head):")
    print(feats.head().to_string(index=False))
    print("\nINPUT features_df (tail):")
    print(feats.tail().to_string(index=False))

    out = estimate_mu_sigma(feats, method="exp_smooth")
    print("\nOUTPUT estimate_mu_sigma (exp_smooth):")
    print(out.to_string(index=False))
    print("="*80 + "\n")

    row = out.iloc[0]
    assert row["item_id"] == "B"
    assert abs(row["mu_hat"] - 3.0) < 1e-6
    assert row["sigma_d_hat"] >= 0.01


def test_multi_sku_with_prints():
    a = _make_constant_features("A", 2.0, days=10)
    b = _make_constant_features("B", 4.0, days=10)
    feats = pd.concat([a, b], ignore_index=True)
    print("\n" + "="*80)
    print("TEST: Multi-SKU (A=2.0/day, B=4.0/day) with ma7 method")
    print("="*80)
    print("\nINPUT multi-SKU features_df (sample per item):")
    print(feats.groupby("item_id").head(2).to_string(index=False))

    out = estimate_mu_sigma(feats, method="ma7")
    print("\nOUTPUT estimate_mu_sigma (ma7, multi):")
    print(out.sort_values("item_id").to_string(index=False))
    print("="*80 + "\n")

    assert set(out["item_id"]) == {"A", "B"}
    mu_map = {r["item_id"]: r["mu_hat"] for _, r in out.iterrows()}
    assert abs(mu_map["A"] - 2.0) < 1e-9
    assert abs(mu_map["B"] - 4.0) < 1e-9


# ---------------------------------------------------------------------------
# _dow_weighted_estimate with stockout data
# ---------------------------------------------------------------------------

class TestDowWeightedStockout:
    def test_stockout_days_pull_rate_upward_via_censored_mle(self, monkeypatch):
        """With CENSORED_DEMAND_ENABLED, stockout days with above-mean
        consumption are treated as right-censored: 'we sold X but demand
        could have been higher'. The MLE rate should land above the naive mean."""
        monkeypatch.setattr(config, "CENSORED_DEMAND_ENABLED", True)
        monkeypatch.setattr(config, "CENSORED_DEMAND_MIN_STOCKOUT_DAYS", 1)

        dates = pd.date_range("2025-11-03", periods=14, freq="D")
        # 10 in-stock days at 5/day, 4 stockout days where we sold 5 before
        # running out (could have been more)
        consumption = [5.0] * 14
        is_stockout = [False] * 10 + [True] * 4

        group = pd.DataFrame({
            "date": dates,
            "item_id": ["A"] * 14,
            "consumption": consumption,
            "is_stockout": is_stockout,
        })

        mu, sigma, mults = _dow_weighted_estimate(group)
        # Censored days at observed=5 should push the MLE above 5.
        assert mu >= 5.0

    def test_empty_group_returns_floors(self):
        """Empty input -> mu_hat and sigma_d_hat at config floors."""
        group = pd.DataFrame({
            "date": pd.Series([], dtype="datetime64[ns]"),
            "item_id": pd.Series([], dtype=str),
            "consumption": pd.Series([], dtype=float),
        })
        mu, sigma, mults = _dow_weighted_estimate(group)
        assert mu == config.MU_FLOOR
        assert sigma == config.SIGMA_FLOOR


# ---------------------------------------------------------------------------
# estimate_mu_sigma input validation
# ---------------------------------------------------------------------------

class TestEstimateMuSigmaStockout:
    def test_invalid_method_raises(self):
        """Invalid method should raise ValueError."""
        feats = _make_constant_features("A", 5.0, days=10)
        with pytest.raises(ValueError, match="method must be"):
            estimate_mu_sigma(feats, method="invalid")

    def test_missing_columns_raises(self):
        """Missing required columns should raise ValueError."""
        feats = pd.DataFrame({"date": [1], "item_id": ["A"]})  # no consumption
        with pytest.raises(ValueError, match="missing required columns"):
            estimate_mu_sigma(feats)


# ---------------------------------------------------------------------------
# apply_category_fallback tests
# ---------------------------------------------------------------------------

class TestApplyCategoryFallback:
    def _make_estimates(self) -> pd.DataFrame:
        return pd.DataFrame({
            "item_id": ["A", "B", "C", "D"],
            "mu_hat": [5.0, 3.0, config.MU_FLOOR, config.MU_FLOOR],
            "sigma_d_hat": [1.0, 0.5, config.SIGMA_FLOOR, config.SIGMA_FLOOR],
            "method": ["dow_weighted"] * 4,
        })

    def test_cold_start_gets_category_average(self):
        """Items with no history and MU_FLOOR get category average."""
        estimates = self._make_estimates()
        category_map = {"A": "toys", "B": "toys", "C": "toys", "D": "food"}
        items_with_history = {"A", "B"}  # C and D are cold-start

        result = apply_category_fallback(estimates, category_map, items_with_history)

        # C is cold-start in "toys" category -> gets avg of A(5.0) and B(3.0) = 4.0
        c_mu = result.loc[result["item_id"] == "C", "mu_hat"].iloc[0]
        assert c_mu == pytest.approx(4.0, abs=0.01)

        # D is cold-start in "food" category -> no other food items with demand, stays MU_FLOOR
        d_mu = result.loc[result["item_id"] == "D", "mu_hat"].iloc[0]
        assert d_mu == config.MU_FLOOR

    def test_low_demand_with_history_not_overwritten(self):
        """Items at MU_FLOOR but WITH history should NOT get fallback."""
        estimates = self._make_estimates()
        category_map = {"A": "toys", "B": "toys", "C": "toys", "D": "toys"}
        # C has history but is genuinely low demand
        items_with_history = {"A", "B", "C"}

        result = apply_category_fallback(estimates, category_map, items_with_history)

        c_mu = result.loc[result["item_id"] == "C", "mu_hat"].iloc[0]
        assert c_mu == config.MU_FLOOR  # NOT overwritten

    def test_no_items_with_history_falls_back_to_heuristic(self):
        """Without items_with_history, all MU_FLOOR items get fallback."""
        estimates = self._make_estimates()
        category_map = {"A": "toys", "B": "toys", "C": "toys", "D": "toys"}

        result = apply_category_fallback(estimates, category_map, items_with_history=None)

        # Both C and D at MU_FLOOR get fallback (heuristic mode)
        c_mu = result.loc[result["item_id"] == "C", "mu_hat"].iloc[0]
        d_mu = result.loc[result["item_id"] == "D", "mu_hat"].iloc[0]
        expected_avg = (5.0 + 3.0) / 2  # avg of A and B
        assert c_mu == pytest.approx(expected_avg, abs=0.01)
        assert d_mu == pytest.approx(expected_avg, abs=0.01)

    def test_does_not_mutate_input(self):
        """Should return a new DataFrame, not mutate the input."""
        estimates = self._make_estimates()
        original_c_mu = estimates.loc[estimates["item_id"] == "C", "mu_hat"].iloc[0]
        category_map = {"A": "toys", "B": "toys", "C": "toys", "D": "toys"}

        apply_category_fallback(estimates, category_map, items_with_history={"A", "B"})

        # Original should be unchanged
        assert estimates.loc[estimates["item_id"] == "C", "mu_hat"].iloc[0] == original_c_mu


# ---------------------------------------------------------------------------
# Stockout-aware estimation
# ---------------------------------------------------------------------------

class TestStockoutAware:
    def test_no_stockout_column_uses_all_data(self):
        """Without is_stockout column, all rows feed the mean (current default
        behavior; censored MLE only kicks in when the column is present and
        the censored-demand flag is on)."""
        dates = pd.date_range("2025-11-03", periods=14, freq="D")
        # 10 days at 5.0, 4 days at 0.0 (would be stockouts if detected)
        consumption = [5.0] * 10 + [0.0] * 4

        feats = pd.DataFrame({
            "date": dates,
            "item_id": ["A"] * 14,
            "consumption": consumption,
        })

        result = estimate_mu_sigma(feats, method="dow_weighted")
        mu = result.iloc[0]["mu_hat"]

        # Mean of all 14 days: (5*10 + 0*4) / 14 = 3.571
        assert mu == pytest.approx(50.0 / 14.0, abs=0.1)

