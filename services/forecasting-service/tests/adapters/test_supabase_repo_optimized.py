"""Tests for optimized repository methods.

This module contains tests for:
1. get_current_inventory() - SQL query against unified location_inventory table
2. upsert_forecasts() - Batch INSERT using single execute() call instead of N+1 pattern
"""

from unittest.mock import MagicMock, patch, call
import json
import re
import sys
import uuid
from datetime import datetime, timezone

import numpy as np
import pandas as pd
import pytest

# Mock kafka module and submodules before any src imports
mock_kafka = MagicMock()
mock_kafka_errors = MagicMock()
mock_kafka.errors = mock_kafka_errors
sys.modules["kafka"] = mock_kafka
sys.modules["kafka.errors"] = mock_kafka_errors

from src.adapters.supabase_repo import SupabaseRepo


class TestGetCurrentInventoryUnifiedQuery:
    """Tests for get_current_inventory using the unified location_inventory table."""

    def test_get_current_inventory_filters_by_item_ids(self):
        """Verify that when item_ids is provided, the query filters with WHERE product_id = ANY."""
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.connect.return_value.__exit__ = MagicMock(return_value=False)

        captured_sql = []

        def capture_read_sql(sql, conn, params=None):
            sql_str = str(sql)
            captured_sql.append(sql_str)
            return pd.DataFrame(columns=["item_id", "as_of_ts", "current_qty"])

        repo = SupabaseRepo(engine=mock_engine)

        test_uuids = [
            "550e8400-e29b-41d4-a716-446655440001",
            "550e8400-e29b-41d4-a716-446655440002",
        ]
        with patch("pandas.read_sql", side_effect=capture_read_sql):
            repo.get_current_inventory(item_ids=test_uuids)

        assert len(captured_sql) == 1, "Expected exactly one SQL query"
        sql = captured_sql[0]

        # Unified schema uses single location_inventory table
        assert "location_inventory" in sql.lower(), (
            "Query should reference location_inventory table"
        )

        # Should have exactly one WHERE clause for item_id filtering
        where_count = sql.lower().count("where")
        assert where_count == 1, (
            f"Expected 1 WHERE clause (unified table), got {where_count}"
        )

        # Should GROUP BY product_id for aggregation
        assert "group by" in sql.lower(), "Query should GROUP BY product_id"

    def test_get_current_inventory_no_filter_when_no_items(self):
        """Verify that when item_ids is None, no WHERE clauses are generated."""
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.connect.return_value.__exit__ = MagicMock(return_value=False)

        captured_sql = []

        def capture_read_sql(sql, conn, params=None):
            sql_str = str(sql)
            captured_sql.append(sql_str)
            return pd.DataFrame(columns=["item_id", "as_of_ts", "current_qty"])

        repo = SupabaseRepo(engine=mock_engine)

        with patch("pandas.read_sql", side_effect=capture_read_sql):
            repo.get_current_inventory(item_ids=None)

        assert len(captured_sql) == 1, "Expected exactly one SQL query"
        sql = captured_sql[0]

        # No WHERE clause when no item_ids filter
        where_count = sql.lower().count("where")
        assert where_count == 0, (
            f"Expected 0 WHERE clauses (no filter), got {where_count}"
        )


class TestUpsertForecastsBatch:
    """Tests for batch upsert: single execute() call instead of N+1 pattern."""

    def test_upsert_forecasts_batch_execution(self):
        """Verify upsert uses single execute() for multiple rows."""
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.begin.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.begin.return_value.__exit__ = MagicMock(return_value=False)

        repo = SupabaseRepo(engine=mock_engine)

        # Create test DataFrame with 5 forecast rows
        test_df = pd.DataFrame({
            "item_id": [str(uuid.uuid4()) for _ in range(5)],
            "computed_at": [datetime.now(timezone.utc)] * 5,
            "horizon_days": [14] * 5,
            "avg_daily_delta": np.random.uniform(-5, 0, 5),
            "days_to_stockout": np.random.uniform(1, 30, 5),
            "suggested_reorder_qty": np.random.randint(1, 50, 5),
            "suggested_order_date": [None] * 5,
            "confidence": np.random.uniform(0.5, 1.0, 5),
            "features": [json.dumps({"mu_hat": -1.5})] * 5,
        })

        repo.upsert_forecasts(test_df)

        # Verify execute was called (batch execution)
        assert mock_conn.execute.called, "Expected execute() to be called for batch upsert"

        # Verify it was a single execute call, not N separate calls
        execute_calls = mock_conn.execute.call_count
        assert execute_calls == 1, (
            f"Expected 1 execute() call (batch), got {execute_calls}. "
            "Batch upsert should use a single execute() call."
        )
