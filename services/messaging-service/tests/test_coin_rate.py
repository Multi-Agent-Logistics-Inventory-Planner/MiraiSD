"""Tests for Phase 3 review-to-coin rate plumbing.

Covers two halves:
  - SupabaseRepo: get_review_coin_rate, increment_daily_count(rate=).
  - ReviewFetcher: rate snapshotted once per batch and stamped on each Kafka event.
"""

from __future__ import annotations

from datetime import date
from unittest.mock import MagicMock, patch

from src.adapters.supabase_repo import SupabaseRepo
from src.application.review_fetcher import ReviewFetcher
from src.application.review_processor import ReviewProcessor


def _build_repo() -> tuple[SupabaseRepo, MagicMock, MagicMock]:
    """Build a SupabaseRepo wired to a stubbed engine/connection."""
    conn = MagicMock()
    # Make `with engine.connect()` yield our conn.
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


def test_get_review_coin_rate_returns_singleton():
    repo, _engine, conn = _build_repo()
    row = MagicMock()
    row.review_coin_rate = 5
    conn.execute.return_value.fetchone.return_value = row

    assert repo.get_review_coin_rate() == 5
    assert conn.execute.call_count == 1


def test_get_review_coin_rate_defaults_to_1_when_row_missing():
    repo, _engine, conn = _build_repo()
    conn.execute.return_value.fetchone.return_value = None

    # Defensive default — the V44 migration inserts the row, but a missing row
    # should not crash review processing.
    assert repo.get_review_coin_rate() == 1


def test_increment_daily_count_default_rate_is_1():
    repo, _engine, conn = _build_repo()
    with patch.object(repo, "get_employee_id_by_name", return_value="00000000-0000-0000-0000-000000000001"):
        ok = repo.increment_daily_count("Alice", date(2026, 5, 20))
    assert ok is True
    _query, params = conn.execute.call_args.args
    assert params["rate"] == 1


def test_increment_daily_count_applies_rate():
    repo, _engine, conn = _build_repo()
    with patch.object(repo, "get_employee_id_by_name", return_value="00000000-0000-0000-0000-000000000001"):
        ok = repo.increment_daily_count("Alice", date(2026, 5, 20), rate=3)
    assert ok is True
    _query, params = conn.execute.call_args.args
    assert params["rate"] == 3


# -----------------------------------------------------------------------------
# ReviewFetcher: rate read once per batch
# -----------------------------------------------------------------------------


class _StubReview:
    """Minimal stand-in for Apify Review objects."""

    def __init__(self, external_id: str, text: str, rating: int, reviewer_name: str, published_at: str):
        self.external_id = external_id
        self.text = text
        self.rating = rating
        self.reviewer_name = reviewer_name
        self.published_at = published_at


def test_daily_fetch_reads_rate_once_per_run():
    apify = MagicMock()
    producer = MagicMock()
    slack = MagicMock()
    repo = MagicMock()

    # Simulate a rate change mid-run: only the first read matters.
    repo.get_review_coin_rate.side_effect = [4, 99]

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
    # Snapshot read exactly once even though side_effect has more values queued.
    assert repo.get_review_coin_rate.call_count == 1

    published = producer.publish_batch.call_args.args[0]
    assert [p["coin_rate"] for p in published] == [4, 4]


def test_daily_fetch_uses_explicit_rate_override():
    apify = MagicMock()
    producer = MagicMock()
    slack = MagicMock()
    repo = MagicMock()
    repo.get_review_coin_rate.return_value = 1  # should NOT be consulted

    target = date(2026, 5, 20)
    apify.fetch_reviews.return_value = [
        _StubReview("e1", "Alice ftw", 5, "Reviewer", f"{target.isoformat()}T10:00:00"),
    ]
    producer.publish_batch.return_value = ["evt-1"]

    fetcher = ReviewFetcher(apify_client=apify, kafka_producer=producer, slack_notifier=slack, repo=repo)
    fetcher.fetch_and_publish_daily(target_date=target, coin_rate=7)

    repo.get_review_coin_rate.assert_not_called()
    published = producer.publish_batch.call_args.args[0]
    assert published[0]["coin_rate"] == 7


# -----------------------------------------------------------------------------
# ReviewProcessor: coin_rate parsed off the Kafka payload
# -----------------------------------------------------------------------------


def test_kafka_message_carries_coin_rate_through_to_increment():
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

    processor = ReviewProcessor(repo=repo)
    event = ReviewProcessor.parse_kafka_message({
        "event_id": "evt-1",
        "payload": {
            "external_id": "ext-1",
            "review_text": "Alice was amazing",
            "rating": 5,
            "reviewer_name": "Customer",
            "published_at": "2026-05-20T10:00:00",
            "coin_rate": 4,
        },
    })
    assert event is not None
    assert event.coin_rate == 4

    processor.process_review(event)

    repo.increment_daily_count.assert_called_once()
    _name, _date = repo.increment_daily_count.call_args.args
    assert repo.increment_daily_count.call_args.kwargs["rate"] == 4


def test_kafka_message_defaults_coin_rate_to_1_when_missing():
    event = ReviewProcessor.parse_kafka_message({
        "event_id": "evt-1",
        "payload": {
            "external_id": "ext-1",
            "review_text": "no rate field here",
            "rating": 5,
            "reviewer_name": "Customer",
            "published_at": "2026-05-20T10:00:00",
        },
    })
    assert event is not None
    assert event.coin_rate == 1
