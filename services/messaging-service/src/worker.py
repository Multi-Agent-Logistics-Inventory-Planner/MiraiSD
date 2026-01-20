"""Worker entry point for real-time messaging service.

Continuously consumes Kafka events, checks for reorder point alerts,
and sends Slack notifications when thresholds are breached.
"""

from __future__ import annotations

import logging
import signal
import sys

from . import config
from .adapters.kafka_consumer import KafkaEventConsumer
from .adapters.slack_notifier import AlertMessage, SlackNotifier
from .adapters.supabase_repo import SupabaseRepo
from .application.alert_checker import AlertChecker

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger(__name__)


class MessagingWorker:
    """Worker that processes Kafka events and sends alerts."""

    def __init__(self):
        self._consumer = KafkaEventConsumer()
        self._repo = SupabaseRepo()
        self._alert_checker = AlertChecker(self._repo)
        self._slack_notifier = SlackNotifier()
        self._running = False

    def start(self) -> None:
        """Start the worker."""
        logger.info("Starting messaging worker")
        logger.info("Config: kafka=%s, topic=%s, group=%s", 
                   config.KAFKA_BOOTSTRAP_SERVERS,
                   config.KAFKA_TOPIC,
                   config.KAFKA_CONSUMER_GROUP)
        logger.info("Slack enabled: %s", config.SLACK_ENABLED)

        self._running = True
        self._consumer.start()

        # Register signal handlers for graceful shutdown
        signal.signal(signal.SIGTERM, self._handle_signal)
        signal.signal(signal.SIGINT, self._handle_signal)

        self._run_loop()

    def stop(self) -> None:
        """Stop the worker gracefully."""
        logger.info("Stopping messaging worker")
        self._running = False
        self._consumer.stop()

    def _handle_signal(self, signum: int, frame) -> None:
        """Handle shutdown signals."""
        sig_name = signal.Signals(signum).name
        logger.info("Received signal %s, initiating graceful shutdown", sig_name)
        self.stop()

    def _run_loop(self) -> None:
        """Main worker loop: poll → check alerts → send notifications."""
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
                import time
                time.sleep(1)

        logger.info("Worker stopped (processed %d events total)", processed_count)

    def _process_event(self, event) -> None:
        """Process a single inventory change event.

        Args:
            event: NormalizedEvent from Kafka
        """
        # Check if alert should be sent
        alert_result = self._alert_checker.check_reorder_point(event)

        if not alert_result.should_alert:
            logger.debug("No alert needed: %s", alert_result.reason)
            return

        if alert_result.product is None:
            logger.warning("Alert result indicates alert needed but no product data")
            return

        product = alert_result.product

        # Send Slack notification
        alert_message = AlertMessage(
            product_name=product.name,
            product_sku=product.sku,
            current_quantity=product.current_quantity,
            reorder_point=product.reorder_point,
            product_id=product.id,
        )

        success = self._slack_notifier.send_alert(alert_message)

        if success:
            logger.info(
                "Sent reorder alert for product %s (id=%s, qty=%d, reorder=%d)",
                product.name,
                product.id,
                product.current_quantity,
                product.reorder_point,
            )
        else:
            logger.error("Failed to send alert for product %s", product.name)


def main() -> None:
    """Main entry point."""
    logger.info("=" * 60)
    logger.info("Messaging Service Worker")
    logger.info("=" * 60)
    logger.info("Kafka: %s", config.KAFKA_BOOTSTRAP_SERVERS)
    logger.info("Topic: %s", config.KAFKA_TOPIC)
    logger.info("Consumer Group: %s", config.KAFKA_CONSUMER_GROUP)
    logger.info("Slack Enabled: %s", config.SLACK_ENABLED)
    logger.info("=" * 60)

    worker = MessagingWorker()
    try:
        worker.start()
    except Exception as e:
        logger.exception("Worker failed: %s", e)
        sys.exit(1)


if __name__ == "__main__":
    main()

