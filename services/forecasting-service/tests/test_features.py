import pandas as pd
import numpy as np

from src.features import build_daily_usage, build_stats, detect_stockout_days


def _df(rows: list[dict]) -> pd.DataFrame:
    df = pd.DataFrame(rows)
    if not df.empty and "at" in df.columns:
        df["at"] = pd.to_datetime(df["at"], utc=True)
    return df


def test_build_daily_usage_ignores_shipments_and_aggregates_sales():
    events = _df(
        [
            {
                "event_id": "e1",
                "item_id": "A",
                "quantity_change": -2,
                "reason": "sale",
                "at": "2025-11-03T09:00:00Z",
            },
            {
                "event_id": "e2",
                "item_id": "A",
                "quantity_change": -1,
                "reason": "sale",
                "at": "2025-11-03T12:00:00Z",
            },
            {
                "event_id": "e3",
                "item_id": "A",
                "quantity_change": 30,
                "reason": "shipment",
                "at": "2025-11-03T18:00:00Z",
            },
            {
                "event_id": "e4",
                "item_id": "B",
                "quantity_change": 10,
                "reason": "shipment",
                "at": "2025-11-03T10:00:00Z",
            },
            {
                "event_id": "e5",
                "item_id": "B",
                "quantity_change": -5,
                "reason": "sale",
                "at": "2025-11-05T10:00:00Z",
            },
        ]
    )

    daily = build_daily_usage(events)

    # Expect date range 2025-11-03..2025-11-05 for both A and B
    daily["date"] = pd.to_datetime(daily["date"])  # normalize for comparisons

    def get(item, y, m, d):
        return float(
            daily.loc[
                (daily["item_id"] == item) & (daily["date"] == pd.Timestamp(y, m, d)), "consumption"
            ].iloc[0]
        )

    assert get("A", 2025, 11, 3) == 3.0  # -2 + -1 → 3 consumption
    assert get("A", 2025, 11, 4) == 0.0
    assert get("A", 2025, 11, 5) == 0.0

    assert get("B", 2025, 11, 3) == 0.0  # shipment ignored
    assert get("B", 2025, 11, 4) == 0.0
    assert get("B", 2025, 11, 5) == 5.0


def test_build_stats_rolling_and_calendar_features():
    daily = pd.DataFrame(
        {
            "date": pd.to_datetime(["2025-11-01", "2025-11-02", "2025-11-03"]),
            "item_id": ["A", "A", "A"],
            "consumption": [0.0, 2.0, 4.0],
        }
    )

    feats = build_stats(daily)

    # Select last day row
    row = feats.iloc[-1]
    # Rolling means
    assert abs(row["ma7"] - 2.0) < 1e-9
    assert abs(row["ma14"] - 2.0) < 1e-9
    # std14 with ddof=0 over [0,2,4] = sqrt(8/3)
    expected_std = (8.0 / 3.0) ** 0.5
    assert abs(row["std14"] - expected_std) < 1e-9

    # Calendar features for 2025-11-03 (Monday)
    assert int(row["dow"]) == 0
    assert bool(row["is_weekend"]) is False


def test_build_daily_usage_empty():
    empty = pd.DataFrame(columns=["event_id", "item_id", "quantity_change", "reason", "at"])
    out = build_daily_usage(empty)
    assert list(out.columns) == ["date", "item_id", "consumption"]
    assert out.empty


def test_build_stats_preserves_is_stockout():
    """build_stats should not drop the is_stockout column."""
    daily = pd.DataFrame({
        "date": pd.to_datetime(["2025-11-01", "2025-11-02", "2025-11-03"]),
        "item_id": ["A", "A", "A"],
        "consumption": [2.0, 0.0, 3.0],
        "is_stockout": [False, True, False],
    })
    feats = build_stats(daily)
    assert "is_stockout" in feats.columns
    assert feats["is_stockout"].tolist() == [False, True, False]


# ---------------------------------------------------------------------------
# detect_stockout_days tests
# ---------------------------------------------------------------------------

class TestDetectStockoutDays:
    """A day is stockout only if the SKU had zero inventory the entire day.

    Sellout days (started with stock, sold to zero) are NOT stockouts -- they
    had real demand and must remain in the training set.
    """

    def _make_movements(self, records: list[dict]) -> pd.DataFrame:
        return pd.DataFrame(records)

    def test_empty_input_returns_empty(self):
        empty = pd.DataFrame(columns=["item_id", "at", "current_quantity", "previous_quantity"])
        result = detect_stockout_days(empty)
        assert result.empty
        assert list(result.columns) == ["item_id", "date", "is_stockout"]

    def test_missing_inventory_columns_returns_empty(self):
        movements = pd.DataFrame({
            "item_id": ["A"],
            "at": ["2025-01-01T12:00:00Z"],
            "quantity_change": [-1],
            "reason": ["sale"],
        })
        result = detect_stockout_days(movements)
        assert result.empty

    def test_sellout_day_is_not_stockout(self):
        """The bug fix: a day that started with stock and sold to zero
        had real demand and must NOT be flagged stockout."""
        movements = self._make_movements([
            # Day 2: started with 2 units, sold both, ended at 0.
            {"item_id": "A", "at": "2025-01-02T10:00:00Z",
             "current_quantity": 0, "previous_quantity": 2},
        ])
        result = detect_stockout_days(movements)
        assert bool(result["is_stockout"].iloc[0]) is False

    def test_true_stockout_day_is_flagged(self):
        """Day starts at zero (carried over from prior day) and gets no restock."""
        movements = self._make_movements([
            # Jan 1: ended at 0.
            {"item_id": "A", "at": "2025-01-01T10:00:00Z",
             "current_quantity": 0, "previous_quantity": 2},
            # Jan 2: no movements (handled by forward-fill).
            # Jan 3: a restock day; should not be stockout.
            {"item_id": "A", "at": "2025-01-03T10:00:00Z",
             "current_quantity": 10, "previous_quantity": 0},
        ])
        result = detect_stockout_days(movements)
        result["date"] = pd.to_datetime(result["date"])
        # Jan 2 had no inventory all day -> stockout.
        day2 = result[(result["item_id"] == "A") & (result["date"] == "2025-01-02")]
        assert bool(day2["is_stockout"].iloc[0]) is True

    def test_restock_day_is_not_stockout(self):
        """Started empty, restock arrived mid-day. The SKU could and did sell."""
        movements = self._make_movements([
            # First movement of the day is a restock from 0 -> 100.
            {"item_id": "A", "at": "2025-01-01T10:00:00Z",
             "current_quantity": 100, "previous_quantity": 0},
        ])
        result = detect_stockout_days(movements)
        assert bool(result["is_stockout"].iloc[0]) is False

    def test_no_movement_day_inherits_prior_eod(self):
        """No-movement day at the START of the window inherits prior state."""
        movements = self._make_movements([
            {"item_id": "A", "at": "2025-01-01T10:00:00Z",
             "current_quantity": 50, "previous_quantity": 55},
            # No movements Jan 2, Jan 3
            {"item_id": "A", "at": "2025-01-04T10:00:00Z",
             "current_quantity": 48, "previous_quantity": 50},
        ])
        result = detect_stockout_days(movements)
        result["date"] = pd.to_datetime(result["date"])
        # Jan 2 and Jan 3 had qty=50 at start, no movements -> not stockout.
        for d in ("2025-01-02", "2025-01-03"):
            row = result[(result["item_id"] == "A") & (result["date"] == d)]
            assert bool(row["is_stockout"].iloc[0]) is False

    def test_multiple_items_independent(self):
        movements = self._make_movements([
            # Item A: out of stock all day (start = 0 from prev, no movement = no restock).
            # Use a no-movement day flanked by stockout endpoints to make A's day
            # unambiguously empty.
            {"item_id": "A", "at": "2025-01-01T10:00:00Z",
             "current_quantity": 0, "previous_quantity": 0},
            # Item B: full stock on the same day.
            {"item_id": "B", "at": "2025-01-01T10:00:00Z",
             "current_quantity": 5, "previous_quantity": 8},
        ])
        result = detect_stockout_days(movements)
        a_row = result[result["item_id"] == "A"].iloc[0]
        b_row = result[result["item_id"] == "B"].iloc[0]
        assert bool(a_row["is_stockout"]) is True
        assert bool(b_row["is_stockout"]) is False

    def test_partial_day_stockout_then_restock_is_not_stockout(self):
        """Started empty, restocked mid-day after a brief stockout window."""
        movements = self._make_movements([
            # First movement: restock from 0 to 10 at 2pm.
            {"item_id": "A", "at": "2025-01-01T14:00:00Z",
             "current_quantity": 10, "previous_quantity": 0},
            # Second: a sale later that day.
            {"item_id": "A", "at": "2025-01-01T18:00:00Z",
             "current_quantity": 8, "previous_quantity": 10},
        ])
        result = detect_stockout_days(movements)
        # Peak inventory during the day was 10, so SKU could sell -> NOT stockout.
        assert bool(result["is_stockout"].iloc[0]) is False
