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

# Estimator selection. Default is "dow_weighted_events" -- the Phase 4 backtest
# (run 2026-05-29) showed it beats plain dow_weighted on lead-time WAPE
# (84.18% -> 81.77%, -2.4pp) by learning a global demand uplift multiplier
# for SHIPMENT_RECEIPT and DISPLAY_SET events in the prior 7 days and
# applying it to lead-time demand. Plain dow_weighted and tsb stay selectable
# via env var: dow_weighted as the rollback ("turn events off") and tsb for
# re-evaluation when more history or exogenous features land. The Phase 1
# decision to hold tsb on this data substrate still applies.
FORECAST_METHOD = os.getenv("FORECAST_METHOD", "dow_weighted_events")

# TSB smoothing constants. 0.1 is the textbook default. Higher alpha makes
# the sale probability decay faster when a series goes cold; higher beta
# makes the sale-day demand react faster to recent sale sizes.
TSB_ALPHA = float(os.getenv("TSB_ALPHA", "0.1"))
TSB_BETA = float(os.getenv("TSB_BETA", "0.1"))

# Demand-regime routing for safety stock distribution.
# CV computed from sale-day-only consumption over CV_WINDOW_DAYS. Hysteresis
# avoids day-to-day regime flips around the boundary: only move to "bursty"
# once CV exceeds CV_THRESHOLD_HIGH, only move to "steady" once it drops
# below CV_THRESHOLD_LOW. SKUs in the band keep their prior regime.
CV_WINDOW_DAYS = int(os.getenv("CV_WINDOW_DAYS", "60"))
CV_THRESHOLD_LOW = float(os.getenv("CV_THRESHOLD_LOW", "0.9"))
CV_THRESHOLD_HIGH = float(os.getenv("CV_THRESHOLD_HIGH", "1.1"))
# Default regime for items with no prior state and CV inside the deadband.
# "bursty" is the safer side -- NegBin gives a larger buffer than Poisson, so
# the worst case from misclassification is over-buffering, not stockout.
CV_DEFAULT_REGIME = os.getenv("CV_DEFAULT_REGIME", "bursty")

# Policy defaults
SERVICE_LEVEL_DEFAULT = float(os.getenv("SERVICE_LEVEL_DEFAULT", "0.95"))
LEAD_TIME_STD_DEFAULT_DAYS = float(os.getenv("LEAD_TIME_STD_DEFAULT_DAYS", "2.0"))
LEAD_TIME_GLOBAL_FALLBACK_DAYS = float(os.getenv("LEAD_TIME_GLOBAL_FALLBACK_DAYS", "11.0"))
EPSILON_MU = float(os.getenv("EPSILON_MU", "0.1"))

# Dynamic lead time from shipment history
LEAD_TIME_LOOKBACK_MONTHS = int(os.getenv("LEAD_TIME_LOOKBACK_MONTHS", "6"))
LEAD_TIME_MIN_SHIPMENTS = int(os.getenv("LEAD_TIME_MIN_SHIPMENTS", "2"))
LEAD_TIME_MAX_SHIPMENTS = int(os.getenv("LEAD_TIME_MAX_SHIPMENTS", "10"))

# Rolling backtest accuracy
BACKTEST_HORIZON_DAYS = int(os.getenv("BACKTEST_HORIZON_DAYS", "14"))
MAPE_EPSILON = float(os.getenv("MAPE_EPSILON", "0.1"))

# Confidence formula: enable MAPE blending after 60-90 days of history
CONFIDENCE_MAPE_ENABLED = os.getenv("CONFIDENCE_MAPE_ENABLED", "false").lower() == "true"

# Stockout correction: minimum in-stock training days required to filter
# stockout days. If an item has fewer in-stock days than this, all days
# (including stockouts) are used to avoid noisy estimates from tiny samples.
MIN_IN_STOCK_DAYS = int(os.getenv("MIN_IN_STOCK_DAYS", "7"))

# Backtest evaluation: minimum in-stock days in test window required to
# include an item-origin pair in accuracy metrics. Prevents measuring
# predictions against unobservable demand in stockout-heavy test windows.
MIN_TEST_IN_STOCK_DAYS = int(os.getenv("MIN_TEST_IN_STOCK_DAYS", "3"))

# Stockout filter: disabled by default. The detector in features.py was
# rewritten 2026-05-29 to use start-of-day-plus-peak inventory (was end-of-day,
# which mis-flagged sellout days as stockouts). Backtest with the fix enabled
# showed the overall WAPE moves <0.1pp because most bias is on items with
# stable inventory and intermittent demand -- not on items whose forward
# demand the filter could meaningfully correct. Chronic-stockout SKUs also
# over-correct under the filter (predicted demand inflates 3-4x vs realized).
# Leave off unless you've added exogenous features that disentangle "no
# demand" from "no inventory".
STOCKOUT_FILTER_ENABLED = os.getenv("STOCKOUT_FILTER_ENABLED", "false").lower() == "true"

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
KAFKA_DLQ_TOPIC = os.getenv("KAFKA_DLQ_TOPIC", "inventory-changes.DLQ")

# Real-time batching
BATCH_WINDOW_SECONDS = int(os.getenv("BATCH_WINDOW_SECONDS", "30"))
BATCH_SIZE_TRIGGER = int(os.getenv("BATCH_SIZE_TRIGGER", "50"))
ITEM_DEBOUNCE_SECONDS = float(os.getenv("ITEM_DEBOUNCE_SECONDS", "5.0"))

# Kafka consumer tuning
KAFKA_FETCH_MIN_BYTES = int(os.getenv("KAFKA_FETCH_MIN_BYTES", "1"))
KAFKA_MAX_POLL_RECORDS = int(os.getenv("KAFKA_MAX_POLL_RECORDS", "500"))
KAFKA_SESSION_TIMEOUT_MS = int(os.getenv("KAFKA_SESSION_TIMEOUT_MS", "30000"))
KAFKA_HEARTBEAT_INTERVAL_MS = int(os.getenv("KAFKA_HEARTBEAT_INTERVAL_MS", "10000"))
