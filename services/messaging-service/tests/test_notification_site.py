"""Tests for notification site stamping (.specs/phase-7-notifications-reviews-audit)."""

from __future__ import annotations

import uuid
from unittest.mock import MagicMock

from src.adapters.supabase_repo import SupabaseRepo
from src.application.alert_checker import AlertResult, AlertType
from src.events import EventEnvelope, NormalizedEvent
from src.worker import MessagingWorker

_SITE_ID = "00000000-0000-0000-0000-0000000000bb"
_ITEM_ID = "00000000-0000-0000-0000-000000000001"


def _build_repo() -> tuple[SupabaseRepo, MagicMock]:
    conn = MagicMock()
    cm = MagicMock()
    cm.__enter__.return_value = conn
    cm.__exit__.return_value = False
    engine = MagicMock()
    engine.connect.return_value = cm
    return SupabaseRepo(engine=engine), conn


def _envelope(site_id: str | None) -> dict:
    body = {
        "event_id": "evt-1",
        "topic": "inventory-changes",
        "event_type": "CREATED",
        "entity_type": "stock_movement",
        "entity_id": _ITEM_ID,
        "created_at": "2026-10-15T12:00:00Z",
        "payload": {
            "item_id": _ITEM_ID,
            "quantity_change": -1,
            "reason": "SALE",
            "at": "2026-10-15T12:00:00Z",
        },
    }
    if site_id is not None:
        body["site_id"] = site_id
    return body


def test_envelope_keeps_site_id():
    assert EventEnvelope(**_envelope(_SITE_ID)).site_id == _SITE_ID


def test_envelope_without_site_id_still_parses():
    assert EventEnvelope(**_envelope(None)).site_id is None


def test_create_notification_writes_site_id_with_dedupe_key():
    repo, conn = _build_repo()
    conn.execute.return_value.fetchone.return_value = (uuid.uuid4(),)

    repo.create_notification(
        notification_type="OUT_OF_STOCK",
        severity="CRITICAL",
        message="m",
        item_id=_ITEM_ID,
        dedupe_key="k",
        site_id=_SITE_ID,
    )

    query, params = conn.execute.call_args.args
    assert "site_id" in str(query)
    assert params["site_id"] == uuid.UUID(_SITE_ID)


def test_create_notification_writes_site_id_without_dedupe_key():
    repo, conn = _build_repo()
    conn.execute.return_value.fetchone.return_value = (uuid.uuid4(),)

    repo.create_notification(
        notification_type="OUT_OF_STOCK",
        severity="CRITICAL",
        message="m",
        item_id=_ITEM_ID,
        site_id=_SITE_ID,
    )

    query, params = conn.execute.call_args.args
    assert "site_id" in str(query)
    assert params["site_id"] == uuid.UUID(_SITE_ID)


def test_create_notification_without_site_stays_null():
    repo, conn = _build_repo()
    conn.execute.return_value.fetchone.return_value = (uuid.uuid4(),)

    repo.create_notification(
        notification_type="OUT_OF_STOCK", severity="CRITICAL", message="m", item_id=_ITEM_ID
    )

    _query, params = conn.execute.call_args.args
    assert params["site_id"] is None


def test_alert_notification_carries_the_events_site():
    worker = MessagingWorker.__new__(MessagingWorker)
    worker._repo = MagicMock()
    worker._build_alert_message = MagicMock(return_value="msg")
    event = NormalizedEvent(
        event_id=str(uuid.uuid4()),
        item_id=_ITEM_ID,
        quantity_change=-1,
        reason="SALE",
        at=MagicMock(),
        site_id=_SITE_ID,
    )
    alert = AlertResult(
        should_alert=True,
        alert_type=AlertType.TOTAL_OUT_OF_STOCK,
        previous_qty=1,
        current_qty=0,
        threshold=5,
        location_code=None,
    )

    worker._create_notification_for_alert(event, alert)

    assert worker._repo.create_notification.call_args.kwargs["site_id"] == _SITE_ID
