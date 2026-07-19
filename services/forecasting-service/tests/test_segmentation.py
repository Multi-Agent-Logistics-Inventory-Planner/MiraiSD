"""Tests for demand-shape segmentation (continuous / drop / dead / new).

The classifier routes SKUs to the policy that matches their demand shape:
- continuous: normal daily-mu path
- drop: burst sellers (booster boxes) whose zero-filled OOS days crush mu
- dead: no meaningful sales; suppressed from at-risk outputs
- new: too little history to judge; keeps the normal cold-start path
"""

from datetime import date

import numpy as np
import pandas as pd
import pytest

from src import config, segmentation as seg


TODAY = date(2026, 7, 1)


def _daily(item_id: str, spec: dict[str, float], start: str = "2026-05-02", days: int = 60):
    """Build a zero-filled daily grid with consumption spikes at given day offsets."""
    dates = pd.date_range(start, periods=days, freq="D")
    consumption = np.zeros(days)
    for offset, units in spec.items():
        consumption[int(offset)] = units
    return pd.DataFrame({"item_id": item_id, "date": dates, "consumption": consumption})


class TestComputeSegmentSignals:
    def test_basic_counts(self):
        df = _daily("a", {0: 5, 10: 3, 20: 2})
        signals = seg.compute_segment_signals(df, today=TODAY)
        row = signals.loc[signals["item_id"] == "a"].iloc[0]
        assert row["sale_days"] == 3
        assert row["total_units"] == 10
        assert row["window_days"] == 60
        assert row["top3_share"] == 1.0

    def test_top3_share_partial(self):
        # 10 sale days of 1 unit + one day of 90: top3 = 92/100
        spec = {i: 1.0 for i in range(10)}
        spec[20] = 90.0
        df = _daily("a", spec)
        signals = seg.compute_segment_signals(df, today=TODAY)
        row = signals.iloc[0]
        assert row["top3_share"] == pytest.approx(0.92)

    def test_days_since_last_sale(self):
        df = _daily("a", {0: 5})  # sale on 2026-05-02
        signals = seg.compute_segment_signals(df, today=TODAY)
        assert signals.iloc[0]["days_since_last_sale"] == 60

    def test_no_sales_days_since_is_nan(self):
        df = _daily("a", {})
        signals = seg.compute_segment_signals(df, today=TODAY)
        assert np.isnan(signals.iloc[0]["days_since_last_sale"])

    def test_drop_cluster_detection_with_gap_tolerance(self):
        # Sales on days 0,1,3 (gap of 1 zero-day) form ONE cluster;
        # day 30 forms a second cluster.
        df = _daily("a", {0: 50, 1: 40, 3: 30, 30: 60})
        signals = seg.compute_segment_signals(df, today=TODAY)
        row = signals.iloc[0]
        assert row["last_drop_size"] == 60
        assert row["last_drop_days"] == 1
        assert row["drop_sizes"] == [120.0, 60.0]

    def test_drop_cluster_span_days(self):
        df = _daily("a", {10: 50, 12: 100})
        row = seg.compute_segment_signals(df, today=TODAY).iloc[0]
        assert row["last_drop_size"] == 150
        assert row["last_drop_days"] == 3  # day 10..12 inclusive

    def test_stockout_frac_and_rate_while_available(self):
        df = _daily("a", {0: 30}, days=10)
        stockout = pd.DataFrame(
            {
                "item_id": "a",
                "date": pd.date_range("2026-05-02", periods=10, freq="D"),
                "is_stockout": [False] * 5 + [True] * 5,
            }
        )
        row = seg.compute_segment_signals(df, stockout_df=stockout, today=TODAY).iloc[0]
        assert row["stockout_frac"] == pytest.approx(0.5)
        assert row["rate_while_available"] == pytest.approx(30 / 5)

    def test_no_stockout_df_defaults_to_zero_frac(self):
        df = _daily("a", {0: 10}, days=10)
        row = seg.compute_segment_signals(df, today=TODAY).iloc[0]
        assert row["stockout_frac"] == 0.0
        assert row["rate_while_available"] == pytest.approx(1.0)

    def test_first_activity_overrides_window_days(self):
        df = _daily("a", {})
        row = seg.compute_segment_signals(
            df, today=TODAY, first_activity={"a": date(2026, 6, 25)}
        ).iloc[0]
        assert row["window_days"] == 7

    def test_empty_input(self):
        signals = seg.compute_segment_signals(
            pd.DataFrame(columns=["item_id", "date", "consumption"]), today=TODAY
        )
        assert signals.empty


def _signal_row(**overrides):
    base = {
        "item_id": "a",
        "window_days": 60,
        "sale_days": 20,
        "total_units": 40.0,
        "top3_share": 0.3,
        "stockout_frac": 0.0,
        "days_since_last_sale": 1.0,
        "rate_while_available": 0.7,
        "last_drop_size": np.nan,
        "last_drop_days": np.nan,
        "drop_sizes": [],
    }
    base.update(overrides)
    return pd.DataFrame([base])


class TestClassifySegments:
    def test_continuous_default(self):
        assert seg.classify_segments(_signal_row())["a"] == seg.SEGMENT_CONTINUOUS

    def test_new_item_short_window_no_sales(self):
        signals = _signal_row(window_days=5, sale_days=0, total_units=0.0)
        assert seg.classify_segments(signals)["a"] == seg.SEGMENT_NEW

    def test_dead_no_sales_long_window(self):
        signals = _signal_row(sale_days=0, total_units=0.0, days_since_last_sale=np.nan)
        assert seg.classify_segments(signals)["a"] == seg.SEGMENT_DEAD

    def test_dead_trickle_gone_quiet(self):
        signals = _signal_row(sale_days=2, total_units=3.0, days_since_last_sale=35.0)
        assert seg.classify_segments(signals)["a"] == seg.SEGMENT_DEAD

    def test_trickle_recent_sale_not_dead(self):
        signals = _signal_row(sale_days=2, total_units=3.0, days_since_last_sale=5.0)
        assert seg.classify_segments(signals)["a"] == seg.SEGMENT_CONTINUOUS

    def test_old_big_burst_is_drop_not_dead(self):
        # A booster box that sold 150 units in 2 days and has been OOS for
        # 48 days is a restock candidate, not a dead item.
        signals = _signal_row(
            sale_days=2,
            total_units=150.0,
            top3_share=1.0,
            stockout_frac=0.8,
            days_since_last_sale=48.0,
        )
        assert seg.classify_segments(signals)["a"] == seg.SEGMENT_DROP

    def test_drop_by_stockout_frac(self):
        signals = _signal_row(
            total_units=180.0, top3_share=0.95, stockout_frac=0.4, sale_days=8
        )
        assert seg.classify_segments(signals)["a"] == seg.SEGMENT_DROP

    def test_drop_by_few_sale_days(self):
        signals = _signal_row(total_units=120.0, top3_share=0.9, sale_days=3)
        assert seg.classify_segments(signals)["a"] == seg.SEGMENT_DROP

    def test_concentrated_but_no_stockout_many_sale_days_is_continuous(self):
        signals = _signal_row(
            total_units=100.0, top3_share=0.9, stockout_frac=0.0, sale_days=20
        )
        assert seg.classify_segments(signals)["a"] == seg.SEGMENT_CONTINUOUS

    def test_low_volume_never_drop(self):
        signals = _signal_row(total_units=6.0, top3_share=1.0, sale_days=2)
        assert seg.classify_segments(signals)["a"] != seg.SEGMENT_DROP

    def test_hysteresis_prior_drop_keeps_at_lower_share(self):
        signals = _signal_row(
            total_units=100.0, top3_share=0.7, stockout_frac=0.4, sale_days=5
        )
        assert (
            seg.classify_segments(signals, prior_segments={"a": seg.SEGMENT_DROP})["a"]
            == seg.SEGMENT_DROP
        )
        # Without the prior, 0.7 share does not enter drop
        assert seg.classify_segments(signals)["a"] == seg.SEGMENT_CONTINUOUS


class TestPolicyOverrides:
    def test_drop_mu_override(self):
        mu = pd.Series([0.3, 2.0], index=[0, 1])
        ids = pd.Series(["drop-item", "cont-item"], index=[0, 1])
        new_mu, applied = seg.apply_drop_mu_override(
            mu, ids, {"drop-item": seg.SEGMENT_DROP}, {"drop-item": 6.0}
        )
        assert new_mu.iloc[0] == pytest.approx(6.0)
        assert new_mu.iloc[1] == pytest.approx(2.0)
        assert applied.iloc[0] and not applied.iloc[1]

    def test_drop_mu_override_missing_rate_is_noop(self):
        mu = pd.Series([0.3])
        ids = pd.Series(["drop-item"])
        new_mu, applied = seg.apply_drop_mu_override(
            mu, ids, {"drop-item": seg.SEGMENT_DROP}, {}
        )
        assert new_mu.iloc[0] == pytest.approx(0.3)
        assert not applied.iloc[0]

    def test_dead_policy_mask(self):
        days_out = pd.Series([3.0, 5.0])
        qty = pd.Series([10, 20])
        ids = pd.Series(["dead-item", "cont-item"])
        masked_days, masked_qty = seg.apply_dead_policy_mask(
            days_out, qty, ids, {"dead-item": seg.SEGMENT_DEAD}
        )
        assert masked_days.iloc[0] == np.inf  # -> None downstream
        assert masked_qty.iloc[0] == 0
        assert masked_days.iloc[1] == 5.0 and masked_qty.iloc[1] == 20

    def test_drop_qty_override_nets_out_stock_and_on_order(self):
        qty = pd.Series([0, 7])
        current = pd.Series([20, 50])
        on_order = pd.Series([30, 0])
        ids = pd.Series(["drop-item", "cont-item"])
        out = seg.apply_drop_qty_override(
            qty, current, ids, {"drop-item": seg.SEGMENT_DROP}, {"drop-item": 150.0},
            on_order=on_order,
        )
        assert out.iloc[0] == 100  # ceil(150) - 20 - 30
        assert out.iloc[1] == 7  # untouched

    def test_drop_qty_override_floors_at_zero(self):
        qty = pd.Series([0])
        current = pd.Series([500])
        ids = pd.Series(["drop-item"])
        out = seg.apply_drop_qty_override(
            qty, current, ids, {"drop-item": seg.SEGMENT_DROP}, {"drop-item": 100.0}
        )
        assert out.iloc[0] == 0

    def test_drop_order_qty_mean_of_last_n(self):
        signals = _signal_row(drop_sizes=[80.0, 100.0, 120.0])
        qty_map = seg.build_drop_order_qty(signals, avg_last_n=2)
        assert qty_map["a"] == pytest.approx(110.0)

    def test_drop_order_qty_empty_drops_absent(self):
        qty_map = seg.build_drop_order_qty(_signal_row(drop_sizes=[]))
        assert "a" not in qty_map


class TestClassifyQuietItem:
    """new/dead split for items with zero movements in the whole window --
    they never reach classify_segments, so this helper covers them."""

    def test_recently_created_is_new(self):
        created = pd.Timestamp(TODAY) - pd.Timedelta(days=3)
        assert seg.classify_quiet_item(created, TODAY) == seg.SEGMENT_NEW

    def test_boundary_at_new_max_history_days(self):
        just_new = pd.Timestamp(TODAY) - pd.Timedelta(
            days=config.SEGMENT_NEW_MAX_HISTORY_DAYS - 1
        )
        just_dead = pd.Timestamp(TODAY) - pd.Timedelta(
            days=config.SEGMENT_NEW_MAX_HISTORY_DAYS
        )
        assert seg.classify_quiet_item(just_new, TODAY) == seg.SEGMENT_NEW
        assert seg.classify_quiet_item(just_dead, TODAY) == seg.SEGMENT_DEAD

    def test_old_item_is_dead(self):
        created = pd.Timestamp(TODAY) - pd.Timedelta(days=100)
        assert seg.classify_quiet_item(created, TODAY) == seg.SEGMENT_DEAD

    def test_missing_created_at_is_dead(self):
        assert seg.classify_quiet_item(None, TODAY) == seg.SEGMENT_DEAD
        assert seg.classify_quiet_item(pd.NaT, TODAY) == seg.SEGMENT_DEAD

    def test_timezone_aware_created_at(self):
        created = pd.Timestamp(TODAY, tz="UTC") - pd.Timedelta(days=3)
        assert seg.classify_quiet_item(created, TODAY) == seg.SEGMENT_NEW
