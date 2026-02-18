"""Review fetcher - scheduled job to fetch reviews from Apify and publish to Kafka."""

from __future__ import annotations

import logging
from datetime import date, datetime

from ..adapters.apify_client import ApifyClient, ApifyClientError
from ..adapters.kafka_producer import ReviewKafkaProducer
from ..adapters.slack_notifier import SlackNotifier
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
    ):
        self._apify = apify_client or ApifyClient()
        self._producer = kafka_producer or ReviewKafkaProducer()
        self._slack = slack_notifier or SlackNotifier()

    def fetch_and_publish_daily(self, target_date: date | None = None) -> int:
        """Fetch today's reviews from Apify and publish to Kafka.

        Args:
            target_date: Date to filter reviews for. Defaults to today.

        Returns:
            Number of reviews published.
        """
        target = target_date or date.today()
        target_str = target.strftime("%Y-%m-%d")

        logger.info("Starting daily review fetch for %s", target_str)

        try:
            # Fetch reviews from Apify
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

            # Publish to Kafka
            self._producer.start()
            event_ids = self._producer.publish_batch([
                {
                    "external_id": r.external_id,
                    "review_text": r.text,
                    "rating": r.rating,
                    "reviewer_name": r.reviewer_name,
                    "published_at": r.published_at,
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

    def fetch_and_publish_backfill(self, days: int = 7) -> int:
        """Backfill reviews for the past N days.

        Useful if daily fetches were missed.

        Args:
            days: Number of days to backfill.

        Returns:
            Total number of reviews published.
        """
        from datetime import timedelta

        total = 0
        today = date.today()

        for i in range(days):
            target = today - timedelta(days=i)
            try:
                count = self.fetch_and_publish_daily(target)
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
