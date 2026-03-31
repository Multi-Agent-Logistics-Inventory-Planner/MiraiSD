"""Tests for event-carried state inventory fields.

TDD Step 1: RED - These tests verify backward compatibility and new inventory fields.
"""

import json
from pathlib import Path

import pandas as pd
import pytest

from src.events import EventEnvelope, EventPayload, NormalizedEvent


class TestEventPayloadInventoryFields:
    """Tests for EventPayload optional inventory fields."""

    def test_parses_new_inventory_fields(self):
        """EventPayload should parse current_total_qty and previous_total_qty."""
        payload_data = {
            "item_id": "item-123",
            "quantity_change": -5,
            "reason": "sale",
            "at": "2025-11-03T12:00:00Z",
            "current_total_qty": 45,
            "previous_total_qty": 50,
        }

        payload = EventPayload(**payload_data)

        assert payload.current_total_qty == 45
        assert payload.previous_total_qty == 50

    def test_backward_compatible_without_inventory_fields(self):
        """Old events without inventory fields should still parse correctly."""
        payload_data = {
            "item_id": "item-123",
            "quantity_change": -5,
            "reason": "sale",
            "at": "2025-11-03T12:00:00Z",
        }

        payload = EventPayload(**payload_data)

        assert payload.current_total_qty is None
        assert payload.previous_total_qty is None
        assert payload.item_id == "item-123"
        assert payload.quantity_change == -5

    def test_partial_inventory_fields(self):
        """Should handle only one inventory field provided."""
        payload_data = {
            "item_id": "item-123",
            "quantity_change": -5,
            "reason": "sale",
            "at": "2025-11-03T12:00:00Z",
            "current_total_qty": 45,
            # previous_total_qty not provided
        }

        payload = EventPayload(**payload_data)

        assert payload.current_total_qty == 45
        assert payload.previous_total_qty is None


class TestNormalizedEventInventoryFields:
    """Tests for NormalizedEvent inventory fields."""

    def test_has_inventory_fields(self):
        """NormalizedEvent should have current_total_qty and previous_total_qty."""
        event = NormalizedEvent(
            event_id="evt-001",
            item_id="item-123",
            quantity_change=-5,
            reason="sale",
            at="2025-11-03T12:00:00Z",
            current_total_qty=45,
            previous_total_qty=50,
        )

        assert event.current_total_qty == 45
        assert event.previous_total_qty == 50

    def test_defaults_to_none(self):
        """Inventory fields should default to None."""
        event = NormalizedEvent(
            event_id="evt-001",
            item_id="item-123",
            quantity_change=-5,
            reason="sale",
            at="2025-11-03T12:00:00Z",
        )

        assert event.current_total_qty is None
        assert event.previous_total_qty is None


class TestEventEnvelopeToNormalized:
    """Tests for _to_normalized carrying inventory fields."""

    def test_carries_inventory_fields_through(self):
        """_to_normalized should carry inventory fields from payload."""
        from src.events import _parse_line

        event_json = json.dumps(
            {
                "event_id": "evt-001",
                "topic": "inventory-changes",
                "event_type": "CREATED",
                "entity_type": "stock_movement",
                "entity_id": "sm-001",
                "payload": {
                    "item_id": "item-123",
                    "quantity_change": -5,
                    "reason": "sale",
                    "at": "2025-11-03T12:00:00Z",
                    "current_total_qty": 45,
                    "previous_total_qty": 50,
                },
                "created_at": "2025-11-03T12:00:00Z",
            }
        )

        result = _parse_line(event_json, strict=True)

        assert result is not None
        assert result["current_total_qty"] == 45
        assert result["previous_total_qty"] == 50

    def test_old_events_without_inventory_still_parse(self):
        """Old events without inventory fields should parse with None values."""
        from src.events import _parse_line

        event_json = json.dumps(
            {
                "event_id": "evt-002",
                "topic": "inventory-changes",
                "event_type": "CREATED",
                "entity_type": "stock_movement",
                "entity_id": "sm-002",
                "payload": {
                    "item_id": "item-456",
                    "quantity_change": 10,
                    "reason": "restock",
                    "at": "2025-11-03T14:00:00Z",
                },
                "created_at": "2025-11-03T14:00:00Z",
            }
        )

        result = _parse_line(event_json, strict=True)

        assert result is not None
        assert result["current_total_qty"] is None
        assert result["previous_total_qty"] is None
        # Original fields still work
        assert result["item_id"] == "item-456"
        assert result["quantity_change"] == 10


class TestLoadEventsWindowWithInventory:
    """Tests for load_events_window with inventory fields."""

    def _write_ndjson(self, tmp_path: Path, lines: list[dict]) -> Path:
        p = tmp_path / "inventory-changes.ndjson"
        with p.open("w", encoding="utf-8") as f:
            for obj in lines:
                f.write(json.dumps(obj) + "\n")
        return p

    def test_load_events_includes_inventory_fields(self, tmp_path: Path):
        """load_events_window should include inventory fields in output."""
        from src.events import load_events_window

        lines = [
            {
                "event_id": "e1",
                "topic": "inventory-changes",
                "event_type": "CREATED",
                "entity_type": "stock_movement",
                "entity_id": "sm-001",
                "payload": {
                    "item_id": "A",
                    "quantity_change": -5,
                    "reason": "sale",
                    "at": "2025-11-03T10:00:00Z",
                    "current_total_qty": 95,
                    "previous_total_qty": 100,
                },
                "created_at": "2025-11-03T10:00:00Z",
            },
        ]
        path = self._write_ndjson(tmp_path, lines)

        df = load_events_window(
            "2025-11-03T00:00:00Z", "2025-11-03T23:59:59Z", path=path
        )

        assert "current_total_qty" in df.columns
        assert "previous_total_qty" in df.columns
        assert df.iloc[0]["current_total_qty"] == 95
        assert df.iloc[0]["previous_total_qty"] == 100

    def test_load_events_handles_missing_inventory(self, tmp_path: Path):
        """load_events_window should handle old events without inventory."""
        from src.events import load_events_window

        lines = [
            {
                "event_id": "e1",
                "topic": "inventory-changes",
                "event_type": "CREATED",
                "entity_type": "stock_movement",
                "entity_id": "sm-001",
                "payload": {
                    "item_id": "A",
                    "quantity_change": -5,
                    "reason": "sale",
                    "at": "2025-11-03T10:00:00Z",
                    # No inventory fields
                },
                "created_at": "2025-11-03T10:00:00Z",
            },
        ]
        path = self._write_ndjson(tmp_path, lines)

        df = load_events_window(
            "2025-11-03T00:00:00Z", "2025-11-03T23:59:59Z", path=path
        )

        assert "current_total_qty" in df.columns
        assert "previous_total_qty" in df.columns
        assert pd.isna(df.iloc[0]["current_total_qty"])
        assert pd.isna(df.iloc[0]["previous_total_qty"])
