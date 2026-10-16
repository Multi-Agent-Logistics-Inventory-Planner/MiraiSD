"""Tests for the stale-display query and its notification site (.specs/phase-7-notifications-reviews-audit)."""

from __future__ import annotations

import uuid
from datetime import UTC, datetime
from types import SimpleNamespace
from unittest.mock import MagicMock

from src import scheduler
from src.adapters.supabase_repo import SupabaseRepo

_SITE_ID = "00000000-0000-0000-0000-0000000000cc"


def _build_repo() -> tuple[SupabaseRepo, MagicMock]:
    conn = MagicMock()
    cm = MagicMock()
    cm.__enter__.return_value = conn
    cm.__exit__.return_value = False
    engine = MagicMock()
    engine.connect.return_value = cm
    return SupabaseRepo(engine=engine), conn


def test_stale_display_query_reads_the_real_table_and_site():
    repo, conn = _build_repo()
    conn.execute.return_value.fetchall.return_value = []

    repo.get_stale_displays()

    sql = str(conn.execute.call_args.args[0])
    assert "FROM machine_display md" in sql
    assert "machine_displays" not in sql
    assert "md.site_id" in sql


def test_stale_display_rows_include_site_id():
    repo, conn = _build_repo()
    row = SimpleNamespace(
        id=uuid.uuid4(),
        machine_id=uuid.uuid4(),
        product_id=uuid.uuid4(),
        location_type="SINGLE_CLAW_MACHINE",
        product_name="Sonny",
        product_sku="S1",
        started_at=datetime(2026, 8, 1, tzinfo=UTC),
        days_active=60,
        site_id=uuid.UUID(_SITE_ID),
    )
    conn.execute.return_value.fetchall.return_value = [row]

    displays = repo.get_stale_displays()

    assert displays[0]["site_id"] == _SITE_ID


def test_stale_display_notification_carries_site():
    repo, _conn = _build_repo()
    repo.create_notification = MagicMock(return_value="n1")

    repo.create_stale_display_notification(
        product_id=str(uuid.uuid4()),
        product_name="Sonny",
        machine_id=str(uuid.uuid4()),
        location_type="SINGLE_CLAW_MACHINE",
        days_active=60,
        site_id=_SITE_ID,
    )

    assert repo.create_notification.call_args.kwargs["site_id"] == _SITE_ID


def test_daily_job_passes_each_displays_site(monkeypatch):
    repo = MagicMock()
    repo.get_stale_displays.return_value = [
        {
            "product_id": "p",
            "product_name": "Sonny",
            "machine_id": "m",
            "location_type": "SINGLE_CLAW_MACHINE",
            "days_active": 60,
            "site_id": _SITE_ID,
        }
    ]
    repo.create_stale_display_notification.return_value = "n1"
    monkeypatch.setattr(scheduler, "_get_shared_repo", lambda: repo)

    scheduler.daily_stale_display_check_job()

    assert repo.create_stale_display_notification.call_args.kwargs["site_id"] == _SITE_ID
