"""Tests for vectorized policy functions.

TDD Step 1: RED - These tests should FAIL initially because
vectorized functions do not exist yet.
"""

import math

import numpy as np
import pandas as pd
import pytest

from src.policy import (
    compute_safety_stock,
    days_to_stockout,
    reorder_point,
    suggest_order,
    z_for_service_level,
)


class TestComputeSafetyStockVectorized:
    """Tests for compute_safety_stock_vectorized function."""

    def test_matches_scalar_version_single_row(self):
        """Vectorized output should match scalar for single row."""
        from src.policy import compute_safety_stock_vectorized

        mu = pd.Series([5.0])
        sigma_d = pd.Series([2.0])
        L = 7
        alpha = 0.95

        vectorized_result = compute_safety_stock_vectorized(mu, sigma_d, L, alpha)
        scalar_result = compute_safety_stock(5.0, 2.0, 7, 0.95)

        assert len(vectorized_result) == 1
        assert abs(vectorized_result.iloc[0] - scalar_result) < 1e-9

    def test_matches_scalar_version_multiple_rows(self):
        """Vectorized output should match scalar for multiple rows."""
        from src.policy import compute_safety_stock_vectorized

        mu = pd.Series([5.0, 3.0, 10.0, 0.5])
        sigma_d = pd.Series([2.0, 1.5, 4.0, 0.2])
        L = pd.Series([7, 5, 14, 3])
        alpha = 0.95

        vectorized_result = compute_safety_stock_vectorized(mu, sigma_d, L, alpha)

        for i in range(len(mu)):
            scalar_result = compute_safety_stock(
                mu.iloc[i], sigma_d.iloc[i], L.iloc[i], alpha
            )
            assert abs(vectorized_result.iloc[i] - scalar_result) < 1e-9, (
                f"Row {i}: vectorized={vectorized_result.iloc[i]}, "
                f"scalar={scalar_result}"
            )

    def test_with_integer_lead_time(self):
        """Works with scalar integer lead time."""
        from src.policy import compute_safety_stock_vectorized

        mu = pd.Series([5.0, 3.0])
        sigma_d = pd.Series([2.0, 1.5])
        L = 7  # Scalar int
        alpha = 0.95

        result = compute_safety_stock_vectorized(mu, sigma_d, L, alpha)
        assert len(result) == 2
        assert not result.isna().any()

    def test_zero_sigma(self):
        """Zero sigma should produce zero safety stock."""
        from src.policy import compute_safety_stock_vectorized

        mu = pd.Series([5.0, 3.0])
        sigma_d = pd.Series([0.0, 0.0])
        L = 7
        alpha = 0.95

        result = compute_safety_stock_vectorized(mu, sigma_d, L, alpha)
        assert all(result == 0.0)

    def test_returns_series(self):
        """Result should be a pandas Series."""
        from src.policy import compute_safety_stock_vectorized

        mu = pd.Series([5.0])
        sigma_d = pd.Series([2.0])

        result = compute_safety_stock_vectorized(mu, sigma_d, 7, 0.95)
        assert isinstance(result, pd.Series)


class TestReorderPointVectorized:
    """Tests for reorder_point_vectorized function."""

    def test_matches_scalar_version_single_row(self):
        """Vectorized output should match scalar for single row."""
        from src.policy import reorder_point_vectorized

        mu = pd.Series([5.0])
        ss = pd.Series([8.7])
        L = 7

        vectorized_result = reorder_point_vectorized(mu, ss, L)
        scalar_result = reorder_point(5.0, 8.7, 7)

        assert abs(vectorized_result.iloc[0] - scalar_result) < 1e-9

    def test_matches_scalar_version_multiple_rows(self):
        """Vectorized output should match scalar for multiple rows."""
        from src.policy import reorder_point_vectorized

        mu = pd.Series([5.0, 3.0, 10.0])
        ss = pd.Series([8.7, 5.2, 18.0])
        L = pd.Series([7, 5, 14])

        vectorized_result = reorder_point_vectorized(mu, ss, L)

        for i in range(len(mu)):
            scalar_result = reorder_point(mu.iloc[i], ss.iloc[i], L.iloc[i])
            assert abs(vectorized_result.iloc[i] - scalar_result) < 1e-9

    def test_zero_lead_time(self):
        """Zero lead time should return safety stock only."""
        from src.policy import reorder_point_vectorized

        mu = pd.Series([5.0, 3.0])
        ss = pd.Series([8.7, 5.2])
        L = 0

        result = reorder_point_vectorized(mu, ss, L)
        assert all(result == ss)


class TestDaysToStockoutVectorized:
    """Tests for days_to_stockout_vectorized function."""

    def test_matches_scalar_version_single_row(self):
        """Vectorized output should match scalar for single row."""
        from src.policy import days_to_stockout_vectorized

        qty = pd.Series([50.0])
        mu = pd.Series([5.0])

        vectorized_result = days_to_stockout_vectorized(qty, mu)
        scalar_result = days_to_stockout(50.0, 5.0)

        assert abs(vectorized_result.iloc[0] - scalar_result) < 1e-9

    def test_matches_scalar_version_multiple_rows(self):
        """Vectorized output should match scalar for multiple rows."""
        from src.policy import days_to_stockout_vectorized

        qty = pd.Series([50.0, 100.0, 25.0])
        mu = pd.Series([5.0, 10.0, 2.5])

        vectorized_result = days_to_stockout_vectorized(qty, mu)

        for i in range(len(qty)):
            scalar_result = days_to_stockout(qty.iloc[i], mu.iloc[i])
            assert abs(vectorized_result.iloc[i] - scalar_result) < 1e-9

    def test_zero_demand_returns_inf(self):
        """Zero demand should return infinity (matches scalar behavior)."""
        from src.policy import days_to_stockout_vectorized

        qty = pd.Series([50.0, 100.0])
        mu = pd.Series([0.0, 0.0])
        epsilon = 0.1

        result = days_to_stockout_vectorized(qty, mu, epsilon)
        # mu < epsilon returns infinity
        assert all(np.isinf(result))

    def test_near_zero_demand_returns_inf(self):
        """Near-zero demand (< epsilon) should return infinity."""
        from src.policy import days_to_stockout_vectorized

        qty = pd.Series([50.0])
        mu = pd.Series([0.05])  # Less than default epsilon
        epsilon = 0.1

        result = days_to_stockout_vectorized(qty, mu, epsilon)
        # mu < epsilon returns infinity
        assert np.isinf(result.iloc[0])

    def test_custom_epsilon(self):
        """Custom epsilon value should be used."""
        from src.policy import days_to_stockout_vectorized

        qty = pd.Series([100.0])
        mu = pd.Series([0.01])
        epsilon = 0.5

        result = days_to_stockout_vectorized(qty, mu, epsilon)
        # mu < epsilon returns infinity
        assert np.isinf(result.iloc[0])


class TestSuggestOrderVectorized:
    """Tests for suggest_order_vectorized function."""

    def test_matches_scalar_version_single_row(self):
        """Vectorized output should match scalar for single row."""
        from src.policy import suggest_order_vectorized

        qty = pd.Series([60])
        mu = pd.Series([5.0])
        L = 7
        ss = pd.Series([8.7])
        target_days = 21

        vectorized_result = suggest_order_vectorized(qty, mu, L, ss, target_days)
        scalar_result = suggest_order(60, 5.0, 7, 8.7, 21)

        assert vectorized_result.iloc[0] == scalar_result

    def test_matches_scalar_version_multiple_rows(self):
        """Vectorized output should match scalar for multiple rows."""
        from src.policy import suggest_order_vectorized

        qty = pd.Series([60, 100, 10, 200])
        mu = pd.Series([5.0, 8.0, 2.0, 3.0])
        L = pd.Series([7, 5, 14, 10])
        ss = pd.Series([8.7, 12.0, 5.5, 6.0])
        target_days = 21

        vectorized_result = suggest_order_vectorized(qty, mu, L, ss, target_days)

        for i in range(len(qty)):
            scalar_result = suggest_order(
                qty.iloc[i], mu.iloc[i], L.iloc[i], ss.iloc[i], target_days
            )
            assert vectorized_result.iloc[i] == scalar_result, (
                f"Row {i}: vectorized={vectorized_result.iloc[i]}, "
                f"scalar={scalar_result}"
            )

    def test_no_order_needed_when_sufficient_stock(self):
        """Should return 0 when current stock exceeds target."""
        from src.policy import suggest_order_vectorized

        qty = pd.Series([500])  # High stock
        mu = pd.Series([5.0])
        L = 7
        ss = pd.Series([8.7])
        target_days = 21

        result = suggest_order_vectorized(qty, mu, L, ss, target_days)
        assert result.iloc[0] == 0

    def test_returns_integer_series(self):
        """Result should be integer series."""
        from src.policy import suggest_order_vectorized

        qty = pd.Series([60, 100])
        mu = pd.Series([5.0, 8.0])
        L = 7
        ss = pd.Series([8.7, 12.0])
        target_days = 21

        result = suggest_order_vectorized(qty, mu, L, ss, target_days)
        assert result.dtype in [np.int64, np.int32, int]


class TestVectorizedPolicyEdgeCases:
    """Test edge cases for all vectorized policy functions."""

    def test_empty_series(self):
        """All functions should handle empty series gracefully."""
        from src.policy import (
            compute_safety_stock_vectorized,
            days_to_stockout_vectorized,
            reorder_point_vectorized,
            suggest_order_vectorized,
        )

        empty = pd.Series([], dtype=float)
        empty_int = pd.Series([], dtype=int)

        ss_result = compute_safety_stock_vectorized(empty, empty, 7, 0.95)
        assert len(ss_result) == 0

        rop_result = reorder_point_vectorized(empty, empty, 7)
        assert len(rop_result) == 0

        dso_result = days_to_stockout_vectorized(empty_int, empty)
        assert len(dso_result) == 0

        order_result = suggest_order_vectorized(empty_int, empty, 7, empty, 21)
        assert len(order_result) == 0

    def test_large_dataset_performance(self):
        """Vectorized functions should handle large datasets efficiently."""
        from src.policy import (
            compute_safety_stock_vectorized,
            days_to_stockout_vectorized,
            reorder_point_vectorized,
            suggest_order_vectorized,
        )
        import time

        n = 10000
        np.random.seed(42)
        mu = pd.Series(np.random.uniform(0.5, 20.0, n))
        sigma_d = pd.Series(np.random.uniform(0.1, 5.0, n))
        qty = pd.Series(np.random.randint(0, 500, n))
        L = pd.Series(np.random.randint(3, 14, n))

        start = time.time()
        ss = compute_safety_stock_vectorized(mu, sigma_d, L, 0.95)
        rop = reorder_point_vectorized(mu, ss, L)
        dso = days_to_stockout_vectorized(qty, mu)
        order = suggest_order_vectorized(qty, mu, L, ss, 21)
        elapsed = time.time() - start

        # Vectorized operations on 10k rows should complete in < 1 second
        assert elapsed < 1.0, f"Vectorized operations took {elapsed:.2f}s"
        assert len(ss) == n
        assert len(rop) == n
        assert len(dso) == n
        assert len(order) == n
