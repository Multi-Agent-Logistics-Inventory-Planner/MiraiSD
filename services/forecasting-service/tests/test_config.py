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

    def test_censored_demand_disabled_by_default(self):
        assert hasattr(config, "CENSORED_DEMAND_ENABLED")
        assert config.CENSORED_DEMAND_ENABLED is False

    def test_dow_multiplier_floor_default(self):
        assert hasattr(config, "DOW_MULTIPLIER_FLOOR")
        # 0.0 = disabled by default; live backtest 2026-05-31 showed the 0.2
        # default cost ~1.1pp WAPE on this substrate.
        assert config.DOW_MULTIPLIER_FLOOR == 0.0

    def test_event_multiplier_cap_default(self):
        assert hasattr(config, "EVENT_MULTIPLIER_CAP")
        assert config.EVENT_MULTIPLIER_CAP == 2.0

    def test_shrinkage_disabled_by_default(self):
        assert hasattr(config, "SHRINKAGE_ENABLED")
        assert config.SHRINKAGE_ENABLED is False
        assert config.SHRINKAGE_STRENGTH == 10.0
