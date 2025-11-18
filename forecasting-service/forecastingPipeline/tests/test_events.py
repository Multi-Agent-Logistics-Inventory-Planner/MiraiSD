import json
from pathlib import Path

import pandas as pd
import pytest

from src.events import load_events_window, stream_events


def _write_ndjson(tmp_path: Path, lines: list[dict]) -> Path:
    p = tmp_path / "inventory-changes.ndjson"
    with p.open("w", encoding="utf-8") as f:
        for obj in lines:
            f.write(json.dumps(obj) + "\n")
    return p


def test_load_events_window_sorts_and_filters(tmp_path: Path):
    # Seed out-of-order events
    lines = [
        {
            "event_id": "e3",
            "topic": "inventory-changes",
            "event_type": "UPDATED",
            "entity_type": "stock_movement",
            "entity_id": "sm-003",
            "payload": {
                "item_id": "A",
                "quantity_change": -1,
                "reason": "sale",
                "at": "2025-11-03T12:00:00Z",
            },
            "created_at": "2025-11-03T12:00:00Z",
        },
        {
            "event_id": "e1",
            "topic": "inventory-changes",
            "event_type": "UPDATED",
            "entity_type": "stock_movement",
            "entity_id": "sm-001",
            "payload": {
                "item_id": "A",
                "quantity_change": -2,
                "reason": "sale",
                "at": "2025-11-03T09:00:00Z",
            },
            "created_at": "2025-11-03T09:00:00Z",
        },
        {
            "event_id": "e2",
            "topic": "inventory-changes",
            "event_type": "UPDATED",
            "entity_type": "stock_movement",
            "entity_id": "sm-002",
            "payload": {
                "item_id": "B",
                "quantity_change": 30,
                "reason": "shipment",
                "at": "2025-11-03T10:00:00Z",
            },
            "created_at": "2025-11-03T10:00:00Z",
        },
    ]
    path = _write_ndjson(tmp_path, lines)

    df = load_events_window("2025-11-03T00:00:00Z", "2025-11-03T23:59:59Z", path=path)
    # Should be sorted by time: e1 (09:00), e2 (10:00), e3 (12:00)
    assert list(df["event_id"]) == ["e1", "e2", "e3"]
    assert pd.api.types.is_datetime64_any_dtype(df["at"])  # tz-aware

    # Filter a smaller window
    df_small = load_events_window("2025-11-03T09:30:00Z", "2025-11-03T10:15:00Z", path=path)
    assert list(df_small["event_id"]) == ["e2"]


def test_load_events_window_raises_on_bad_schema(tmp_path: Path):
    bad_line = {
        "event_id": "e-bad",
        "topic": "inventory-changes",
        "event_type": "UPDATED",
        "entity_type": "stock_movement",
        "entity_id": "sm-999",
        "payload": {
            # Missing item_id
            "quantity_change": -1,
            "reason": "sale",
            "at": "2025-11-03T09:00:00Z",
        },
        "created_at": "2025-11-03T09:00:00Z",
    }
    path = _write_ndjson(tmp_path, [bad_line])
    with pytest.raises(ValueError):
        _ = load_events_window("2025-11-03T00:00:00Z", "2025-11-03T23:59:59Z", path=path)


def test_stream_events_bounded(tmp_path: Path):
    lines = [
        {
            "event_id": "e10",
            "topic": "inventory-changes",
            "event_type": "UPDATED",
            "entity_type": "stock_movement",
            "entity_id": "sm-010",
            "payload": {
                "item_id": "Z",
                "quantity_change": -5,
                "reason": "sale",
                "at": "2025-11-05T09:00:00Z",
            },
            "created_at": "2025-11-05T09:00:00Z",
        },
        {
            "event_id": "e11",
            "topic": "inventory-changes",
            "event_type": "UPDATED",
            "entity_type": "stock_movement",
            "entity_id": "sm-011",
            "payload": {
                "item_id": "Z",
                "quantity_change": 10,
                "reason": "shipment",
                "at": "2025-11-05T10:00:00Z",
            },
            "created_at": "2025-11-05T10:00:00Z",
        },
    ]
    path = _write_ndjson(tmp_path, lines)
    out = list(
        stream_events(from_ts="2025-11-05T09:30:00Z", to_ts="2025-11-05T23:59:59Z", path=path)
    )
    # Only e11 should be included
    assert len(out) == 1 and out[0]["event_id"] == "e11"
