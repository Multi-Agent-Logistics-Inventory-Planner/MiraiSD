#!/usr/bin/env python3
"""Test the full review scraper pipeline: Apify -> Kafka.

Usage:
    # Test with auto-search (loops through last 7 days)
    python test_full_pipeline.py

    # Test a specific date
    python test_full_pipeline.py --date 2026-02-18

    # Test yesterday
    python test_full_pipeline.py --date yesterday

    # Apify fetch only (no Kafka)
    python test_full_pipeline.py --no-kafka

    # Skip confirmation prompt
    python test_full_pipeline.py --yes
"""

from __future__ import annotations

import argparse
import sys
from datetime import date, timedelta
from pathlib import Path

# Add src to path
sys.path.insert(0, str(Path(__file__).parent))

from src.application.review_fetcher import ReviewFetcher
from src.application.review_processor import ReviewProcessor
from src.adapters.apify_client import ApifyClient
from src.adapters.supabase_repo import SupabaseRepo
from src.adapters.slack_notifier import SlackNotifier
from src import config


def parse_date(date_str: str) -> date:
    """Parse a date string into a date object.

    Accepts:
        - 'today'
        - 'yesterday'
        - ISO format: '2026-02-18'
    """
    if date_str == "today":
        return date.today()
    if date_str == "yesterday":
        return date.today() - timedelta(days=1)
    return date.fromisoformat(date_str)


def preview_employee_mentions(reviews, target_date: date | None = None):
    """Preview employee mentions from reviews using database employee mappings.

    Args:
        reviews: List of Review objects from Apify.
        target_date: Optional date to filter reviews.

    Returns:
        Tuple of (counts dict, reviews_with_mentions list)
    """
    from collections import defaultdict

    # Use ReviewProcessor to load employee names from database
    processor = ReviewProcessor()
    processor.load_employee_mappings()

    # Filter by date if specified
    if target_date:
        target_str = target_date.strftime("%Y-%m-%d")
        filtered = [r for r in reviews if r.published_at.startswith(target_str)]
    else:
        filtered = reviews

    # Filter to 5-star only (matching production behavior)
    filtered = [r for r in filtered if r.rating == 5]

    # Count employee mentions using the same logic as production
    counts: dict[str, int] = defaultdict(int)
    reviews_with_mentions = []

    for review in filtered:
        employee = processor.extract_employee(review.text)
        if employee:
            counts[employee] += 1
            reviews_with_mentions.append((review, employee))

    return dict(sorted(counts.items(), key=lambda x: -x[1])), reviews_with_mentions


def test_apify_only():
    """Test just the Apify fetch (no Kafka)."""
    print("=" * 60)
    print("STEP 1: Testing Apify fetch only")
    print("=" * 60)

    print(f"  APIFY_API_TOKEN: {'set' if config.APIFY_API_TOKEN else 'NOT SET'}")
    print(f"  GOOGLE_PLACE_URL: {config.GOOGLE_PLACE_URL[:50]}..." if config.GOOGLE_PLACE_URL else "  GOOGLE_PLACE_URL: NOT SET")
    print(f"  APIFY_ACTOR_ID: {config.APIFY_ACTOR_ID}")
    print()

    if not config.APIFY_API_TOKEN or not config.GOOGLE_PLACE_URL:
        print("ERROR: Missing required env vars. Set APIFY_API_TOKEN and GOOGLE_PLACE_URL")
        return None

    client = ApifyClient()
    print(f"Fetching up to {config.REVIEW_MAX_REVIEWS} reviews...")

    reviews = client.fetch_reviews(max_reviews=config.REVIEW_MAX_REVIEWS)

    print(f"\nFetched {len(reviews)} reviews from Apify")

    if reviews:
        print("\nSample reviews:")
        for r in reviews[:3]:
            print(f"  [{r.rating} star] {r.reviewer_name} ({r.published_at[:10]})")
            text_preview = r.text[:80].replace('\n', ' ') if r.text else "(no text)"
            print(f"    {text_preview}...")

        # Show rating distribution
        ratings = {}
        for r in reviews:
            ratings[r.rating] = ratings.get(r.rating, 0) + 1
        print(f"\nRating distribution: {dict(sorted(ratings.items(), reverse=True))}")

        # Show 5-star reviews (what gets published)
        five_star = [r for r in reviews if r.rating == 5]
        print(f"5-star reviews (will be published): {len(five_star)}")

    return reviews


def send_slack_summary(target_date: date):
    """Send Slack summary for the given date.

    Waits briefly for worker to process reviews, then sends summary.
    """
    import time

    print("\n" + "=" * 60)
    print("SENDING SLACK SUMMARY")
    print("=" * 60)

    # Wait for worker to process reviews from Kafka
    print("Waiting 5 seconds for worker to process reviews...")
    time.sleep(5)

    # Load counts from database
    repo = SupabaseRepo()
    counts = repo.get_daily_counts(target_date)

    print(f"Found {len(counts)} employees with mentions for {target_date}")
    for name, count in counts:
        print(f"  {name}: {count}")

    if not counts:
        print("No employee mentions found in database.")
        print("Make sure the worker is running to consume from Kafka.")
        return

    # Send to Slack
    slack = SlackNotifier()
    success = slack.send_daily_review_summary(counts, target_date)

    if success:
        print(f"\nSlack summary sent for {target_date}")
    else:
        print("\nFailed to send Slack summary. Check SLACK_WEBHOOK_URL.")


def show_employee_preview(reviews, target_date: date | None = None):
    """Show employee mention preview for reviews."""
    print("\n" + "=" * 60)
    print("EMPLOYEE MENTIONS PREVIEW")
    print("=" * 60)

    if target_date:
        print(f"  Filtering for date: {target_date}")

    counts, reviews_with_mentions = preview_employee_mentions(reviews, target_date)

    if not counts:
        print("\nNo employee mentions found in 5-star reviews.")
        return counts

    total = sum(counts.values())
    print(f"\nTotal employee mentions: {total}")
    print(f"Employees mentioned: {len(counts)}")
    print("\nBreakdown:")

    for name, count in counts.items():
        bar = "#" * min(count, 30)
        print(f"  {name:12} : {count:3} {bar}")

    # Show sample reviews with mentions
    print("\nSample reviews with mentions:")
    for review, employee in reviews_with_mentions[:5]:
        text_preview = (review.text or "")[:60].replace('\n', ' ')
        print(f"  [{employee}] {review.reviewer_name}: {text_preview}...")

    return counts


def test_full_pipeline(reviews=None, target_date: date | None = None):
    """Test the full pipeline with Kafka.

    Args:
        reviews: Prefetched reviews to use (avoids extra Apify call).
        target_date: Specific date to publish. If None, loops through last 7 days.
    """
    print("\n" + "=" * 60)
    print("STEP 3: Publishing to Kafka")
    print("=" * 60)

    print(f"  KAFKA_BOOTSTRAP_SERVERS: {config.KAFKA_BOOTSTRAP_SERVERS}")
    print(f"  KAFKA_REVIEWS_TOPIC: {config.KAFKA_REVIEWS_TOPIC}")
    if target_date:
        print(f"  Target date: {target_date}")
    print()

    fetcher = ReviewFetcher()

    # If no reviews were passed, fetch them once
    if reviews is None:
        print("No prefetched reviews, fetching from Apify...")
        client = ApifyClient()
        reviews = client.fetch_reviews(max_reviews=config.REVIEW_MAX_REVIEWS)
        print(f"Fetched {len(reviews)} reviews")

    # If specific date provided, only try that date
    if target_date:
        dates_to_try = [target_date]
    else:
        # Try filtering for recent dates (reviews might not exist for today)
        dates_to_try = [date.today() - timedelta(days=i) for i in range(7)]

    for target in dates_to_try:
        print(f"Trying {target}...")

        try:
            # Use prefetched reviews to avoid redundant Apify calls
            count = fetcher.fetch_and_publish_daily(
                target_date=target,
                prefetched_reviews=reviews,
            )
            if count > 0:
                print(f"\nSUCCESS: Published {count} reviews for {target} to Kafka topic '{config.KAFKA_REVIEWS_TOPIC}'")
                return True
            elif target_date:
                # Specific date requested but no reviews found
                print(f"\nNo 5-star reviews found for {target}")
                return False
        except Exception as e:
            print(f"  Error: {e}")

    print("\nNo 5-star reviews found in the last 7 days to publish")
    return False


def main():
    parser = argparse.ArgumentParser(
        description="Test the review scraper pipeline (Apify -> Kafka)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python test_full_pipeline.py                    # Auto-search last 7 days
  python test_full_pipeline.py --date yesterday   # Test yesterday only
  python test_full_pipeline.py --date 2026-02-18  # Test specific date
  python test_full_pipeline.py --no-kafka         # Apify fetch only
  python test_full_pipeline.py --yes              # Skip confirmation
  python test_full_pipeline.py --slack            # Send Slack summary after publish
        """,
    )
    parser.add_argument(
        "--date", "-d",
        type=str,
        help="Target date: 'today', 'yesterday', or ISO format (2026-02-18)",
    )
    parser.add_argument(
        "--no-kafka",
        action="store_true",
        help="Only test Apify fetch, skip Kafka publishing",
    )
    parser.add_argument(
        "--yes", "-y",
        action="store_true",
        help="Skip confirmation prompt",
    )
    parser.add_argument(
        "--slack",
        action="store_true",
        help="Send Slack summary after publishing (waits for worker to process)",
    )
    args = parser.parse_args()

    # Parse target date if provided
    target_date = None
    if args.date:
        try:
            target_date = parse_date(args.date)
            print(f"Target date: {target_date}")
        except ValueError:
            print(f"ERROR: Invalid date '{args.date}'. Use 'today', 'yesterday', or YYYY-MM-DD format.")
            return 1

    print("Review Scraper Pipeline Test")
    print("=" * 60)
    print()

    # Test Apify
    reviews = test_apify_only()

    if reviews is None:
        print("\nAborting: Apify test failed")
        return 1

    if not reviews:
        print("\nNo reviews fetched from Apify. Check your GOOGLE_PLACE_URL.")
        return 1

    # Show employee mentions preview
    show_employee_preview(reviews, target_date)

    # Exit early if --no-kafka
    if args.no_kafka:
        print("\n--no-kafka flag set, skipping Kafka publishing")
        print("=" * 60)
        return 0

    # Test full pipeline (publish to Kafka)
    if not args.yes:
        input("\nPress Enter to publish to Kafka...")

    success = test_full_pipeline(reviews, target_date=target_date)

    if success:
        print("\n" + "=" * 60)
        print("Pipeline test PASSED")
        print("=" * 60)

        # Send Slack summary if requested
        if args.slack:
            send_slack_summary(target_date or date.today())

        return 0
    else:
        print("\n" + "=" * 60)
        print("Pipeline test completed (no reviews published)")
        print("=" * 60)
        return 0


if __name__ == "__main__":
    sys.exit(main())
