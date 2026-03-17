"""Event models for inventory changes."""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import datetime, timezone

from pydantic import BaseModel, Field, field_validator


def _parse_datetime(value: str | datetime) -> datetime:
    """Parse datetime from various formats including PostgreSQL timestamptz.

    Handles:
    - ISO 8601: '2026-03-02T23:17:20.516350+00:00'
    - PostgreSQL: '2026-03-02 23:17:20.51635+00'
    - With/without microseconds
    - Various timezone offset formats (+00, +00:00, Z)
    """
    if isinstance(value, datetime):
        if value.tzinfo is None:
            return value.replace(tzinfo=timezone.utc)
        return value

    if not isinstance(value, str):
        raise ValueError(f"Expected str or datetime, got {type(value)}")

    # Normalize PostgreSQL format to ISO 8601
    # Replace space with 'T' between date and time
    normalized = re.sub(r"(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2})", r"\1T\2", value)

    # Normalize microseconds to exactly 6 digits (Python requires 3 or 6)
    # PostgreSQL may produce 1-6 digits, other systems may send nanoseconds (7+)
    normalized = re.sub(r"\.(\d+)([+-]|Z|$)", _normalize_microseconds, normalized)

    # Normalize timezone: +00 -> +00:00, -05 -> -05:00
    # Match timezone at end that doesn't have colon (e.g., +00, -0530)
    normalized = re.sub(r"([+-])(\d{2})(\d{2})?$", _normalize_tz_offset, normalized)

    # Handle 'Z' suffix
    if normalized.endswith("Z"):
        normalized = normalized[:-1] + "+00:00"

    try:
        dt = datetime.fromisoformat(normalized)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt
    except ValueError as e:
        raise ValueError(f"Cannot parse datetime '{value}': {e}") from e


def _normalize_microseconds(match: re.Match) -> str:
    """Normalize microseconds to exactly 6 digits (truncate or pad)."""
    microseconds = match.group(1)
    suffix = match.group(2)
    # Truncate if longer than 6, pad if shorter
    if len(microseconds) > 6:
        normalized = microseconds[:6]
    else:
        normalized = microseconds.ljust(6, "0")
    return f".{normalized}{suffix}"


def _normalize_tz_offset(match: re.Match) -> str:
    """Normalize timezone offset to +HH:MM format."""
    sign = match.group(1)
    hours = match.group(2)
    minutes = match.group(3) or "00"
    return f"{sign}{hours}:{minutes}"


class EventPayload(BaseModel):
    """Event payload model."""

    item_id: str = Field(..., description="Product/item UUID")
    quantity_change: int = Field(..., description="Change in quantity (can be negative)")
    reason: str = Field(..., description="Reason for change (sale, restock, etc.)")
    at: datetime = Field(..., description="Timestamp of the event")

    @field_validator("at", mode="before")
    @classmethod
    def validate_at(cls, v: str | datetime) -> datetime:
        """Parse datetime from ISO 8601 or PostgreSQL timestamptz format."""
        return _parse_datetime(v)

    # Location codes (e.g., "B1", "S2", "D1", "K1", "C1", "R3", etc.)
    to_location_code: str | None = Field(None, description="Target location code")
    from_location_code: str | None = Field(None, description="Source location code")

    # Location-level quantities for crossing logic
    previous_location_qty: int | None = Field(None, description="Quantity at location before change")
    current_location_qty: int | None = Field(None, description="Quantity at location after change")

    # Total-level quantities for crossing logic
    previous_total_qty: int | None = Field(None, description="Total inventory before change")
    current_total_qty: int | None = Field(None, description="Total inventory after change")

    # Product config
    reorder_point: int | None = Field(None, description="Product reorder threshold")
    sku: str | None = Field(None, description="Product SKU")
    product_id: str | None = Field(None, description="Product UUID (alias for item_id)")
    product_name: str | None = Field(None, description="Product name")

    # Actor and reference
    actor_id: str | None = Field(None, description="User who triggered the change")
    stock_movement_id: str | None = Field(None, description="Stock movement record ID")


class EventEnvelope(BaseModel):
    """Kafka event envelope model."""

    event_id: str = Field(..., description="Unique event ID")
    topic: str = Field(..., description="Kafka topic")
    event_type: str = Field(..., description="Event type (CREATED, UPDATED)")
    entity_type: str = Field(..., description="Entity type (stock_movement)")
    entity_id: str = Field(..., description="Entity UUID")
    payload: EventPayload = Field(..., description="Event payload")
    created_at: datetime = Field(..., description="Event creation timestamp")

    @field_validator("created_at", mode="before")
    @classmethod
    def validate_created_at(cls, v: str | datetime) -> datetime:
        """Parse datetime from ISO 8601 or PostgreSQL timestamptz format."""
        return _parse_datetime(v)


@dataclass
class NormalizedEvent:
    """Normalized event for internal processing."""

    event_id: str
    item_id: str
    quantity_change: int
    reason: str
    at: datetime

    # Location codes
    to_location_code: str | None = None
    from_location_code: str | None = None

    # Location-level quantities for crossing logic
    previous_location_qty: int | None = None
    current_location_qty: int | None = None

    # Total-level quantities for crossing logic
    previous_total_qty: int | None = None
    current_total_qty: int | None = None

    # Product config
    reorder_point: int | None = None
    sku: str | None = None
    product_name: str | None = None

    # Actor and reference
    actor_id: str | None = None
    stock_movement_id: str | None = None


