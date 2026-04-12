"""Tests for SupabaseRepo.get_items() method."""
import sys
from unittest.mock import MagicMock, patch

# Mock kafka before imports
sys.modules["kafka"] = MagicMock()
sys.modules["kafka.errors"] = MagicMock()

import pandas as pd
import pytest


class TestGetItems:
    """Unit tests for get_items() SQL query and behavior."""

    def test_get_items_returns_correct_columns(self):
        """Verify get_items returns expected columns without 'category'."""
        # Setup mock engine
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.connect.return_value.__exit__ = MagicMock(return_value=False)

        from src.adapters.supabase_repo import SupabaseRepo
        repo = SupabaseRepo(engine=mock_engine)

        # Mock read_sql to return test data
        test_df = pd.DataFrame({
            "item_id": ["uuid-1", "uuid-2"],
            "name": ["Product A", "Product B"],
            "lead_time_days": [7, 14],
            "safety_stock_days": [3, 5],
            "category_name": ["toys", None],
            "preferred_supplier_id": [None, "sup-1"],
        })

        with patch("pandas.read_sql", return_value=test_df):
            result = repo.get_items()

        expected_columns = ["item_id", "name", "lead_time_days", "safety_stock_days",
                            "category_name", "preferred_supplier_id"]
        assert list(result.columns) == expected_columns
        # Null category_name should be filled with "Unknown"
        assert result.iloc[1]["category_name"] == "Unknown"

    def test_get_items_sql_does_not_reference_category(self):
        """Verify SQL query does not reference non-existent category column."""
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.connect.return_value.__exit__ = MagicMock(return_value=False)

        from src.adapters.supabase_repo import SupabaseRepo
        repo = SupabaseRepo(engine=mock_engine)

        captured_sql = []
        def capture_sql(sql, conn, params=None):
            captured_sql.append(str(sql))
            return pd.DataFrame(columns=["item_id", "name", "lead_time_days", "safety_stock_days"])

        with patch("pandas.read_sql", side_effect=capture_sql):
            repo.get_items()

        # Verify SQL does not contain 'category' as a column
        sql = captured_sql[0].lower()
        # Should not have standalone 'category' column (but category_id would be ok)
        assert "select" in sql
        assert "category," not in sql  # No bare 'category' column

    def test_get_items_empty_returns_correct_schema(self):
        """Verify empty result has correct column schema."""
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.connect.return_value.__exit__ = MagicMock(return_value=False)

        from src.adapters.supabase_repo import SupabaseRepo
        repo = SupabaseRepo(engine=mock_engine)

        with patch("pandas.read_sql", return_value=pd.DataFrame()):
            result = repo.get_items()

        expected_columns = ["item_id", "name", "lead_time_days", "safety_stock_days",
                            "category_name", "preferred_supplier_id"]
        assert list(result.columns) == expected_columns
