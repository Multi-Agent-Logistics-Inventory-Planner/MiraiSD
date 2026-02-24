"""Dead Letter Queue (DLQ) producer for failed Kafka messages."""

from __future__ import annotations

import base64
import json
import logging
from dataclasses import dataclass
from datetime import datetime

from kafka import KafkaProducer
from kafka.errors import KafkaError

from .. import config

logger = logging.getLogger(__name__)


@dataclass
class DLQMessage:
    """Message to be sent to the Dead Letter Queue.

    Contains metadata about the original failed message and the error.
    """

    original_topic: str
    original_offset: int
    original_partition: int
    error_message: str
    raw_value: bytes
    failed_at: datetime

    def to_dict(self) -> dict:
        """Convert to dictionary for Kafka serialization."""
        return {
            "original_topic": self.original_topic,
            "original_offset": self.original_offset,
            "original_partition": self.original_partition,
            "error_message": self.error_message,
            "raw_value_base64": base64.b64encode(self.raw_value).decode("utf-8"),
            "failed_at": self.failed_at.isoformat(),
        }


class DLQProducer:
    """Producer for sending failed messages to Dead Letter Queue."""

    def __init__(
        self,
        bootstrap_servers: str | None = None,
        topic: str | None = None,
    ):
        self._bootstrap_servers = bootstrap_servers or config.KAFKA_BOOTSTRAP_SERVERS
        self._topic = topic or config.KAFKA_DLQ_TOPIC
        self._producer: KafkaProducer | None = None

    def start(self) -> None:
        """Start the DLQ producer."""
        if self._producer is not None:
            return

        logger.info(
            "Starting DLQ producer: topic=%s, servers=%s",
            self._topic,
            self._bootstrap_servers,
        )

        self._producer = KafkaProducer(
            bootstrap_servers=self._bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k else None,
            acks="all",
            retries=3,
        )

    def stop(self) -> None:
        """Stop the DLQ producer gracefully."""
        if self._producer is None:
            return

        logger.info("Stopping DLQ producer")
        try:
            self._producer.flush(timeout=5)
            self._producer.close(timeout=5)
        except KafkaError as e:
            logger.warning("Error closing DLQ producer: %s", e)
        finally:
            self._producer = None

    def send(self, message: DLQMessage) -> None:
        """Send a failed message to the DLQ.

        Args:
            message: DLQMessage containing the failed message metadata.

        Raises:
            RuntimeError: If producer not started.
        """
        if self._producer is None:
            raise RuntimeError("DLQ producer not started. Call start() first.")

        try:
            key = f"{message.original_topic}:{message.original_partition}:{message.original_offset}"
            self._producer.send(
                self._topic,
                key=key,
                value=message.to_dict(),
            )
            logger.warning(
                "Sent message to DLQ: topic=%s, offset=%d, partition=%d, error=%s",
                message.original_topic,
                message.original_offset,
                message.original_partition,
                message.error_message,
            )
        except KafkaError as e:
            logger.error("Failed to send to DLQ: %s", e)
            # Don't raise - DLQ failures shouldn't stop processing
