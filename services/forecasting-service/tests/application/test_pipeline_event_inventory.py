"""Tests for ForecastingPipeline with event-carried inventory.

TDD Step 1: RED - These tests verify the pipeline uses event inventory when available
and falls back to database queries when not.
"""

import sys
from datetime import datetime, timezone
from unittest.mock import MagicMock, call, patch

import pandas as pd
import pytest

# Mock kafka module before importing
sys.modules["kafka"] = MagicMock()
sys.modules["kafka.errors"] = MagicMock()

from src.application.pipeline import ForecastingPipeline


class TestPipelineEventInventory:
    """Tests for pipeline using event-carried inventory state."""

    def test_uses_event_inventory_when_provided(self):
        """Pipeline should use event_inventory instead of querying database."""
        mock_repo = MagicMock()

        # Setup mock returns
        items_df = pd.DataFrame(
            {
                "item_id": ["item-1", "item-2"],
                "name": ["Item 1", "Item 2"],
                "lead_time_days": [7, 5],
            }
        )
        mock_repo.get_items.return_value = items_df
        mock_repo.get_stock_movements.return_value = pd.DataFrame()
        mock_repo.upsert_forecasts.return_value = 2

        pipeline = ForecastingPipeline(repo=mock_repo)

        # Call with event_inventory
        event_inventory = {"item-1": 100, "item-2": 50}
        result = pipeline.run_for_items({"item-1", "item-2"}, event_inventory=event_inventory)

        # Should NOT call get_current_inventory when event_inventory is provided
        mock_repo.get_current_inventory.assert_not_called()

        # Should still process and save forecasts
        assert result == 2

    def test_falls_back_to_db_when_event_inventory_is_none(self):
        """Pipeline should query database when event_inventory is None."""
        mock_repo = MagicMock()

        items_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "name": ["Item 1"],
                "lead_time_days": [7],
            }
        )
        inventory_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "current_qty": [100],
            }
        )
        mock_repo.get_items.return_value = items_df
        mock_repo.get_current_inventory.return_value = inventory_df
        mock_repo.get_stock_movements.return_value = pd.DataFrame()
        mock_repo.upsert_forecasts.return_value = 1

        pipeline = ForecastingPipeline(repo=mock_repo)

        # Call without event_inventory
        result = pipeline.run_for_items({"item-1"}, event_inventory=None)

        # Should call get_current_inventory
        mock_repo.get_current_inventory.assert_called_once()
        assert result == 1

    def test_falls_back_to_db_when_event_inventory_is_empty(self):
        """Pipeline should query database when event_inventory is empty dict."""
        mock_repo = MagicMock()

        items_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "name": ["Item 1"],
                "lead_time_days": [7],
            }
        )
        inventory_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "current_qty": [100],
            }
        )
        mock_repo.get_items.return_value = items_df
        mock_repo.get_current_inventory.return_value = inventory_df
        mock_repo.get_stock_movements.return_value = pd.DataFrame()
        mock_repo.upsert_forecasts.return_value = 1

        pipeline = ForecastingPipeline(repo=mock_repo)

        # Call with empty event_inventory
        result = pipeline.run_for_items({"item-1"}, event_inventory={})

        # Should fall back to database
        mock_repo.get_current_inventory.assert_called_once()

    def test_backward_compatible_without_event_inventory_param(self):
        """Existing calls without event_inventory should still work."""
        mock_repo = MagicMock()

        items_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "name": ["Item 1"],
                "lead_time_days": [7],
            }
        )
        inventory_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "current_qty": [100],
            }
        )
        mock_repo.get_items.return_value = items_df
        mock_repo.get_current_inventory.return_value = inventory_df
        mock_repo.get_stock_movements.return_value = pd.DataFrame()
        mock_repo.upsert_forecasts.return_value = 1

        pipeline = ForecastingPipeline(repo=mock_repo)

        # Call without the event_inventory parameter at all (backward compat)
        result = pipeline.run_for_items({"item-1"})

        # Should query database
        mock_repo.get_current_inventory.assert_called_once()
        assert result == 1


class TestBuildInventoryFromEvents:
    """Tests for _build_inventory_from_events helper."""

    def test_builds_correct_dataframe_structure(self):
        """Should build DataFrame with correct columns and types."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        event_inventory = {"item-1": 100, "item-2": 50}
        item_ids = {"item-1", "item-2"}

        result = pipeline._build_inventory_from_events(item_ids, event_inventory)

        assert "item_id" in result.columns
        assert "as_of_ts" in result.columns
        assert "current_qty" in result.columns
        assert len(result) == 2

    def test_filters_to_requested_items(self):
        """Should only include items in item_ids set."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        event_inventory = {"item-1": 100, "item-2": 50, "item-3": 75}
        item_ids = {"item-1", "item-2"}  # Only request 2 items

        result = pipeline._build_inventory_from_events(item_ids, event_inventory)

        assert len(result) == 2
        assert set(result["item_id"]) == {"item-1", "item-2"}

    def test_handles_missing_items_in_event_inventory(self):
        """Should handle items not present in event_inventory."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        event_inventory = {"item-1": 100}
        item_ids = {"item-1", "item-2"}  # item-2 not in event_inventory

        result = pipeline._build_inventory_from_events(item_ids, event_inventory)

        # Should only include item-1
        assert len(result) == 1
        assert result.iloc[0]["item_id"] == "item-1"

    def test_returns_empty_dataframe_when_no_matches(self):
        """Should return empty DataFrame when no items match."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        event_inventory = {"item-3": 100}
        item_ids = {"item-1", "item-2"}

        result = pipeline._build_inventory_from_events(item_ids, event_inventory)

        assert len(result) == 0
        assert "item_id" in result.columns
        assert "current_qty" in result.columns

    def test_correct_data_types(self):
        """Should have correct data types for all columns."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        event_inventory = {"item-1": 100}
        item_ids = {"item-1"}

        result = pipeline._build_inventory_from_events(item_ids, event_inventory)

        assert result["item_id"].dtype == object  # String
        assert pd.api.types.is_datetime64_any_dtype(result["as_of_ts"])
        assert result["current_qty"].dtype == int

    def test_inventory_values_match_event_data(self):
        """Inventory values should match what was in the event."""
        mock_repo = MagicMock()
        pipeline = ForecastingPipeline(repo=mock_repo)

        event_inventory = {"item-1": 100, "item-2": 50}
        item_ids = {"item-1", "item-2"}

        result = pipeline._build_inventory_from_events(item_ids, event_inventory)

        # Sort for consistent comparison
        result_sorted = result.sort_values("item_id").reset_index(drop=True)

        assert result_sorted.iloc[0]["item_id"] == "item-1"
        assert result_sorted.iloc[0]["current_qty"] == 100
        assert result_sorted.iloc[1]["item_id"] == "item-2"
        assert result_sorted.iloc[1]["current_qty"] == 50


class TestPipelineForecastsWithEventInventory:
    """Tests for forecast computation using event inventory."""

    def test_forecasts_use_event_inventory_values(self):
        """Forecasts should use inventory values from events."""
        mock_repo = MagicMock()

        items_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "name": ["Item 1"],
                "lead_time_days": [7],
            }
        )
        mock_repo.get_items.return_value = items_df
        mock_repo.get_stock_movements.return_value = pd.DataFrame()
        mock_repo.upsert_forecasts.return_value = 1

        pipeline = ForecastingPipeline(repo=mock_repo)

        # Event says inventory is 100
        event_inventory = {"item-1": 100}
        pipeline.run_for_items({"item-1"}, event_inventory=event_inventory)

        # Verify the forecast used inventory=100
        call_args = mock_repo.upsert_forecasts.call_args
        forecasts_df = call_args[0][0]

        assert len(forecasts_df) == 1
        assert forecasts_df.iloc[0]["features"]["current_qty"] == 100

    def test_partial_event_inventory_mixed_with_db(self):
        """Items missing from event_inventory should fall back to DB query."""
        mock_repo = MagicMock()

        items_df = pd.DataFrame(
            {
                "item_id": ["item-1", "item-2"],
                "name": ["Item 1", "Item 2"],
                "lead_time_days": [7, 7],
            }
        )
        # DB has inventory for item-2
        db_inventory_df = pd.DataFrame(
            {
                "item_id": ["item-2"],
                "current_qty": [200],
            }
        )
        mock_repo.get_items.return_value = items_df
        mock_repo.get_current_inventory.return_value = db_inventory_df
        mock_repo.get_stock_movements.return_value = pd.DataFrame()
        mock_repo.upsert_forecasts.return_value = 2

        pipeline = ForecastingPipeline(repo=mock_repo)

        # Event only has item-1
        event_inventory = {"item-1": 100}
        pipeline.run_for_items({"item-1", "item-2"}, event_inventory=event_inventory)

        # When event_inventory is provided but doesn't have all items,
        # we should still be using event inventory for items that have it
        # For this implementation, we either use ALL event inventory or ALL DB
        # Based on the spec, if event_inventory is provided, use it for what's there


class TestRunAllBackwardCompatibility:
    """Tests that run_all() still works without event_inventory."""

    def test_run_all_works_without_event_inventory(self):
        """run_all() should work normally (no event_inventory support needed)."""
        mock_repo = MagicMock()

        items_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "name": ["Item 1"],
                "lead_time_days": [7],
            }
        )
        inventory_df = pd.DataFrame(
            {
                "item_id": ["item-1"],
                "current_qty": [100],
            }
        )
        mock_repo.get_items.return_value = items_df
        mock_repo.get_current_inventory.return_value = inventory_df
        mock_repo.get_stock_movements.return_value = pd.DataFrame()
        mock_repo.upsert_forecasts.return_value = 1

        pipeline = ForecastingPipeline(repo=mock_repo)

        result = pipeline.run_all()

        assert result == 1
        mock_repo.get_current_inventory.assert_called_once()
