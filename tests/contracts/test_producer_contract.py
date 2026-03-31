"""Contract tests: validate that the producer payload structure matches the shared schema.

These tests ensure that the event structure built by EventOutboxService (Java)
is faithfully represented in the JSON Schema contract. Any field added/removed
on the producer side must be reflected here.
"""

import jsonschema
import pytest


class TestProducerPayloadMatchesContract:
    """Validate producer-shaped payloads against the shared JSON Schema."""

    def test_full_payload_validates(self, event_envelope_schema, sample_full_payload):
        """A complete payload with all fields should pass validation."""
        jsonschema.validate(instance=sample_full_payload, schema=event_envelope_schema)

    def test_minimal_payload_validates(self, event_envelope_schema, sample_minimal_payload):
        """A payload with only required fields should pass validation."""
        jsonschema.validate(instance=sample_minimal_payload, schema=event_envelope_schema)

    def test_null_optionals_validate(self, event_envelope_schema, sample_null_optionals_payload):
        """A payload with all optional fields set to null should pass validation."""
        jsonschema.validate(
            instance=sample_null_optionals_payload, schema=event_envelope_schema
        )

    def test_missing_event_id_fails(self, event_envelope_schema, sample_full_payload):
        """event_id is required at the envelope level."""
        del sample_full_payload["event_id"]
        with pytest.raises(jsonschema.ValidationError):
            jsonschema.validate(instance=sample_full_payload, schema=event_envelope_schema)

    def test_missing_payload_fails(self, event_envelope_schema, sample_full_payload):
        """payload is required at the envelope level."""
        del sample_full_payload["payload"]
        with pytest.raises(jsonschema.ValidationError):
            jsonschema.validate(instance=sample_full_payload, schema=event_envelope_schema)

    def test_missing_item_id_in_payload_fails(self, event_envelope_schema, sample_full_payload):
        """item_id is required inside payload."""
        del sample_full_payload["payload"]["item_id"]
        with pytest.raises(jsonschema.ValidationError):
            jsonschema.validate(instance=sample_full_payload, schema=event_envelope_schema)

    def test_missing_quantity_change_in_payload_fails(
        self, event_envelope_schema, sample_full_payload
    ):
        """quantity_change is required inside payload."""
        del sample_full_payload["payload"]["quantity_change"]
        with pytest.raises(jsonschema.ValidationError):
            jsonschema.validate(instance=sample_full_payload, schema=event_envelope_schema)

    def test_missing_at_in_payload_fails(self, event_envelope_schema, sample_full_payload):
        """at (timestamp) is required inside payload."""
        del sample_full_payload["payload"]["at"]
        with pytest.raises(jsonschema.ValidationError):
            jsonschema.validate(instance=sample_full_payload, schema=event_envelope_schema)

    def test_quantity_change_must_be_integer(self, event_envelope_schema, sample_full_payload):
        """quantity_change must be an integer, not a string or float."""
        sample_full_payload["payload"]["quantity_change"] = "five"
        with pytest.raises(jsonschema.ValidationError):
            jsonschema.validate(instance=sample_full_payload, schema=event_envelope_schema)

    def test_unknown_payload_field_rejected(self, event_envelope_schema, sample_full_payload):
        """additionalProperties is false on payload -- unknown fields are rejected."""
        sample_full_payload["payload"]["unexpected_field"] = "oops"
        with pytest.raises(jsonschema.ValidationError):
            jsonschema.validate(instance=sample_full_payload, schema=event_envelope_schema)

    def test_all_producer_fields_present_in_schema(self, event_envelope_schema):
        """Verify every field that EventOutboxService produces is in the schema.

        This is the canonical list from EventOutboxService.createStockMovementEvent().
        If a field is added/removed in Java, this test must be updated.
        """
        expected_payload_fields = {
            "product_id",
            "product_name",
            "sku",
            "item_id",
            "quantity_change",
            "reason",
            "at",
            "from_location_code",
            "to_location_code",
            "previous_location_qty",
            "current_location_qty",
            "previous_total_qty",
            "current_total_qty",
            "reorder_point",
            "actor_id",
            "stock_movement_id",
        }
        schema_payload_fields = set(
            event_envelope_schema["properties"]["payload"]["properties"].keys()
        )
        assert expected_payload_fields == schema_payload_fields, (
            f"Schema drift detected.\n"
            f"  In producer but not schema: {expected_payload_fields - schema_payload_fields}\n"
            f"  In schema but not producer: {schema_payload_fields - expected_payload_fields}"
        )
