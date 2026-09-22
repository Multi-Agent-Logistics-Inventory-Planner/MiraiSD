"""Contract tests: prove both consumers parse an envelope carrying the AC-4 fields.

.specs/phase-6-inventory T-6c-9. F-6c-7 assumed Pydantic v2's default extra="ignore" behavior
would let forecasting-service and messaging-service tolerate the new site_id/event_version/
causation_id/idempotency_key/correlation_id fields T-6c-8 started publishing, without ever running
either model against a payload that actually carries them. This turns that assumption into an
executed assertion for both real consumer models (not a re-implementation of them).
"""

import json

import events as forecasting_events
import messaging_events


class TestForecastingServiceEnvelopeCompatibility:
    """src/events.py's EventEnvelope (forecasting-service)."""

    def test_parses_envelope_with_new_fields(self, sample_full_payload):
        envelope = forecasting_events.EventEnvelope.model_validate(sample_full_payload)
        assert envelope.correlation_id == sample_full_payload["correlation_id"]

    def test_ignores_unknown_extra_fields_it_does_not_declare(self, sample_full_payload):
        """site_id/event_version/causation_id/idempotency_key aren't modeled here (this service
        never reads them) -- Pydantic's default extra="ignore" must not raise.
        """
        payload = dict(sample_full_payload)
        assert "site_id" not in forecasting_events.EventEnvelope.model_fields
        envelope = forecasting_events.EventEnvelope.model_validate(payload)
        assert envelope.event_id == payload["event_id"]

    def test_parses_from_json_string_with_new_fields(self, sample_full_payload):
        """model_validate_json is the real parse path (_parse_line uses it), not model_validate."""
        line = json.dumps(sample_full_payload)
        envelope = forecasting_events.EventEnvelope.model_validate_json(line)
        assert envelope.correlation_id == sample_full_payload["correlation_id"]

    def test_parses_envelope_missing_new_fields(self, sample_minimal_payload):
        """An old, not-yet-upgraded producer's row (T-6c-7's compatibility case) still parses."""
        envelope = forecasting_events.EventEnvelope.model_validate(sample_minimal_payload)
        assert envelope.correlation_id is None


class TestMessagingServiceEnvelopeCompatibility:
    """src/events.py's EventEnvelope (messaging-service)."""

    def test_parses_envelope_with_new_fields(self, sample_full_payload):
        envelope = messaging_events.EventEnvelope.model_validate(sample_full_payload)
        assert envelope.correlation_id == sample_full_payload["correlation_id"]

    def test_ignores_unknown_extra_fields_it_does_not_declare(self, sample_full_payload):
        assert "site_id" not in messaging_events.EventEnvelope.model_fields
        envelope = messaging_events.EventEnvelope.model_validate(sample_full_payload)
        assert envelope.event_id == sample_full_payload["event_id"]

    def test_parses_from_json_string_with_new_fields(self, sample_full_payload):
        line = json.dumps(sample_full_payload)
        envelope = messaging_events.EventEnvelope.model_validate_json(line)
        assert envelope.correlation_id == sample_full_payload["correlation_id"]
