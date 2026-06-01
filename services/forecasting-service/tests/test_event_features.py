"""Tests for Phase 4 event-feature engineering.

build_event_features turns the raw stock_movements stream into per (item, day)
binary flags for "has a SHIPMENT_RECEIPT or DISPLAY_SET happened in the prior
N days." compute_global_event_multipliers learns the demand uplift associated
with each flag from training data.
"""
import pandas as pd

from src.features import build_event_features, compute_global_event_multipliers


def _movements(rows: list[dict]) -> pd.DataFrame:
    df = pd.DataFrame(rows)
    if not df.empty:
        df["at"] = pd.to_datetime(df["at"], utc=True)
    return df


class TestBuildEventFeatures:
    def test_empty_returns_empty(self):
        empty = pd.DataFrame(columns=["item_id", "reason", "at"])
        out = build_event_features(empty)
        assert out.empty

    def test_missing_columns_returns_empty(self):
        bogus = pd.DataFrame({"x": [1]})
        out = build_event_features(bogus)
        assert out.empty

    def test_shipment_flags_following_days_excludes_event_day(self):
        # Jan 1 shipment for item A. Window=3.
        # Jan 1 itself: 0 (event happens that day, not "recent prior")
        # Jan 2,3,4: 1 (within prior 3 days)
        # Jan 5: 0 (out of window)
        mv = _movements([
            {"item_id": "A", "reason": "SHIPMENT_RECEIPT", "at": "2026-01-01T10:00:00Z"},
            # Anchor the window end with a sale on Jan 5 so the date range covers all days.
            {"item_id": "A", "reason": "SALE", "at": "2026-01-05T10:00:00Z"},
        ])
        out = build_event_features(mv, window_days=3)
        out["date"] = pd.to_datetime(out["date"])
        col = "recent_shipment_3d"

        def flag(day: str) -> int:
            row = out[(out["item_id"] == "A") & (out["date"] == day)]
            return int(row[col].iloc[0])

        assert flag("2026-01-01") == 0  # event day itself
        assert flag("2026-01-02") == 1
        assert flag("2026-01-03") == 1
        assert flag("2026-01-04") == 1
        assert flag("2026-01-05") == 0  # past the window

    def test_display_set_flag_independent_of_shipment(self):
        mv = _movements([
            {"item_id": "A", "reason": "DISPLAY_SET", "at": "2026-01-01T10:00:00Z"},
            {"item_id": "A", "reason": "SHIPMENT_RECEIPT", "at": "2026-01-03T10:00:00Z"},
            {"item_id": "A", "reason": "SALE", "at": "2026-01-05T10:00:00Z"},
        ])
        out = build_event_features(mv, window_days=7)
        out["date"] = pd.to_datetime(out["date"])
        row = out[(out["item_id"] == "A") & (out["date"] == "2026-01-04")]
        # Within 7d of display (Jan 1) AND shipment (Jan 3).
        assert int(row["recent_display_7d"].iloc[0]) == 1
        assert int(row["recent_shipment_7d"].iloc[0]) == 1

    def test_multiple_items_are_independent(self):
        mv = _movements([
            {"item_id": "A", "reason": "SHIPMENT_RECEIPT", "at": "2026-01-01T10:00:00Z"},
            {"item_id": "B", "reason": "SALE", "at": "2026-01-04T10:00:00Z"},
        ])
        out = build_event_features(mv, window_days=7)
        out["date"] = pd.to_datetime(out["date"])
        # B never had a shipment -- always 0.
        b_rows = out[out["item_id"] == "B"]
        assert b_rows["recent_shipment_7d"].sum() == 0
        # A's shipment doesn't leak to B.
        a_jan4 = out[(out["item_id"] == "A") & (out["date"] == "2026-01-04")]
        assert int(a_jan4["recent_shipment_7d"].iloc[0]) == 1


class TestComputeGlobalEventMultipliers:
    def _train(self, on_consumption: list[float], off_consumption: list[float]) -> pd.DataFrame:
        rows = (
            [{"consumption": c, "recent_shipment_7d": 1, "recent_display_7d": 0} for c in on_consumption]
            + [{"consumption": c, "recent_shipment_7d": 0, "recent_display_7d": 0} for c in off_consumption]
        )
        return pd.DataFrame(rows)

    def test_learns_uplift_multiplier(self):
        # Post-shipment days average 6, baseline 3 -> 2x.
        train = self._train(on_consumption=[6.0] * 40, off_consumption=[3.0] * 40)
        out = compute_global_event_multipliers(
            train, event_cols=["recent_shipment_7d", "recent_display_7d"], min_n=10,
        )
        assert abs(out["recent_shipment_7d"] - 2.0) < 1e-6
        # No display signal in the synthetic data -> 1.0.
        assert out["recent_display_7d"] == 1.0

    def test_falls_back_to_one_when_sample_too_small(self):
        train = self._train(on_consumption=[6.0] * 3, off_consumption=[3.0] * 40)
        out = compute_global_event_multipliers(
            train, event_cols=["recent_shipment_7d"], min_n=10,
        )
        assert out["recent_shipment_7d"] == 1.0

    def test_clips_at_cap(self):
        # Massive uplift signal but cap=3.0 holds it.
        train = self._train(on_consumption=[100.0] * 40, off_consumption=[1.0] * 40)
        out = compute_global_event_multipliers(
            train, event_cols=["recent_shipment_7d"], min_n=10, cap=3.0,
        )
        assert out["recent_shipment_7d"] == 3.0

    def test_clips_at_lower_cap(self):
        # Production default is now cap=2.0 after the 6.6pp WAPE spike pointed
        # at over-amplification. Same upward signal should clip at 2.0.
        train = self._train(on_consumption=[100.0] * 40, off_consumption=[1.0] * 40)
        out = compute_global_event_multipliers(
            train, event_cols=["recent_shipment_7d"], min_n=10, cap=2.0,
        )
        assert out["recent_shipment_7d"] == 2.0

    def test_empty_input_returns_neutral(self):
        out = compute_global_event_multipliers(
            pd.DataFrame(), event_cols=["recent_shipment_7d"], min_n=10,
        )
        assert out["recent_shipment_7d"] == 1.0
