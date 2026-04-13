"""Tests for configuration defaults."""

import sys
from unittest.mock import MagicMock

# Mock kafka before importing config
sys.modules["kafka"] = MagicMock()
sys.modules["kafka.errors"] = MagicMock()

from src import config


class TestConfigDefaults:
    def test_lead_time_std_default(self):
        assert config.LEAD_TIME_STD_DEFAULT_DAYS == 2.0

    def test_lead_time_global_fallback(self):
        assert hasattr(config, "LEAD_TIME_GLOBAL_FALLBACK_DAYS")
        assert config.LEAD_TIME_GLOBAL_FALLBACK_DAYS == 11.0

    def test_stockout_filter_disabled_by_default(self):
        assert hasattr(config, "STOCKOUT_FILTER_ENABLED")
        assert config.STOCKOUT_FILTER_ENABLED is False
