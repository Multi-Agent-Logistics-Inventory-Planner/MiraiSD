"""E2E tests: Inventory adjustment -> Kafka -> Forecasting -> Database.

These tests verify the full cross-service flow:
1. POST /adjust creates audit log, stock movement, and outbox record
2. Outbox publisher sends event to Kafka
3. Forecasting service consumes the event and runs the pipeline
4. Forecast prediction is written to the database

Prerequisites:
- Docker must be running
- Run via: cd tests/e2e && pytest -v --timeout=120
"""

from datetime import datetime, timezone
from uuid import uuid4

import pytest

from helpers.api_client import adjust_inventory, make_admin_token
from helpers.db_client import (
    count_audit_logs_after,
    count_forecasts_after,
    count_stock_movements_for_item,
    get_forecast_for_item,
    get_outbox_published_after,
)
from helpers.wait import wait_for_condition


class TestFullAuditToForecastFlow:
    """End-to-end tests for the inventory audit -> forecast pipeline."""

    def test_adjust_creates_audit_and_movement(
        self, db_engine, test_inventory_record, test_start_time
    ):
        """POST /adjust should create audit_log and stock_movement records immediately."""
        resp = adjust_inventory(
            location_type="BOX_BIN",
            inventory_id=test_inventory_record["inventory_id"],
            quantity_change=-1,
            reason="SALE",
        )

        assert resp.status_code == 201, f"Expected 201, got {resp.status_code}: {resp.text}"

        # Verify audit log was created
        audit_count = count_audit_logs_after(db_engine, test_start_time)
        assert audit_count >= 1, "Expected at least 1 audit log after adjustment"

        # Verify stock movement was created
        movement_count = count_stock_movements_for_item(
            db_engine, test_inventory_record["product_id"], test_start_time
        )
        assert movement_count >= 1, "Expected at least 1 stock movement after adjustment"

    def test_outbox_event_published_to_kafka(
        self, db_engine, test_inventory_record, test_start_time
    ):
        """After adjustment, outbox event should be published (marked with published_at)."""
        adjust_inventory(
            location_type="BOX_BIN",
            inventory_id=test_inventory_record["inventory_id"],
            quantity_change=-1,
            reason="SALE",
        )

        # Wait for outbox to be published (outbox polls every 10s)
        published = wait_for_condition(
            lambda: get_outbox_published_after(db_engine, test_start_time),
            timeout_seconds=25,
            poll_interval=1,
            description="outbox event published to Kafka",
        )

        assert len(published) >= 1
        event = published[0]
        assert event["topic"] == "inventory-changes"
        assert event["event_type"] == "CREATED"
        assert event["entity_type"] == "stock_movement"

        # Verify payload structure
        payload = event["payload"]
        assert payload["product_id"] == test_inventory_record["product_id"]
        assert payload["quantity_change"] == -1
        assert payload["reason"] == "sale"

    def test_full_flow_adjust_to_forecast(
        self, db_engine, test_inventory_record, test_start_time
    ):
        """Complete flow: adjustment -> Kafka -> forecasting -> forecast_predictions in DB.

        This is the primary E2E test that verifies the entire audit-to-forecast pipeline.
        """
        product_id = test_inventory_record["product_id"]
        original_qty = test_inventory_record["quantity"]

        # Make the adjustment
        resp = adjust_inventory(
            location_type="BOX_BIN",
            inventory_id=test_inventory_record["inventory_id"],
            quantity_change=-2,
            reason="SALE",
        )
        assert resp.status_code == 201

        # Phase 1: Verify outbox event is published (up to 20s, outbox polls every 10s)
        wait_for_condition(
            lambda: get_outbox_published_after(db_engine, test_start_time),
            timeout_seconds=20,
            poll_interval=1,
            description="outbox event published",
        )

        # Phase 2: Wait for forecast to appear in DB
        # Timing: outbox publish (0-10s) + Kafka delivery (~1s) + batch window (5s) + pipeline (~2s)
        forecast = wait_for_condition(
            lambda: get_forecast_for_item(db_engine, product_id, test_start_time),
            timeout_seconds=45,
            poll_interval=2,
            description="forecast prediction written to DB",
        )

        # Verify forecast structure
        assert forecast is not None
        assert forecast["item_id"] == product_id
        assert forecast["horizon_days"] is not None and forecast["horizon_days"] > 0

        # Verify confidence is in valid range
        if forecast["confidence"] is not None:
            assert 0.0 <= forecast["confidence"] <= 1.0

        # Verify features JSONB has expected keys
        features = forecast["features"]
        assert features is not None
        assert "mu_hat" in features
        assert "safety_stock" in features
        assert "reorder_point" in features
        assert "current_qty" in features

    def test_restock_adjustment_produces_forecast(
        self, db_engine, test_inventory_record, test_start_time
    ):
        """A restock (positive quantity) should also trigger a forecast update."""
        product_id = test_inventory_record["product_id"]

        resp = adjust_inventory(
            location_type="BOX_BIN",
            inventory_id=test_inventory_record["inventory_id"],
            quantity_change=5,
            reason="RESTOCK",
        )
        assert resp.status_code == 201

        # Wait for forecast to appear
        forecast = wait_for_condition(
            lambda: get_forecast_for_item(db_engine, product_id, test_start_time),
            timeout_seconds=45,
            poll_interval=2,
            description="forecast prediction after restock",
        )

        assert forecast is not None
        assert forecast["item_id"] == product_id


class TestErrorScenarios:
    """E2E tests for error paths -- verify no side effects on failure."""

    def test_unauthenticated_request_rejected(self, test_inventory_record):
        """Request without JWT token should be rejected with 401/403."""
        import requests

        resp = requests.post(
            f"http://localhost:4001/api/stock-movements/BOX_BIN/{test_inventory_record['inventory_id']}/adjust",
            json={"quantityChange": -1, "reason": "SALE"},
            headers={"Content-Type": "application/json"},
            timeout=10,
        )
        assert resp.status_code in (401, 403)

    def test_missing_required_fields_rejected(self, test_inventory_record):
        """Request missing required fields should return 400."""
        import requests

        token = make_admin_token()
        resp = requests.post(
            f"http://localhost:4001/api/stock-movements/BOX_BIN/{test_inventory_record['inventory_id']}/adjust",
            json={},  # Missing quantityChange and reason
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json",
            },
            timeout=10,
        )
        assert resp.status_code == 400
