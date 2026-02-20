"""Review processor - extracts employee mentions from reviews and stores in database."""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from datetime import date
from typing import Any

from ..adapters.supabase_repo import SupabaseRepo

logger = logging.getLogger(__name__)


@dataclass
class ReviewEvent:
    """Parsed review event from Kafka."""

    event_id: str
    external_id: str
    review_text: str
    rating: int
    reviewer_name: str
    published_at: str


class ReviewProcessor:
    """Processes review events and extracts employee mentions.

    Loads employee name mappings from the database and uses regex to
    find mentions in review text.
    """

    def __init__(self, repo: SupabaseRepo | None = None):
        self._repo = repo or SupabaseRepo()
        self._name_map: dict[str, str] = {}  # variant -> canonical
        self._patterns: dict[str, re.Pattern] = {}  # variant -> compiled pattern
        self._loaded = False

    def load_employee_mappings(self) -> None:
        """Load employee name variants from database."""
        employees = self._repo.get_review_employees()

        self._name_map.clear()
        self._patterns.clear()

        for emp in employees:
            canonical = emp["canonical_name"]
            for variant in emp["name_variants"]:
                variant_lower = variant.lower()
                self._name_map[variant_lower] = canonical
                # Word boundary pattern for matching
                self._patterns[variant_lower] = re.compile(
                    rf"\b{re.escape(variant)}\b",
                    re.IGNORECASE,
                )

        self._loaded = True
        logger.info(
            "Loaded %d employee name variants from %d employees",
            len(self._name_map),
            len(employees),
        )

    def extract_employee(self, review_text: str) -> str | None:
        """Extract employee name from review text.

        Args:
            review_text: Review text to search.

        Returns:
            Canonical employee name if found, None otherwise.
        """
        if not self._loaded:
            self.load_employee_mappings()

        if not review_text:
            return None

        text_lower = review_text.lower()

        # Check each pattern
        for variant, pattern in self._patterns.items():
            if pattern.search(review_text):
                return self._name_map[variant]

        return None

    def process_review(self, event: ReviewEvent) -> bool:
        """Process a single review event.

        Extracts employee mention, stores review, and updates daily count.

        Args:
            event: ReviewEvent from Kafka.

        Returns:
            True if an employee was found and recorded, False otherwise.
        """
        if not self._loaded:
            self.load_employee_mappings()

        # Extract employee mention
        employee = self.extract_employee(event.review_text)

        if not employee:
            logger.debug("No employee mention in review %s", event.external_id)
            return False

        # Parse review date
        review_date = date.fromisoformat(event.published_at.split("T")[0])

        # Store the individual review
        review_id = self._repo.create_review(
            employee_name=employee,
            external_id=event.external_id,
            review_date=review_date,
            review_text=event.review_text,
            rating=event.rating,
            reviewer_name=event.reviewer_name,
        )

        if review_id:
            logger.info(
                "Stored review %s for employee %s on %s",
                event.external_id,
                employee,
                review_date,
            )

            # Update daily count
            self._repo.increment_daily_count(employee, review_date)
            return True

        # review_id is None means duplicate (external_id already exists)
        logger.debug("Review %s already exists, skipping", event.external_id)
        return False

    def process_batch(self, events: list[ReviewEvent]) -> int:
        """Process multiple review events.

        Args:
            events: List of ReviewEvent objects.

        Returns:
            Number of reviews with employee mentions found.
        """
        if not self._loaded:
            self.load_employee_mappings()

        found = 0
        for event in events:
            try:
                if self.process_review(event):
                    found += 1
            except Exception as e:
                logger.error("Error processing review %s: %s", event.external_id, e)

        logger.info("Processed %d events, found %d employee mentions", len(events), found)
        return found

    @staticmethod
    def parse_kafka_message(value: dict[str, Any]) -> ReviewEvent | None:
        """Parse a Kafka message into a ReviewEvent.

        Args:
            value: Deserialized Kafka message value.

        Returns:
            ReviewEvent if valid, None otherwise.
        """
        try:
            payload = value.get("payload", {})
            return ReviewEvent(
                event_id=value.get("event_id", ""),
                external_id=payload.get("external_id", ""),
                review_text=payload.get("review_text", ""),
                rating=payload.get("rating", 0),
                reviewer_name=payload.get("reviewer_name", ""),
                published_at=payload.get("published_at", ""),
            )
        except Exception as e:
            logger.warning("Failed to parse review event: %s", e)
            return None
