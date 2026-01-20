"""Kafka consumer for inventory-changes events."""

from __future__ import annotations

import json
import logging
from collections.abc import Iterator
from typing import TYPE_CHECKING

from kafka import KafkaConsumer
from kafka.errors import KafkaError

from .. import config
from ..events import EventEnvelope, NormalizedEvent

if TYPE_CHECKING:
    from kafka.consumer.fetcher import ConsumerRecord

logger = logging.getLogger(__name__)


class KafkaEventConsumer:
    """Kafka consumer for inventory change events with manual offset management."""

    def __init__(
        self,
        bootstrap_servers: str | None = None,
        topic: str | None = None,
        group_id: str | None = None,
    ):
        self._bootstrap_servers = bootstrap_servers or config.KAFKA_BOOTSTRAP_SERVERS
        self._topic = topic or config.KAFKA_TOPIC
        self._group_id = group_id or config.KAFKA_CONSUMER_GROUP
        self._consumer: KafkaConsumer | None = None
        self._running = False

    def _create_consumer(self) -> KafkaConsumer:
        """Create and configure Kafka consumer."""
        return KafkaConsumer(
            self._topic,
            bootstrap_servers=self._bootstrap_servers,
            group_id=self._group_id,
            # Manual offset management - commit only after successful processing
            enable_auto_commit=False,
            auto_offset_reset="earliest",
            # Deserialize JSON messages
            value_deserializer=lambda m: json.loads(m.decode("utf-8")),
            key_deserializer=lambda k: k.decode("utf-8") if k else None,
            # Consumer tuning from config
            fetch_min_bytes=config.KAFKA_FETCH_MIN_BYTES,
            max_poll_records=config.KAFKA_MAX_POLL_RECORDS,
            session_timeout_ms=config.KAFKA_SESSION_TIMEOUT_MS,
            heartbeat_interval_ms=config.KAFKA_HEARTBEAT_INTERVAL_MS,
        )

    def start(self) -> None:
        """Start the consumer."""
        if self._consumer is not None:
            return
        logger.info(
            "Starting Kafka consumer: topic=%s, group=%s, servers=%s",
            self._topic,
            self._group_id,
            self._bootstrap_servers,
        )
        self._consumer = self._create_consumer()
        self._running = True

    def stop(self) -> None:
        """Stop the consumer gracefully."""
        self._running = False
        if self._consumer is not None:
            logger.info("Stopping Kafka consumer")
            try:
                self._consumer.close(autocommit=False)
            except KafkaError as e:
                logger.warning("Error closing Kafka consumer: %s", e)
            finally:
                self._consumer = None

    def commit(self) -> None:
        """Commit current offsets."""
        if self._consumer is not None:
            try:
                self._consumer.commit()
                logger.debug("Committed Kafka offsets")
            except KafkaError as e:
                logger.error("Failed to commit offsets: %s", e)
                raise

    def poll(self, timeout_ms: int = 1000) -> list[NormalizedEvent]:
        """Poll for new messages and return normalized events.

        Args:
            timeout_ms: Maximum time to block waiting for messages.

        Returns:
            List of NormalizedEvent objects parsed from Kafka messages.
        """
        if self._consumer is None:
            raise RuntimeError("Consumer not started. Call start() first.")

        events: list[NormalizedEvent] = []
        records = self._consumer.poll(timeout_ms=timeout_ms)

        for topic_partition, messages in records.items():
            for record in messages:
                try:
                    event = self._parse_record(record)
                    if event is not None:
                        events.append(event)
                except Exception as e:
                    logger.warning(
                        "Failed to parse message at offset %d: %s",
                        record.offset,
                        e,
                    )

        return events

    def stream(self, poll_timeout_ms: int = 1000) -> Iterator[NormalizedEvent]:
        """Stream events continuously until stopped.

        Yields NormalizedEvent objects as they arrive. Does NOT auto-commit;
        caller must call commit() after processing batches.
        """
        if self._consumer is None:
            self.start()

        logger.info("Starting event stream")
        while self._running:
            events = self.poll(timeout_ms=poll_timeout_ms)
            for event in events:
                yield event

    def _parse_record(self, record: ConsumerRecord) -> NormalizedEvent | None:
        """Parse a Kafka record into a NormalizedEvent.

        Expected message format from inventory-service:
        {
            "event_id": "uuid",
            "topic": "inventory-changes",
            "event_type": "CREATED",
            "entity_type": "stock_movement",
            "entity_id": "uuid",
            "payload": {
                "item_id": "uuid",
                "quantity_change": -1,
                "reason": "sale",
                "at": "2024-01-01T12:00:00Z",
                ...
            },
            "created_at": "2024-01-01T12:00:00Z"
        }
        """
        value = record.value
        if not isinstance(value, dict):
            logger.warning("Unexpected message format: %s", type(value))
            return None

        try:
            # Parse using existing Pydantic model
            envelope = EventEnvelope.model_validate(value)

            # Convert to normalized event
            normalized = NormalizedEvent(
                event_id=envelope.event_id,
                item_id=envelope.payload.item_id,
                quantity_change=envelope.payload.quantity_change,
                reason=envelope.payload.reason,
                at=envelope.payload.at,
            )

            logger.debug(
                "Parsed event: id=%s, item=%s, qty=%d, reason=%s",
                normalized.event_id,
                normalized.item_id,
                normalized.quantity_change,
                normalized.reason,
            )
            return normalized

        except Exception as e:
            logger.warning("Failed to validate event envelope: %s", e)
            return None

    @property
    def is_running(self) -> bool:
        """Check if consumer is running."""
        return self._running and self._consumer is not None

