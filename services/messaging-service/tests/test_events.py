"""Tests for event models and datetime parsing."""

from datetime import datetime, timezone

import pytest

from src.events import EventEnvelope, EventPayload, _parse_datetime


class TestParseDatetime:
    """Tests for the _parse_datetime helper function."""

    def test_parse_iso8601_with_offset(self):
        """Should parse standard ISO 8601 format."""
        result = _parse_datetime("2026-03-02T23:17:20.516350+00:00")
        assert result.year == 2026
        assert result.month == 3
        assert result.day == 2
        assert result.hour == 23
        assert result.minute == 17
        assert result.second == 20
        assert result.tzinfo is not None

    def test_parse_postgresql_timestamptz(self):
        """Should parse PostgreSQL timestamptz format (the failing format from logs)."""
        result = _parse_datetime("2026-03-02 23:17:20.51635+00")
        assert result.year == 2026
        assert result.month == 3
        assert result.day == 2
        assert result.hour == 23
        assert result.minute == 17
        assert result.second == 20
        assert result.tzinfo is not None

    def test_parse_postgresql_with_negative_offset(self):
        """Should parse PostgreSQL format with negative timezone offset."""
        result = _parse_datetime("2026-03-02 15:30:00.123456-05")
        assert result.year == 2026
        assert result.hour == 15
        assert result.tzinfo is not None

    def test_parse_iso8601_with_z_suffix(self):
        """Should parse ISO 8601 with Z suffix."""
        result = _parse_datetime("2026-03-02T23:17:20Z")
        assert result.year == 2026
        assert result.tzinfo is not None

    def test_parse_datetime_object(self):
        """Should pass through datetime objects."""
        dt = datetime(2026, 3, 2, 12, 0, 0, tzinfo=timezone.utc)
        result = _parse_datetime(dt)
        assert result == dt

    def test_parse_naive_datetime_adds_utc(self):
        """Should add UTC timezone to naive datetime."""
        dt = datetime(2026, 3, 2, 12, 0, 0)
        result = _parse_datetime(dt)
        assert result.tzinfo == timezone.utc

    def test_parse_without_microseconds(self):
        """Should parse datetime without microseconds."""
        result = _parse_datetime("2026-03-02T23:17:20+00:00")
        assert result.second == 20
        assert result.microsecond == 0

    def test_parse_three_digit_milliseconds(self):
        """Should parse datetime with 3-digit milliseconds and pad to 6."""
        result = _parse_datetime("2026-03-02T23:17:20.516+00:00")
        assert result.microsecond == 516000  # 516 padded to 516000

    def test_parse_nanoseconds_truncated(self):
        """Should truncate nanoseconds (7+ digits) to microseconds."""
        result = _parse_datetime("2026-03-02T23:17:20.123456789+00:00")
        assert result.microsecond == 123456  # Truncated to 6 digits

    def test_parse_four_digit_timezone_offset(self):
        """Should parse 4-digit timezone offset without colon."""
        result = _parse_datetime("2026-03-02T15:30:00+0530")
        assert result.hour == 15
        assert result.utcoffset().total_seconds() == 5.5 * 3600

    def test_parse_invalid_format_raises(self):
        """Should raise ValueError for invalid format."""
        with pytest.raises(ValueError, match="Cannot parse datetime"):
            _parse_datetime("not-a-date")

    def test_parse_invalid_type_raises(self):
        """Should raise ValueError for invalid type."""
        with pytest.raises(ValueError, match="Expected str or datetime"):
            _parse_datetime(12345)


class TestEventPayload:
    """Tests for EventPayload datetime validation."""

    def test_payload_parses_iso8601(self):
        """Should parse ISO 8601 datetime in payload."""
        payload = EventPayload(
            item_id="abc-123",
            quantity_change=-1,
            reason="sale",
            at="2026-03-02T23:17:20.516350+00:00",
        )
        assert payload.at.year == 2026
        assert payload.at.tzinfo is not None

    def test_payload_parses_postgresql_format(self):
        """Should parse PostgreSQL timestamptz format in payload."""
        payload = EventPayload(
            item_id="abc-123",
            quantity_change=-1,
            reason="sale",
            at="2026-03-02 23:17:20.51635+00",
        )
        assert payload.at.year == 2026
        assert payload.at.tzinfo is not None


class TestEventEnvelope:
    """Tests for EventEnvelope datetime validation."""

    def test_envelope_parses_iso8601(self):
        """Should parse ISO 8601 datetime in envelope."""
        envelope = EventEnvelope(
            event_id="evt-123",
            topic="inventory-changes",
            event_type="CREATED",
            entity_type="stock_movement",
            entity_id="ent-456",
            payload={
                "item_id": "abc-123",
                "quantity_change": -1,
                "reason": "sale",
                "at": "2026-03-02T23:17:20+00:00",
            },
            created_at="2026-03-02T23:17:20.516350+00:00",
        )
        assert envelope.created_at.year == 2026
        assert envelope.payload.at.year == 2026

    def test_envelope_parses_postgresql_format(self):
        """Should parse PostgreSQL timestamptz format in both fields."""
        envelope = EventEnvelope(
            event_id="evt-123",
            topic="inventory-changes",
            event_type="CREATED",
            entity_type="stock_movement",
            entity_id="ent-456",
            payload={
                "item_id": "abc-123",
                "quantity_change": -1,
                "reason": "sale",
                "at": "2026-03-02 23:17:20.51635+00",
            },
            created_at="2026-03-02 23:17:20.51635+00",
        )
        assert envelope.created_at.year == 2026
        assert envelope.payload.at.year == 2026
        assert envelope.created_at.tzinfo is not None
        assert envelope.payload.at.tzinfo is not None
