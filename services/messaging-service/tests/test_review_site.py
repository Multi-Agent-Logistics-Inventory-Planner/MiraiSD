"""Tests for review-coin site-of-action plumbing (.specs/phase-7-kuji-lootbox).

Covers three halves:
  - SupabaseRepo: get_review_site_id, increment_daily_count(site_id=).
  - ReviewFetcher: site resolved once per batch and stamped on each Kafka event.
  - ReviewProcessor: site_id parsed off the Kafka payload, with a DB fallback
    for events queued before the field existed.
"""

from __future__ import annotations

from datetime import date
from unittest.mock import MagicMock, patch

import pytest

from src.adapters.supabase_repo import SupabaseRepo
from src.application.review_fetcher import ReviewFetcher
from src.application.review_processor import ReviewProcessor

_MAIN_SITE_ID = "00000000-0000-0000-0000-0000000000aa"


def _build_repo() -> tuple[SupabaseRepo, MagicMock, MagicMock]:
    """Build a SupabaseRepo wired to a stubbed engine/connection."""
    conn = MagicMock()
    cm = MagicMock()
    cm.__enter__.return_value = conn
    cm.__exit__.return_value = False
    engine = MagicMock()
    engine.connect.return_value = cm
    repo = SupabaseRepo(engine=engine)
    return repo, engine, conn


# -----------------------------------------------------------------------------
# SupabaseRepo
# -----------------------------------------------------------------------------


def test_get_review_site_id_resolves_configured_code():
    repo, _engine, conn = _build_repo()
    row = MagicMock()
    row.id = _MAIN_SITE_ID
    conn.execute.return_value.fetchone.return_value = row

    with patch("src.adapters.supabase_repo.config.REVIEW_SITE_CODE", "MAIN"):
        site_id = repo.get_review_site_id()

    assert site_id == _MAIN_SITE_ID
    _query, params = conn.execute.call_args.args
    assert params["code"] == "MAIN"


def test_get_review_site_id_raises_when_code_unmatched():
    repo, _engine, conn = _build_repo()
    conn.execute.return_value.fetchone.return_value = None

    with patch("src.adapters.supabase_repo.config.REVIEW_SITE_CODE", "GHOST"):
        with pytest.raises(ValueError, match="GHOST"):
            repo.get_review_site_id()


def test_increment_daily_count_sends_site_id():
    repo, _engine, conn = _build_repo()
    with patch.object(repo, "get_employee_id_by_name", return_value="00000000-0000-0000-0000-000000000001"):
        ok = repo.increment_daily_count("Alice", date(2026, 5, 20), _MAIN_SITE_ID)
    assert ok is True
    _query, params = conn.execute.call_args.args
    assert params["site_id"] == __import__("uuid").UUID(_MAIN_SITE_ID)


# -----------------------------------------------------------------------------
# ReviewFetcher: site read once per batch
# -----------------------------------------------------------------------------


class _StubReview:
    """Minimal stand-in for Apify Review objects."""

    def __init__(self, external_id: str, text: str, rating: int, reviewer_name: str, published_at: str):
        self.external_id = external_id
        self.text = text
        self.rating = rating
        self.reviewer_name = reviewer_name
        self.published_at = published_at


def test_daily_fetch_reads_site_once_per_run():
    apify = MagicMock()
    producer = MagicMock()
    slack = MagicMock()
    repo = MagicMock()

    repo.get_review_coin_rate.return_value = 1
    # Simulate a config change mid-run: only the first read matters.
    repo.get_review_site_id.side_effect = [_MAIN_SITE_ID, "should-not-be-used"]

    target = date(2026, 5, 20)
    reviews = [
        _StubReview("e1", "Loved Alice!", 5, "Reviewer One", f"{target.isoformat()}T10:00:00"),
        _StubReview("e2", "Alice is the best", 5, "Reviewer Two", f"{target.isoformat()}T11:00:00"),
    ]
    apify.fetch_reviews.return_value = reviews
    producer.publish_batch.return_value = ["evt-1", "evt-2"]

    fetcher = ReviewFetcher(apify_client=apify, kafka_producer=producer, slack_notifier=slack, repo=repo)
    count = fetcher.fetch_and_publish_daily(target_date=target)

    assert count == 2
    assert repo.get_review_site_id.call_count == 1

    published = producer.publish_batch.call_args.args[0]
    assert [p["site_id"] for p in published] == [_MAIN_SITE_ID, _MAIN_SITE_ID]


def test_daily_fetch_uses_explicit_site_override():
    apify = MagicMock()
    producer = MagicMock()
    slack = MagicMock()
    repo = MagicMock()
    repo.get_review_coin_rate.return_value = 1
    repo.get_review_site_id.return_value = _MAIN_SITE_ID  # should NOT be consulted

    target = date(2026, 5, 20)
    apify.fetch_reviews.return_value = [
        _StubReview("e1", "Alice ftw", 5, "Reviewer", f"{target.isoformat()}T10:00:00"),
    ]
    producer.publish_batch.return_value = ["evt-1"]

    other_site = "00000000-0000-0000-0000-0000000000bb"
    fetcher = ReviewFetcher(apify_client=apify, kafka_producer=producer, slack_notifier=slack, repo=repo)
    fetcher.fetch_and_publish_daily(target_date=target, site_id=other_site)

    repo.get_review_site_id.assert_not_called()
    published = producer.publish_batch.call_args.args[0]
    assert published[0]["site_id"] == other_site


# -----------------------------------------------------------------------------
# ReviewProcessor: site_id parsed off the Kafka payload, with a DB fallback
# -----------------------------------------------------------------------------


def _repo_with_alice(site_id: str = _MAIN_SITE_ID) -> MagicMock:
    repo = MagicMock()
    repo.get_review_employees.return_value = [
        {
            "id": "00000000-0000-0000-0000-000000000001",
            "canonical_name": "Alice",
            "name_variants": ["alice"],
            "is_active": True,
        }
    ]
    repo.create_review.return_value = "00000000-0000-0000-0000-000000000abc"
    repo.get_review_site_id.return_value = site_id
    return repo


def test_kafka_message_carries_site_id_through_to_increment():
    repo = _repo_with_alice()
    processor = ReviewProcessor(repo=repo)
    event = ReviewProcessor.parse_kafka_message({
        "event_id": "evt-1",
        "payload": {
            "external_id": "ext-1",
            "review_text": "Alice was amazing",
            "rating": 5,
            "reviewer_name": "Customer",
            "published_at": "2026-05-20T10:00:00",
            "coin_rate": 1,
            "site_id": _MAIN_SITE_ID,
        },
    })
    assert event is not None
    assert event.site_id == _MAIN_SITE_ID

    processor.process_review(event)

    repo.increment_daily_count.assert_called_once()
    _name, _date, site_id = repo.increment_daily_count.call_args.args
    assert site_id == _MAIN_SITE_ID
    repo.get_review_site_id.assert_not_called()


def test_kafka_message_missing_site_id_falls_back_to_repo_lookup():
    repo = _repo_with_alice(site_id=_MAIN_SITE_ID)
    processor = ReviewProcessor(repo=repo)
    event = ReviewProcessor.parse_kafka_message({
        "event_id": "evt-1",
        "payload": {
            "external_id": "ext-1",
            "review_text": "Alice was amazing",
            "rating": 5,
            "reviewer_name": "Customer",
            "published_at": "2026-05-20T10:00:00",
            # No site_id field — simulates an event queued before this rolled out.
        },
    })
    assert event is not None
    assert event.site_id is None

    processor.process_review(event)

    repo.get_review_site_id.assert_called_once()
    _name, _date, site_id = repo.increment_daily_count.call_args.args
    assert site_id == _MAIN_SITE_ID
