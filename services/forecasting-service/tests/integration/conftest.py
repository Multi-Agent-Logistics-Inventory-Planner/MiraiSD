"""Fixtures for forecasting-service integration tests.

These tests verify the Kafka event -> EventAggregator -> ForecastingPipeline -> DB write
flow using mocked repository methods. When Docker is available, testcontainers can be
used for real Kafka and PostgreSQL.
"""

import sys
from datetime import datetime, timezone
from unittest.mock import MagicMock
from uuid import uuid4

import pandas as pd
import pytest

# Mock kafka module before any src imports (kafka-python may not be installed in test env)
sys.modules.setdefault("kafka", MagicMock())
sys.modules.setdefault("kafka.errors", MagicMock())


@pytest.fixture()
def item_id() -> str:
    return str(uuid4())


@pytest.fixture()
def mock_repo(item_id):
    """Create a mock SupabaseRepo that returns realistic DataFrames."""
    repo = MagicMock()

    # get_items returns item metadata
    repo.get_items.return_value = pd.DataFrame(
        {
            "item_id": [item_id],
            "name": ["Test Product"],
            "lead_time_days": [7],
            "safety_stock_days": [3],
        }
    )

    # get_shipment_lead_times returns empty (no shipment history)
    repo.get_shipment_lead_times.return_value = pd.DataFrame(
        columns=["item_id", "ordered_at", "received_at"]
    )

    # get_current_inventory returns current qty
    repo.get_current_inventory.return_value = pd.DataFrame(
        {
            "item_id": [item_id],
            "as_of_ts": [pd.Timestamp.now(tz="UTC")],
            "current_qty": [45],
        }
    )

    # get_stock_movements returns recent movements (14 days of sales)
    now = pd.Timestamp.now(tz="UTC")
    repo.get_stock_movements.return_value = pd.DataFrame(
        {
            "event_id": [str(uuid4()) for _ in range(14)],
            "item_id": [item_id] * 14,
            "quantity_change": [-3, -2, -1, -4, -2, -3, -1, -5, -2, -3, -1, -2, -4, -3],
            "reason": ["sale"] * 14,
            "at": pd.date_range(end=now, periods=14, freq="D", tz="UTC"),
        }
    )

    # get_historical_forecasts returns empty (no prior forecasts)
    repo.get_historical_forecasts.return_value = pd.DataFrame(
        columns=[
            "item_id",
            "computed_at",
            "avg_daily_delta",
            "days_to_stockout",
        ]
    )

    # upsert_forecasts is a no-op (we verify it was called with correct args)
    repo.upsert_forecasts.return_value = None

    return repo
