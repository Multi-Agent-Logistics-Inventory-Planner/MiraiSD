import json
import time
from collections.abc import Iterator
from datetime import datetime
from pathlib import Path

import pandas as pd
from pydantic import BaseModel, ValidationError, field_validator

from . import config


# Pydantic Models
class EventPayload(BaseModel):
    """Payload inside the event envelope."""

    item_id: str
    quantity_change: int
    reason: str | None = None
    at: datetime
    from_box_id: str | None = None
    to_box_id: str | None = None
    actor_id: str | None = None

    @field_validator("at")
    @classmethod
    def validate_at(cls, v):
        """Ensure datetime is UTC-aware."""
        if isinstance(v, str):
            # Parse ISO string
            v = pd.to_datetime(v, utc=True)
        if hasattr(v, "tzinfo") and v.tzinfo is None:
            v = v.replace(tzinfo=datetime.UTC)
        return v


class EventEnvelope(BaseModel):
    """Complete event structure from NDJSON file."""

    event_id: str
    payload: EventPayload
    topic: str | None = None
    event_type: str | None = None
    entity_type: str | None = None
    entity_id: str | None = None
    created_at: datetime | None = None


class NormalizedEvent(BaseModel):
    """Normalized event output (what we return)."""

    event_id: str
    item_id: str
    quantity_change: int
    reason: str | None = None
    at: datetime


def _default_events_path(path: Path | str | None) -> Path:
    if path is None:
        return config.EVENTS_DIR / "inventory-changes.ndjson"
    return Path(path)


def _parse_line(line: str, strict: bool) -> dict | None:
    """Parse a single NDJSON line using Pydantic validation."""
    line = line.strip()
    if not line:
        return None

    try:
        envelope = EventEnvelope.model_validate_json(line)

        # Convert to normalized format
        normalized = NormalizedEvent(
            event_id=envelope.event_id,
            item_id=envelope.payload.item_id,
            quantity_change=envelope.payload.quantity_change,
            reason=envelope.payload.reason,
            at=envelope.payload.at,
        )

        # Convert to dict
        return normalized.model_dump()
    except ValidationError as e:
        if strict:
            raise ValueError(f"Invalid event schema: {e}") from e
        return None
    except json.JSONDecodeError as e:
        if strict:
            raise ValueError(f"Malformed JSON: {e}") from e
        return None


def stream_events(
    from_ts: str | None = None,
    to_ts: str | None = None,
    path: Path | str | None = None,
    poll_interval: float = 1.0,
) -> Iterator[dict]:
    """Stream events from NDJSON as normalized dicts.

    If from/to bounds are provided, reads the file once, filters to the window
    and yields events sorted by time. If no bounds, tails the file and yields
    new events as they arrive (like `tail -f`).
    """
    events_path = _default_events_path(path)
    events_path.parent.mkdir(parents=True, exist_ok=True)
    events_path.touch(exist_ok=True)

    start_ts = pd.to_datetime(from_ts, utc=True) if from_ts else None
    end_ts = pd.to_datetime(to_ts, utc=True) if to_ts else None

    # Bounded mode: read once and yield sorted
    if start_ts is not None or end_ts is not None:
        rows: list[dict] = []
        with events_path.open("r", encoding="utf-8") as f:
            for line in f:
                parsed = _parse_line(line, strict=False)
                if parsed is None:
                    continue
                ts = parsed["at"]
                if start_ts is not None and ts < start_ts:
                    continue
                if end_ts is not None and ts > end_ts:
                    continue
                rows.append(parsed)
        rows.sort(key=lambda r: r["at"])  # time order
        yield from rows
        return

    # Unbounded mode: tail
    with events_path.open("r", encoding="utf-8") as f:
        f.seek(0, 2)
        while True:
            line = f.readline()
            if not line:
                time.sleep(poll_interval)
                continue
            parsed = _parse_line(line, strict=False)
            if parsed is None:
                continue
            yield parsed


def load_events_window(
    start: str,
    end: str,
    path: Path | str | None = None,
) -> pd.DataFrame:
    """Load events within [start, end] inclusive, validated and time-sorted.

    Returns a DataFrame with columns:
    event_id, item_id, quantity_change, reason, at (datetime64[ns, UTC]).
    Raises ValueError on bad schema rows.
    """
    events_path = _default_events_path(path)
    start_ts = pd.to_datetime(start, utc=True)
    end_ts = pd.to_datetime(end, utc=True)

    rows: list[dict] = []
    with events_path.open("r", encoding="utf-8") as f:
        for line in f:
            parsed = _parse_line(line, strict=True)  # strict: raise on bad
            if parsed is None:
                continue
            ts = parsed["at"]
            if ts < start_ts or ts > end_ts:
                continue
            rows.append(parsed)

    if not rows:
        # Return empty DataFrame with correct dtypes
        empty = pd.DataFrame(columns=["event_id", "item_id", "quantity_change", "reason", "at"])
        empty["at"] = pd.to_datetime(empty["at"], utc=True)
        return empty

    rows.sort(key=lambda r: r["at"])  # enforce order
    df = pd.DataFrame(rows, columns=["event_id", "item_id", "quantity_change", "reason", "at"])
    # Ensure dtypes
    df["event_id"] = df["event_id"].astype(str)
    df["item_id"] = df["item_id"].astype(str)
    df["quantity_change"] = pd.to_numeric(df["quantity_change"], errors="raise").astype(int)
    df["reason"] = df["reason"].astype("string")
    df["at"] = pd.to_datetime(df["at"], utc=True, errors="raise")
    return df


class FileEventSource:
    """Back-compat tailing interface (simulates Kafka)."""

    def __init__(self, path="events/inventory-changes.ndjson"):
        self.path = Path(path)

    def stream(self, poll_interval=1.0) -> Iterator[dict]:
        yield from stream_events(path=self.path, poll_interval=poll_interval)
