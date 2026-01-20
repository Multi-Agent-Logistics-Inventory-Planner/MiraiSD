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

