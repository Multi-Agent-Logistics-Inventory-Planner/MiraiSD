"""Event models for inventory changes."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

from pydantic import BaseModel, Field


class EventPayload(BaseModel):
    """Event payload model."""

    item_id: str = Field(..., description="Product/item UUID")
    quantity_change: int = Field(..., description="Change in quantity (can be negative)")
    reason: str = Field(..., description="Reason for change (sale, restock, etc.)")
    at: datetime = Field(..., description="Timestamp of the event")

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

    # Actor and reference
    actor_id: str | None = None
    stock_movement_id: str | None = None

