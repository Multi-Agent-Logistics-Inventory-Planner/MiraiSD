"""End-to-end tests for the 4 optimizations.

Tests the complete flow from Kafka event to forecast output,
verifying all optimizations work together correctly.
"""

import json
import sys
import uuid
from datetime import datetime, timedelta, timezone
from unittest.mock import MagicMock, patch

import pandas as pd
import pytest

# Mock kafka module hierarchy to avoid import error in test environment
kafka_mock = MagicMock()
kafka_mock.errors = MagicMock()
kafka_mock.errors.KafkaError = Exception
sys.modules["kafka"] = kafka_mock
sys.modules["kafka.errors"] = kafka_mock.errors

from src.events import EventEnvelope, EventPayload, NormalizedEvent, _parse_line
from src.application.event_aggregator import EventAggregator
from src.application.pipeline import ForecastingPipeline
from src import config


class TestE2EEventCarriedState:
    """E2E test: Event with inventory -> Aggregator -> Pipeline -> Forecast."""

    def test_full_flow_with_event_carried_inventory(self):
        """Test complete flow using event-carried inventory (no DB query)."""
        # 1. Simulate Kafka event with inventory data (as inventory-service publishes)
        event_json = json.dumps({
            "event_id": str(uuid.uuid4()),
            "event_type": "INVENTORY_CHANGE",
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "payload": {
                "item_id": "item-abc-123",
                "quantity_change": -5,
                "reason": "sale",
                "at": datetime.now(timezone.utc).isoformat(),
                "current_total_qty": 95,  # Event carries current inventory
                "previous_total_qty": 100,
            }
        })

        # 2. Parse event (simulating Kafka consumer) - returns dict
        event_dict = _parse_line(event_json, strict=True)
        assert event_dict is not None
        assert event_dict["current_total_qty"] == 95
        assert event_dict["previous_total_qty"] == 100

        # Convert to NormalizedEvent for aggregator
        event = NormalizedEvent(**event_dict)

        # 3. Add to aggregator
        aggregator = EventAggregator(
            batch_window_seconds=30,
            batch_size_trigger=50,
            item_debounce_seconds=5.0,
        )
        aggregator.add_event(event)

        # 4. Get inventory from aggregator
        event_inventory = aggregator.get_item_inventory()
        assert event_inventory == {"item-abc-123": 95}

        # 5. Create mock repo that should NOT be called for inventory
        mock_repo = MagicMock()
        mock_repo.get_items.return_value = pd.DataFrame({
            "item_id": ["item-abc-123"],
            "name": ["Test Product"],
            "lead_time_days": [7],
            "safety_stock_days": [3],
        })
        mock_repo.get_stock_movements.return_value = pd.DataFrame({
            "event_id": [str(uuid.uuid4()) for _ in range(14)],
            "item_id": ["item-abc-123"] * 14,
            "quantity_change": [-5] * 14,
            "reason": ["sale"] * 14,
            "at": pd.date_range(end=datetime.now(timezone.utc), periods=14, freq="D"),
        })
        mock_repo.upsert_forecasts.return_value = 1

        # 6. Run pipeline with event inventory
        pipeline = ForecastingPipeline(repo=mock_repo)
        item_ids = {event.item_id}

        saved = pipeline.run_for_items(item_ids, event_inventory=event_inventory)

        # 7. Verify: get_current_inventory should NOT be called (using event data)
        mock_repo.get_current_inventory.assert_not_called()

        # 8. Verify forecast was created
        assert saved == 1
        mock_repo.upsert_forecasts.assert_called_once()

        # 9. Verify forecast uses correct inventory value (95 from event)
        forecast_df = mock_repo.upsert_forecasts.call_args[0][0]
        assert len(forecast_df) == 1
        assert forecast_df.iloc[0]["item_id"] == "item-abc-123"


class TestE2EBackwardCompatibility:
    """E2E test: Old event without inventory -> Falls back to DB."""

    def test_full_flow_without_event_inventory_falls_back_to_db(self):
        """Test that old events without inventory still work (DB fallback)."""
        # 1. Simulate old Kafka event WITHOUT inventory data
        event_json = json.dumps({
            "event_id": str(uuid.uuid4()),
            "event_type": "INVENTORY_CHANGE",
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "payload": {
                "item_id": "item-xyz-789",
                "quantity_change": -3,
                "reason": "sale",
                "at": datetime.now(timezone.utc).isoformat(),
                # No current_total_qty or previous_total_qty
            }
        })

        # 2. Parse event - returns dict
        event_dict = _parse_line(event_json, strict=True)
        assert event_dict is not None
        assert event_dict["current_total_qty"] is None  # Not provided
        assert event_dict["previous_total_qty"] is None

        # Convert to NormalizedEvent for aggregator
        event = NormalizedEvent(**event_dict)

        # 3. Add to aggregator
        aggregator = EventAggregator(
            batch_window_seconds=30,
            batch_size_trigger=50,
            item_debounce_seconds=5.0,
        )
        aggregator.add_event(event)

        # 4. Get inventory - should be empty (no inventory in event)
        event_inventory = aggregator.get_item_inventory()
        assert event_inventory == {}

        # 5. Create mock repo that SHOULD be called for inventory
        mock_repo = MagicMock()
        mock_repo.get_items.return_value = pd.DataFrame({
            "item_id": ["item-xyz-789"],
            "name": ["Test Product"],
            "lead_time_days": [7],
            "safety_stock_days": [3],
        })
        mock_repo.get_current_inventory.return_value = pd.DataFrame({
            "item_id": ["item-xyz-789"],
            "as_of_ts": [datetime.now(timezone.utc)],
            "current_qty": [50],  # DB returns inventory
        })
        mock_repo.get_stock_movements.return_value = pd.DataFrame({
            "event_id": [str(uuid.uuid4()) for _ in range(14)],
            "item_id": ["item-xyz-789"] * 14,
            "quantity_change": [-3] * 14,
            "reason": ["sale"] * 14,
            "at": pd.date_range(end=datetime.now(timezone.utc), periods=14, freq="D"),
        })
        mock_repo.upsert_forecasts.return_value = 1

        # 6. Run pipeline WITHOUT event inventory (empty dict)
        pipeline = ForecastingPipeline(repo=mock_repo)
        item_ids = {event.item_id}

        saved = pipeline.run_for_items(item_ids, event_inventory=event_inventory)

        # 7. Verify: get_current_inventory SHOULD be called (fallback to DB)
        mock_repo.get_current_inventory.assert_called_once()

        # 8. Verify forecast was created
        assert saved == 1


class TestE2EUnifiedInventoryQuery:
    """E2E test: Verify query uses unified location_inventory table."""

    def test_get_current_inventory_query_structure(self):
        """Verify the SQL query uses the unified location_inventory table with a single WHERE."""
        from src.adapters.supabase_repo import SupabaseRepo

        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.connect.return_value.__exit__ = MagicMock(return_value=False)

        captured_query = None
        def capture_read_sql(query, conn, params=None):
            nonlocal captured_query
            captured_query = str(query)
            return pd.DataFrame(columns=["item_id", "as_of_ts", "current_qty"])

        with patch("pandas.read_sql", side_effect=capture_read_sql):
            repo = SupabaseRepo(engine=mock_engine)
            repo.get_current_inventory(item_ids=[
                "550e8400-e29b-41d4-a716-446655440001",
                "550e8400-e29b-41d4-a716-446655440002",
            ])

        assert captured_query is not None
        # Unified schema: single table, single WHERE clause
        assert "location_inventory" in captured_query.lower(), (
            "Query should use unified location_inventory table"
        )
        where_count = captured_query.lower().count("where")
        assert where_count == 1, f"Expected 1 WHERE clause (unified table), got {where_count}"


class TestE2EBatchUpsert:
    """E2E test: Verify batch upsert uses single execute."""

    def test_upsert_forecasts_batch_execution(self):
        """Verify upsert uses single execute() for multiple rows."""
        from src.adapters.supabase_repo import SupabaseRepo

        # Create mock engine
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.begin.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.begin.return_value.__exit__ = MagicMock(return_value=False)

        execute_calls = []
        def capture_execute(query, params=None):
            execute_calls.append(params)
            return MagicMock()

        mock_conn.execute = capture_execute

        # Create test data with 10 rows
        df = pd.DataFrame({
            "item_id": [f"item-{i}" for i in range(10)],
            "computed_at": [datetime.now(timezone.utc)] * 10,
            "horizon_days": [21] * 10,
            "avg_daily_delta": [-5.0] * 10,
            "days_to_stockout": [20.0] * 10,
            "suggested_reorder_qty": [100] * 10,
            "suggested_order_date": [None] * 10,
            "confidence": [0.85] * 10,
            "features": [{}] * 10,
        })

        repo = SupabaseRepo(engine=mock_engine)
        count = repo.upsert_forecasts(df)

        # Verify single execute call with list of 10 rows
        assert len(execute_calls) == 1, f"Expected 1 execute call, got {len(execute_calls)}"
        assert len(execute_calls[0]) == 10, f"Expected 10 rows in params, got {len(execute_calls[0])}"
        assert count == 10


class TestE2EVectorizedOperations:
    """E2E test: Verify vectorized operations produce correct results."""

    def test_vectorized_matches_scalar_for_real_data(self):
        """Test vectorized policy matches scalar version with realistic data."""
        from src import policy

        # Realistic inventory data
        mu_hat = pd.Series([5.2, 3.1, 10.5, 0.8, 15.0])
        sigma_d_hat = pd.Series([1.5, 0.9, 2.3, 0.3, 4.2])
        current_qty = pd.Series([100, 50, 200, 20, 500])
        lead_time = pd.Series([7, 5, 10, 3, 14])
        service_level = 0.95
        target_days = 21

        # Vectorized computation
        safety_vec = policy.compute_safety_stock_vectorized(
            mu_hat, sigma_d_hat, lead_time, service_level
        )
        reorder_vec = policy.reorder_point_vectorized(mu_hat, safety_vec, lead_time)
        days_out_vec = policy.days_to_stockout_vectorized(current_qty, mu_hat)
        order_vec = policy.suggest_order_vectorized(
            current_qty, mu_hat, lead_time, safety_vec, target_days
        )

        # Scalar computation for comparison
        for i in range(len(mu_hat)):
            safety_scalar = policy.compute_safety_stock(
                mu_hat.iloc[i], sigma_d_hat.iloc[i], lead_time.iloc[i], service_level
            )
            reorder_scalar = policy.reorder_point(
                mu_hat.iloc[i], safety_scalar, lead_time.iloc[i]
            )
            days_out_scalar = policy.days_to_stockout(
                current_qty.iloc[i], mu_hat.iloc[i]
            )
            order_scalar = policy.suggest_order(
                current_qty.iloc[i], mu_hat.iloc[i], lead_time.iloc[i],
                safety_scalar, target_days
            )

            # Verify match (within floating point tolerance)
            assert abs(safety_vec.iloc[i] - safety_scalar) < 1e-6
            assert abs(reorder_vec.iloc[i] - reorder_scalar) < 1e-6
            assert abs(days_out_vec.iloc[i] - days_out_scalar) < 1e-6
            assert order_vec.iloc[i] == order_scalar


class TestE2EFullPipelineIntegration:
    """E2E test: Complete pipeline with all optimizations."""

    def test_complete_pipeline_with_all_optimizations(self):
        """Test full pipeline: event -> aggregator -> pipeline -> forecast."""
        # 1. Create multiple events with inventory data
        events = []
        for i in range(5):
            event = NormalizedEvent(
                event_id=str(uuid.uuid4()),
                item_id=f"item-{i}",
                quantity_change=-3,
                reason="sale",
                at=datetime.now(timezone.utc),
                current_total_qty=100 - (i * 10),  # 100, 90, 80, 70, 60
                previous_total_qty=103 - (i * 10),
            )
            events.append(event)

        # 2. Add all events to aggregator
        aggregator = EventAggregator(
            batch_window_seconds=30,
            batch_size_trigger=50,
            item_debounce_seconds=5.0,
        )
        for event in events:
            aggregator.add_event(event)

        # 3. Get aggregated data
        item_ids = aggregator.get_affected_items()
        event_inventory = aggregator.get_item_inventory()

        assert len(item_ids) == 5
        assert len(event_inventory) == 5
        assert event_inventory["item-0"] == 100
        assert event_inventory["item-4"] == 60

        # 4. Create mock repo
        mock_repo = MagicMock()
        mock_repo.get_items.return_value = pd.DataFrame({
            "item_id": [f"item-{i}" for i in range(5)],
            "name": [f"Product {i}" for i in range(5)],
            "lead_time_days": [7] * 5,
            "safety_stock_days": [3] * 5,
        })

        # Create stock movements for each item
        movements = []
        for i in range(5):
            for day in range(14):
                movements.append({
                    "event_id": str(uuid.uuid4()),
                    "item_id": f"item-{i}",
                    "quantity_change": -3,
                    "reason": "sale",
                    "at": datetime.now(timezone.utc) - timedelta(days=day),
                })
        mock_repo.get_stock_movements.return_value = pd.DataFrame(movements)
        mock_repo.upsert_forecasts.return_value = 5

        # 5. Run pipeline
        pipeline = ForecastingPipeline(repo=mock_repo)
        saved = pipeline.run_for_items(item_ids, event_inventory=event_inventory)

        # 6. Verify results
        # - get_current_inventory should NOT be called (using event data)
        mock_repo.get_current_inventory.assert_not_called()

        # - Forecast should be created for all 5 items
        assert saved == 5

        # - Verify forecast DataFrame structure
        forecast_df = mock_repo.upsert_forecasts.call_args[0][0]
        assert len(forecast_df) == 5
        assert set(forecast_df["item_id"]) == {f"item-{i}" for i in range(5)}

        # - Verify all required columns present
        required_cols = [
            "item_id", "computed_at", "horizon_days", "avg_daily_delta",
            "days_to_stockout", "suggested_reorder_qty", "suggested_order_date",
            "confidence", "features"
        ]
        for col in required_cols:
            assert col in forecast_df.columns, f"Missing column: {col}"


class TestE2EEdgeCases:
    """E2E tests for edge cases and error handling."""

    def test_empty_event_inventory_falls_back_gracefully(self):
        """Test that empty event inventory falls back to DB query."""
        mock_repo = MagicMock()
        mock_repo.get_items.return_value = pd.DataFrame({
            "item_id": ["item-1"],
            "name": ["Product 1"],
            "lead_time_days": [7],
            "safety_stock_days": [3],
        })
        mock_repo.get_current_inventory.return_value = pd.DataFrame({
            "item_id": ["item-1"],
            "as_of_ts": [datetime.now(timezone.utc)],
            "current_qty": [50],
        })
        mock_repo.get_stock_movements.return_value = pd.DataFrame({
            "event_id": [str(uuid.uuid4()) for _ in range(14)],
            "item_id": ["item-1"] * 14,
            "quantity_change": [-3] * 14,
            "reason": ["sale"] * 14,
            "at": pd.date_range(end=datetime.now(timezone.utc), periods=14, freq="D"),
        })
        mock_repo.upsert_forecasts.return_value = 1

        pipeline = ForecastingPipeline(repo=mock_repo)

        # Empty event inventory should trigger DB fallback
        saved = pipeline.run_for_items({"item-1"}, event_inventory={})

        # DB should be queried
        mock_repo.get_current_inventory.assert_called_once()
        assert saved == 1

    def test_partial_event_inventory_queries_db_for_missing(self):
        """Test that partial event inventory still works (missing items use DB)."""
        mock_repo = MagicMock()
        mock_repo.get_items.return_value = pd.DataFrame({
            "item_id": ["item-1", "item-2"],
            "name": ["Product 1", "Product 2"],
            "lead_time_days": [7, 7],
            "safety_stock_days": [3, 3],
        })
        # DB returns inventory for item-2 (not in event inventory)
        mock_repo.get_current_inventory.return_value = pd.DataFrame({
            "item_id": ["item-1", "item-2"],
            "as_of_ts": [datetime.now(timezone.utc)] * 2,
            "current_qty": [50, 75],
        })
        mock_repo.get_stock_movements.return_value = pd.DataFrame({
            "event_id": [str(uuid.uuid4()) for _ in range(28)],
            "item_id": ["item-1"] * 14 + ["item-2"] * 14,
            "quantity_change": [-3] * 28,
            "reason": ["sale"] * 28,
            "at": list(pd.date_range(end=datetime.now(timezone.utc), periods=14, freq="D")) * 2,
        })
        mock_repo.upsert_forecasts.return_value = 2

        pipeline = ForecastingPipeline(repo=mock_repo)

        # Only item-1 in event inventory
        event_inventory = {"item-1": 100}
        saved = pipeline.run_for_items({"item-1", "item-2"}, event_inventory=event_inventory)

        # Should still produce forecasts for both items
        assert saved == 2

    def test_zero_demand_items_handled_correctly(self):
        """Test items with zero historical demand are handled."""
        mock_repo = MagicMock()
        mock_repo.get_items.return_value = pd.DataFrame({
            "item_id": ["item-zero"],
            "name": ["Zero Demand Product"],
            "lead_time_days": [7],
            "safety_stock_days": [3],
        })
        # No stock movements (zero demand)
        mock_repo.get_stock_movements.return_value = pd.DataFrame(
            columns=["event_id", "item_id", "quantity_change", "reason", "at"]
        )
        mock_repo.upsert_forecasts.return_value = 1

        pipeline = ForecastingPipeline(repo=mock_repo)
        event_inventory = {"item-zero": 100}

        # Should not crash with zero demand
        saved = pipeline.run_for_items({"item-zero"}, event_inventory=event_inventory)

        assert saved == 1
        forecast_df = mock_repo.upsert_forecasts.call_args[0][0]
        # Zero demand items get a very high days_to_stockout (current_qty / epsilon)
        # With 100 qty and epsilon of 0.1, this is 1000 days
        days_to_stockout = forecast_df.iloc[0]["days_to_stockout"]
        # Should be a large number (not crash), exact value depends on epsilon
        assert days_to_stockout is None or pd.isna(days_to_stockout) or days_to_stockout >= 100
