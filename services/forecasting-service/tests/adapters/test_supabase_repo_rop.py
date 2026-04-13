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

    def _make_repo(self, rowcount=1):
        mock_engine = MagicMock()
        mock_conn = MagicMock()
        mock_result = MagicMock()
        mock_result.rowcount = rowcount
        mock_conn.execute.return_value = mock_result
        mock_engine.begin.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.begin.return_value.__exit__ = MagicMock(return_value=False)
        return SupabaseRepo(engine=mock_engine), mock_conn, mock_result

    def test_normal_case_sends_correct_values(self):
        """Each valid row gets its own execute call with correct ROP."""
        repo, mock_conn, _ = self._make_repo(rowcount=1)

        item_a = str(uuid.uuid4())
        item_b = str(uuid.uuid4())
        df = _make_forecasts_df([
            (item_a, {"reorder_point": 15.7, "mu_hat": 1.2}),
            (item_b, {"reorder_point": 8.3, "mu_hat": 0.5}),
        ])

        updated = repo.update_product_reorder_points(df)

        assert updated == 2
        assert mock_conn.execute.call_count == 2

        calls = mock_conn.execute.call_args_list
        row_a = calls[0][0][1]
        row_b = calls[1][0][1]
        assert row_a["item_id"] == item_a
        assert row_a["reorder_point"] == 16
        assert row_b["item_id"] == item_b
        assert row_b["reorder_point"] == 8

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
        """Only valid rows get execute calls; invalid rows silently skipped."""
        repo, mock_conn, _ = self._make_repo(rowcount=1)

        valid_id = str(uuid.uuid4())
        df = _make_forecasts_df([
            (valid_id, {"reorder_point": 10.0}),
            (str(uuid.uuid4()), {"mu_hat": 1.0}),
            (str(uuid.uuid4()), "bad features"),
        ])

        updated = repo.update_product_reorder_points(df)

        assert updated == 1
        assert mock_conn.execute.call_count == 1
        row = mock_conn.execute.call_args[0][1]
        assert row["item_id"] == valid_id

    def test_reorder_point_rounds_correctly(self):
        """Reorder point should be rounded to nearest integer."""
        repo, mock_conn, _ = self._make_repo(rowcount=1)

        df = _make_forecasts_df([
            (str(uuid.uuid4()), {"reorder_point": 5.4}),
            (str(uuid.uuid4()), {"reorder_point": 5.5}),
            (str(uuid.uuid4()), {"reorder_point": 5.6}),
        ])

        repo.update_product_reorder_points(df)

        calls = mock_conn.execute.call_args_list
        rops = [c[0][1]["reorder_point"] for c in calls]
        assert rops == [5, 6, 6]

    def test_accumulates_rowcount_across_calls(self):
        """Should sum rowcount from each individual execute, not use last."""
        repo, mock_conn, _ = self._make_repo(rowcount=1)

        results = [MagicMock(rowcount=1), MagicMock(rowcount=1), MagicMock(rowcount=0)]
        mock_conn.execute.side_effect = results

        df = _make_forecasts_df([
            (str(uuid.uuid4()), {"reorder_point": 10}),
            (str(uuid.uuid4()), {"reorder_point": 20}),
            (str(uuid.uuid4()), {"reorder_point": 30}),
        ])

        updated = repo.update_product_reorder_points(df)

        assert updated == 2
        assert mock_conn.execute.call_count == 3
