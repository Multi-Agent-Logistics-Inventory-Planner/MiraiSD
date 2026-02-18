# config.py
"""Configuration settings for the messaging service."""
import os
from pathlib import Path

# Load environment variables from .env file if it exists
from dotenv import load_dotenv

# Load .env file from the project root (MiraiSD/)
env_path = Path(__file__).parent.parent.parent.parent / ".env"
load_dotenv(env_path)

# Kafka settings
KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
KAFKA_TOPIC = os.getenv("KAFKA_TOPIC", "inventory-changes")
KAFKA_CONSUMER_GROUP = os.getenv("KAFKA_CONSUMER_GROUP", "messaging-service")

# Kafka consumer tuning
KAFKA_FETCH_MIN_BYTES = int(os.getenv("KAFKA_FETCH_MIN_BYTES", "1"))
KAFKA_MAX_POLL_RECORDS = int(os.getenv("KAFKA_MAX_POLL_RECORDS", "500"))
KAFKA_SESSION_TIMEOUT_MS = int(os.getenv("KAFKA_SESSION_TIMEOUT_MS", "30000"))
KAFKA_HEARTBEAT_INTERVAL_MS = int(os.getenv("KAFKA_HEARTBEAT_INTERVAL_MS", "10000"))

# Supabase connection (for querying product data)
SUPABASE_DB_URL = os.getenv("SUPABASE_DB_URL", "")
SUPABASE_DB_USERNAME = os.getenv("SUPABASE_DB_USERNAME", "postgres")
SUPABASE_DB_PASSWORD = os.getenv("SUPABASE_DB_PASSWORD", "")

# Slack settings
SLACK_WEBHOOK_URL = os.getenv("SLACK_WEBHOOK_URL", "")
SLACK_CHANNEL = os.getenv("SLACK_CHANNEL", "#inventory-alerts")
SLACK_ENABLED = os.getenv("SLACK_ENABLED", "true").lower() == "true"

# Alert settings
ALERT_DEBOUNCE_SECONDS = float(os.getenv("ALERT_DEBOUNCE_SECONDS", "300.0"))  # 5 minutes default

# Location-level stock thresholds (for individual storage locations like box bins, machines)
LOCATION_LOW_STOCK_THRESHOLD = int(os.getenv("LOCATION_LOW_STOCK_THRESHOLD", "5"))
LOCATION_LOW_STOCK_RECOVERY = int(os.getenv("LOCATION_LOW_STOCK_RECOVERY", "7"))  # threshold + 2 for hysteresis

# Notification polling interval for Slack delivery (seconds)
NOTIFICATION_POLL_INTERVAL = float(os.getenv("NOTIFICATION_POLL_INTERVAL", "5.0"))

# API settings (optional health endpoint)
API_HOST = os.getenv("API_HOST", "0.0.0.0")
API_PORT = int(os.getenv("API_PORT", "5001"))

# Review tracking settings
KAFKA_REVIEWS_TOPIC = os.getenv("KAFKA_REVIEWS_TOPIC", "employee-reviews")
APIFY_API_TOKEN = os.getenv("APIFY_API_TOKEN", "")
APIFY_ACTOR_ID = os.getenv("APIFY_ACTOR_ID", "compass/google-maps-reviews-scraper")
GOOGLE_PLACE_URL = os.getenv("GOOGLE_PLACE_URL", "")
REVIEW_SLACK_CHANNEL = os.getenv("REVIEW_SLACK_CHANNEL", "#piggly-review")
REVIEW_FETCH_HOUR = int(os.getenv("REVIEW_FETCH_HOUR", "6"))
REVIEW_MAX_REVIEWS = int(os.getenv("REVIEW_MAX_REVIEWS", "100"))

