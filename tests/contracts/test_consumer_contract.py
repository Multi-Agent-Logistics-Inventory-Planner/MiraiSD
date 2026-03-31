"""Contract tests: validate that the forecasting-service consumer accepts contract-compliant events.

These tests import the Pydantic models from the forecasting-service and feed
them payloads that match the shared JSON Schema. This ensures the consumer
can parse everything the producer sends.
"""

import json
from datetime import datetime, timezone
from uuid import uuid4

import pytest
from pydantic import ValidationError

# Imported via sys.path manipulation in conftest.py
from events import EventEnvelope, EventPayload, NormalizedEvent


class TestConsumerAcceptsContractPayloads:
    """Verify that Pydantic models parse contract-compliant events."""

    def test_full_payload_parses(self, sample_full_payload):
        """Consumer should parse a complete event without errors."""
        envelope = EventEnvelope.model_validate(sample_full_payload)

        assert envelope.event_id == sample_full_payload["event_id"]
        assert envelope.payload.item_id == sample_full_payload["payload"]["item_id"]
        assert envelope.payload.quantity_change == -3
        assert envelope.payload.reason == "sale"
        assert envelope.payload.from_location_code == "B1"
        assert envelope.payload.to_location_code is None

    def test_minimal_payload_parses(self, sample_minimal_payload):
        """Consumer should parse a minimal event (only required fields)."""
        envelope = EventEnvelope.model_validate(sample_minimal_payload)

        assert envelope.event_id == sample_minimal_payload["event_id"]
        assert envelope.payload.item_id == sample_minimal_payload["payload"]["item_id"]
        assert envelope.payload.quantity_change == -1
        assert envelope.payload.reason is None
        assert envelope.payload.from_location_code is None
        assert envelope.payload.to_location_code is None
        assert envelope.payload.current_total_qty is None

    def test_null_optionals_parse(self, sample_null_optionals_payload):
        """Consumer should handle all optional fields being null."""
        envelope = EventEnvelope.model_validate(sample_null_optionals_payload)

        assert envelope.payload.reason is None
        assert envelope.payload.from_location_code is None
        assert envelope.payload.to_location_code is None
        assert envelope.payload.actor_id is None
        assert envelope.payload.current_total_qty is None
        assert envelope.payload.previous_total_qty is None

    def test_normalized_event_fields_correct(self, sample_full_payload):
        """NormalizedEvent should extract the correct subset of fields."""
        envelope = EventEnvelope.model_validate(sample_full_payload)

        normalized = NormalizedEvent(
            event_id=envelope.event_id,
            item_id=envelope.payload.item_id,
            quantity_change=envelope.payload.quantity_change,
            reason=envelope.payload.reason,
            at=envelope.payload.at,
            current_total_qty=envelope.payload.current_total_qty,
            previous_total_qty=envelope.payload.previous_total_qty,
        )

        assert normalized.item_id == sample_full_payload["payload"]["item_id"]
        assert normalized.quantity_change == -3
        assert normalized.reason == "sale"
        assert normalized.current_total_qty == 47
        assert normalized.previous_total_qty == 50

    def test_json_string_parses(self, sample_full_payload):
        """Consumer should parse from JSON string (simulates Kafka message)."""
        json_str = json.dumps(sample_full_payload)
        envelope = EventEnvelope.model_validate_json(json_str)

        assert envelope.event_id == sample_full_payload["event_id"]
        assert envelope.payload.quantity_change == -3

    def test_event_carried_inventory_fields(self):
        """Verify current_total_qty and previous_total_qty are parsed correctly.

        These fields enable event-carried state in the forecasting pipeline,
        avoiding a DB query for current inventory.
        """
        payload = {
            "event_id": str(uuid4()),
            "payload": {
                "item_id": str(uuid4()),
                "quantity_change": -5,
                "at": datetime.now(timezone.utc).isoformat(),
                "current_total_qty": 45,
                "previous_total_qty": 50,
            },
        }
        envelope = EventEnvelope.model_validate(payload)

        assert envelope.payload.current_total_qty == 45
        assert envelope.payload.previous_total_qty == 50


class TestConsumerRejectsMalformedPayloads:
    """Verify that the consumer correctly rejects invalid events."""

    def test_missing_item_id_rejected(self):
        """item_id is required -- consumer must reject its absence."""
        payload = {
            "event_id": str(uuid4()),
            "payload": {
                "quantity_change": -1,
                "at": datetime.now(timezone.utc).isoformat(),
            },
        }
        with pytest.raises(ValidationError) as exc_info:
            EventEnvelope.model_validate(payload)
        assert "item_id" in str(exc_info.value)

    def test_missing_quantity_change_rejected(self):
        """quantity_change is required -- consumer must reject its absence."""
        payload = {
            "event_id": str(uuid4()),
            "payload": {
                "item_id": str(uuid4()),
                "at": datetime.now(timezone.utc).isoformat(),
            },
        }
        with pytest.raises(ValidationError) as exc_info:
            EventEnvelope.model_validate(payload)
        assert "quantity_change" in str(exc_info.value)

    def test_missing_at_rejected(self):
        """at (timestamp) is required -- consumer must reject its absence."""
        payload = {
            "event_id": str(uuid4()),
            "payload": {
                "item_id": str(uuid4()),
                "quantity_change": -1,
            },
        }
        with pytest.raises(ValidationError) as exc_info:
            EventEnvelope.model_validate(payload)
        assert "at" in str(exc_info.value)

    def test_missing_event_id_rejected(self):
        """event_id is required at the envelope level."""
        payload = {
            "payload": {
                "item_id": str(uuid4()),
                "quantity_change": -1,
                "at": datetime.now(timezone.utc).isoformat(),
            },
        }
        with pytest.raises(ValidationError) as exc_info:
            EventEnvelope.model_validate(payload)
        assert "event_id" in str(exc_info.value)

    def test_non_integer_quantity_change_rejected(self):
        """quantity_change must be coercible to int."""
        payload = {
            "event_id": str(uuid4()),
            "payload": {
                "item_id": str(uuid4()),
                "quantity_change": "not_a_number",
                "at": datetime.now(timezone.utc).isoformat(),
            },
        }
        with pytest.raises(ValidationError):
            EventEnvelope.model_validate(payload)
