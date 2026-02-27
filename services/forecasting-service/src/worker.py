"""Worker entry point for real-time forecasting pipeline.

Continuously consumes Kafka events, aggregates them with debouncing,
and triggers the forecasting pipeline when batch conditions are met.
"""

from __future__ import annotations

import logging
import os
import signal
import sys
import threading
import time
from pathlib import Path

from . import config
from .adapters.dlq_producer import DLQProducer
from .adapters.kafka_consumer import KafkaEventConsumer
from .adapters.supabase_repo import SupabaseRepo
from .application.event_aggregator import EventAggregator
from .application.pipeline import ForecastingPipeline

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger(__name__)

# Healthcheck heartbeat file - Docker healthcheck verifies this file is recent
HEARTBEAT_FILE = Path(os.environ.get("HEARTBEAT_FILE", "/tmp/forecasting-worker-heartbeat"))


class ForecastingWorker:
    """Worker that processes Kafka events and triggers forecasting pipeline."""

    def __init__(self):
        self._dlq_producer = DLQProducer()
        self._consumer = KafkaEventConsumer(dlq_producer=self._dlq_producer)
        self._aggregator = EventAggregator()
        self._repo = SupabaseRepo()
        self._pipeline = ForecastingPipeline(repo=self._repo)
        self._running = False

    @staticmethod
    def _start_api_server() -> None:
        """Start FastAPI server in a daemon thread for HTTP trigger access."""
        import uvicorn

        from .api.main import app

        logger.info("Starting FastAPI server on 0.0.0.0:5000")
        thread = threading.Thread(
            target=uvicorn.run,
            args=(app,),
            kwargs={"host": "0.0.0.0", "port": 5000, "log_level": "info"},
            daemon=True,
        )
        thread.start()

    def start(self) -> None:
        """Start the worker."""
        logger.info("Starting forecasting worker")
        self._start_api_server()
        logger.info(
            "Config: batch_window=%ds, batch_size=%d, debounce=%.1fs",
            config.BATCH_WINDOW_SECONDS,
            config.BATCH_SIZE_TRIGGER,
            config.ITEM_DEBOUNCE_SECONDS,
        )
        logger.info("DLQ topic: %s", config.KAFKA_DLQ_TOPIC)

        self._running = True
        self._dlq_producer.start()
        self._consumer.start()
        self._write_heartbeat()  # Initial heartbeat for Docker healthcheck

        # Register signal handlers for graceful shutdown
        signal.signal(signal.SIGTERM, self._handle_signal)
        signal.signal(signal.SIGINT, self._handle_signal)

        self._run_loop()

    def stop(self) -> None:
        """Stop the worker gracefully."""
        logger.info("Stopping forecasting worker")
        self._running = False
        self._consumer.stop()
        self._dlq_producer.stop()

    def _write_heartbeat(self) -> None:
        """Write heartbeat file for Docker healthcheck."""
        try:
            HEARTBEAT_FILE.touch()
        except OSError as e:
            logger.warning("Failed to write heartbeat file: %s", e)

    def _handle_signal(self, signum: int, frame) -> None:
        """Handle shutdown signals."""
        sig_name = signal.Signals(signum).name
        logger.info("Received signal %s, initiating graceful shutdown", sig_name)
        self.stop()

    def _run_loop(self) -> None:
        """Main worker loop: poll → aggregate → process batches."""
        poll_timeout_ms = 1000  # 1 second poll timeout
        poll_count = 0

        while self._running:
            poll_count += 1
            # Log heartbeat every 30 polls (30 seconds) and update heartbeat file
            if poll_count % 30 == 0:
                logger.info("Worker heartbeat: %d polls, aggregator has %d items",
                           poll_count, len(self._aggregator.get_affected_items()))
                self._write_heartbeat()
            try:
                # Poll for new events
                events = self._consumer.poll(timeout_ms=poll_timeout_ms)

                # Add events to aggregator
                if events:
                    logger.info(
                        "Received %d events from Kafka",
                        len(events),
                    )
                    added = self._aggregator.add_events(events)
                    if added > 0:
                        logger.info(
                            "Added %d events to aggregator (%d debounced)",
                            added,
                            len(events) - added,
                        )

                # Check if batch is ready
                batch_result = self._aggregator.check_batch_ready()

                if batch_result.ready:
                    self._process_batch(batch_result.item_ids)

            except KeyboardInterrupt:
                logger.info("Interrupted by user")
                break
            except Exception as e:
                logger.exception("Error in worker loop: %s", e)
                # Brief pause before retrying on error
                time.sleep(1)

        # Process any remaining events before shutdown
        if not self._aggregator.is_empty:
            logger.info("Processing remaining events before shutdown")
            remaining_items = self._aggregator.get_affected_items()
            if remaining_items:
                self._process_batch(remaining_items)

        logger.info("Worker stopped")

    def _process_batch(self, item_ids: set[str]) -> None:
        """Process a batch of items through the pipeline."""
        logger.info(
            "Processing batch: %d items",
            len(item_ids),
        )

        try:
            # Get event-carried inventory from aggregator
            event_inventory = self._aggregator.get_item_inventory()

            # Run the forecasting pipeline with event inventory
            saved = self._pipeline.run_for_items(
                item_ids,
                event_inventory=event_inventory if event_inventory else None,
            )

            # Commit Kafka offsets only after successful processing
            self._consumer.commit()
            logger.info(
                "Batch complete: saved %d forecasts, committed offsets",
                saved,
            )

            # Clear the aggregator
            self._aggregator.flush()

        except Exception as e:
            logger.exception("Failed to process batch: %s", e)
            # Don't commit offsets on failure - events will be reprocessed
            # Clear aggregator to avoid infinite retry of same batch
            self._aggregator.reset()


def main() -> None:
    """Main entry point."""
    logger.info("=" * 60)
    logger.info("Forecasting Service Worker")
    logger.info("=" * 60)
    logger.info("Kafka: %s", config.KAFKA_BOOTSTRAP_SERVERS)
    logger.info("Topic: %s", config.KAFKA_TOPIC)
    logger.info("Consumer Group: %s", config.KAFKA_CONSUMER_GROUP)
    logger.info("=" * 60)

    worker = ForecastingWorker()
    try:
        worker.start()
    except Exception as e:
        logger.exception("Worker failed: %s", e)
        sys.exit(1)


if __name__ == "__main__":
    main()
