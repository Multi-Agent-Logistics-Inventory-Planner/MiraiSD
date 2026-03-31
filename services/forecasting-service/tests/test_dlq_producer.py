"""Tests for DLQ producer."""

import base64
import json
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

import pytest

from src.adapters.dlq_producer import DLQMessage, DLQProducer


class TestModuleImports:
    """Tests for module-level imports and structure."""

    def test_module_imports_correctly(self):
        """Should import the module without errors."""
        import src.adapters.dlq_producer as dlq_module

        assert hasattr(dlq_module, "DLQMessage")
        assert hasattr(dlq_module, "DLQProducer")

    def test_dlq_message_is_dataclass(self):
        """Should be a proper dataclass with expected fields."""
        from dataclasses import fields

        field_names = [f.name for f in fields(DLQMessage)]
        expected_fields = [
            "original_topic",
            "original_offset",
            "original_partition",
            "error_message",
            "raw_value",
            "failed_at",
        ]
        assert field_names == expected_fields


class TestDLQMessage:
    """Tests for DLQMessage dataclass."""

    def test_create_dlq_message(self):
        """Should create DLQMessage with all fields."""
        msg = DLQMessage(
            original_topic="inventory-changes",
            original_offset=123,
            original_partition=0,
            error_message="Parse error: missing item_id",
            raw_value=b'{"invalid": "json"}',
            failed_at=datetime(2024, 1, 1, 12, 0, 0, tzinfo=timezone.utc),
        )

        assert msg.original_topic == "inventory-changes"
        assert msg.original_offset == 123
        assert msg.original_partition == 0
        assert msg.error_message == "Parse error: missing item_id"
        assert msg.raw_value == b'{"invalid": "json"}'
        assert msg.failed_at.year == 2024

    def test_to_dict(self):
        """Should convert DLQMessage to dict for Kafka serialization."""
        msg = DLQMessage(
            original_topic="inventory-changes",
            original_offset=456,
            original_partition=1,
            error_message="Validation failed",
            raw_value=b'{"bad": "data"}',
            failed_at=datetime(2024, 2, 15, 10, 30, 0, tzinfo=timezone.utc),
        )

        result = msg.to_dict()

        assert result["original_topic"] == "inventory-changes"
        assert result["original_offset"] == 456
        assert result["original_partition"] == 1
        assert result["error_message"] == "Validation failed"
        assert result["raw_value_base64"] is not None  # base64 encoded
        assert "failed_at" in result

    def test_to_dict_base64_encoding(self):
        """Should correctly base64 encode raw_value bytes."""
        raw_bytes = b'{"test": "data", "number": 123}'
        msg = DLQMessage(
            original_topic="test-topic",
            original_offset=100,
            original_partition=0,
            error_message="Test error",
            raw_value=raw_bytes,
            failed_at=datetime(2024, 1, 1, 0, 0, 0, tzinfo=timezone.utc),
        )

        result = msg.to_dict()

        # Verify base64 can be decoded back to original bytes
        decoded_bytes = base64.b64decode(result["raw_value_base64"])
        assert decoded_bytes == raw_bytes

    def test_to_dict_datetime_isoformat(self):
        """Should format datetime as ISO format string."""
        test_datetime = datetime(2024, 6, 15, 14, 30, 45, tzinfo=timezone.utc)
        msg = DLQMessage(
            original_topic="test-topic",
            original_offset=200,
            original_partition=1,
            error_message="Test error",
            raw_value=b"test",
            failed_at=test_datetime,
        )

        result = msg.to_dict()

        assert result["failed_at"] == "2024-06-15T14:30:45+00:00"


class TestDLQProducer:
    """Tests for DLQProducer."""

    @patch("src.adapters.dlq_producer.KafkaProducer")
    def test_start_creates_producer(self, mock_kafka_producer_class):
        """Should create Kafka producer on start."""
        mock_producer = MagicMock()
        mock_kafka_producer_class.return_value = mock_producer

        dlq = DLQProducer(
            bootstrap_servers="localhost:9092",
            topic="test.DLQ",
        )
        dlq.start()

        mock_kafka_producer_class.assert_called_once()
        assert dlq._producer is not None

    @patch("src.adapters.dlq_producer.KafkaProducer")
    def test_stop_closes_producer(self, mock_kafka_producer_class):
        """Should close Kafka producer on stop."""
        mock_producer = MagicMock()
        mock_kafka_producer_class.return_value = mock_producer

        dlq = DLQProducer()
        dlq.start()
        dlq.stop()

        mock_producer.close.assert_called_once()

    @patch("src.adapters.dlq_producer.KafkaProducer")
    def test_send_publishes_message(self, mock_kafka_producer_class):
        """Should send DLQ message to Kafka topic."""
        mock_producer = MagicMock()
        mock_kafka_producer_class.return_value = mock_producer

        dlq = DLQProducer(topic="test.DLQ")
        dlq.start()

        msg = DLQMessage(
            original_topic="inventory-changes",
            original_offset=789,
            original_partition=2,
            error_message="Test error",
            raw_value=b"test data",
            failed_at=datetime.now(timezone.utc),
        )

        dlq.send(msg)

        mock_producer.send.assert_called_once()
        call_args = mock_producer.send.call_args
        assert call_args[0][0] == "test.DLQ"

    @patch("src.adapters.dlq_producer.KafkaProducer")
    def test_send_without_start_raises_error(self, mock_kafka_producer_class):
        """Should raise error if send called before start."""
        dlq = DLQProducer()

        msg = DLQMessage(
            original_topic="test",
            original_offset=0,
            original_partition=0,
            error_message="error",
            raw_value=b"data",
            failed_at=datetime.now(timezone.utc),
        )

        with pytest.raises(RuntimeError):
            dlq.send(msg)

    @patch("src.adapters.dlq_producer.KafkaProducer")
    def test_stop_is_idempotent(self, mock_kafka_producer_class):
        """Should handle multiple stop calls gracefully."""
        mock_producer = MagicMock()
        mock_kafka_producer_class.return_value = mock_producer

        dlq = DLQProducer()
        dlq.start()
        dlq.stop()
        dlq.stop()  # Second stop should not raise

        assert mock_producer.close.call_count == 1
