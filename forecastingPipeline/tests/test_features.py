import pandas as pd

from src.features import build_daily_usage, build_stats


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
