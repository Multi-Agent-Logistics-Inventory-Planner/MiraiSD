"""End-to-end tests for demand-shape segmentation in the pipeline.

Verifies the flag matrix:
- both flags off  -> outputs unchanged, no segment JSONB keys
- SEGMENTATION_ENABLED only -> labels persisted, policy outputs unchanged
- + SEGMENT_POLICY_ENABLED  -> drop mu override, dead masking
"""

import sys
from unittest.mock import MagicMock, patch
from uuid import uuid4

import pandas as pd
import pytest

sys.modules.setdefault("kafka", MagicMock())
sys.modules.setdefault("kafka.errors", MagicMock())

from src.application.pipeline import ForecastingPipeline


DROP_ID = str(uuid4())
CONT_ID = str(uuid4())
DEAD_ID = str(uuid4())


def _movements():
    """60 days: a drop item (150 units in 2 days, then OOS), a continuous
    seller (2/day), and a dead item (2 units, 40+ days ago)."""
    now = pd.Timestamp.now(tz="UTC").floor("D")
    rows = []

    # Continuous: 2 units every day for 60 days
    for d in range(60):
        rows.append(
            {
                "item_id": CONT_ID,
                "quantity_change": -2,
                "reason": "sale",
                "at": now - pd.Timedelta(days=d),
                "previous_quantity": 100 - d,
                "current_quantity": 100 - d - 2,
            }
        )

    # Drop: restock of 150 on day 50, sells 75+75 on days 49-48, zero since
    rows.append(
        {
            "item_id": DROP_ID,
            "quantity_change": 150,
            "reason": "SHIPMENT_RECEIPT",
            "at": now - pd.Timedelta(days=50),
            "previous_quantity": 0,
            "current_quantity": 150,
        }
    )
    rows.append(
        {
            "item_id": DROP_ID,
            "quantity_change": -75,
            "reason": "sale",
            "at": now - pd.Timedelta(days=49),
            "previous_quantity": 150,
            "current_quantity": 75,
        }
    )
    rows.append(
        {
            "item_id": DROP_ID,
            "quantity_change": -75,
            "reason": "sale",
            "at": now - pd.Timedelta(days=48),
            "previous_quantity": 75,
            "current_quantity": 0,
        }
    )

    # Dead: one sale of 2 units, 40 days ago
    rows.append(
        {
            "item_id": DEAD_ID,
            "quantity_change": -2,
            "reason": "sale",
            "at": now - pd.Timedelta(days=40),
            "previous_quantity": 5,
            "current_quantity": 3,
        }
    )

    df = pd.DataFrame(rows)
    df["event_id"] = [str(uuid4()) for _ in range(len(df))]
    return df


def _build_repo():
    repo = MagicMock()
    ids = [DROP_ID, CONT_ID, DEAD_ID]
    repo.get_items.return_value = pd.DataFrame(
        {
            "item_id": ids,
            "name": ["Drop Box", "Steady Plush", "Dead Item"],
            "lead_time_days": [10] * 3,
            "safety_stock_days": [3] * 3,
        }
    )
    repo.get_current_inventory.return_value = pd.DataFrame(
        {
            "item_id": ids,
            "as_of_ts": [pd.Timestamp.now(tz="UTC")] * 3,
            "current_qty": [0, 60, 3],
        }
    )
    repo.get_stock_movements.return_value = _movements()
    repo.get_historical_forecasts.return_value = pd.DataFrame(
        columns=["item_id", "computed_at", "avg_daily_delta", "days_to_stockout"]
    )
    repo.get_latest_demand_segments.return_value = {}
    repo.get_latest_demand_regimes.return_value = {}
    repo.get_on_order_quantities.return_value = {}
    repo.upsert_forecasts.side_effect = lambda df: len(df)
    repo.update_product_reorder_points.return_value = 0
    return repo


def _run(seg_enabled: bool, policy_enabled: bool):
    repo = _build_repo()
    pipeline = ForecastingPipeline(repo=repo)
    with patch("src.application.pipeline.config.SEGMENTATION_ENABLED", seg_enabled), patch(
        "src.application.pipeline.config.SEGMENT_POLICY_ENABLED", policy_enabled
    ):
        pipeline.run_for_items({DROP_ID, CONT_ID, DEAD_ID})
    return repo.upsert_forecasts.call_args[0][0]


def _row(df, item_id):
    return df[df["item_id"] == item_id].iloc[0]


class TestFlagsOff:
    def test_no_segment_keys_when_disabled(self):
        df = _run(seg_enabled=False, policy_enabled=False)
        for iid in (DROP_ID, CONT_ID, DEAD_ID):
            assert "demand_segment" not in _row(df, iid)["features"]

    def test_drop_item_shows_crushed_mu_when_disabled(self):
        # Documents the bug being fixed: OOS zero-fill crushes drop mu.
        df = _run(seg_enabled=False, policy_enabled=False)
        assert -_row(df, DROP_ID)["avg_daily_delta"] < 4.0


QUIET_ID = str(uuid4())


def _repo_with_quiet_item(created_days_ago: int | None):
    """_build_repo plus one item with zero movements in the window.

    created_days_ago=None omits the created_at column entirely (older mocks /
    callers that predate the column).
    """
    repo = _build_repo()
    now = pd.Timestamp.now(tz="UTC")
    items = repo.get_items.return_value.copy()
    items = pd.concat(
        [
            items,
            pd.DataFrame(
                [
                    {
                        "item_id": QUIET_ID,
                        "name": "Quiet Item",
                        "lead_time_days": 10,
                        "safety_stock_days": 3,
                    }
                ]
            ),
        ],
        ignore_index=True,
    )
    if created_days_ago is not None:
        items["created_at"] = [now - pd.Timedelta(days=200)] * 3 + [
            now - pd.Timedelta(days=created_days_ago)
        ]
    repo.get_items.return_value = items

    inv = repo.get_current_inventory.return_value.copy()
    inv = pd.concat(
        [
            inv,
            pd.DataFrame(
                [{"item_id": QUIET_ID, "as_of_ts": now, "current_qty": 10}]
            ),
        ],
        ignore_index=True,
    )
    repo.get_current_inventory.return_value = inv
    return repo


class TestQuietItemClassification:
    """Items with no movements at all must still get the new/dead split --
    a product created days ago awaiting its first order is 'new', not 'dead'
    (dead items are hidden from the action center entirely)."""

    def _run_quiet(self, created_days_ago: int | None):
        repo = _repo_with_quiet_item(created_days_ago)
        pipeline = ForecastingPipeline(repo=repo)
        with patch("src.application.pipeline.config.SEGMENTATION_ENABLED", True):
            pipeline.run_for_items({DROP_ID, CONT_ID, DEAD_ID, QUIET_ID})
        return repo.upsert_forecasts.call_args[0][0]

    def test_recently_created_quiet_item_is_new(self):
        df = self._run_quiet(created_days_ago=3)
        assert _row(df, QUIET_ID)["features"]["demand_segment"] == "new"

    def test_old_quiet_item_is_dead(self):
        df = self._run_quiet(created_days_ago=100)
        assert _row(df, QUIET_ID)["features"]["demand_segment"] == "dead"

    def test_quiet_item_without_created_at_defaults_dead(self):
        df = self._run_quiet(created_days_ago=None)
        assert _row(df, QUIET_ID)["features"]["demand_segment"] == "dead"


class TestObserveOnly:
    def test_labels_persisted(self):
        df = _run(seg_enabled=True, policy_enabled=False)
        assert _row(df, DROP_ID)["features"]["demand_segment"] == "drop"
        assert _row(df, CONT_ID)["features"]["demand_segment"] == "continuous"
        assert _row(df, DEAD_ID)["features"]["demand_segment"] == "dead"

    def test_signals_persisted(self):
        df = _run(seg_enabled=True, policy_enabled=False)
        sig = _row(df, DROP_ID)["features"]["segment_signals"]
        assert sig["total_units"] == 150.0
        assert sig["top3_share"] == 1.0
        assert sig["last_drop_size"] == 150.0

    def test_no_policy_marker_in_observe_mode(self):
        # The Java read path keys policy behavior (dead exclusion, drop
        # velocity swap) off this marker; observe-only must not set it.
        df = _run(seg_enabled=True, policy_enabled=False)
        for iid in (DROP_ID, CONT_ID, DEAD_ID):
            assert "segment_policy_applied" not in _row(df, iid)["features"]

    def test_policy_outputs_unchanged_in_observe_mode(self):
        base = _run(seg_enabled=False, policy_enabled=False)
        observed = _run(seg_enabled=True, policy_enabled=False)
        for iid in (DROP_ID, CONT_ID, DEAD_ID):
            assert _row(observed, iid)["avg_daily_delta"] == pytest.approx(
                _row(base, iid)["avg_daily_delta"]
            )
            assert _row(observed, iid)["suggested_reorder_qty"] == _row(base, iid)[
                "suggested_reorder_qty"
            ]


class TestPolicyEnabled:
    def test_drop_item_mu_overridden_to_in_stock_rate(self):
        df = _run(seg_enabled=True, policy_enabled=True)
        row = _row(df, DROP_ID)
        # 150 units over ~11 in-stock days (rest of window is stockout)
        assert -row["avg_daily_delta"] > 4.0
        assert row["features"]["mu_pre_segment"] is not None

    def test_oos_drop_item_is_urgent(self):
        df = _run(seg_enabled=True, policy_enabled=True)
        row = _row(df, DROP_ID)
        assert row["days_to_stockout"] == pytest.approx(0.0)
        assert row["suggested_reorder_qty"] >= 100  # ~last drop size

    def test_dead_item_suppressed(self):
        df = _run(seg_enabled=True, policy_enabled=True)
        row = _row(df, DEAD_ID)
        # None in a float column reads back as NaN; the DB writer persists NULL
        assert pd.isna(row["days_to_stockout"])
        assert row["suggested_reorder_qty"] == 0

    def test_policy_marker_persisted(self):
        df = _run(seg_enabled=True, policy_enabled=True)
        for iid in (DROP_ID, CONT_ID, DEAD_ID):
            assert _row(df, iid)["features"]["segment_policy_applied"] is True

    def test_continuous_item_unchanged(self):
        base = _run(seg_enabled=False, policy_enabled=False)
        routed = _run(seg_enabled=True, policy_enabled=True)
        assert _row(routed, CONT_ID)["avg_daily_delta"] == pytest.approx(
            _row(base, CONT_ID)["avg_daily_delta"]
        )


class TestOnOrderNetting:
    def _run_with_on_order(self, v2_enabled: bool, on_order: dict):
        repo = _build_repo()
        repo.get_on_order_quantities.return_value = on_order
        pipeline = ForecastingPipeline(repo=repo)
        with patch(
            "src.application.pipeline.config.SUGGEST_ORDER_V2_ENABLED", v2_enabled
        ), patch("src.application.pipeline.config.SEGMENTATION_ENABLED", True), patch(
            "src.application.pipeline.config.SEGMENT_POLICY_ENABLED", True
        ):
            pipeline.run_for_items({DROP_ID, CONT_ID, DEAD_ID})
        return repo.upsert_forecasts.call_args[0][0]

    def test_v2_nets_out_on_order_for_continuous(self):
        without = self._run_with_on_order(True, {})
        with_inbound = self._run_with_on_order(True, {CONT_ID: 5.0})
        assert (
            _row(with_inbound, CONT_ID)["suggested_reorder_qty"]
            == _row(without, CONT_ID)["suggested_reorder_qty"] - 5
        )

    def test_v2_includes_lead_time_demand(self):
        v1 = self._run_with_on_order(False, {})
        v2 = self._run_with_on_order(True, {})
        # v2 covers lead-time demand on top of target cover, so it orders more
        assert (
            _row(v2, CONT_ID)["suggested_reorder_qty"]
            > _row(v1, CONT_ID)["suggested_reorder_qty"]
        )

    def test_drop_qty_nets_on_order(self):
        without = self._run_with_on_order(False, {})
        with_inbound = self._run_with_on_order(False, {DROP_ID: 50.0})
        assert (
            _row(with_inbound, DROP_ID)["suggested_reorder_qty"]
            == _row(without, DROP_ID)["suggested_reorder_qty"] - 50
        )

    def test_on_order_persisted_in_features(self):
        df = self._run_with_on_order(True, {CONT_ID: 20.0})
        assert _row(df, CONT_ID)["features"]["on_order_qty"] == 20.0
