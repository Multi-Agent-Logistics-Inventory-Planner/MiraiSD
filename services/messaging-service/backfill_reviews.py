#!/usr/bin/env python3
"""Backfill reviews for a date range or entire month.

Usage:
    # Backfill February 2026 (writes directly to DB)
    python backfill_reviews.py --month 2 --year 2026 --direct

    # Backfill with custom max reviews
    python backfill_reviews.py --month 2 --year 2026 --max-reviews 1000 --direct

    # Backfill a specific date range
    python backfill_reviews.py --start 2026-02-01 --end 2026-02-28 --direct

    # Preview only (no DB/Kafka write)
    python backfill_reviews.py --month 2 --year 2026 --dry-run

    # Use Kafka (requires Kafka to be running)
    python backfill_reviews.py --month 2 --year 2026

    # Skip confirmation prompt
    python backfill_reviews.py --month 2 --year 2026 --direct --yes
"""

from __future__ import annotations

import argparse
import calendar
import json
import sys
from collections import defaultdict
from datetime import date, datetime, timedelta
from pathlib import Path

# Add src to path
sys.path.insert(0, str(Path(__file__).parent))

from src.application.review_fetcher import ReviewFetcher
from src.application.review_processor import ReviewProcessor
from src.adapters.apify_client import ApifyClient
from src.adapters.supabase_repo import SupabaseRepo
from src import config


def save_reviews_to_file(
    reviews,
    start_date: date,
    end_date: date,
    by_date: dict,
    employee_totals: dict,
    output_dir: Path | None = None,
) -> Path:
    """Save all reviews to a JSON file.

    Args:
        reviews: List of Review objects from Apify.
        start_date: Start of date range.
        end_date: End of date range.
        by_date: Dict of date -> list of (review, employee) tuples.
        employee_totals: Dict of employee -> total count.
        output_dir: Directory to save file (defaults to script directory).

    Returns:
        Path to the saved file.
    """
    if output_dir is None:
        output_dir = Path(__file__).parent

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"reviews_{start_date}_{end_date}_{timestamp}.json"
    filepath = output_dir / filename

    # Build output data
    output = {
        "metadata": {
            "start_date": start_date.isoformat(),
            "end_date": end_date.isoformat(),
            "generated_at": datetime.now().isoformat(),
            "total_reviews_fetched": len(reviews),
            "five_star_reviews": len([r for r in reviews if r.rating == 5]),
        },
        "summary": {
            "total_employee_mentions": sum(employee_totals.values()),
            "days_with_mentions": len(by_date),
            "by_employee": dict(sorted(employee_totals.items(), key=lambda x: -x[1])),
        },
        "reviews_by_date": {},
        "all_reviews": [],
    }

    # Add reviews by date with employee mentions
    for d in sorted(by_date.keys()):
        date_str = d.isoformat()
        output["reviews_by_date"][date_str] = [
            {
                "employee": employee,
                "reviewer_name": review.reviewer_name,
                "rating": review.rating,
                "text": review.text,
                "published_at": review.published_at,
                "external_id": review.external_id,
            }
            for review, employee in by_date[d]
        ]

    # Add all fetched reviews
    for r in reviews:
        output["all_reviews"].append({
            "external_id": r.external_id,
            "reviewer_name": r.reviewer_name,
            "rating": r.rating,
            "text": r.text,
            "published_at": r.published_at,
        })

    # Write to file
    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)

    return filepath


def get_date_range(
    start: date | None = None,
    end: date | None = None,
    month: int | None = None,
    year: int | None = None,
) -> tuple[date, date]:
    """Get start and end dates from arguments.

    Args:
        start: Start date (takes precedence).
        end: End date (takes precedence).
        month: Month number (1-12).
        year: Year (e.g., 2026).

    Returns:
        Tuple of (start_date, end_date).
    """
    if start and end:
        return start, end

    if month and year:
        first_day = date(year, month, 1)
        last_day = date(year, month, calendar.monthrange(year, month)[1])
        return first_day, last_day

    raise ValueError("Must provide either --start/--end or --month/--year")


def preview_reviews_by_date(reviews, start_date: date, end_date: date):
    """Preview reviews grouped by date with employee mentions.

    Args:
        reviews: List of Review objects from Apify.
        start_date: Start of date range.
        end_date: End of date range.

    Returns:
        Dict of date -> list of (review, employee) tuples.
    """
    processor = ReviewProcessor()
    processor.load_employee_mappings()

    by_date: dict[date, list] = defaultdict(list)
    current = start_date

    while current <= end_date:
        date_str = current.strftime("%Y-%m-%d")
        for r in reviews:
            if r.published_at.startswith(date_str) and r.rating == 5:
                employee = processor.extract_employee(r.text)
                if employee:
                    by_date[current].append((r, employee))
        current += timedelta(days=1)

    return by_date


def backfill_direct_to_db(
    reviews,
    by_date: dict[date, list],
) -> dict[date, int]:
    """Write reviews directly to database, bypassing Kafka.

    Args:
        reviews: List of Review objects from Apify.
        by_date: Dict of date -> list of (review, employee) tuples.

    Returns:
        Dict of date -> count of reviews stored.
    """
    print("\n" + "=" * 60)
    print("WRITING DIRECTLY TO DATABASE")
    print("=" * 60)

    repo = SupabaseRepo()
    results: dict[date, int] = {}

    for d in sorted(by_date.keys()):
        mentions = by_date[d]
        if not mentions:
            continue

        count = 0
        for review, employee in mentions:
            review_date = date.fromisoformat(review.published_at.split("T")[0])

            # Store the individual review
            review_id = repo.create_review(
                employee_name=employee,
                external_id=review.external_id,
                review_date=review_date,
                review_text=review.text,
                rating=review.rating,
                reviewer_name=review.reviewer_name,
            )

            if review_id:
                # Update daily count
                repo.increment_daily_count(employee, review_date)
                count += 1

        if count > 0:
            print(f"  {d}: {count} reviews stored")
            results[d] = count

    print(f"\nTotal: {sum(results.values())} reviews stored across {len(results)} days")
    return results


def backfill_date_range(
    start_date: date,
    end_date: date,
    max_reviews: int = 1000,
    dry_run: bool = False,
    direct: bool = False,
) -> dict[date, int]:
    """Backfill reviews for a date range.

    Args:
        start_date: Start of date range (inclusive).
        end_date: End of date range (inclusive).
        max_reviews: Maximum reviews to fetch from Apify.
        dry_run: If True, preview only without publishing.
        direct: If True, write directly to DB instead of using Kafka.

    Returns:
        Dict of date -> count of reviews published.
    """
    print("=" * 60)
    print(f"BACKFILL: {start_date} to {end_date}")
    print("=" * 60)
    print(f"  Max reviews to fetch: {max_reviews}")
    print(f"  Dry run: {dry_run}")
    print(f"  Direct to DB: {direct}")
    print()

    # Fetch all reviews once from Apify
    print(f"Fetching up to {max_reviews} reviews from Apify...")
    client = ApifyClient()
    reviews = client.fetch_reviews(max_reviews=max_reviews)
    print(f"Fetched {len(reviews)} reviews total")

    # Show rating distribution
    ratings: dict[int, int] = defaultdict(int)
    for r in reviews:
        ratings[r.rating] += 1
    print(f"Rating distribution: {dict(sorted(ratings.items(), reverse=True))}")

    five_star = [r for r in reviews if r.rating == 5]
    print(f"5-star reviews: {len(five_star)}")

    # Preview by date
    print("\n" + "=" * 60)
    print("PREVIEW BY DATE")
    print("=" * 60)

    by_date = preview_reviews_by_date(reviews, start_date, end_date)

    total_mentions = 0
    employee_totals: dict[str, int] = defaultdict(int)

    for d in sorted(by_date.keys()):
        mentions = by_date[d]
        if mentions:
            print(f"\n{d}: {len(mentions)} employee mentions")
            for review, employee in mentions:
                employee_totals[employee] += 1
                total_mentions += 1
                text_preview = (review.text or "")[:50].replace('\n', ' ')
                print(f"  [{employee}] {review.reviewer_name}: {text_preview}...")

    # Summary
    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"Total employee mentions: {total_mentions}")
    print(f"Days with mentions: {len(by_date)}")
    print("\nBy employee:")
    for name, count in sorted(employee_totals.items(), key=lambda x: -x[1]):
        bar = "#" * min(count, 30)
        print(f"  {name:15} : {count:3} {bar}")

    # Save reviews to file
    filepath = save_reviews_to_file(
        reviews=reviews,
        start_date=start_date,
        end_date=end_date,
        by_date=by_date,
        employee_totals=employee_totals,
    )
    print(f"\nReviews saved to: {filepath}")

    if dry_run:
        print("\n[DRY RUN] No reviews published")
        return {}

    # Direct mode: write straight to database
    if direct:
        results = backfill_direct_to_db(reviews, by_date)
        print("\n" + "=" * 60)
        print(f"DONE: Stored {sum(results.values())} reviews across {len(results)} days")
        print("=" * 60)
        return results

    # Kafka mode: publish to Kafka for worker to process
    print("\n" + "=" * 60)
    print("PUBLISHING TO KAFKA")
    print("=" * 60)

    fetcher = ReviewFetcher()
    results: dict[date, int] = {}
    current = start_date

    while current <= end_date:
        try:
            count = fetcher.fetch_and_publish_daily(
                target_date=current,
                prefetched_reviews=reviews,
            )
            if count > 0:
                print(f"  {current}: {count} reviews published")
                results[current] = count
        except Exception as e:
            print(f"  {current}: ERROR - {e}")

        current += timedelta(days=1)

    print("\n" + "=" * 60)
    print(f"DONE: Published {sum(results.values())} reviews across {len(results)} days")
    print("=" * 60)

    return results


def main():
    parser = argparse.ArgumentParser(
        description="Backfill reviews for a date range or month",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python backfill_reviews.py --month 2 --year 2026
  python backfill_reviews.py --month 2 --year 2026 --max-reviews 1000
  python backfill_reviews.py --start 2026-02-01 --end 2026-02-28
  python backfill_reviews.py --month 2 --year 2026 --dry-run
  python backfill_reviews.py --month 2 --year 2026 --yes
        """,
    )

    # Date range options
    parser.add_argument(
        "--start", "-s",
        type=str,
        help="Start date (YYYY-MM-DD format)",
    )
    parser.add_argument(
        "--end", "-e",
        type=str,
        help="End date (YYYY-MM-DD format)",
    )
    parser.add_argument(
        "--month", "-m",
        type=int,
        choices=range(1, 13),
        metavar="MONTH",
        help="Month number (1-12)",
    )
    parser.add_argument(
        "--year", "-Y",
        type=int,
        help="Year (e.g., 2026)",
    )

    # Other options
    parser.add_argument(
        "--max-reviews", "-M",
        type=int,
        default=1000,
        help="Maximum reviews to fetch from Apify (default: 1000)",
    )
    parser.add_argument(
        "--dry-run", "-n",
        action="store_true",
        help="Preview only, don't publish to Kafka or DB",
    )
    parser.add_argument(
        "--direct", "-D",
        action="store_true",
        help="Write directly to database (bypasses Kafka, use when Kafka is unavailable)",
    )
    parser.add_argument(
        "--yes", "-y",
        action="store_true",
        help="Skip confirmation prompt",
    )

    args = parser.parse_args()

    # Validate arguments
    has_range = args.start and args.end
    has_month = args.month and args.year

    if not has_range and not has_month:
        parser.error("Must provide either --start/--end or --month/--year")

    if has_range and has_month:
        parser.error("Cannot use both --start/--end and --month/--year")

    # Parse dates
    try:
        if has_range:
            start_date = date.fromisoformat(args.start)
            end_date = date.fromisoformat(args.end)
        else:
            start_date, end_date = get_date_range(month=args.month, year=args.year)
    except ValueError as e:
        parser.error(f"Invalid date: {e}")

    if start_date > end_date:
        parser.error("Start date must be before or equal to end date")

    # Confirm
    print(f"Backfill reviews from {start_date} to {end_date}")
    print(f"Max reviews: {args.max_reviews}")

    if not args.yes and not args.dry_run:
        response = input("\nProceed? [y/N] ")
        if response.lower() != "y":
            print("Aborted.")
            return 1

    # Run backfill
    results = backfill_date_range(
        start_date=start_date,
        end_date=end_date,
        max_reviews=args.max_reviews,
        dry_run=args.dry_run,
        direct=args.direct,
    )

    return 0


if __name__ == "__main__":
    sys.exit(main())
