"""Tests for EventAggregator inventory tracking.

TDD Step 1: RED - These tests verify event-carried state tracking.
"""

import sys
import time
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

import pytest

# Mock kafka module before importing
sys.modules["kafka"] = MagicMock()
sys.modules["kafka.errors"] = MagicMock()

from src.application.event_aggregator import EventAggregator
from src.events import NormalizedEvent


def create_event(
    event_id: str,
    item_id: str,
    quantity_change: int = -1,
    current_total_qty: int | None = None,
    previous_total_qty: int | None = None,
) -> NormalizedEvent:
    """Helper to create test events."""
    return NormalizedEvent(
        event_id=event_id,
        item_id=item_id,
        quantity_change=quantity_change,
        reason="sale",
        at=datetime.now(timezone.utc),
        current_total_qty=current_total_qty,
        previous_total_qty=previous_total_qty,
    )


class TestEventAggregatorInventoryTracking:
    """Tests for inventory tracking in EventAggregator."""

    def test_tracks_latest_inventory_per_item(self):
        """Should track the latest current_total_qty for each item."""
        aggregator = EventAggregator(
            item_debounce_seconds=0,  # Disable debounce for testing
        )

        # Add events with inventory
        event1 = create_event("e1", "item-A", current_total_qty=100)
        event2 = create_event("e2", "item-B", current_total_qty=50)

        aggregator.add_event(event1)
        aggregator.add_event(event2)

        inventory = aggregator.get_item_inventory()

        assert inventory == {"item-A": 100, "item-B": 50}

    def test_updates_inventory_on_new_event_for_same_item(self):
        """Should update inventory when a newer event arrives for same item."""
        aggregator = EventAggregator(
            item_debounce_seconds=0,
        )

        event1 = create_event("e1", "item-A", current_total_qty=100)
        event2 = create_event("e2", "item-A", current_total_qty=95)

        aggregator.add_event(event1)
        aggregator.add_event(event2)

        inventory = aggregator.get_item_inventory()

        assert inventory["item-A"] == 95

    def test_ignores_none_inventory(self):
        """Should not track items with None inventory."""
        aggregator = EventAggregator(
            item_debounce_seconds=0,
        )

        event1 = create_event("e1", "item-A", current_total_qty=100)
        event2 = create_event("e2", "item-B", current_total_qty=None)  # Old event

        aggregator.add_event(event1)
        aggregator.add_event(event2)

        inventory = aggregator.get_item_inventory()

        assert "item-A" in inventory
        assert "item-B" not in inventory

    def test_returns_copy_of_inventory(self):
        """get_item_inventory should return a copy, not the internal dict."""
        aggregator = EventAggregator(
            item_debounce_seconds=0,
        )

        event = create_event("e1", "item-A", current_total_qty=100)
        aggregator.add_event(event)

        inventory = aggregator.get_item_inventory()
        inventory["item-A"] = 999  # Modify the returned dict

        # Original should be unchanged
        assert aggregator.get_item_inventory()["item-A"] == 100

    def test_flush_clears_inventory(self):
        """flush() should clear the inventory tracking."""
        aggregator = EventAggregator(
            item_debounce_seconds=0,
        )

        event = create_event("e1", "item-A", current_total_qty=100)
        aggregator.add_event(event)

        # Verify inventory exists
        assert aggregator.get_item_inventory() == {"item-A": 100}

        # Flush
        aggregator.flush()

        # Inventory should be empty
        assert aggregator.get_item_inventory() == {}

    def test_empty_aggregator_returns_empty_inventory(self):
        """Empty aggregator should return empty inventory dict."""
        aggregator = EventAggregator()

        inventory = aggregator.get_item_inventory()

        assert inventory == {}

    def test_debounced_event_still_updates_inventory(self):
        """Even debounced events should update inventory tracking."""
        aggregator = EventAggregator(
            item_debounce_seconds=60,  # Long debounce window
        )

        # First event - accepted
        event1 = create_event("e1", "item-A", current_total_qty=100)
        added1 = aggregator.add_event(event1)

        # Second event - debounced (within window)
        event2 = create_event("e2", "item-A", current_total_qty=95)
        added2 = aggregator.add_event(event2)

        assert added1 is True
        assert added2 is False  # Debounced

        # Despite being debounced, inventory should be updated
        inventory = aggregator.get_item_inventory()
        assert inventory["item-A"] == 95

    def test_multiple_items_tracked_independently(self):
        """Should track inventory for multiple items independently."""
        aggregator = EventAggregator(
            item_debounce_seconds=0,
        )

        events = [
            create_event("e1", "item-A", current_total_qty=100),
            create_event("e2", "item-B", current_total_qty=200),
            create_event("e3", "item-C", current_total_qty=300),
            create_event("e4", "item-A", current_total_qty=90),  # Update A
            create_event("e5", "item-B", current_total_qty=195),  # Update B
        ]

        for event in events:
            aggregator.add_event(event)

        inventory = aggregator.get_item_inventory()

        assert inventory == {
            "item-A": 90,
            "item-B": 195,
            "item-C": 300,
        }


class TestEventAggregatorReset:
    """Tests for reset behavior with inventory."""

    def test_reset_clears_inventory(self):
        """reset() should also clear inventory tracking."""
        aggregator = EventAggregator(
            item_debounce_seconds=0,
        )

        event = create_event("e1", "item-A", current_total_qty=100)
        aggregator.add_event(event)

        aggregator.reset()

        assert aggregator.get_item_inventory() == {}


class TestEventAggregatorBackwardCompatibility:
    """Tests ensuring backward compatibility with existing behavior."""

    def test_existing_functionality_unchanged(self):
        """Original add_event behavior should remain unchanged."""
        aggregator = EventAggregator(
            batch_window_seconds=60,
            batch_size_trigger=5,
            item_debounce_seconds=0,
        )

        # Add events without inventory fields
        for i in range(3):
            event = NormalizedEvent(
                event_id=f"e{i}",
                item_id=f"item-{i}",
                quantity_change=-1,
                reason="sale",
                at=datetime.now(timezone.utc),
            )
            aggregator.add_event(event)

        assert aggregator.event_count == 3
        assert aggregator.get_affected_items() == {"item-0", "item-1", "item-2"}

    def test_check_batch_ready_unchanged(self):
        """check_batch_ready should work as before."""
        aggregator = EventAggregator(
            batch_size_trigger=2,
            item_debounce_seconds=0,
        )

        event1 = create_event("e1", "item-A")
        aggregator.add_event(event1)
        assert aggregator.check_batch_ready().ready is False

        event2 = create_event("e2", "item-B")
        aggregator.add_event(event2)
        assert aggregator.check_batch_ready().ready is True
