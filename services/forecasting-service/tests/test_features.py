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
    def _make_movements(self, records: list[dict]) -> pd.DataFrame:
        """Create a movements DataFrame from simple records."""
        return pd.DataFrame(records)

    def test_empty_input_returns_empty(self):
        empty = pd.DataFrame(columns=["item_id", "at", "current_quantity", "previous_quantity"])
        result = detect_stockout_days(empty)
        assert result.empty
        assert list(result.columns) == ["item_id", "date", "is_stockout"]

    def test_missing_inventory_columns_returns_empty(self):
        """Without current_quantity/previous_quantity, returns empty."""
        movements = pd.DataFrame({
            "item_id": ["A"],
            "at": ["2025-01-01T12:00:00Z"],
            "quantity_change": [-1],
            "reason": ["sale"],
        })
        result = detect_stockout_days(movements)
        assert result.empty

    def test_detects_zero_inventory_as_stockout(self):
        """Days where current_quantity=0 should be marked as stockout."""
        movements = self._make_movements([
            {"item_id": "A", "at": "2025-01-01T10:00:00Z",
             "current_quantity": 5, "previous_quantity": 8},
            {"item_id": "A", "at": "2025-01-02T10:00:00Z",
             "current_quantity": 0, "previous_quantity": 2},
            {"item_id": "A", "at": "2025-01-03T10:00:00Z",
             "current_quantity": 10, "previous_quantity": 0},
        ])
        result = detect_stockout_days(movements)
        result["date"] = pd.to_datetime(result["date"])

        day1 = result[(result["item_id"] == "A") & (result["date"] == "2025-01-01")]
        day2 = result[(result["item_id"] == "A") & (result["date"] == "2025-01-02")]
        day3 = result[(result["item_id"] == "A") & (result["date"] == "2025-01-03")]

        assert day1["is_stockout"].iloc[0] is np.bool_(False)
        assert day2["is_stockout"].iloc[0] is np.bool_(True)
        assert day3["is_stockout"].iloc[0] is np.bool_(False)

    def test_forward_fills_inventory_on_missing_days(self):
        """Days with no movements should inherit prior day's inventory."""
        movements = self._make_movements([
            {"item_id": "A", "at": "2025-01-01T10:00:00Z",
             "current_quantity": 0, "previous_quantity": 3},
            # No movement on Jan 2
            {"item_id": "A", "at": "2025-01-03T10:00:00Z",
             "current_quantity": 5, "previous_quantity": 0},
        ])
        result = detect_stockout_days(movements)
        result["date"] = pd.to_datetime(result["date"])

        # Jan 2 should be forward-filled from Jan 1 (qty=0 -> stockout)
        day2 = result[(result["item_id"] == "A") & (result["date"] == "2025-01-02")]
        assert day2["is_stockout"].iloc[0] is np.bool_(True)

    def test_multiple_items_independent(self):
        """Stockout detection should be independent per item."""
        movements = self._make_movements([
            {"item_id": "A", "at": "2025-01-01T10:00:00Z",
             "current_quantity": 0, "previous_quantity": 1},
            {"item_id": "B", "at": "2025-01-01T10:00:00Z",
             "current_quantity": 5, "previous_quantity": 8},
        ])
        result = detect_stockout_days(movements)

        a_row = result[result["item_id"] == "A"].iloc[0]
        b_row = result[result["item_id"] == "B"].iloc[0]
        assert a_row["is_stockout"] is np.bool_(True)
        assert b_row["is_stockout"] is np.bool_(False)

    def test_last_movement_of_day_wins(self):
        """When multiple movements on same day, last one determines EOD inventory."""
        movements = self._make_movements([
            {"item_id": "A", "at": "2025-01-01T08:00:00Z",
             "current_quantity": 0, "previous_quantity": 2},
            {"item_id": "A", "at": "2025-01-01T18:00:00Z",
             "current_quantity": 10, "previous_quantity": 0},
        ])
        result = detect_stockout_days(movements)
        # Last movement has qty=10, so NOT stockout
        assert result["is_stockout"].iloc[0] is np.bool_(False)
