"""Review fetcher - scheduled job to fetch reviews from Apify and publish to Kafka."""

from __future__ import annotations

import logging
from datetime import date, datetime

from ..adapters.apify_client import ApifyClient, ApifyClientError
from ..adapters.kafka_producer import ReviewKafkaProducer
from ..adapters.slack_notifier import SlackNotifier
from ..adapters.supabase_repo import SupabaseRepo
from .. import config

logger = logging.getLogger(__name__)


class ReviewFetcher:
    """Fetches reviews from Apify and publishes them to Kafka.

    This is used by the scheduler to run daily review fetches.
    """

    def __init__(
        self,
        apify_client: ApifyClient | None = None,
        kafka_producer: ReviewKafkaProducer | None = None,
        slack_notifier: SlackNotifier | None = None,
        repo: SupabaseRepo | None = None,
    ):
        self._apify = apify_client or ApifyClient()
        self._producer = kafka_producer or ReviewKafkaProducer()
        self._slack = slack_notifier or SlackNotifier()
        self._repo = repo or SupabaseRepo()

    def fetch_and_publish_daily(
        self,
        target_date: date | None = None,
        prefetched_reviews: list | None = None,
        coin_rate: int | None = None,
    ) -> int:
        """Fetch today's reviews from Apify and publish to Kafka.

        Args:
            target_date: Date to filter reviews for. Defaults to today.
            prefetched_reviews: Optional pre-fetched reviews to filter instead of
                calling Apify. Use this when processing multiple dates to avoid
                redundant API calls.

        Returns:
            Number of reviews published.
        """
        target = target_date or date.today()
        target_str = target.strftime("%Y-%m-%d")

        logger.info("Starting daily review fetch for %s", target_str)

        try:
            # Use prefetched reviews or fetch from Apify
            if prefetched_reviews is not None:
                all_reviews = prefetched_reviews
                logger.info("Using %d prefetched reviews", len(all_reviews))
            else:
                all_reviews = self._apify.fetch_reviews(
                    max_reviews=config.REVIEW_MAX_REVIEWS,
                )

            # Filter to target date and 5-star reviews (matching old behavior)
            filtered = [
                r for r in all_reviews
                if r.published_at.startswith(target_str) and r.rating == 5
            ]

            logger.info(
                "Found %d reviews for %s (filtered from %d total)",
                len(filtered),
                target_str,
                len(all_reviews),
            )

            if not filtered:
                logger.info("No 5-star reviews found for %s", target_str)
                return 0

            # Snapshot the rate once for this batch (or use the caller-supplied value).
            # The rate travels on each Kafka event so the consumer applies the rate
            # that was active at ingest, not whatever the admin may have set since.
            rate = coin_rate if coin_rate is not None else self._repo.get_review_coin_rate()
            logger.info("Using review-to-coin rate %d for batch %s", rate, target_str)

            # Publish to Kafka
            self._producer.start()
            event_ids = self._producer.publish_batch([
                {
                    "external_id": r.external_id,
                    "review_text": r.text,
                    "rating": r.rating,
                    "reviewer_name": r.reviewer_name,
                    "published_at": r.published_at,
                    "coin_rate": rate,
                }
                for r in filtered
            ])

            logger.info("Published %d review events to Kafka", len(event_ids))
            return len(event_ids)

        except ApifyClientError as e:
            logger.error("Failed to fetch reviews from Apify: %s", e)
            # Send failure alert to Slack
            self._send_failure_alert(str(e))
            raise
        except Exception as e:
            logger.exception("Unexpected error in review fetch: %s", e)
            self._send_failure_alert(str(e))
            raise

    def fetch_and_publish_backfill(self, days: int = 7, coin_rate: int | None = None) -> int:
        """Backfill reviews for the past N days.

        Useful if daily fetches were missed. Fetches reviews once from Apify
        and filters by date for each day to avoid redundant API calls.

        Args:
            days: Number of days to backfill.
            coin_rate: Optional override for the review-to-coin rate. When omitted,
                snapshots the current rate from the DB once and reuses it across
                all days in the backfill (the spirit of "read once per batch").

        Returns:
            Total number of reviews published.
        """
        from datetime import timedelta

        total = 0
        today = date.today()
        rate = coin_rate if coin_rate is not None else self._repo.get_review_coin_rate()

        # Fetch all reviews once from Apify
        logger.info("Fetching reviews from Apify for %d-day backfill", days)
        try:
            all_reviews = self._apify.fetch_reviews(
                max_reviews=config.REVIEW_MAX_REVIEWS,
            )
            logger.info("Fetched %d reviews from Apify", len(all_reviews))
        except Exception as e:
            logger.error("Failed to fetch reviews from Apify: %s", e)
            self._send_failure_alert(str(e))
            raise

        # Process each day using the prefetched reviews
        for i in range(days):
            target = today - timedelta(days=i)
            try:
                count = self.fetch_and_publish_daily(
                    target_date=target,
                    prefetched_reviews=all_reviews,
                    coin_rate=rate,
                )
                total += count
            except Exception as e:
                logger.error("Failed to backfill %s: %s", target, e)

        return total

    def _send_failure_alert(self, error_message: str) -> None:
        """Send a Slack alert when review fetch fails."""
        try:
            self._slack.send_notification({
                "type": "REVIEW_FETCH_ERROR",
                "severity": "WARNING",
                "message": f"Daily review fetch failed: {error_message}",
                "metadata": {
                    "error": error_message,
                    "date": date.today().isoformat(),
                },
            })
        except Exception as e:
            logger.error("Failed to send failure alert: %s", e)
