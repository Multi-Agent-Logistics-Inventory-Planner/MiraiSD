"""Tests for SupabaseRepo.get_items() method."""
import sys
from unittest.mock import MagicMock, patch

# Mock kafka before imports
sys.modules["kafka"] = MagicMock()
sys.modules["kafka.errors"] = MagicMock()

import pandas as pd
import pytest


EXPECTED_COLUMNS = [
    "item_id", "name", "lead_time_days", "safety_stock_days",
    "preferred_supplier_id", "category_name",
]


class TestGetItems:
    """Unit tests for get_items() SQL query and behavior."""

    def test_get_items_returns_correct_columns(self):
        """Verify get_items returns expected columns including category_name and preferred_supplier_id."""
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.connect.return_value.__exit__ = MagicMock(return_value=False)

        from src.adapters.supabase_repo import SupabaseRepo
        repo = SupabaseRepo(engine=mock_engine)

        test_df = pd.DataFrame({
            "item_id": ["uuid-1", "uuid-2"],
            "name": ["Product A", "Product B"],
            "lead_time_days": [7, 14],
            "safety_stock_days": [3, 5],
            "preferred_supplier_id": ["sup-1", None],
            "category_name": ["Toys", "Food"],
        })

        with patch("pandas.read_sql", return_value=test_df):
            result = repo.get_items()

        assert set(result.columns) == set(EXPECTED_COLUMNS)
        assert "category" not in result.columns

    def test_get_items_sql_joins_categories(self):
        """Verify SQL query joins categories table for category_name."""
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.connect.return_value.__exit__ = MagicMock(return_value=False)

        from src.adapters.supabase_repo import SupabaseRepo
        repo = SupabaseRepo(engine=mock_engine)

        captured_sql = []
        def capture_sql(sql, conn, params=None):
            captured_sql.append(str(sql))
            return pd.DataFrame(columns=EXPECTED_COLUMNS)

        with patch("pandas.read_sql", side_effect=capture_sql):
            repo.get_items()

        sql = captured_sql[0].lower()
        assert "category_name" in sql
        assert "left join" in sql
        assert "preferred_supplier_id" in sql

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

        assert set(result.columns) == set(EXPECTED_COLUMNS)
