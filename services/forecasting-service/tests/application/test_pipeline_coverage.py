"""Tests for pipeline coverage: no eligible item may be silently dropped.

Live-data diagnosis (2026-07-01) found 69 eligible items with forecasts >7d
stale. Root cause: build_daily_usage only emits rows for items present in
movements_df, and _compute_forecasts inner-merges estimates -- so an eligible
item with zero movements in the lookback window got no forecast row at all
and stayed stale forever. These tests pin the fix.
"""

import sys
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch
from uuid import uuid4

import pandas as pd
import pytest

sys.modules.setdefault("kafka", MagicMock())
sys.modules.setdefault("kafka.errors", MagicMock())

from src.application.pipeline import ForecastingPipeline


def _build_mock_repo(
    item_ids: list[str],
    movement_item_ids: list[str],
    categories: list[str] | None = None,
):
    """Mock repo where only movement_item_ids have stock movements."""
    repo = MagicMock()

    repo.get_items.return_value = pd.DataFrame(
        {
            "item_id": item_ids,
            "name": [f"Item {i}" for i in range(len(item_ids))],
            "lead_time_days": [7] * len(item_ids),
            "safety_stock_days": [3] * len(item_ids),
            "category_name": categories or ["Unknown"] * len(item_ids),
        }
    )

    repo.get_current_inventory.return_value = pd.DataFrame(
        {
            "item_id": item_ids,
            "as_of_ts": [pd.Timestamp.now(tz="UTC")] * len(item_ids),
            "current_qty": [20] * len(item_ids),
        }
    )

    now = pd.Timestamp.now(tz="UTC")
    frames = []
    for iid in movement_item_ids:
        frames.append(
            pd.DataFrame(
                {
                    "event_id": [str(uuid4()) for _ in range(7)],
                    "item_id": [iid] * 7,
                    "quantity_change": [-2, -1, -3, -2, -1, -2, -3],
                    "reason": ["sale"] * 7,
                    "at": pd.date_range(end=now, periods=7, freq="D", tz="UTC"),
                }
            )
        )
    if frames:
        repo.get_stock_movements.return_value = pd.concat(frames, ignore_index=True)
    else:
        repo.get_stock_movements.return_value = pd.DataFrame(
            columns=["event_id", "item_id", "quantity_change", "reason", "at"]
        )

    repo.get_historical_forecasts.return_value = pd.DataFrame(
        columns=["item_id", "computed_at", "avg_daily_delta", "days_to_stockout"]
    )
    repo.upsert_forecasts.side_effect = lambda df: len(df)
    repo.update_product_reorder_points.return_value = 0
    return repo


class TestNoMovementCoverage:
    """Items with no movements in the lookback window still get forecasts."""

    def test_item_without_movements_gets_forecast_row(self):
        active_id = str(uuid4())
        quiet_id = str(uuid4())
        repo = _build_mock_repo([active_id, quiet_id], [active_id])

        pipeline = ForecastingPipeline(repo=repo)
        saved = pipeline.run_for_items({active_id, quiet_id})

        assert saved == 2
        upserted = repo.upsert_forecasts.call_args[0][0]
        assert set(upserted["item_id"]) == {active_id, quiet_id}

    def test_quiet_item_gets_category_mean_mu(self):
        """Quiet items are cold-start: the category fallback must fire for them.

        The active peer sells ~2/day in the same category, so the quiet item's
        mu must be pulled to the category mean, not left at MU_FLOOR (which
        would report a 40-unit item as ~400 days of runway).
        """
        active_id = str(uuid4())
        quiet_id = str(uuid4())
        repo = _build_mock_repo(
            [active_id, quiet_id], [active_id], categories=["TCG", "TCG"]
        )

        pipeline = ForecastingPipeline(repo=repo)
        pipeline.run_for_items({active_id, quiet_id})

        upserted = repo.upsert_forecasts.call_args[0][0]
        quiet_row = upserted[upserted["item_id"] == quiet_id].iloc[0]
        # Category mean is ~2/day; anything near MU_FLOOR (0.1) means the
        # fallback was bypassed.
        assert quiet_row["avg_daily_delta"] <= -1.0

    def test_quiet_item_without_category_peers_keeps_floor(self):
        active_id = str(uuid4())
        quiet_id = str(uuid4())
        repo = _build_mock_repo(
            [active_id, quiet_id], [active_id], categories=["TCG", "Plush"]
        )

        pipeline = ForecastingPipeline(repo=repo)
        pipeline.run_for_items({active_id, quiet_id})

        upserted = repo.upsert_forecasts.call_args[0][0]
        quiet_row = upserted[upserted["item_id"] == quiet_id].iloc[0]
        # No demand-bearing peers in "Plush": mu stays at the floor.
        assert quiet_row["avg_daily_delta"] >= -0.2

    def test_all_items_quiet_still_covered(self):
        ids = [str(uuid4()), str(uuid4())]
        repo = _build_mock_repo(ids, [])

        pipeline = ForecastingPipeline(repo=repo)
        saved = pipeline.run_for_items(set(ids))

        assert saved == 2
        upserted = repo.upsert_forecasts.call_args[0][0]
        assert set(upserted["item_id"]) == set(ids)


class TestRunAllChunking:
    """run_all: single full pass on the happy path (so cross-item pooling --
    shrinkage category means, supplier lead-time stats -- sees the whole
    catalog), chunked fallback for error isolation, and a raised error when
    every chunk fails so total failure is never reported as success."""

    def _repo_with_items(self, n: int):
        repo = MagicMock()
        repo.get_items.return_value = pd.DataFrame(
            {
                "item_id": [str(uuid4()) for _ in range(n)],
                "name": [f"Item {i}" for i in range(n)],
                "lead_time_days": [7] * n,
                "safety_stock_days": [3] * n,
            }
        )
        return repo

    def test_run_all_happy_path_is_single_full_pass(self):
        repo = self._repo_with_items(5)
        pipeline = ForecastingPipeline(repo=repo)

        with patch("src.application.pipeline.config.RUN_ALL_CHUNK_SIZE", 2), patch.object(
            pipeline, "run_for_items", return_value=5
        ) as run_mock:
            total = pipeline.run_all()

        assert run_mock.call_count == 1
        assert len(run_mock.call_args[0][0]) == 5  # all items in one batch
        assert total == 5

    def test_run_all_falls_back_to_chunks_on_failure(self):
        repo = self._repo_with_items(5)
        pipeline = ForecastingPipeline(repo=repo)

        calls = []

        def full_pass_fails(batch):
            calls.append(batch)
            if len(calls) == 1:
                raise RuntimeError("db timeout")
            return len(batch)

        with patch("src.application.pipeline.config.RUN_ALL_CHUNK_SIZE", 2), patch.object(
            pipeline, "run_for_items", side_effect=full_pass_fails
        ):
            total = pipeline.run_all()

        # 1 failed full pass + 3 chunks (2 + 2 + 1)
        assert len(calls) == 4
        assert total == 5

    def test_run_all_fallback_survives_chunk_failure(self):
        repo = self._repo_with_items(4)
        pipeline = ForecastingPipeline(repo=repo)

        calls = []

        def flaky(batch):
            calls.append(batch)
            # full pass fails, then the first chunk fails, second succeeds
            if len(calls) <= 2:
                raise RuntimeError("db timeout")
            return len(batch)

        with patch("src.application.pipeline.config.RUN_ALL_CHUNK_SIZE", 2), patch.object(
            pipeline, "run_for_items", side_effect=flaky
        ):
            total = pipeline.run_all()

        assert len(calls) == 3  # full pass + both chunks attempted
        assert total == 2  # only the surviving chunk counted

    def test_run_all_raises_when_all_chunks_fail(self):
        repo = self._repo_with_items(4)
        pipeline = ForecastingPipeline(repo=repo)

        with patch("src.application.pipeline.config.RUN_ALL_CHUNK_SIZE", 2), patch.object(
            pipeline, "run_for_items", side_effect=RuntimeError("db down")
        ):
            with pytest.raises(RuntimeError, match="all .* chunks failed"):
                pipeline.run_all()

    def test_run_all_logs_completion_summary(self, caplog):
        import logging

        repo = self._repo_with_items(3)
        pipeline = ForecastingPipeline(repo=repo)

        with patch("src.application.pipeline.config.RUN_ALL_CHUNK_SIZE", 2), patch.object(
            pipeline, "run_for_items", return_value=3
        ), caplog.at_level(logging.INFO):
            pipeline.run_all()

        assert any("run_all complete" in r.message for r in caplog.records)
