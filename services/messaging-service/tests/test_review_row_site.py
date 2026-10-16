"""Tests for stamping the site on review rows (.specs/phase-7-notifications-reviews-audit)."""

from __future__ import annotations

import uuid
from datetime import date
from unittest.mock import MagicMock, patch

from src.adapters.supabase_repo import SupabaseRepo
from src.application.review_processor import ReviewProcessor

_SITE_ID = "00000000-0000-0000-0000-0000000000dd"
_USER_ID = "00000000-0000-0000-0000-000000000001"


def _build_repo() -> tuple[SupabaseRepo, MagicMock]:
    conn = MagicMock()
    cm = MagicMock()
    cm.__enter__.return_value = conn
    cm.__exit__.return_value = False
    engine = MagicMock()
    engine.connect.return_value = cm
    return SupabaseRepo(engine=engine), conn


def test_create_review_writes_the_site():
    repo, conn = _build_repo()
    conn.execute.return_value.fetchone.return_value = (uuid.uuid4(),)
    with patch.object(repo, "get_employee_id_by_name", return_value=_USER_ID):
        repo.create_review(
            employee_name="Alice",
            external_id="ext-1",
            review_date=date(2026, 9, 10),
            review_text="great",
            rating=5,
            reviewer_name="Customer",
            site_id=_SITE_ID,
        )

    query, params = conn.execute.call_args.args
    assert "site_id" in str(query)
    assert params["site_id"] == uuid.UUID(_SITE_ID)


def test_create_review_without_a_site_stays_null():
    repo, conn = _build_repo()
    conn.execute.return_value.fetchone.return_value = (uuid.uuid4(),)
    with patch.object(repo, "get_employee_id_by_name", return_value=_USER_ID):
        repo.create_review("Alice", "ext-1", date(2026, 9, 10), "great", 5, "Customer")

    _query, params = conn.execute.call_args.args
    assert params["site_id"] is None


def _processor(site_lookup: str = _SITE_ID) -> tuple[ReviewProcessor, MagicMock]:
    repo = MagicMock()
    repo.get_review_employees.return_value = [
        {"id": _USER_ID, "canonical_name": "Alice", "name_variants": ["alice"], "is_active": True}
    ]
    repo.create_review.return_value = str(uuid.uuid4())
    repo.get_review_site_id.return_value = site_lookup
    return ReviewProcessor(repo=repo), repo


def _event(site_id: str | None) -> dict:
    payload = {
        "external_id": "ext-1",
        "review_text": "Alice was amazing",
        "rating": 5,
        "reviewer_name": "Customer",
        "published_at": "2026-05-20T10:00:00",
        "coin_rate": 1,
    }
    if site_id is not None:
        payload["site_id"] = site_id
    return {"event_id": "evt-1", "payload": payload}


def test_processor_passes_the_events_site_to_create_review():
    processor, repo = _processor()
    event = ReviewProcessor.parse_kafka_message(_event(_SITE_ID))

    processor.process_review(event)

    assert repo.create_review.call_args.kwargs["site_id"] == _SITE_ID
    repo.get_review_site_id.assert_not_called()


def test_processor_resolves_a_missing_site_once_for_both_writes():
    processor, repo = _processor(site_lookup=_SITE_ID)
    event = ReviewProcessor.parse_kafka_message(_event(None))

    processor.process_review(event)

    repo.get_review_site_id.assert_called_once()
    assert repo.create_review.call_args.kwargs["site_id"] == _SITE_ID
    assert repo.increment_daily_count.call_args.args[2] == _SITE_ID
