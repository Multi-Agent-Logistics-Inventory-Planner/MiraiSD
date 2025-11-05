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
