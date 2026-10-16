"""Review attribution is limited to active members of the review site, and alert dedupe is per site."""

from __future__ import annotations

import uuid
from datetime import date
from unittest.mock import MagicMock, patch

from src.adapters.supabase_repo import SupabaseRepo
from src.application.alert_checker import AlertResult, AlertType
from src.events import NormalizedEvent
from src.worker import MessagingWorker

_SITE_ID = "00000000-0000-0000-0000-0000000000ee"
_ITEM_ID = "00000000-0000-0000-0000-000000000001"


def _build_repo() -> tuple[SupabaseRepo, MagicMock]:
    conn = MagicMock()
    cm = MagicMock()
    cm.__enter__.return_value = conn
    cm.__exit__.return_value = False
    engine = MagicMock()
    engine.connect.return_value = cm
    return SupabaseRepo(engine=engine), conn


def test_employee_lookup_is_limited_to_active_members_of_the_site():
    repo, conn = _build_repo()
    conn.execute.return_value.fetchone.return_value = None

    repo.get_employee_id_by_name("Alice", site_id=_SITE_ID)

    query, params = conn.execute.call_args.args
    sql = str(query)
    assert "user_site_memberships" in sql
    assert "is_active" in sql
    assert params["site_id"] == uuid.UUID(_SITE_ID)


def test_create_review_looks_the_employee_up_within_the_site():
    repo, conn = _build_repo()
    conn.execute.return_value.fetchone.return_value = (uuid.uuid4(),)
    with patch.object(repo, "get_employee_id_by_name", return_value=None) as lookup:
        result = repo.create_review("Alice", "ext-1", date(2026, 9, 10), "t", 5, "C", site_id=_SITE_ID)

    lookup.assert_called_once_with("Alice", site_id=_SITE_ID)
    assert result is None


def test_daily_count_looks_the_employee_up_within_the_site():
    repo, _conn = _build_repo()
    with patch.object(repo, "get_employee_id_by_name", return_value=None) as lookup:
        ok = repo.increment_daily_count("Alice", date(2026, 9, 10), _SITE_ID)

    lookup.assert_called_once_with("Alice", site_id=_SITE_ID)
    assert ok is False


def _alert_key(site_id: str | None) -> str:
    worker = MessagingWorker.__new__(MessagingWorker)
    worker._repo = MagicMock()
    worker._build_alert_message = MagicMock(return_value="msg")
    event = NormalizedEvent(
        event_id=str(uuid.uuid4()), item_id=_ITEM_ID, quantity_change=-1, reason="SALE",
        at=MagicMock(), site_id=site_id,
    )
    alert = AlertResult(
        should_alert=True, alert_type=AlertType.TOTAL_OUT_OF_STOCK,
        previous_qty=1, current_qty=0, threshold=5,
    )
    worker._create_notification_for_alert(event, alert)
    return worker._repo.create_notification.call_args.kwargs["dedupe_key"]


def test_alert_dedupe_key_differs_between_sites():
    other = "00000000-0000-0000-0000-0000000000ff"
    assert _alert_key(_SITE_ID) != _alert_key(other)
    assert _SITE_ID in _alert_key(_SITE_ID)
