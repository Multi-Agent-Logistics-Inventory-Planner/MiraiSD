"""Event aggregator with debouncing and batching for efficient processing."""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field

from .. import config
from ..events import NormalizedEvent

logger = logging.getLogger(__name__)


@dataclass
class BatchResult:
    """Result of checking if a batch is ready."""

    ready: bool
    item_ids: set[str]
    event_count: int
    trigger_reason: str | None = None


@dataclass
class EventAggregator:
    """Aggregates events with per-item debouncing and batch triggering.

    Batching strategy:
    - Per-item debounce: Ignore rapid duplicate item events within ITEM_DEBOUNCE_SECONDS
    - Batch triggers: Fire when BATCH_WINDOW_SECONDS elapsed OR BATCH_SIZE_TRIGGER events accumulated
    """

    batch_window_seconds: float = field(default_factory=lambda: config.BATCH_WINDOW_SECONDS)
    batch_size_trigger: int = field(default_factory=lambda: config.BATCH_SIZE_TRIGGER)
    item_debounce_seconds: float = field(default_factory=lambda: config.ITEM_DEBOUNCE_SECONDS)

    # Internal state
    _events: list[NormalizedEvent] = field(default_factory=list)
    _item_last_seen: dict[str, float] = field(default_factory=dict)
    _batch_start_time: float | None = field(default=None)

    def add_event(self, event: NormalizedEvent) -> bool:
        """Add an event to the aggregator.

        Returns True if the event was added, False if it was debounced.
        """
        now = time.monotonic()
        item_id = event.item_id

        # Start batch timer on first event
        if self._batch_start_time is None:
            self._batch_start_time = now
            logger.debug("Started new batch window")

        # Per-item debouncing: skip if same item seen within debounce window
        last_seen = self._item_last_seen.get(item_id)
        if last_seen is not None and (now - last_seen) < self.item_debounce_seconds:
            logger.debug(
                "Debounced event for item %s (%.2fs since last)",
                item_id,
                now - last_seen,
            )
            return False

        # Accept event
        self._events.append(event)
        self._item_last_seen[item_id] = now
        logger.debug(
            "Added event: item=%s, total_events=%d",
            item_id,
            len(self._events),
        )
        return True

    def add_events(self, events: list[NormalizedEvent]) -> int:
        """Add multiple events, returning count of non-debounced events."""
        added = 0
        for event in events:
            if self.add_event(event):
                added += 1
        return added

    def check_batch_ready(self) -> BatchResult:
        """Check if a batch is ready to process.

        Batch triggers:
        1. Time window elapsed (BATCH_WINDOW_SECONDS since first event)
        2. Event count threshold reached (BATCH_SIZE_TRIGGER events)
        """
        if not self._events:
            return BatchResult(ready=False, item_ids=set(), event_count=0)

        now = time.monotonic()
        event_count = len(self._events)
        item_ids = {e.item_id for e in self._events}

        # Check size trigger
        if event_count >= self.batch_size_trigger:
            logger.info(
                "Batch ready: size trigger (%d events, %d items)",
                event_count,
                len(item_ids),
            )
            return BatchResult(
                ready=True,
                item_ids=item_ids,
                event_count=event_count,
                trigger_reason="size",
            )

        # Check time window trigger
        if self._batch_start_time is not None:
            elapsed = now - self._batch_start_time
            if elapsed >= self.batch_window_seconds:
                logger.info(
                    "Batch ready: time trigger (%.1fs elapsed, %d events, %d items)",
                    elapsed,
                    event_count,
                    len(item_ids),
                )
                return BatchResult(
                    ready=True,
                    item_ids=item_ids,
                    event_count=event_count,
                    trigger_reason="time",
                )

        return BatchResult(ready=False, item_ids=item_ids, event_count=event_count)

    def get_affected_items(self) -> set[str]:
        """Get unique item IDs from accumulated events."""
        return {e.item_id for e in self._events}

    def flush(self) -> list[NormalizedEvent]:
        """Flush and return all accumulated events, resetting state."""
        events = self._events
        self._events = []
        self._item_last_seen.clear()
        self._batch_start_time = None
        logger.debug("Flushed %d events", len(events))
        return events

    def reset(self) -> None:
        """Reset aggregator state without returning events."""
        self._events.clear()
        self._item_last_seen.clear()
        self._batch_start_time = None

    @property
    def event_count(self) -> int:
        """Current number of accumulated events."""
        return len(self._events)

    @property
    def is_empty(self) -> bool:
        """Check if aggregator has no events."""
        return len(self._events) == 0

    def time_until_batch(self) -> float | None:
        """Return seconds until time-based batch trigger, or None if no events."""
        if self._batch_start_time is None:
            return None
        elapsed = time.monotonic() - self._batch_start_time
        remaining = self.batch_window_seconds - elapsed
        return max(0.0, remaining)
