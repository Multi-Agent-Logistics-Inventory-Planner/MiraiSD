"""Integration tests: Kafka event consumption -> EventAggregator -> ForecastingPipeline -> DB.

These tests verify the full internal flow of the forecasting service:
1. Events are parsed into NormalizedEvent objects
2. EventAggregator correctly batches and tracks event-carried inventory
3. ForecastingPipeline processes the batch and produces valid forecasts
4. upsert_forecasts is called with structurally correct DataFrame

Kafka is not required -- we simulate event arrival by directly feeding
NormalizedEvent objects into the aggregator. The real Kafka consumption
is tested at the E2E layer (tests/e2e/).
"""

import sys
from datetime import datetime, timezone
from unittest.mock import MagicMock
from uuid import uuid4

import pytest

# Mock kafka before any src imports
sys.modules.setdefault("kafka", MagicMock())
sys.modules.setdefault("kafka.errors", MagicMock())

from src.application.event_aggregator import EventAggregator
from src.application.pipeline import ForecastingPipeline
from src.events import EventEnvelope, NormalizedEvent


class TestEventToAggregatorFlow:
    """Verify event parsing and aggregation behavior."""

    def test_normalized_event_feeds_into_aggregator(self, item_id):
        """A NormalizedEvent should be accepted by the aggregator."""
        aggregator = EventAggregator(
            batch_window_seconds=0,
            batch_size_trigger=1,
            item_debounce_seconds=0,
        )

        event = NormalizedEvent(
            event_id=str(uuid4()),
            item_id=item_id,
            quantity_change=-3,
            reason="sale",
            at=datetime.now(timezone.utc),
            current_total_qty=47,
            previous_total_qty=50,
        )

        added = aggregator.add_event(event)
        assert added is True
        assert aggregator.event_count == 1
        assert item_id in aggregator.get_affected_items()

    def test_event_carried_inventory_tracked(self, item_id):
        """Aggregator should track the latest current_total_qty per item."""
        aggregator = EventAggregator(
            batch_window_seconds=60,
            batch_size_trigger=100,
            item_debounce_seconds=0,
        )

        event1 = NormalizedEvent(
            event_id=str(uuid4()),
            item_id=item_id,
            quantity_change=-3,
            reason="sale",
            at=datetime.now(timezone.utc),
            current_total_qty=50,
        )
        aggregator.add_event(event1)

        event2 = NormalizedEvent(
            event_id=str(uuid4()),
            item_id=item_id,
            quantity_change=-3,
            reason="sale",
            at=datetime.now(timezone.utc),
            current_total_qty=47,
        )
        aggregator.add_event(event2)

        inventory = aggregator.get_item_inventory()
        assert inventory[item_id] == 47

    def test_batch_triggers_on_size(self):
        """Aggregator should trigger batch when event count hits threshold."""
        aggregator = EventAggregator(
            batch_window_seconds=9999,
            batch_size_trigger=3,
            item_debounce_seconds=0,
        )

        for _ in range(3):
            event = NormalizedEvent(
                event_id=str(uuid4()),
                item_id=str(uuid4()),
                quantity_change=-1,
                reason="sale",
                at=datetime.now(timezone.utc),
            )
            aggregator.add_event(event)

        result = aggregator.check_batch_ready()
        assert result.ready is True
        assert result.event_count == 3
        assert result.trigger_reason == "size"

    def test_multiple_items_batched_together(self):
        """Multiple items should be batched into a single pipeline run."""
        aggregator = EventAggregator(
            batch_window_seconds=0,
            batch_size_trigger=5,
            item_debounce_seconds=0,
        )

        item_ids = [str(uuid4()) for _ in range(5)]
        for iid in item_ids:
            event = NormalizedEvent(
                event_id=str(uuid4()),
                item_id=iid,
                quantity_change=-2,
                reason="sale",
                at=datetime.now(timezone.utc),
                current_total_qty=40,
            )
            aggregator.add_event(event)

        result = aggregator.check_batch_ready()
        assert result.ready is True
        assert result.item_ids == set(item_ids)


class TestAggregatorToPipelineFlow:
    """Verify that aggregated events produce valid forecasts."""

    def test_pipeline_produces_forecast_from_events(self, item_id, mock_repo):
        """Full flow: events -> aggregator -> pipeline -> upsert_forecasts called."""
        aggregator = EventAggregator(
            batch_window_seconds=0,
            batch_size_trigger=1,
            item_debounce_seconds=0,
        )

        event = NormalizedEvent(
            event_id=str(uuid4()),
            item_id=item_id,
            quantity_change=-3,
            reason="sale",
            at=datetime.now(timezone.utc),
            current_total_qty=45,
        )
        aggregator.add_event(event)

        batch_result = aggregator.check_batch_ready()
        assert batch_result.ready is True

        pipeline = ForecastingPipeline(repo=mock_repo)
        event_inventory = aggregator.get_item_inventory()
        pipeline.run_for_items(
            batch_result.item_ids,
            event_inventory=event_inventory,
        )

        # Verify upsert_forecasts was called
        assert mock_repo.upsert_forecasts.called
        forecast_df = mock_repo.upsert_forecasts.call_args[0][0]

        # Verify forecast DataFrame structure
        assert "item_id" in forecast_df.columns
        assert "computed_at" in forecast_df.columns
        assert "avg_daily_delta" in forecast_df.columns
        assert "days_to_stockout" in forecast_df.columns
        assert "suggested_reorder_qty" in forecast_df.columns
        assert "confidence" in forecast_df.columns
        assert "features" in forecast_df.columns

        assert forecast_df.iloc[0]["item_id"] == item_id

        # Verify features JSONB contains expected keys
        features = forecast_df.iloc[0]["features"]
        assert "mu_hat" in features
        assert "sigma_d_hat" in features
        assert "safety_stock" in features
        assert "reorder_point" in features
        assert "current_qty" in features

    def test_event_carried_inventory_used_over_db_query(self, item_id, mock_repo):
        """When event_inventory is provided, pipeline should skip DB inventory query."""
        pipeline = ForecastingPipeline(repo=mock_repo)

        pipeline.run_for_items({item_id}, event_inventory={item_id: 42})

        mock_repo.get_current_inventory.assert_not_called()

        forecast_df = mock_repo.upsert_forecasts.call_args[0][0]
        features = forecast_df.iloc[0]["features"]
        assert features["current_qty"] == 42

    def test_pipeline_falls_back_to_db_when_no_event_inventory(self, item_id, mock_repo):
        """When event_inventory is empty/None, pipeline should query DB."""
        pipeline = ForecastingPipeline(repo=mock_repo)

        pipeline.run_for_items({item_id}, event_inventory=None)

        mock_repo.get_current_inventory.assert_called_once()

    def test_forecast_confidence_in_valid_range(self, item_id, mock_repo):
        """Forecast confidence should be between 0 and 1."""
        pipeline = ForecastingPipeline(repo=mock_repo)
        pipeline.run_for_items({item_id}, event_inventory={item_id: 45})

        forecast_df = mock_repo.upsert_forecasts.call_args[0][0]
        confidence = float(forecast_df.iloc[0]["confidence"])
        assert 0.0 <= confidence <= 1.0

    def test_forecast_days_to_stockout_positive(self, item_id, mock_repo):
        """days_to_stockout should be positive when there is inventory and demand."""
        pipeline = ForecastingPipeline(repo=mock_repo)
        pipeline.run_for_items({item_id}, event_inventory={item_id: 45})

        forecast_df = mock_repo.upsert_forecasts.call_args[0][0]
        days = forecast_df.iloc[0]["days_to_stockout"]
        assert days is not None
        assert float(days) > 0

    def test_forecast_reorder_point_computed(self, item_id, mock_repo):
        """Reorder point should be computed and stored in features."""
        pipeline = ForecastingPipeline(repo=mock_repo)
        pipeline.run_for_items({item_id}, event_inventory={item_id: 45})

        forecast_df = mock_repo.upsert_forecasts.call_args[0][0]
        features = forecast_df.iloc[0]["features"]
        assert "reorder_point" in features
        assert features["reorder_point"] >= 0


class TestEndToEndEventFlow:
    """Simulate the complete event processing flow as the worker would execute it."""

    def test_full_worker_simulation(self, item_id, mock_repo):
        """Simulate: event arrival -> aggregate -> batch trigger -> pipeline -> save.

        This mirrors what ForecastingWorker._process_batch() does.
        """
        events = [
            NormalizedEvent(
                event_id=str(uuid4()),
                item_id=item_id,
                quantity_change=-3,
                reason="sale",
                at=datetime.now(timezone.utc),
                current_total_qty=47,
                previous_total_qty=50,
            ),
            NormalizedEvent(
                event_id=str(uuid4()),
                item_id=item_id,
                quantity_change=-2,
                reason="sale",
                at=datetime.now(timezone.utc),
                current_total_qty=45,
                previous_total_qty=47,
            ),
        ]

        aggregator = EventAggregator(
            batch_window_seconds=0,
            batch_size_trigger=2,
            item_debounce_seconds=0,
        )
        added = aggregator.add_events(events)
        assert added == 2

        batch_result = aggregator.check_batch_ready()
        assert batch_result.ready is True
        assert item_id in batch_result.item_ids

        event_inventory = aggregator.get_item_inventory()
        assert event_inventory[item_id] == 45

        pipeline = ForecastingPipeline(repo=mock_repo)
        pipeline.run_for_items(
            batch_result.item_ids,
            event_inventory=event_inventory,
        )

        assert mock_repo.upsert_forecasts.called
        forecast_df = mock_repo.upsert_forecasts.call_args[0][0]

        row = forecast_df.iloc[0]
        assert row["item_id"] == item_id
        assert row["features"]["current_qty"] == 45
        assert row["features"]["mu_hat"] > 0

        flushed = aggregator.flush()
        assert len(flushed) == 2
        assert aggregator.is_empty
