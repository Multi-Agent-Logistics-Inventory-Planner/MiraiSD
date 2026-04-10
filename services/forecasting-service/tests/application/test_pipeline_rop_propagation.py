"""Tests for ROP propagation error handling in the pipeline."""

import sys
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

import numpy as np
import pandas as pd
import pytest

sys.modules["kafka"] = MagicMock()
sys.modules["kafka.errors"] = MagicMock()

from src.application.pipeline import ForecastingPipeline


def _build_mock_repo():
    """Build a mock repo that returns minimal valid data for the pipeline."""
    repo = MagicMock()

    items_df = pd.DataFrame({
        "item_id": ["item-1"],
        "name": ["Test Item"],
        "lead_time_days": [14],
        "safety_stock_days": [7],
    })
    repo.get_items.return_value = items_df

    repo.get_shipment_lead_times.return_value = pd.DataFrame(
        columns=["item_id", "lead_time_days"]
    )

    inventory_df = pd.DataFrame({
        "item_id": ["item-1"],
        "as_of_ts": [datetime.now(timezone.utc)],
        "current_qty": [50],
    })
    repo.get_current_inventory.return_value = inventory_df

    movements_df = pd.DataFrame({
        "event_id": ["ev-1"],
        "item_id": ["item-1"],
        "quantity_change": [-3],
        "reason": ["sale"],
        "at": [datetime.now(timezone.utc)],
    })
    repo.get_stock_movements.return_value = movements_df

    repo.get_historical_forecasts.return_value = pd.DataFrame(
        columns=["item_id", "computed_at", "mu_hat"]
    )

    repo.upsert_forecasts.return_value = 1
    repo.update_product_reorder_points.return_value = 1

    return repo


class TestRopPropagationErrorHandling:
    """Pipeline should not crash if ROP propagation fails after forecasts are saved."""

    def test_pipeline_returns_saved_count_when_rop_propagation_fails(self):
        """If update_product_reorder_points raises, pipeline still returns forecast count."""
        repo = _build_mock_repo()
        repo.update_product_reorder_points.side_effect = RuntimeError(
            "DB connection timeout"
        )

        pipeline = ForecastingPipeline(repo=repo)
        result = pipeline.run_for_items({"item-1"})

        # Pipeline should return the number of saved forecasts, not crash
        assert result == 1
        repo.upsert_forecasts.assert_called_once()
        repo.update_product_reorder_points.assert_called_once()

    def test_pipeline_logs_exception_when_rop_propagation_fails(self, caplog):
        """Error should be logged when ROP propagation fails."""
        repo = _build_mock_repo()
        repo.update_product_reorder_points.side_effect = RuntimeError(
            "constraint violation"
        )

        pipeline = ForecastingPipeline(repo=repo)

        with caplog.at_level("ERROR"):
            pipeline.run_for_items({"item-1"})

        assert any(
            "Failed to propagate reorder points" in record.message
            for record in caplog.records
        )

    def test_pipeline_succeeds_when_rop_propagation_succeeds(self):
        """Normal path: both forecast save and ROP propagation succeed."""
        repo = _build_mock_repo()
        repo.upsert_forecasts.return_value = 1
        repo.update_product_reorder_points.return_value = 1

        pipeline = ForecastingPipeline(repo=repo)
        result = pipeline.run_for_items({"item-1"})

        assert result == 1
        repo.upsert_forecasts.assert_called_once()
        repo.update_product_reorder_points.assert_called_once()
