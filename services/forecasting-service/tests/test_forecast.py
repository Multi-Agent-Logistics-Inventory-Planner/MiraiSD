import pandas as pd
import pytest

from src.forecast import (
    _filter_in_stock,
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
# _filter_in_stock tests
# ---------------------------------------------------------------------------

class TestFilterInStock:
    def _make_group(self, n_total: int, n_stockout: int) -> pd.DataFrame:
        """Create a group with n_total rows, n_stockout of which are stockout."""
        dates = pd.date_range("2025-01-01", periods=n_total, freq="D")
        stockout_flags = [True] * n_stockout + [False] * (n_total - n_stockout)
        consumption = [0.0 if s else 3.0 for s in stockout_flags]
        return pd.DataFrame({
            "date": dates,
            "item_id": ["X"] * n_total,
            "consumption": consumption,
            "is_stockout": stockout_flags,
        })

    def test_no_stockout_column_returns_original(self):
        """Without is_stockout column, returns group unchanged."""
        group = pd.DataFrame({
            "date": pd.date_range("2025-01-01", periods=5),
            "item_id": ["A"] * 5,
            "consumption": [1.0] * 5,
        })
        result = _filter_in_stock(group, min_in_stock_days=3)
        assert len(result) == 5

    def test_enough_in_stock_days_filters_stockouts(self):
        """When enough in-stock days remain, stockout rows are removed."""
        group = self._make_group(n_total=20, n_stockout=5)
        result = _filter_in_stock(group, min_in_stock_days=7)
        assert len(result) == 15
        assert not result["is_stockout"].any()

    def test_too_few_in_stock_days_returns_original(self):
        """When filtering leaves fewer than min_in_stock_days, returns all."""
        group = self._make_group(n_total=10, n_stockout=7)
        # Only 3 in-stock days, threshold is 5
        result = _filter_in_stock(group, min_in_stock_days=5)
        assert len(result) == 10  # original returned

    def test_threshold_zero_always_filters(self):
        """With min_in_stock_days=0, always filters (even if 0 remain)."""
        group = self._make_group(n_total=5, n_stockout=5)
        result = _filter_in_stock(group, min_in_stock_days=0)
        assert len(result) == 0

    def test_exact_threshold_filters(self):
        """When in-stock days == min_in_stock_days, filtering is applied."""
        group = self._make_group(n_total=10, n_stockout=3)
        # 7 in-stock days, threshold is 7
        result = _filter_in_stock(group, min_in_stock_days=7)
        assert len(result) == 7


# ---------------------------------------------------------------------------
# _dow_weighted_estimate with stockout data
# ---------------------------------------------------------------------------

class TestDowWeightedStockout:
    def test_stockout_days_excluded_from_mean(self):
        """Stockout days should not drag down the estimated mean."""
        dates = pd.date_range("2025-11-03", periods=14, freq="D")  # Mon start
        consumption = [5.0] * 14
        is_stockout = [False] * 14
        # Make last 4 days stockout with 0 consumption
        for i in range(10, 14):
            consumption[i] = 0.0
            is_stockout[i] = True

        group = pd.DataFrame({
            "date": dates,
            "item_id": ["A"] * 14,
            "consumption": consumption,
            "is_stockout": is_stockout,
        })

        mu, sigma, mults = _dow_weighted_estimate(group, min_in_stock_days=5)
        # With filtering: mean of in-stock days = 5.0
        # Without filtering: mean would be (5*10 + 0*4)/14 = 3.57
        assert mu == pytest.approx(5.0, abs=0.01)

    def test_empty_group_returns_floors(self):
        """All-stockout group with high guard should return MU_FLOOR."""
        group = pd.DataFrame({
            "date": pd.date_range("2025-01-01", periods=5),
            "item_id": ["A"] * 5,
            "consumption": [0.0] * 5,
            "is_stockout": [True] * 5,
        })
        # min_in_stock_days=0 means filter all -> empty
        mu, sigma, mults = _dow_weighted_estimate(group, min_in_stock_days=0)
        assert mu == config.MU_FLOOR
        assert sigma == config.SIGMA_FLOOR


# ---------------------------------------------------------------------------
# estimate_mu_sigma with min_in_stock_days parameter
# ---------------------------------------------------------------------------

class TestEstimateMuSigmaStockout:
    def test_min_in_stock_days_param_threaded(self):
        """min_in_stock_days should be passed through to filtering."""
        dates = pd.date_range("2025-11-03", periods=14, freq="D")
        consumption = [5.0] * 7 + [0.0] * 7
        is_stockout = [False] * 7 + [True] * 7

        feats = pd.DataFrame({
            "date": dates,
            "item_id": ["A"] * 14,
            "consumption": consumption,
            "is_stockout": is_stockout,
        })

        # With high guard (7), filtering should be applied (7 in-stock == 7 threshold)
        result_filtered = estimate_mu_sigma(feats, method="dow_weighted", min_in_stock_days=7)
        mu_filtered = result_filtered.iloc[0]["mu_hat"]

        # With very high guard (20), falls back to all data
        result_all = estimate_mu_sigma(feats, method="dow_weighted", min_in_stock_days=20)
        mu_all = result_all.iloc[0]["mu_hat"]

        # Filtered mean should be higher (excludes zeros)
        assert mu_filtered > mu_all

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
# Stockout filter disabled: all data used (matching main's superior approach)
# ---------------------------------------------------------------------------

class TestStockoutFilterDisabled:
    def test_no_stockout_column_uses_all_data(self):
        """Without is_stockout column (STOCKOUT_FILTER_ENABLED=false path),
        all data including zeros is used in the mean.

        The pipeline gates stockout detection: when disabled, is_stockout
        column is never added, so all rows (including zero-consumption days
        from stockouts) contribute to the estimate.
        """
        dates = pd.date_range("2025-11-03", periods=14, freq="D")
        # 10 days at 5.0, 4 days at 0.0 (would be stockouts if detected)
        consumption = [5.0] * 10 + [0.0] * 4

        feats = pd.DataFrame({
            "date": dates,
            "item_id": ["A"] * 14,
            "consumption": consumption,
        })

        result = estimate_mu_sigma(feats, method="dow_weighted", min_in_stock_days=0)
        mu = result.iloc[0]["mu_hat"]

        # Mean of all 14 days: (5*10 + 0*4) / 14 = 3.571
        assert mu == pytest.approx(50.0 / 14.0, abs=0.1)

    def test_stockout_column_present_with_guard_still_filters(self):
        """When is_stockout IS present (STOCKOUT_FILTER_ENABLED=true path),
        stockout days are excluded and in-stock mean is used."""
        dates = pd.date_range("2025-11-03", periods=14, freq="D")
        consumption = [5.0] * 10 + [0.0] * 4

        feats = pd.DataFrame({
            "date": dates,
            "item_id": ["A"] * 14,
            "consumption": consumption,
            "is_stockout": [False] * 10 + [True] * 4,
        })

        result = estimate_mu_sigma(feats, method="dow_weighted", min_in_stock_days=7)
        mu = result.iloc[0]["mu_hat"]

        # Mean of 10 in-stock days: 5.0
        assert mu == pytest.approx(5.0, abs=0.01)

