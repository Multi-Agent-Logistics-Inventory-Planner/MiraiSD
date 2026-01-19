# config.py
"""Configuration settings for the forecasting pipeline."""
import os
from pathlib import Path

# Paths
DATA_DIR = Path(os.getenv("DATA_DIR", "data"))
EVENTS_DIR = Path(os.getenv("EVENTS_DIR", "events"))
OUTPUT_DIR = Path(os.getenv("OUTPUT_DIR", "out"))

# Forecasting parameters
TARGET_DAYS = int(os.getenv("TARGET_DAYS", "21"))
REORDER_THRESHOLD = int(os.getenv("REORDER_THRESHOLD", "7"))
ROLLING_WINDOW = int(os.getenv("ROLLING_WINDOW", "14"))
ES_ALPHA = float(os.getenv("ES_ALPHA", "0.3"))
MU_FLOOR = float(os.getenv("MU_FLOOR", "0.1"))
SIGMA_FLOOR = float(os.getenv("SIGMA_FLOOR", "0.01"))

# Policy defaults
SERVICE_LEVEL_DEFAULT = float(os.getenv("SERVICE_LEVEL_DEFAULT", "0.95"))
LEAD_TIME_STD_DEFAULT_DAYS = float(os.getenv("LEAD_TIME_STD_DEFAULT_DAYS", "0.0"))
EPSILON_MU = float(os.getenv("EPSILON_MU", "0.1"))

# API settings
API_HOST = os.getenv("API_HOST", "0.0.0.0")
API_PORT = int(os.getenv("API_PORT", "5000"))

# Supabase connection
SUPABASE_DB_URL = os.getenv("SUPABASE_DB_URL", "")
SUPABASE_DB_USERNAME = os.getenv("SUPABASE_DB_USERNAME", "postgres")
SUPABASE_DB_PASSWORD = os.getenv("SUPABASE_DB_PASSWORD", "")

# Kafka settings
KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
KAFKA_TOPIC = os.getenv("KAFKA_TOPIC", "inventory-changes")
KAFKA_CONSUMER_GROUP = os.getenv("KAFKA_CONSUMER_GROUP", "forecasting-service")

# Real-time batching
BATCH_WINDOW_SECONDS = int(os.getenv("BATCH_WINDOW_SECONDS", "30"))
BATCH_SIZE_TRIGGER = int(os.getenv("BATCH_SIZE_TRIGGER", "50"))
ITEM_DEBOUNCE_SECONDS = float(os.getenv("ITEM_DEBOUNCE_SECONDS", "5.0"))

# Kafka consumer tuning
KAFKA_FETCH_MIN_BYTES = int(os.getenv("KAFKA_FETCH_MIN_BYTES", "1"))
KAFKA_MAX_POLL_RECORDS = int(os.getenv("KAFKA_MAX_POLL_RECORDS", "500"))
KAFKA_SESSION_TIMEOUT_MS = int(os.getenv("KAFKA_SESSION_TIMEOUT_MS", "30000"))
KAFKA_HEARTBEAT_INTERVAL_MS = int(os.getenv("KAFKA_HEARTBEAT_INTERVAL_MS", "10000"))
