"""Apify API client for Google Maps Reviews scraper."""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from typing import Any

import requests

from .. import config

logger = logging.getLogger(__name__)

# Retry configuration
MAX_RETRIES = 3
INITIAL_BACKOFF_SECONDS = 2
BACKOFF_MULTIPLIER = 2


@dataclass
class Review:
    """Parsed review from Apify."""

    external_id: str
    text: str
    rating: int
    reviewer_name: str
    published_at: str
    raw_data: dict[str, Any]


class ApifyClientError(Exception):
    """Error from Apify API."""

    pass


class ApifyClient:
    """Client for Apify Google Maps Reviews Scraper.

    Uses the Apify API to run the Google Maps reviews scraper actor
    and retrieve review data for a specified Google Maps place URL.
    """

    BASE_URL = "https://api.apify.com/v2"

    def __init__(self, api_token: str | None = None, actor_id: str | None = None):
        self._token = api_token or config.APIFY_API_TOKEN
        self._actor_id = actor_id or config.APIFY_ACTOR_ID
        if not self._token:
            raise ValueError("APIFY_API_TOKEN is required")

    def fetch_reviews(
        self,
        place_url: str | None = None,
        max_reviews: int = 100,
    ) -> list[Review]:
        """Fetch reviews from Google Maps via Apify.

        Args:
            place_url: Google Maps place URL. Defaults to config.GOOGLE_PLACE_URL.
            max_reviews: Maximum number of reviews to fetch.

        Returns:
            List of Review objects.

        Raises:
            ApifyClientError: If the API call fails after retries.
        """
        url = place_url or config.GOOGLE_PLACE_URL
        if not url:
            raise ValueError("GOOGLE_PLACE_URL is required")

        return self._fetch_with_retry(url, max_reviews)

    def _fetch_with_retry(self, place_url: str, max_reviews: int) -> list[Review]:
        """Fetch reviews with exponential backoff retry."""
        last_error: Exception | None = None
        backoff = INITIAL_BACKOFF_SECONDS

        for attempt in range(1, MAX_RETRIES + 1):
            try:
                logger.info(
                    "Fetching reviews (attempt %d/%d): %s",
                    attempt,
                    MAX_RETRIES,
                    place_url[:50],
                )
                return self._do_fetch(place_url, max_reviews)
            except requests.RequestException as e:
                last_error = e
                logger.warning(
                    "Apify request failed (attempt %d/%d): %s",
                    attempt,
                    MAX_RETRIES,
                    e,
                )
                if attempt < MAX_RETRIES:
                    logger.info("Retrying in %d seconds...", backoff)
                    time.sleep(backoff)
                    backoff *= BACKOFF_MULTIPLIER

        raise ApifyClientError(
            f"Failed to fetch reviews after {MAX_RETRIES} attempts"
        ) from last_error

    def _do_fetch(self, place_url: str, max_reviews: int) -> list[Review]:
        """Execute the Apify actor and retrieve results."""
        headers = {"Authorization": f"Bearer {self._token}"}

        # Use synchronous endpoint - waits for actor to complete and returns results
        run_url = f"{self.BASE_URL}/acts/{self._actor_id}/run-sync-get-dataset-items"
        payload = {
            "startUrls": [{"url": place_url}],
            "maxReviews": max_reviews,
            "reviewsSort": "newest",
            "language": "en",
            "reviewsOrigin": "all",
            "personalData": True,
        }

        response = requests.post(
            run_url,
            json=payload,
            headers=headers,
            timeout=300,
        )
        response.raise_for_status()
        items = response.json()

        # Parse reviews
        reviews: list[Review] = []
        for item in items:
            review = Review(
                external_id=item.get("reviewId", ""),
                text=item.get("text") or "",
                rating=item.get("stars", 0),
                reviewer_name=item.get("name") or "",
                published_at=item.get("publishedAtDate") or "",
                raw_data=item,
            )
            reviews.append(review)

        logger.info("Fetched %d reviews from Apify", len(reviews))
        return reviews
