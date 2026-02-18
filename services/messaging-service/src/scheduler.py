"""APScheduler-based job scheduler for review tracking."""

from __future__ import annotations

import logging
from datetime import date

from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger

from . import config
from .adapters.apify_client import ApifyClient
from .adapters.kafka_producer import ReviewKafkaProducer
from .adapters.slack_notifier import SlackNotifier
from .adapters.supabase_repo import SupabaseRepo
from .application.review_fetcher import ReviewFetcher

logger = logging.getLogger(__name__)

# Module-level scheduler instance
_scheduler: BackgroundScheduler | None = None


def daily_review_fetch_job() -> None:
    """Run daily to fetch reviews from Apify and publish to Kafka.

    This job runs at the hour specified by REVIEW_FETCH_HOUR config.
    """
    logger.info("Starting daily review fetch job")

    try:
        fetcher = ReviewFetcher()
        count = fetcher.fetch_and_publish_daily()
        logger.info("Daily review fetch completed: %d reviews published", count)
    except Exception as e:
        logger.exception("Daily review fetch job failed: %s", e)


def daily_review_summary_job() -> None:
    """Run daily after fetch to send Slack summary of today's reviews.

    This runs 30 minutes after fetch to allow time for Kafka processing.
    """
    logger.info("Starting daily review summary job")

    try:
        repo = SupabaseRepo()
        slack = SlackNotifier()

        today = date.today()
        counts = repo.get_daily_counts(today)

        slack.send_daily_review_summary(counts, today)
        logger.info("Daily review summary sent: %d employees mentioned", len(counts))
    except Exception as e:
        logger.exception("Daily review summary job failed: %s", e)


def monthly_review_summary_job() -> None:
    """Run on 1st of month to report previous month's totals to Slack."""
    logger.info("Starting monthly review summary job")

    try:
        repo = SupabaseRepo()
        slack = SlackNotifier()

        # Get previous month
        today = date.today()
        if today.month == 1:
            year, month = today.year - 1, 12
        else:
            year, month = today.year, today.month - 1

        totals = repo.get_monthly_review_totals(year, month)
        slack.send_monthly_review_summary(totals, year, month)
        logger.info("Monthly review summary sent for %d-%02d", year, month)
    except Exception as e:
        logger.exception("Monthly review summary job failed: %s", e)


def start_scheduler() -> BackgroundScheduler:
    """Initialize and start the review scheduler.

    Returns:
        The BackgroundScheduler instance.
    """
    global _scheduler

    if _scheduler is not None and _scheduler.running:
        logger.warning("Scheduler already running")
        return _scheduler

    _scheduler = BackgroundScheduler()

    fetch_hour = config.REVIEW_FETCH_HOUR

    # Daily fetch job - runs at configured hour
    _scheduler.add_job(
        daily_review_fetch_job,
        CronTrigger(hour=fetch_hour, minute=0),
        id="daily_review_fetch",
        replace_existing=True,
        name="Daily Review Fetch",
    )
    logger.info("Scheduled daily review fetch at %02d:00", fetch_hour)

    # Daily summary job - runs 30 minutes after fetch
    summary_minute = 30
    _scheduler.add_job(
        daily_review_summary_job,
        CronTrigger(hour=fetch_hour, minute=summary_minute),
        id="daily_review_summary",
        replace_existing=True,
        name="Daily Review Summary",
    )
    logger.info("Scheduled daily review summary at %02d:%02d", fetch_hour, summary_minute)

    # Monthly summary job - 1st of month at 8 AM
    _scheduler.add_job(
        monthly_review_summary_job,
        CronTrigger(day=1, hour=8, minute=0),
        id="monthly_review_summary",
        replace_existing=True,
        name="Monthly Review Summary",
    )
    logger.info("Scheduled monthly review summary on 1st at 08:00")

    _scheduler.start()
    logger.info("Review scheduler started with %d jobs", len(_scheduler.get_jobs()))

    return _scheduler


def stop_scheduler() -> None:
    """Stop the review scheduler."""
    global _scheduler

    if _scheduler is not None:
        logger.info("Stopping review scheduler")
        _scheduler.shutdown(wait=False)
        _scheduler = None


def get_scheduler() -> BackgroundScheduler | None:
    """Get the current scheduler instance."""
    return _scheduler


def trigger_daily_fetch_now() -> None:
    """Manually trigger the daily fetch job (for testing/debugging)."""
    logger.info("Manually triggering daily review fetch")
    daily_review_fetch_job()


def trigger_daily_summary_now() -> None:
    """Manually trigger the daily summary job (for testing/debugging)."""
    logger.info("Manually triggering daily review summary")
    daily_review_summary_job()


def trigger_monthly_summary_now() -> None:
    """Manually trigger the monthly summary job (for testing/debugging)."""
    logger.info("Manually triggering monthly review summary")
    monthly_review_summary_job()
