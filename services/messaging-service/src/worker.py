"""Worker entry point for real-time messaging service.

Continuously:
1. Consumes Kafka events and creates notifications for threshold crossings
2. Polls undelivered notifications and sends them to Slack
3. Runs scheduled review fetch and summary jobs
4. Processes review events from employee-reviews topic
"""

from __future__ import annotations

import logging
import signal
import sys
import threading
import time

from . import config
from . import scheduler as review_scheduler
from .adapters.kafka_consumer import KafkaEventConsumer
from .adapters.slack_notifier import SlackNotifier
from .adapters.supabase_repo import SupabaseRepo
from .application.alert_checker import AlertChecker, AlertResult, AlertType
from .application.review_processor import ReviewProcessor
from .events import NormalizedEvent

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger(__name__)


class MessagingWorker:
    """Worker that processes Kafka events and delivers Slack notifications."""

    def __init__(self):
        self._consumer = KafkaEventConsumer()
        self._repo = SupabaseRepo()
        self._alert_checker = AlertChecker(self._repo)
        self._slack_notifier = SlackNotifier()
        self._review_processor = ReviewProcessor(self._repo)
        self._running = False
        self._notification_thread: threading.Thread | None = None
        self._review_consumer_thread: threading.Thread | None = None
        self._scheduler_started = False

    def start(self) -> None:
        """Start the worker."""
        logger.info("Starting messaging worker")
        logger.info(
            "Config: kafka=%s, topic=%s, group=%s",
            config.KAFKA_BOOTSTRAP_SERVERS,
            config.KAFKA_TOPIC,
            config.KAFKA_CONSUMER_GROUP,
        )
        logger.info("Slack enabled: %s", config.SLACK_ENABLED)
        logger.info("Review topic: %s", config.KAFKA_REVIEWS_TOPIC)

        self._running = True
        self._consumer.start()

        # Register signal handlers for graceful shutdown
        signal.signal(signal.SIGTERM, self._handle_signal)
        signal.signal(signal.SIGINT, self._handle_signal)

        # Start notification polling thread (for Slack delivery)
        self._notification_thread = threading.Thread(
            target=self._notification_polling_loop,
            name="notification-poller",
            daemon=True,
        )
        self._notification_thread.start()

        # Start review consumer thread (for employee-reviews topic)
        self._review_consumer_thread = threading.Thread(
            target=self._review_consumer_loop,
            name="review-consumer",
            daemon=True,
        )
        self._review_consumer_thread.start()

        # Start the review scheduler (for daily fetch and monthly summary)
        self._start_review_scheduler()

        # Run main Kafka event loop
        self._run_loop()

    def stop(self) -> None:
        """Stop the worker gracefully."""
        logger.info("Stopping messaging worker")
        self._running = False
        self._consumer.stop()

        # Stop the review scheduler
        if self._scheduler_started:
            review_scheduler.stop_scheduler()
            self._scheduler_started = False

    def _handle_signal(self, signum: int, frame) -> None:
        """Handle shutdown signals."""
        sig_name = signal.Signals(signum).name
        logger.info("Received signal %s, initiating graceful shutdown", sig_name)
        self.stop()

    def _run_loop(self) -> None:
        """Main worker loop: poll Kafka → check alerts → create notifications."""
        poll_timeout_ms = 1000  # 1 second poll timeout
        processed_count = 0

        while self._running:
            try:
                # Poll for new events
                events = self._consumer.poll(timeout_ms=poll_timeout_ms)

                if events:
                    logger.info("Received %d events from Kafka", len(events))

                # Process each event
                for event in events:
                    try:
                        self._process_event(event)
                        processed_count += 1
                    except Exception as e:
                        logger.exception("Error processing event %s: %s", event.event_id, e)
                        # Continue processing other events even if one fails

                # Commit Kafka offsets after successful processing
                if events:
                    self._consumer.commit()
                    logger.debug("Committed offsets after processing %d events", len(events))

            except KeyboardInterrupt:
                logger.info("Interrupted by user")
                break
            except Exception as e:
                logger.exception("Error in worker loop: %s", e)
                # Brief pause before retrying on error
                time.sleep(1)

        logger.info("Worker stopped (processed %d events total)", processed_count)

    def _notification_polling_loop(self) -> None:
        """Background thread: poll and deliver undelivered notifications to Slack.

        Uses claiming to prevent double-sends across multiple instances.
        """
        poll_interval = config.NOTIFICATION_POLL_INTERVAL
        logger.info("Starting notification polling thread (interval: %.1fs)", poll_interval)

        while self._running:
            try:
                self._poll_undelivered_notifications()
            except Exception as e:
                logger.exception("Error in notification polling: %s", e)

            time.sleep(poll_interval)

        logger.info("Notification polling thread stopped")

    def _start_review_scheduler(self) -> None:
        """Start the review fetch/summary scheduler."""
        if config.APIFY_API_TOKEN:
            logger.info("Starting review scheduler")
            review_scheduler.start_scheduler()
            self._scheduler_started = True
        else:
            logger.warning(
                "APIFY_API_TOKEN not configured, review scheduler disabled"
            )

    def _review_consumer_loop(self) -> None:
        """Background thread: consume and process review events from Kafka.

        Runs a separate consumer for the employee-reviews topic.
        """
        from kafka import KafkaConsumer
        import json

        logger.info("Starting review consumer thread for topic: %s", config.KAFKA_REVIEWS_TOPIC)

        try:
            consumer = KafkaConsumer(
                config.KAFKA_REVIEWS_TOPIC,
                bootstrap_servers=config.KAFKA_BOOTSTRAP_SERVERS,
                group_id=f"{config.KAFKA_CONSUMER_GROUP}-reviews",
                enable_auto_commit=False,
                auto_offset_reset="earliest",
                value_deserializer=lambda m: json.loads(m.decode("utf-8")),
                key_deserializer=lambda k: k.decode("utf-8") if k else None,
                consumer_timeout_ms=1000,
            )
        except Exception as e:
            logger.error("Failed to create review consumer: %s", e)
            return

        while self._running:
            try:
                # Poll for messages
                records = consumer.poll(timeout_ms=1000)

                for topic_partition, messages in records.items():
                    for record in messages:
                        try:
                            self._process_review_event(record.value)
                        except Exception as e:
                            logger.error(
                                "Error processing review event at offset %d: %s",
                                record.offset,
                                e,
                            )

                # Commit after processing batch
                if records:
                    consumer.commit()

            except Exception as e:
                logger.exception("Error in review consumer loop: %s", e)
                time.sleep(1)

        logger.info("Review consumer thread stopped")
        consumer.close()

    def _process_review_event(self, value: dict) -> None:
        """Process a single review event from Kafka.

        Args:
            value: Deserialized Kafka message value.
        """
        from .application.review_processor import ReviewEvent

        event = self._review_processor.parse_kafka_message(value)
        if event is None:
            logger.warning("Failed to parse review event")
            return

        self._review_processor.process_review(event)

    def _poll_undelivered_notifications(self) -> None:
        """Poll for and deliver pending notifications to Slack."""
        # Atomically claim notifications to prevent double-sends
        notifications = self._repo.claim_undelivered_notifications(limit=10)

        for notif in notifications:
            try:
                success = self._slack_notifier.send_notification(notif)
                if success:
                    self._repo.mark_as_delivered(notif["id"])
                else:
                    # Release claim so it can be retried
                    self._repo.release_claim(notif["id"])
            except Exception as e:
                logger.exception("Failed to deliver notification %s: %s", notif["id"], e)
                self._repo.release_claim(notif["id"])

    def _process_event(self, event: NormalizedEvent) -> None:
        """Process a single inventory change event.

        Checks for threshold crossings and creates notifications.
        One event can create multiple notifications (location + total alerts).

        Args:
            event: NormalizedEvent from Kafka
        """
        # Check all alert conditions
        results = self._alert_checker.check_all(event)

        if not results.has_alerts:
            logger.debug("No alerts triggered for event %s", event.event_id)
            return

        # Process each triggered alert
        for alert in results.get_triggered_alerts():
            self._create_notification_for_alert(event, alert)

    def _create_notification_for_alert(
        self,
        event: NormalizedEvent,
        alert: AlertResult,
    ) -> None:
        """Create a notification record for a triggered alert.

        Uses dedupe_key for idempotency to handle Kafka message duplicates.

        Args:
            event: Source Kafka event
            alert: Triggered alert result
        """
        if alert.alert_type is None:
            return

        # Map alert type to notification type and severity
        type_map = {
            AlertType.LOCATION_OUT_OF_STOCK: ("OUT_OF_STOCK", "CRITICAL"),
            AlertType.LOCATION_LOW_STOCK: ("LOW_STOCK", "WARNING"),
            AlertType.TOTAL_OUT_OF_STOCK: ("OUT_OF_STOCK", "CRITICAL"),
            AlertType.TOTAL_LOW_STOCK: ("LOW_STOCK", "WARNING"),
        }
        notification_type, severity = type_map.get(alert.alert_type, ("LOW_STOCK", "WARNING"))

        # Build human-readable message
        message = self._build_alert_message(event, alert)

        # Build dedupe_key for idempotency
        # Format: {alert_scope}:{item_id}:{location_code_if_applicable}
        if alert.alert_type in (AlertType.LOCATION_OUT_OF_STOCK, AlertType.LOCATION_LOW_STOCK):
            dedupe_key = f"{alert.alert_type.value}:{event.item_id}:{alert.location_code}"
        else:
            dedupe_key = f"{alert.alert_type.value}:{event.item_id}"

        # Build metadata for UI display
        metadata = {
            "alert_type": alert.alert_type.value,
            "previous_qty": alert.previous_qty,
            "current_qty": alert.current_qty,
            "threshold": alert.threshold,
        }
        if alert.location_code:
            metadata["location_code"] = alert.location_code
        if event.sku:
            metadata["sku"] = event.sku

        # Create notification with idempotency
        notification_id = self._repo.create_notification(
            notification_type=notification_type,
            severity=severity,
            message=message,
            item_id=event.item_id,
            recipient_id=None,
            metadata=metadata,
            source_event_id=event.event_id,
            dedupe_key=dedupe_key,
        )

        if notification_id:
            logger.info(
                "Created notification %s: type=%s, item=%s, dedupe=%s",
                notification_id,
                alert.alert_type.value,
                event.item_id,
                dedupe_key,
            )
        else:
            # None means duplicate was skipped (ON CONFLICT DO NOTHING)
            logger.debug(
                "Notification skipped (duplicate): type=%s, item=%s, dedupe=%s",
                alert.alert_type.value,
                event.item_id,
                dedupe_key,
            )

    def _build_alert_message(self, event: NormalizedEvent, alert: AlertResult) -> str:
        """Build human-readable message for an alert.

        Args:
            event: Source event
            alert: Triggered alert

        Returns:
            Message string for notification
        """
        sku_suffix = f" (SKU: {event.sku})" if event.sku else ""

        if alert.alert_type == AlertType.LOCATION_OUT_OF_STOCK:
            return f"Out of stock at location {alert.location_code}{sku_suffix}"

        if alert.alert_type == AlertType.LOCATION_LOW_STOCK:
            return (
                f"Low stock at {alert.location_code}: "
                f"{alert.current_qty} units (below threshold of {alert.threshold}){sku_suffix}"
            )

        if alert.alert_type == AlertType.TOTAL_OUT_OF_STOCK:
            return f"Product is now completely out of stock{sku_suffix}"

        if alert.alert_type == AlertType.TOTAL_LOW_STOCK:
            return (
                f"Total inventory low: "
                f"{alert.current_qty} units (below reorder point of {alert.threshold}){sku_suffix}"
            )

        return f"Stock alert: {alert.reason}{sku_suffix}"


def main() -> None:
    """Main entry point."""
    logger.info("=" * 60)
    logger.info("Messaging Service Worker")
    logger.info("=" * 60)
    logger.info("Kafka: %s", config.KAFKA_BOOTSTRAP_SERVERS)
    logger.info("Topic: %s", config.KAFKA_TOPIC)
    logger.info("Review Topic: %s", config.KAFKA_REVIEWS_TOPIC)
    logger.info("Consumer Group: %s", config.KAFKA_CONSUMER_GROUP)
    logger.info("Slack Enabled: %s", config.SLACK_ENABLED)
    logger.info("Notification Poll Interval: %.1fs", config.NOTIFICATION_POLL_INTERVAL)
    logger.info("Location Low Stock Threshold: %d", config.LOCATION_LOW_STOCK_THRESHOLD)
    logger.info("Review Scheduler: %s", "enabled" if config.APIFY_API_TOKEN else "disabled")
    logger.info("=" * 60)

    worker = MessagingWorker()
    try:
        worker.start()
    except Exception as e:
        logger.exception("Worker failed: %s", e)
        sys.exit(1)


if __name__ == "__main__":
    main()
