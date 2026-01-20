# config.py
"""Configuration settings for the messaging service."""
import os

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

# API settings (optional health endpoint)
API_HOST = os.getenv("API_HOST", "0.0.0.0")
API_PORT = int(os.getenv("API_PORT", "5001"))

