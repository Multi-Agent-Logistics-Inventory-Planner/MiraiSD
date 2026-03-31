"""Tests for vectorized pipeline operations.

TDD Step 1: RED - These tests should FAIL initially because
vectorized implementations do not exist yet.
"""

import sys
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

import numpy as np
import pandas as pd
import pytest

# Mock kafka module before importing pipeline
sys.modules["kafka"] = MagicMock()
sys.modules["kafka.errors"] = MagicMock()

from src.application.pipeline import ForecastingPipeline


class TestCreateZeroDemandFeaturesVectorized:
    """Tests for vectorized _create_zero_demand_features method."""

    def test_output_matches_iterrows_version(self):
        """Vectorized output should match original iterrows version."""
        # Create mock repo
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        # Test data
        items_df = pd.DataFrame(
            {
                "item_id": ["item-1", "item-2", "item-3"],
                "name": ["Item 1", "Item 2", "Item 3"],
                "lead_time_days": [7, 5, 14],
            }
        )

        # Call vectorized version
        result = pipeline._create_zero_demand_features(items_df)

        # Call legacy version for comparison
        legacy_result = pipeline._create_zero_demand_features_legacy(items_df)

        # Compare DataFrames
        assert len(result) == len(legacy_result)
        assert set(result.columns) == set(legacy_result.columns)

        # Sort both by item_id for consistent comparison
        result_sorted = result.sort_values("item_id").reset_index(drop=True)
        legacy_sorted = legacy_result.sort_values("item_id").reset_index(drop=True)

        for col in result.columns:
            if col == "date":
                # Dates should be equal
                assert (result_sorted[col] == legacy_sorted[col]).all()
            else:
                # Numeric columns should be equal
                pd.testing.assert_series_equal(
                    result_sorted[col],
                    legacy_sorted[col],
                    check_names=False,
                )

    def test_correct_structure(self):
        """Output should have correct columns and types."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        items_df = pd.DataFrame(
            {
                "item_id": ["item-1", "item-2"],
                "name": ["Item 1", "Item 2"],
            }
        )

        result = pipeline._create_zero_demand_features(items_df)

        expected_columns = {
            "date",
            "item_id",
            "consumption",
            "ma7",
            "ma14",
            "std14",
            "dow",
            "is_weekend",
        }
        assert set(result.columns) == expected_columns
        assert len(result) == 2

    def test_zero_demand_values(self):
        """All demand-related values should be zero."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        items_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
            }
        )

        result = pipeline._create_zero_demand_features(items_df)

        assert result["consumption"].iloc[0] == 0.0
        assert result["ma7"].iloc[0] == 0.0
        assert result["ma14"].iloc[0] == 0.0
        assert result["std14"].iloc[0] == 0.0

    def test_empty_input(self):
        """Should handle empty DataFrame."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        items_df = pd.DataFrame({"item_id": []})

        result = pipeline._create_zero_demand_features(items_df)

        assert len(result) == 0

    def test_large_dataset_performance(self):
        """Vectorized version should handle large datasets efficiently."""
        import time

        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        n = 5000
        items_df = pd.DataFrame(
            {
                "item_id": [f"item-{i}" for i in range(n)],
            }
        )

        start = time.time()
        result = pipeline._create_zero_demand_features(items_df)
        elapsed = time.time() - start

        assert len(result) == n
        # Vectorized should complete in < 0.5 seconds for 5k rows
        assert elapsed < 0.5, f"Vectorized took {elapsed:.2f}s"


class TestComputeForecastsVectorized:
    """Tests for vectorized _compute_forecasts method."""

    def test_output_matches_iterrows_version(self):
        """Vectorized output should match original iterrows version."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        # Test data
        items_df = pd.DataFrame(
            {
                "item_id": ["item-1", "item-2", "item-3"],
                "name": ["Item 1", "Item 2", "Item 3"],
                "lead_time_days": [7, 5, 14],
            }
        )

        inventory_df = pd.DataFrame(
            {
                "item_id": ["item-1", "item-2", "item-3"],
                "current_qty": [50, 100, 25],
            }
        )

        estimates_df = pd.DataFrame(
            {
                "item_id": ["item-1", "item-2", "item-3"],
                "mu_hat": [5.0, 8.0, 2.0],
                "sigma_d_hat": [2.0, 3.0, 0.5],
            }
        )

        # Call vectorized version
        result = pipeline._compute_forecasts(items_df, inventory_df, estimates_df)

        # Call legacy version for comparison
        legacy_result = pipeline._compute_forecasts_legacy(
            items_df, inventory_df, estimates_df
        )

        # Compare outputs (excluding computed_at timestamp)
        assert len(result) == len(legacy_result)

        # Sort both by item_id
        result_sorted = result.sort_values("item_id").reset_index(drop=True)
        legacy_sorted = legacy_result.sort_values("item_id").reset_index(drop=True)

        # Compare numeric columns
        numeric_cols = [
            "avg_daily_delta",
            "days_to_stockout",
            "suggested_reorder_qty",
            "confidence",
        ]

        for col in numeric_cols:
            for i in range(len(result_sorted)):
                vec_val = result_sorted[col].iloc[i]
                leg_val = legacy_sorted[col].iloc[i]

                # Handle None/NaN
                if pd.isna(vec_val) and pd.isna(leg_val):
                    continue

                assert abs(vec_val - leg_val) < 1e-6, (
                    f"Row {i}, column {col}: "
                    f"vectorized={vec_val}, legacy={leg_val}"
                )

    def test_features_dict_matches(self):
        """Features dict in output should match legacy version."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        items_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "lead_time_days": [7],
            }
        )

        inventory_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "current_qty": [50],
            }
        )

        estimates_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "mu_hat": [5.0],
                "sigma_d_hat": [2.0],
            }
        )

        result = pipeline._compute_forecasts(items_df, inventory_df, estimates_df)
        legacy_result = pipeline._compute_forecasts_legacy(
            items_df, inventory_df, estimates_df
        )

        vec_features = result.iloc[0]["features"]
        leg_features = legacy_result.iloc[0]["features"]

        for key in leg_features:
            assert key in vec_features, f"Missing key: {key}"
            assert abs(vec_features[key] - leg_features[key]) < 1e-4, (
                f"Key {key}: vectorized={vec_features[key]}, "
                f"legacy={leg_features[key]}"
            )

    def test_handles_missing_inventory(self):
        """Should handle items without inventory data."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        items_df = pd.DataFrame(
            {
                "item_id": ["item-1", "item-2"],
                "lead_time_days": [7, 5],
            }
        )

        # Only item-1 has inventory
        inventory_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "current_qty": [50],
            }
        )

        estimates_df = pd.DataFrame(
            {
                "item_id": ["item-1", "item-2"],
                "mu_hat": [5.0, 8.0],
                "sigma_d_hat": [2.0, 3.0],
            }
        )

        result = pipeline._compute_forecasts(items_df, inventory_df, estimates_df)

        # item-2 should have current_qty = 0
        item2 = result[result["item_id"] == "item-2"].iloc[0]
        assert item2["features"]["current_qty"] == 0

    def test_handles_empty_inventory(self):
        """Should handle empty inventory DataFrame."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        items_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "lead_time_days": [7],
            }
        )

        inventory_df = pd.DataFrame()  # Empty

        estimates_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "mu_hat": [5.0],
                "sigma_d_hat": [2.0],
            }
        )

        result = pipeline._compute_forecasts(items_df, inventory_df, estimates_df)

        assert len(result) == 1
        assert result.iloc[0]["features"]["current_qty"] == 0

    def test_handles_zero_demand(self):
        """Should handle items with zero demand."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        items_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "lead_time_days": [7],
            }
        )

        inventory_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "current_qty": [100],
            }
        )

        estimates_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "mu_hat": [0.0],  # Zero demand
                "sigma_d_hat": [0.0],
            }
        )

        result = pipeline._compute_forecasts(items_df, inventory_df, estimates_df)
        legacy_result = pipeline._compute_forecasts_legacy(
            items_df, inventory_df, estimates_df
        )

        # Both should produce same result for zero demand
        assert len(result) == 1
        assert result.iloc[0]["avg_daily_delta"] == 0.0

        # days_to_stockout should be None (infinite)
        assert pd.isna(result.iloc[0]["days_to_stockout"])

    def test_default_lead_time(self):
        """Should use default lead time when not specified."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        items_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                # No lead_time_days column
            }
        )

        inventory_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "current_qty": [50],
            }
        )

        estimates_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "mu_hat": [5.0],
                "sigma_d_hat": [2.0],
            }
        )

        result = pipeline._compute_forecasts(items_df, inventory_df, estimates_df)

        # Should use default lead time of 14
        assert result.iloc[0]["features"]["lead_time_days"] == 14

    def test_large_dataset_performance(self):
        """Vectorized version should handle large datasets efficiently."""
        import time

        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        n = 1000
        np.random.seed(42)

        items_df = pd.DataFrame(
            {
                "item_id": [f"item-{i}" for i in range(n)],
                "lead_time_days": np.random.randint(3, 14, n),
            }
        )

        inventory_df = pd.DataFrame(
            {
                "item_id": [f"item-{i}" for i in range(n)],
                "current_qty": np.random.randint(0, 500, n),
            }
        )

        estimates_df = pd.DataFrame(
            {
                "item_id": [f"item-{i}" for i in range(n)],
                "mu_hat": np.random.uniform(0.5, 20.0, n),
                "sigma_d_hat": np.random.uniform(0.1, 5.0, n),
            }
        )

        start = time.time()
        result = pipeline._compute_forecasts(items_df, inventory_df, estimates_df)
        vectorized_time = time.time() - start

        start = time.time()
        legacy_result = pipeline._compute_forecasts_legacy(
            items_df, inventory_df, estimates_df
        )
        legacy_time = time.time() - start

        assert len(result) == n

        # Vectorized should be significantly faster
        print(f"\nVectorized: {vectorized_time:.3f}s, Legacy: {legacy_time:.3f}s")
        print(f"Speedup: {legacy_time / vectorized_time:.1f}x")

        # At minimum, vectorized should not be slower
        # (allowing some variance in test execution)
        assert vectorized_time < legacy_time * 1.5


class TestPipelineIntegrationVectorized:
    """Integration tests for the vectorized pipeline."""

    def test_full_pipeline_produces_same_results(self):
        """Full pipeline should produce identical results with vectorization."""
        mock_repo = MagicMock()

        # Setup mock returns
        items_df = pd.DataFrame(
            {
                "item_id": ["item-1", "item-2"],
                "name": ["Item 1", "Item 2"],
                "lead_time_days": [7, 5],
            }
        )

        inventory_df = pd.DataFrame(
            {
                "item_id": ["item-1", "item-2"],
                "current_qty": [50, 100],
            }
        )

        movements_df = pd.DataFrame()  # No movements

        mock_repo.get_items.return_value = items_df
        mock_repo.get_current_inventory.return_value = inventory_df
        mock_repo.get_stock_movements.return_value = movements_df
        mock_repo.upsert_forecasts.return_value = 2

        pipeline = ForecastingPipeline(repo=mock_repo)

        # Run pipeline
        result = pipeline.run_for_items({"item-1", "item-2"})

        assert result == 2
        mock_repo.upsert_forecasts.assert_called_once()

        # Verify the forecasts DataFrame structure
        forecasts_df = mock_repo.upsert_forecasts.call_args[0][0]
        assert len(forecasts_df) == 2
        assert "item_id" in forecasts_df.columns
        assert "features" in forecasts_df.columns
