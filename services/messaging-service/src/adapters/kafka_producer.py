"""Kafka producer for review events."""

from __future__ import annotations

import json
import logging
import uuid
from datetime import datetime, timezone
from typing import Any

from kafka import KafkaProducer
from kafka.errors import KafkaError

from .. import config

logger = logging.getLogger(__name__)


class ReviewKafkaProducer:
    """Kafka producer for publishing review events to employee-reviews topic."""

    def __init__(
        self,
        bootstrap_servers: str | None = None,
        topic: str | None = None,
    ):
        self._bootstrap_servers = bootstrap_servers or config.KAFKA_BOOTSTRAP_SERVERS
        self._topic = topic or config.KAFKA_REVIEWS_TOPIC
        self._producer: KafkaProducer | None = None

    def _create_producer(self) -> KafkaProducer:
        """Create and configure Kafka producer."""
        return KafkaProducer(
            bootstrap_servers=self._bootstrap_servers,
            value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k else None,
            acks="all",
            retries=3,
            retry_backoff_ms=1000,
        )

    def start(self) -> None:
        """Start the producer."""
        if self._producer is not None:
            return
        logger.info(
            "Starting Kafka producer: topic=%s, servers=%s",
            self._topic,
            self._bootstrap_servers,
        )
        self._producer = self._create_producer()

    def stop(self) -> None:
        """Stop the producer gracefully."""
        if self._producer is not None:
            logger.info("Stopping Kafka producer")
            try:
                self._producer.flush(timeout=10)
                self._producer.close(timeout=10)
            except KafkaError as e:
                logger.warning("Error closing Kafka producer: %s", e)
            finally:
                self._producer = None

    def publish_review(
        self,
        external_id: str,
        review_text: str,
        rating: int,
        reviewer_name: str,
        published_at: str,
    ) -> str:
        """Publish a review event to Kafka.

        Args:
            external_id: External review ID (from Apify).
            review_text: Review text content.
            rating: Star rating (1-5).
            reviewer_name: Name of reviewer.
            published_at: ISO timestamp when review was published.

        Returns:
            Event ID of the published event.
        """
        if self._producer is None:
            self.start()

        event_id = str(uuid.uuid4())
        event = {
            "event_id": event_id,
            "topic": self._topic,
            "event_type": "REVIEW_FETCHED",
            "entity_type": "review",
            "entity_id": external_id,
            "payload": {
                "external_id": external_id,
                "review_text": review_text,
                "rating": rating,
                "reviewer_name": reviewer_name,
                "published_at": published_at,
            },
            "created_at": datetime.now(timezone.utc).isoformat(),
        }

        try:
            future = self._producer.send(
                self._topic,
                key=external_id,
                value=event,
            )
            # Wait for confirmation
            record_metadata = future.get(timeout=10)
            logger.debug(
                "Published review event: id=%s, partition=%d, offset=%d",
                event_id,
                record_metadata.partition,
                record_metadata.offset,
            )
            return event_id
        except KafkaError as e:
            logger.error("Failed to publish review event: %s", e)
            raise

    def publish_batch(
        self,
        reviews: list[dict[str, Any]],
    ) -> list[str]:
        """Publish multiple review events.

        Args:
            reviews: List of review dicts with keys:
                external_id, review_text, rating, reviewer_name, published_at

        Returns:
            List of event IDs for published events.
        """
        if self._producer is None:
            self.start()

        event_ids: list[str] = []
        for review in reviews:
            try:
                event_id = self.publish_review(
                    external_id=review["external_id"],
                    review_text=review["review_text"],
                    rating=review["rating"],
                    reviewer_name=review["reviewer_name"],
                    published_at=review["published_at"],
                )
                event_ids.append(event_id)
            except Exception as e:
                logger.error(
                    "Failed to publish review %s: %s",
                    review.get("external_id"),
                    e,
                )

        # Flush to ensure all messages are sent
        self._producer.flush(timeout=30)
        logger.info("Published %d review events", len(event_ids))
        return event_ids
