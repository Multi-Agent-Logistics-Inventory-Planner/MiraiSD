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
# Floor on per-DOW multipliers. A SKU like Penguin-with-backpack with
# `[0,0,0,0,0.97,6.03,0]` predicts literal zero on weekdays. A nonzero
# floor (e.g. 0.2) enforces a minimum weekday baseline -- conceptually
# right but the 2026-05-31 ablation on live data showed it costs ~1.1pp
# overall lt-WAPE because most legitimately-weekend-only SKUs get
# inflated weekday predictions vs actual zero sales. Default 0.0
# (disabled); flip via env var if Penguin-class items become a problem.
DOW_MULTIPLIER_FLOOR = float(os.getenv("DOW_MULTIPLIER_FLOOR", "0.0"))
# Cap on globally-learned event-flag multipliers (recent_shipment_7d /
# recent_display_7d). Raw signal was ~5x for shipments, clipped at 3.0 in
# the prior config; lowered to 2.0 after the 6.6pp WAPE spike in 7d windows
# pointed at over-amplification of event days. Clipping is symmetric on the
# log scale so the lower bound is 1/cap = 0.5.
EVENT_MULTIPLIER_CAP = float(os.getenv("EVENT_MULTIPLIER_CAP", "2.0"))

# Hierarchical shrinkage: blend an item's noisy mu_hat toward its category
# prior with weight n / (n + k), where n is the item's observed sale-day
# count and k is SHRINKAGE_STRENGTH. With k=10, an item with 10 sale-days
# gets 50/50 blend with its category mean; an item with 80 sale-days keeps
# ~89% of its own estimate. Replaces the binary cliff in apply_category_fallback
# (which only kicks in for truly cold-start items) with a smooth curve that
# pulls thin-history SKUs toward the more reliable category-level signal.
# Default ON after 2026-05-31 backtest showed -0.9pp lt-WAPE on top of bias
# correction. Rollback: SHRINKAGE_ENABLED=false.
SHRINKAGE_ENABLED = os.getenv("SHRINKAGE_ENABLED", "true").lower() == "true"
SHRINKAGE_STRENGTH = float(os.getenv("SHRINKAGE_STRENGTH", "10.0"))
# Categories with fewer items than this provide a noisy prior, so we skip
# shrinkage for items in such categories rather than blending toward a
# 1-or-2-SKU mean.
SHRINKAGE_MIN_CATEGORY_ITEMS = int(os.getenv("SHRINKAGE_MIN_CATEGORY_ITEMS", "5"))

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

# Stockout treatment: replaced the prior "drop stockout days" filter with a
# right-censored Poisson MLE. On stockout days we know demand was AT LEAST
# the observed consumption (the store sold out); the MLE uses that partial
# information instead of either dropping the row (under-counts demand on
# stockout-prone SKUs) or taking it at face value (under-states peak demand).
# Detector in features.py uses start-of-day-plus-peak inventory; rewrite
# landed 2026-05-29. Default off until backtest validates; flip via env var
# for ~60s rollback. The MIN_STOCKOUT_DAYS knob gates the censored path so
# items with no stockouts skip the optimizer call (no-op).
CENSORED_DEMAND_ENABLED = os.getenv("CENSORED_DEMAND_ENABLED", "false").lower() == "true"
CENSORED_DEMAND_MIN_STOCKOUT_DAYS = int(os.getenv("CENSORED_DEMAND_MIN_STOCKOUT_DAYS", "1"))
# Stockout-fraction gate. The MLE is well-behaved when censored observations
# are a minority of the data (median bump ~1.1-1.7x in the 0-25% bucket on
# live 2026-05-31 data). Above ~50% stockout fraction the censored term
# dominates the likelihood and drags lambda to the optimizer's upper bound
# -- statistically correct for "what demand would have been with inventory"
# but useless for lt-WAPE since realized demand is bounded by inventory.
# Items above this threshold fall through to the naive mean.
CENSORED_DEMAND_MAX_STOCKOUT_PCT = float(os.getenv("CENSORED_DEMAND_MAX_STOCKOUT_PCT", "0.5"))

# Residual bias correction: subtracts the recent per-item signed forecast
# error (forecast_mu - actual_mu) from the next mu_hat before the policy layer.
# Textbook "bias-adjusted forecast" pattern for intermittent demand. Default
# ON after the 2026-05-31 live backtest showed -9.8pp lt-WAPE (87.96% ->
# 78.20%) on this substrate. Rollback is a single env-var flip
# (RESIDUAL_BIAS_CORRECTION_ENABLED=false). The cap is the maximum correction
# as a fraction of mu_hat (0.5 = +/-50%) so one outlier backtest window cannot
# drag a SKU's level to zero or double it. Items with fewer than
# MIN_BACKTEST_DAYS of measured history pass through uncorrected.
RESIDUAL_BIAS_CORRECTION_ENABLED = os.getenv("RESIDUAL_BIAS_CORRECTION_ENABLED", "true").lower() == "true"
RESIDUAL_BIAS_CORRECTION_CAP = float(os.getenv("RESIDUAL_BIAS_CORRECTION_CAP", "0.5"))
RESIDUAL_BIAS_MIN_BACKTEST_DAYS = int(os.getenv("RESIDUAL_BIAS_MIN_BACKTEST_DAYS", "7"))

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

# Demand-shape segmentation. Classifies each SKU as continuous / drop / dead /
# new from its 60d sales shape so the policy layer can stop applying the
# daily-mu math to products it is categorically wrong for (booster-box style
# "drops" that sell out in days, and dead tail items that pollute the at-risk
# list). Two flags: SEGMENTATION_ENABLED computes + persists labels
# (observe-only), SEGMENT_POLICY_ENABLED lets the labels change policy
# outputs. Roll out one at a time; each rollback is a single env flip.
SEGMENTATION_ENABLED = os.getenv("SEGMENTATION_ENABLED", "false").lower() == "true"
SEGMENT_POLICY_ENABLED = os.getenv("SEGMENT_POLICY_ENABLED", "false").lower() == "true"
# Drop entry/exit thresholds on top3_share (share of window units sold on the
# 3 biggest days). Hysteresis mirrors the CV regime deadband: enter drop at
# 0.80, stay until it falls below 0.60.
SEGMENT_TOP3_SHARE_ENTER = float(os.getenv("SEGMENT_TOP3_SHARE_ENTER", "0.80"))
SEGMENT_TOP3_SHARE_EXIT = float(os.getenv("SEGMENT_TOP3_SHARE_EXIT", "0.60"))
SEGMENT_DROP_MIN_UNITS = float(os.getenv("SEGMENT_DROP_MIN_UNITS", "10"))
SEGMENT_DROP_MAX_SALE_DAYS = int(os.getenv("SEGMENT_DROP_MAX_SALE_DAYS", "6"))
SEGMENT_DROP_MIN_STOCKOUT_FRAC = float(os.getenv("SEGMENT_DROP_MIN_STOCKOUT_FRAC", "0.3"))
# Dead: nothing sold in the window, or a 1-2 sale trickle that has been quiet
# for over four weeks.
SEGMENT_DEAD_DAYS_SINCE_SALE = float(os.getenv("SEGMENT_DEAD_DAYS_SINCE_SALE", "28"))
SEGMENT_DEAD_MAX_SALE_DAYS = int(os.getenv("SEGMENT_DEAD_MAX_SALE_DAYS", "2"))
SEGMENT_NEW_MAX_HISTORY_DAYS = int(os.getenv("SEGMENT_NEW_MAX_HISTORY_DAYS", "14"))
# Suggested reorder for drop items = mean of the last N drop sizes.
SEGMENT_DROP_AVG_LAST_N = int(os.getenv("SEGMENT_DROP_AVG_LAST_N", "2"))
# Max gap (consecutive zero-sale days) tolerated inside one drop cluster.
SEGMENT_DROP_CLUSTER_GAP_DAYS = int(os.getenv("SEGMENT_DROP_CLUSTER_GAP_DAYS", "2"))

# suggest_order v2: Q = ceil(mu*(L + target_days) + safety_stock - on_hand
# - on_order). The v1 formula ignores lead time and safety stock (it targets
# cycle stock only) and double-orders anything already inbound on a PENDING
# shipment. Default off until the backtest gate passes; single env-var
# rollback.
SUGGEST_ORDER_V2_ENABLED = os.getenv("SUGGEST_ORDER_V2_ENABLED", "false").lower() == "true"

# run_all chunking: the nightly full recompute processes items in chunks so
# one bad chunk (DB timeout, malformed rows) cannot abort the entire run and
# leave the remaining items stale.
RUN_ALL_CHUNK_SIZE = int(os.getenv("RUN_ALL_CHUNK_SIZE", "100"))

# Real-time batching
BATCH_WINDOW_SECONDS = int(os.getenv("BATCH_WINDOW_SECONDS", "30"))
BATCH_SIZE_TRIGGER = int(os.getenv("BATCH_SIZE_TRIGGER", "50"))
ITEM_DEBOUNCE_SECONDS = float(os.getenv("ITEM_DEBOUNCE_SECONDS", "5.0"))

# Kafka consumer tuning
KAFKA_FETCH_MIN_BYTES = int(os.getenv("KAFKA_FETCH_MIN_BYTES", "1"))
KAFKA_MAX_POLL_RECORDS = int(os.getenv("KAFKA_MAX_POLL_RECORDS", "500"))
KAFKA_SESSION_TIMEOUT_MS = int(os.getenv("KAFKA_SESSION_TIMEOUT_MS", "30000"))
KAFKA_HEARTBEAT_INTERVAL_MS = int(os.getenv("KAFKA_HEARTBEAT_INTERVAL_MS", "10000"))
