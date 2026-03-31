"""Tests for optimized repository methods.

This module contains tests for:
1. get_current_inventory() - SQL query pushes WHERE clauses into each UNION branch
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


class TestGetCurrentInventoryOptimizedQuery:
    """Tests for query optimization: pushing filters into UNION branches."""

    # The 9 inventory tables that should each have a WHERE clause when filtered
    INVENTORY_TABLES = [
        "box_bin_inventory",
        "rack_inventory",
        "cabinet_inventory",
        "single_claw_machine_inventory",
        "double_claw_machine_inventory",
        "pusher_machine_inventory",
        "four_corner_machine_inventory",
        "window_inventory",
        "not_assigned_inventory",
    ]

    def test_get_current_inventory_pushes_filter_into_union(self):
        """Verify that when item_ids is provided, each UNION branch has its own WHERE clause.

        The optimized query should have 9 WHERE clauses (one per inventory table)
        instead of wrapping the entire UNION in an outer SELECT with a single WHERE.
        """
        # Create mock engine that captures the executed SQL
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.connect.return_value.__exit__ = MagicMock(return_value=False)

        # Track the SQL that gets executed
        captured_sql = []

        def capture_read_sql(sql, conn, params=None):
            # Extract the actual SQL string from the text() object
            sql_str = str(sql)
            captured_sql.append(sql_str)
            # Return empty DataFrame to simulate no results
            return pd.DataFrame(columns=["item_id", "as_of_ts", "current_qty"])

        repo = SupabaseRepo(engine=mock_engine)

        test_uuids = [
            "550e8400-e29b-41d4-a716-446655440001",
            "550e8400-e29b-41d4-a716-446655440002",
        ]
        with patch("pandas.read_sql", side_effect=capture_read_sql):
            repo.get_current_inventory(item_ids=test_uuids)

        # Verify we captured the SQL
        assert len(captured_sql) == 1, "Expected exactly one SQL query"
        sql = captured_sql[0]

        # Count WHERE clauses - should be 9 (one per inventory table)
        where_count = sql.lower().count("where")
        assert where_count == 9, (
            f"Expected 9 WHERE clauses (one per inventory table), got {where_count}. "
            "Filter should be pushed into each UNION branch."
        )

        # Verify each table has its own WHERE clause with item_id filter
        for table in self.INVENTORY_TABLES:
            # Pattern: SELECT ... FROM table_name WHERE item_id
            pattern = rf"SELECT\s+.*?\s+FROM\s+{table}\s+WHERE\s+item_id"
            match = re.search(pattern, sql, re.IGNORECASE | re.DOTALL)
            assert match is not None, (
                f"Table '{table}' should have WHERE item_id clause in its SELECT. "
                f"Filter not pushed into this UNION branch."
            )

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

        # No WHERE clauses should exist when no filter is applied
        where_count = sql.lower().count("where")
        assert where_count == 0, (
            f"Expected 0 WHERE clauses when item_ids is None, got {where_count}. "
            "Query should not include unnecessary WHERE clauses."
        )

    def test_get_current_inventory_uses_parameterized_query(self):
        """Verify that item_ids filter uses parameterized query (no SQL injection)."""
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.connect.return_value.__exit__ = MagicMock(return_value=False)

        captured_params = []

        def capture_read_sql(sql, conn, params=None):
            captured_params.append(params)
            return pd.DataFrame(columns=["item_id", "as_of_ts", "current_qty"])

        repo = SupabaseRepo(engine=mock_engine)

        test_ids = [
            "550e8400-e29b-41d4-a716-446655440003",
            "550e8400-e29b-41d4-a716-446655440004",
        ]
        with patch("pandas.read_sql", side_effect=capture_read_sql):
            repo.get_current_inventory(item_ids=test_ids)

        assert len(captured_params) == 1, "Expected params to be captured"
        params = captured_params[0]

        # Verify item_ids are passed as parameters (not embedded in SQL string)
        assert params is not None, "Params should not be None when item_ids provided"
        assert "item_ids" in params, "Params should contain 'item_ids' key"
        # item_ids should be converted to UUID objects for psycopg2 native handling
        expected_uuids = [uuid.UUID(iid) for iid in test_ids]
        assert params["item_ids"] == expected_uuids, (
            "item_ids should be passed as UUID list parameter"
        )


class TestGetCurrentInventoryBehavior:
    """Tests for backward compatibility - behavior should remain identical."""

    def test_get_current_inventory_returns_correct_columns(self):
        """Verify the return DataFrame has correct columns."""
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.connect.return_value.__exit__ = MagicMock(return_value=False)

        def mock_read_sql(sql, conn, params=None):
            return pd.DataFrame(columns=["item_id", "as_of_ts", "current_qty"])

        repo = SupabaseRepo(engine=mock_engine)

        with patch("pandas.read_sql", side_effect=mock_read_sql):
            result = repo.get_current_inventory()

        expected_columns = ["item_id", "as_of_ts", "current_qty"]
        assert list(result.columns) == expected_columns, (
            f"Expected columns {expected_columns}, got {list(result.columns)}"
        )

    def test_get_current_inventory_returns_empty_df_on_no_results(self):
        """Verify empty DataFrame is returned when no results found."""
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.connect.return_value.__exit__ = MagicMock(return_value=False)

        def mock_read_sql(sql, conn, params=None):
            return pd.DataFrame()

        repo = SupabaseRepo(engine=mock_engine)

        with patch("pandas.read_sql", side_effect=mock_read_sql):
            # Use a valid UUID that doesn't exist in the database
            result = repo.get_current_inventory(
                item_ids=["00000000-0000-0000-0000-000000000000"]
            )

        assert result.empty, "Should return empty DataFrame when no results"
        assert list(result.columns) == ["item_id", "as_of_ts", "current_qty"], (
            "Empty DataFrame should still have correct columns"
        )

    def test_get_current_inventory_converts_types_correctly(self):
        """Verify data types are correctly converted in the result."""
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.connect.return_value.__exit__ = MagicMock(return_value=False)

        def mock_read_sql(sql, conn, params=None):
            return pd.DataFrame({
                "item_id": ["uuid-1", "uuid-2"],
                "as_of_ts": pd.to_datetime(["2025-01-01", "2025-01-02"], utc=True),
                "current_qty": [10, 20],
            })

        repo = SupabaseRepo(engine=mock_engine)

        with patch("pandas.read_sql", side_effect=mock_read_sql):
            result = repo.get_current_inventory()

        # Verify types (check string-like types - could be 'object' or 'str' depending on pandas version)
        assert result["item_id"].dtype in ("object", "str", "string"), "item_id should be string type"
        assert pd.api.types.is_datetime64_any_dtype(result["as_of_ts"]), (
            "as_of_ts should be datetime type"
        )
        assert pd.api.types.is_integer_dtype(result["current_qty"]), (
            "current_qty should be integer type"
        )


class TestUpsertForecastsOptimizedBatch:
    """Tests for upsert_forecasts() optimization: single execute() call instead of N+1 loop."""

    def _create_mock_engine(self):
        """Create a mock SQLAlchemy engine with connection context manager."""
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.begin.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.begin.return_value.__exit__ = MagicMock(return_value=False)
        return mock_engine, mock_conn

    def _create_test_dataframe(self, num_rows: int) -> pd.DataFrame:
        """Create a test DataFrame with the required forecast columns."""
        return pd.DataFrame({
            "item_id": [f"uuid-{i}" for i in range(num_rows)],
            "computed_at": [datetime.now(timezone.utc) for _ in range(num_rows)],
            "horizon_days": [21 for _ in range(num_rows)],
            "avg_daily_delta": [3.5 + i * 0.1 for i in range(num_rows)],
            "days_to_stockout": [10.0 + i for i in range(num_rows)],
            "suggested_reorder_qty": [45 + i for i in range(num_rows)],
            "suggested_order_date": ["2025-11-05" for _ in range(num_rows)],
            "confidence": [0.95 for _ in range(num_rows)],
            "features": [{"feature_key": f"value_{i}"} for i in range(num_rows)],
        })

    def test_upsert_forecasts_uses_single_execute(self):
        """Verify that upsert_forecasts makes only ONE execute() call regardless of row count.

        This tests the batch optimization - should use executemany pattern internally
        instead of N separate INSERT statements.
        """
        mock_engine, mock_conn = self._create_mock_engine()
        repo = SupabaseRepo(engine=mock_engine)

        # Create DataFrame with 100 rows - should still be 1 execute call
        df = self._create_test_dataframe(num_rows=100)

        result = repo.upsert_forecasts(df)

        # Verify only ONE execute call was made (batch operation)
        assert mock_conn.execute.call_count == 1, (
            f"Expected exactly 1 execute() call for batch operation, "
            f"got {mock_conn.execute.call_count} calls. "
            "This indicates N+1 query pattern - each row is being inserted separately."
        )

        # Verify the return count is correct
        assert result == 100, f"Expected 100 rows upserted, got {result}"

    def test_upsert_forecasts_passes_list_to_execute(self):
        """Verify that execute() is called with a list of dicts for batch operation."""
        mock_engine, mock_conn = self._create_mock_engine()
        repo = SupabaseRepo(engine=mock_engine)

        df = self._create_test_dataframe(num_rows=3)

        repo.upsert_forecasts(df)

        # Get the arguments passed to execute()
        call_args = mock_conn.execute.call_args
        assert call_args is not None, "execute() should have been called"

        # The second argument should be a list of dicts (for executemany)
        args, kwargs = call_args
        assert len(args) >= 2, "execute() should receive SQL and params"

        params = args[1]
        assert isinstance(params, list), (
            f"execute() second argument should be a list for batch operation, "
            f"got {type(params).__name__}"
        )
        assert len(params) == 3, f"Expected 3 rows in params list, got {len(params)}"
        assert all(isinstance(row, dict) for row in params), (
            "Each element in params list should be a dict"
        )

    def test_upsert_forecasts_handles_null_values(self):
        """Verify that NULL values (NaN/None) in optional columns are handled correctly."""
        mock_engine, mock_conn = self._create_mock_engine()
        repo = SupabaseRepo(engine=mock_engine)

        # Create DataFrame with NULL values in optional columns
        df = pd.DataFrame({
            "item_id": ["uuid-1", "uuid-2"],
            "computed_at": [datetime.now(timezone.utc), datetime.now(timezone.utc)],
            "horizon_days": [21, 14],
            "avg_daily_delta": [np.nan, 2.5],  # First row has NaN
            "days_to_stockout": [None, 10.0],  # First row has None
            "suggested_reorder_qty": [np.nan, 30],  # First row has NaN
            "suggested_order_date": [None, "2025-11-05"],  # First row has None
            "confidence": [0.9, np.nan],  # Second row has NaN
            "features": [{}, {"key": "value"}],
        })

        result = repo.upsert_forecasts(df)

        # Should succeed without errors
        assert result == 2, f"Expected 2 rows upserted, got {result}"

        # Verify execute was called
        assert mock_conn.execute.call_count == 1

        # Get the params list and verify NULL handling
        call_args = mock_conn.execute.call_args
        params = call_args[0][1]

        # First row should have None for nullable fields
        assert params[0]["avg_daily_delta"] is None, "NaN should be converted to None"
        assert params[0]["days_to_stockout"] is None, "None should remain None"
        assert params[0]["suggested_reorder_qty"] is None, "NaN should be converted to None"
        assert params[0]["suggested_order_date"] is None, "None should remain None"

        # Second row should have actual values except confidence
        assert params[1]["avg_daily_delta"] == 2.5
        assert params[1]["confidence"] is None, "NaN should be converted to None"

    def test_upsert_forecasts_serializes_features_dict(self):
        """Verify that features dict is correctly serialized to JSON string."""
        mock_engine, mock_conn = self._create_mock_engine()
        repo = SupabaseRepo(engine=mock_engine)

        df = pd.DataFrame({
            "item_id": ["uuid-1", "uuid-2", "uuid-3"],
            "computed_at": [datetime.now(timezone.utc)] * 3,
            "horizon_days": [21, 21, 21],
            "avg_daily_delta": [3.5, 3.5, 3.5],
            "days_to_stockout": [10.0, 10.0, 10.0],
            "suggested_reorder_qty": [45, 45, 45],
            "suggested_order_date": ["2025-11-05"] * 3,
            "confidence": [0.95, 0.95, 0.95],
            "features": [
                {"nested": {"key": "value"}, "array": [1, 2, 3]},  # Complex dict
                '{"already": "json"}',  # Already a JSON string
                None,  # None value
            ],
        })

        result = repo.upsert_forecasts(df)

        assert result == 3

        # Get the params list
        call_args = mock_conn.execute.call_args
        params = call_args[0][1]

        # First row: dict should be serialized to JSON string
        features_1 = params[0]["features"]
        assert isinstance(features_1, str), "Dict features should be serialized to string"
        parsed = json.loads(features_1)
        assert parsed == {"nested": {"key": "value"}, "array": [1, 2, 3]}

        # Second row: already JSON string should be preserved
        features_2 = params[1]["features"]
        assert features_2 == '{"already": "json"}'

        # Third row: None/NaN should become empty JSON object
        features_3 = params[2]["features"]
        assert features_3 == "{}", "None features should become empty JSON object"

    def test_upsert_forecasts_returns_correct_count(self):
        """Verify that upsert_forecasts returns the correct number of rows."""
        mock_engine, mock_conn = self._create_mock_engine()
        repo = SupabaseRepo(engine=mock_engine)

        # Test with different row counts
        for num_rows in [0, 1, 5, 50]:
            mock_conn.reset_mock()
            df = self._create_test_dataframe(num_rows=num_rows)
            result = repo.upsert_forecasts(df)
            assert result == num_rows, f"Expected {num_rows} rows, got {result}"

    def test_upsert_forecasts_empty_dataframe_no_execute(self):
        """Verify that empty DataFrame does not call execute()."""
        mock_engine, mock_conn = self._create_mock_engine()
        repo = SupabaseRepo(engine=mock_engine)

        df = pd.DataFrame(columns=[
            "item_id", "computed_at", "horizon_days", "avg_daily_delta",
            "days_to_stockout", "suggested_reorder_qty", "suggested_order_date",
            "confidence", "features"
        ])

        result = repo.upsert_forecasts(df)

        assert result == 0
        mock_conn.execute.assert_not_called()

    def test_upsert_forecasts_preserves_data_integrity(self):
        """Verify that all data is correctly prepared for the batch insert."""
        mock_engine, mock_conn = self._create_mock_engine()
        repo = SupabaseRepo(engine=mock_engine)

        computed_at = datetime(2025, 1, 15, 10, 30, 0, tzinfo=timezone.utc)
        df = pd.DataFrame({
            "item_id": ["550e8400-e29b-41d4-a716-446655440000"],
            "computed_at": [computed_at],
            "horizon_days": [21],
            "avg_daily_delta": [3.5],
            "days_to_stockout": [10.5],
            "suggested_reorder_qty": [45],
            "suggested_order_date": ["2025-02-01"],
            "confidence": [0.95],
            "features": [{"model": "prophet", "version": "1.0"}],
        })

        repo.upsert_forecasts(df)

        call_args = mock_conn.execute.call_args
        params = call_args[0][1]
        row = params[0]

        # Verify all fields are correctly transformed
        assert row["item_id"] == "550e8400-e29b-41d4-a716-446655440000"
        assert row["horizon_days"] == 21
        assert row["avg_daily_delta"] == 3.5
        assert row["days_to_stockout"] == 10.5
        assert row["suggested_reorder_qty"] == 45
        assert row["suggested_order_date"] == "2025-02-01"
        assert row["confidence"] == 0.95
        assert json.loads(row["features"]) == {"model": "prophet", "version": "1.0"}
        assert "id" in row, "Each row should have a generated UUID"
