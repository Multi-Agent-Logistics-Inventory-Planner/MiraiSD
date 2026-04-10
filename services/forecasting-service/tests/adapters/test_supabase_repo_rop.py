"""Tests for update_product_reorder_points repository method."""

import sys
import uuid
from datetime import datetime, timezone
from unittest.mock import MagicMock, call

import pandas as pd
import pytest

mock_kafka = MagicMock()
mock_kafka.errors = MagicMock()
sys.modules["kafka"] = mock_kafka
sys.modules["kafka.errors"] = mock_kafka.errors

from src.adapters.supabase_repo import SupabaseRepo


def _make_forecasts_df(rows):
    """Build a forecasts DataFrame from a list of (item_id, features) tuples."""
    return pd.DataFrame({
        "item_id": [r[0] for r in rows],
        "computed_at": [datetime.now(timezone.utc)] * len(rows),
        "features": [r[1] for r in rows],
    })


class TestUpdateProductReorderPoints:
    """Tests for propagating reorder points from forecasts to products."""

    def _make_repo(self):
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_result = MagicMock()
        mock_result.rowcount = 0
        mock_conn.execute.return_value = mock_result
        mock_engine.begin.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.begin.return_value.__exit__ = MagicMock(return_value=False)
        return SupabaseRepo(engine=mock_engine), mock_conn, mock_result

    def test_normal_case_sends_correct_values(self):
        """Rows with valid reorder_point in features are sent to the DB."""
        repo, mock_conn, mock_result = self._make_repo()
        mock_result.rowcount = 2

        item_a = str(uuid.uuid4())
        item_b = str(uuid.uuid4())
        df = _make_forecasts_df([
            (item_a, {"reorder_point": 15.7, "mu_hat": 1.2}),
            (item_b, {"reorder_point": 8.3, "mu_hat": 0.5}),
        ])

        updated = repo.update_product_reorder_points(df)

        assert updated == 2
        assert mock_conn.execute.call_count == 1

        # Verify the rows passed to execute
        args = mock_conn.execute.call_args
        rows = args[0][1]
        rop_by_id = {r["item_id"]: r["reorder_point"] for r in rows}
        assert rop_by_id[item_a] == 16  # round(15.7)
        assert rop_by_id[item_b] == 8   # round(8.3)

    def test_empty_dataframe_returns_zero(self):
        """Empty DataFrame should short-circuit and return 0."""
        repo, mock_conn, _ = self._make_repo()

        result = repo.update_product_reorder_points(pd.DataFrame())

        assert result == 0
        assert mock_conn.execute.call_count == 0

    def test_missing_reorder_point_in_features_skipped(self):
        """Rows where features dict lacks reorder_point are skipped."""
        repo, mock_conn, _ = self._make_repo()

        df = _make_forecasts_df([
            (str(uuid.uuid4()), {"mu_hat": 1.2}),
            (str(uuid.uuid4()), {"sigma_d_hat": 0.5}),
        ])

        result = repo.update_product_reorder_points(df)

        assert result == 0
        assert mock_conn.execute.call_count == 0

    def test_non_dict_features_skipped(self):
        """Rows where features is not a dict are skipped."""
        repo, mock_conn, _ = self._make_repo()

        df = _make_forecasts_df([
            (str(uuid.uuid4()), "not a dict"),
            (str(uuid.uuid4()), None),
            (str(uuid.uuid4()), 42),
        ])

        result = repo.update_product_reorder_points(df)

        assert result == 0
        assert mock_conn.execute.call_count == 0

    def test_reorder_point_none_skipped(self):
        """Rows where reorder_point is explicitly None are skipped."""
        repo, mock_conn, _ = self._make_repo()

        df = _make_forecasts_df([
            (str(uuid.uuid4()), {"reorder_point": None}),
        ])

        result = repo.update_product_reorder_points(df)

        assert result == 0
        assert mock_conn.execute.call_count == 0

    def test_mixed_valid_and_invalid_rows(self):
        """Only valid rows are sent; invalid rows are silently skipped."""
        repo, mock_conn, mock_result = self._make_repo()
        mock_result.rowcount = 1

        valid_id = str(uuid.uuid4())
        df = _make_forecasts_df([
            (valid_id, {"reorder_point": 10.0}),
            (str(uuid.uuid4()), {"mu_hat": 1.0}),       # no rop
            (str(uuid.uuid4()), "bad features"),          # not a dict
        ])

        updated = repo.update_product_reorder_points(df)

        assert updated == 1
        rows = mock_conn.execute.call_args[0][1]
        assert len(rows) == 1
        assert rows[0]["item_id"] == valid_id

    def test_reorder_point_rounds_correctly(self):
        """Reorder point should be rounded to nearest integer."""
        repo, mock_conn, mock_result = self._make_repo()
        mock_result.rowcount = 3

        df = _make_forecasts_df([
            (str(uuid.uuid4()), {"reorder_point": 5.4}),
            (str(uuid.uuid4()), {"reorder_point": 5.5}),
            (str(uuid.uuid4()), {"reorder_point": 5.6}),
        ])

        repo.update_product_reorder_points(df)

        rows = mock_conn.execute.call_args[0][1]
        rops = [r["reorder_point"] for r in rows]
        assert rops == [5, 6, 6]

    def test_uses_result_rowcount_not_len(self):
        """Should return DB rowcount, not the number of rows sent."""
        repo, mock_conn, mock_result = self._make_repo()
        # Simulate: 3 rows sent but only 2 matched in DB (one item deleted)
        mock_result.rowcount = 2

        df = _make_forecasts_df([
            (str(uuid.uuid4()), {"reorder_point": 10}),
            (str(uuid.uuid4()), {"reorder_point": 20}),
            (str(uuid.uuid4()), {"reorder_point": 30}),
        ])

        updated = repo.update_product_reorder_points(df)

        assert updated == 2  # rowcount, not 3
