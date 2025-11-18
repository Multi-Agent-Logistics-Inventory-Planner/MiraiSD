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
