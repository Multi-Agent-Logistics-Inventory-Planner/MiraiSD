"""Tests for Kafka consumer DLQ integration."""

from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

import pytest

from src.adapters.dlq_producer import DLQMessage, DLQProducer
from src.adapters.kafka_consumer import KafkaEventConsumer


class TestKafkaConsumerDLQ:
    """Tests for Kafka consumer with DLQ integration."""

    @patch("src.adapters.kafka_consumer.KafkaConsumer")
    def test_consumer_sends_to_dlq_on_parse_failure(self, mock_kafka_consumer_class):
        """Should send malformed message to DLQ."""
        mock_consumer = MagicMock()
        mock_kafka_consumer_class.return_value = mock_consumer

        # Create mock DLQ producer
        mock_dlq = MagicMock(spec=DLQProducer)

        # Create consumer with DLQ
        consumer = KafkaEventConsumer(dlq_producer=mock_dlq)
        consumer.start()

        # Simulate a malformed record
        mock_record = MagicMock()
        mock_record.value = "not a dict"  # Invalid format
        mock_record.topic = "inventory-changes"
        mock_record.offset = 100
        mock_record.partition = 0

        # Poll returns the malformed record
        mock_consumer.poll.return_value = {
            ("inventory-changes", 0): [mock_record]
        }
        mock_consumer.assignment.return_value = {("inventory-changes", 0)}

        # Poll should not raise
        events = consumer.poll()

        # Should have sent to DLQ
        mock_dlq.send.assert_called_once()
        call_args = mock_dlq.send.call_args[0][0]
        assert isinstance(call_args, DLQMessage)
        assert call_args.original_topic == "inventory-changes"
        assert call_args.original_offset == 100

    @patch("src.adapters.kafka_consumer.KafkaConsumer")
    def test_consumer_sends_to_dlq_on_validation_failure(self, mock_kafka_consumer_class):
        """Should send message with invalid schema to DLQ."""
        mock_consumer = MagicMock()
        mock_kafka_consumer_class.return_value = mock_consumer

        mock_dlq = MagicMock(spec=DLQProducer)

        consumer = KafkaEventConsumer(dlq_producer=mock_dlq)
        consumer.start()

        # Record with invalid schema (missing required fields)
        mock_record = MagicMock()
        mock_record.value = {
            "event_id": "test",
            # Missing payload
        }
        mock_record.topic = "inventory-changes"
        mock_record.offset = 200
        mock_record.partition = 1

        mock_consumer.poll.return_value = {
            ("inventory-changes", 1): [mock_record]
        }
        mock_consumer.assignment.return_value = {("inventory-changes", 1)}

        events = consumer.poll()

        # Should have sent to DLQ
        mock_dlq.send.assert_called_once()

    @patch("src.adapters.kafka_consumer.KafkaConsumer")
    def test_consumer_works_without_dlq(self, mock_kafka_consumer_class):
        """Should work normally when no DLQ producer is configured."""
        mock_consumer = MagicMock()
        mock_kafka_consumer_class.return_value = mock_consumer

        # Consumer without DLQ
        consumer = KafkaEventConsumer(dlq_producer=None)
        consumer.start()

        # Malformed record
        mock_record = MagicMock()
        mock_record.value = "invalid"
        mock_record.topic = "inventory-changes"
        mock_record.offset = 300
        mock_record.partition = 0

        mock_consumer.poll.return_value = {
            ("inventory-changes", 0): [mock_record]
        }
        mock_consumer.assignment.return_value = {("inventory-changes", 0)}

        # Should not raise
        events = consumer.poll()
        assert events == []

    @patch("src.adapters.kafka_consumer.KafkaConsumer")
    def test_consumer_continues_on_dlq_failure(self, mock_kafka_consumer_class):
        """Should continue processing even if DLQ send fails."""
        mock_consumer = MagicMock()
        mock_kafka_consumer_class.return_value = mock_consumer

        mock_dlq = MagicMock(spec=DLQProducer)
        mock_dlq.send.side_effect = Exception("DLQ unavailable")

        consumer = KafkaEventConsumer(dlq_producer=mock_dlq)
        consumer.start()

        mock_record = MagicMock()
        mock_record.value = "bad data"
        mock_record.topic = "inventory-changes"
        mock_record.offset = 400
        mock_record.partition = 0

        mock_consumer.poll.return_value = {
            ("inventory-changes", 0): [mock_record]
        }
        mock_consumer.assignment.return_value = {("inventory-changes", 0)}

        # Should not raise even when DLQ fails
        events = consumer.poll()
        assert events == []
