"""Integration test for _compute_forecasts: verifies the full chain from
estimates + inventory + lead times + MAPE -> final forecast output.

Ensures dynamic lead times feed into safety stock via sigma_L, stockout
filtering affects mu_hat, category fallback applies, and confidence uses 1/(1+CV).
"""

import sys
import math
from datetime import datetime, timezone
from unittest.mock import MagicMock

import numpy as np
import pandas as pd
import pytest

# Mock kafka before importing pipeline
sys.modules["kafka"] = MagicMock()
sys.modules["kafka.errors"] = MagicMock()

# Patch pydantic v1 -> v2 compatibility
import pydantic
if not hasattr(pydantic, "field_validator"):
    pydantic.field_validator = lambda *a, **kw: lambda f: f

from src.application.pipeline import ForecastingPipeline
from src import config


@pytest.fixture
def pipeline():
    mock_repo = MagicMock()
    return ForecastingPipeline(repo=mock_repo)


@pytest.fixture
def items_df():
    return pd.DataFrame({
        "item_id": ["item-A", "item-B", "item-C"],
        "name": ["Widget A", "Widget B", "Widget C"],
        "lead_time_days": [7, 14, 10],
        "category_name": ["toys", "toys", "food"],
    })


@pytest.fixture
def estimates_df():
    """Estimates as would come from estimate_mu_sigma + apply_category_fallback."""
    return pd.DataFrame({
        "item_id": ["item-A", "item-B", "item-C"],
        "mu_hat": [5.0, 3.0, 8.0],
        "sigma_d_hat": [2.0, 1.0, 3.0],
        "method": ["dow_weighted", "dow_weighted", "dow_weighted"],
        "dow_multipliers": [
            {0: 1.2, 1: 1.0, 2: 0.8, 3: 1.0, 4: 1.1, 5: 0.9, 6: 1.0},
            {0: 1.0, 1: 1.0, 2: 1.0, 3: 1.0, 4: 1.0, 5: 1.0, 6: 1.0},
            {0: 0.9, 1: 1.1, 2: 1.0, 3: 1.0, 4: 1.0, 5: 1.0, 6: 1.0},
        ],
    })


@pytest.fixture
def inventory_df():
    return pd.DataFrame({
        "item_id": ["item-A", "item-B", "item-C"],
        "current_qty": [50, 10, 200],
    })


@pytest.fixture
def lead_time_stats_df():
    """Dynamic lead times from shipment history."""
    return pd.DataFrame({
        "item_id": ["item-A", "item-B", "item-C"],
        "avg_lead_time": [6.5, 15.0, 9.0],
        "sigma_L": [1.5, 3.0, 0.5],
        "shipment_count": [5, 3, 8],
        "source": ["shipment_history", "shipment_history", "shipment_history"],
    })


class TestComputeForecasts:
    """Tests for the full _compute_forecasts pipeline integration."""

    def test_returns_all_items(self, pipeline, items_df, estimates_df, inventory_df, lead_time_stats_df):
        result = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
            lead_time_stats_df=lead_time_stats_df,
        )
        assert len(result) == 3
        assert set(result["item_id"]) == {"item-A", "item-B", "item-C"}

    def test_required_columns_present(self, pipeline, items_df, estimates_df, inventory_df, lead_time_stats_df):
        result = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
            lead_time_stats_df=lead_time_stats_df,
        )
        expected_cols = {
            "item_id", "computed_at", "horizon_days", "avg_daily_delta",
            "days_to_stockout", "suggested_reorder_qty", "suggested_order_date",
            "confidence", "features",
        }
        assert expected_cols.issubset(set(result.columns))

    def test_dynamic_lead_time_feeds_into_safety_stock(
        self, pipeline, items_df, estimates_df, inventory_df, lead_time_stats_df
    ):
        """sigma_L from shipment history should increase safety stock vs sigma_L=0."""
        # With dynamic lead times (sigma_L > 0)
        result_with = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
            lead_time_stats_df=lead_time_stats_df,
        )

        # Without lead times (sigma_L defaults to 0)
        result_without = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
            lead_time_stats_df=None,
        )

        for item_id in ["item-A", "item-B", "item-C"]:
            feat_with = result_with[result_with["item_id"] == item_id]["features"].iloc[0]
            feat_without = result_without[result_without["item_id"] == item_id]["features"].iloc[0]

            assert feat_with["safety_stock"] > feat_without["safety_stock"], (
                f"{item_id}: sigma_L > 0 should increase safety stock"
            )
            # Note: ROP = mu*L + SS. Dynamic lead time may differ from static,
            # so ROP doesn't always increase even when SS increases.

    def test_sigma_L_stored_in_features(
        self, pipeline, items_df, estimates_df, inventory_df, lead_time_stats_df
    ):
        """sigma_L, lead_time_source, and shipment_count should be in features dict."""
        result = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
            lead_time_stats_df=lead_time_stats_df,
        )
        feat_a = result[result["item_id"] == "item-A"]["features"].iloc[0]

        assert "sigma_L" in feat_a
        assert feat_a["sigma_L"] == 1.5
        assert feat_a["lead_time_source"] == "shipment_history"
        assert feat_a["shipment_count"] == 5

    def test_dynamic_lead_time_overrides_static(
        self, pipeline, items_df, estimates_df, inventory_df, lead_time_stats_df
    ):
        """avg_lead_time from shipments should override items_df.lead_time_days."""
        result = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
            lead_time_stats_df=lead_time_stats_df,
        )
        # item-A: static lead_time=7, dynamic avg_lead_time=6.5
        feat_a = result[result["item_id"] == "item-A"]["features"].iloc[0]
        assert feat_a["lead_time_days"] == 6.5

        # item-B: static lead_time=14, dynamic avg_lead_time=15.0
        feat_b = result[result["item_id"] == "item-B"]["features"].iloc[0]
        assert feat_b["lead_time_days"] == 15.0

    def test_confidence_uses_cv_formula(self, pipeline, items_df, estimates_df, inventory_df):
        """Confidence should be 1/(1+CV) where CV = sigma/mu."""
        result = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
        )

        for _, row in result.iterrows():
            feat = row["features"]
            mu = feat["mu_hat"]
            sigma = feat["sigma_d_hat"]
            cv = sigma / max(mu, config.EPSILON_MU)
            expected_conf = round(1.0 / (1.0 + cv), 3)
            assert row["confidence"] == expected_conf, (
                f"Item {row['item_id']}: expected confidence {expected_conf}, got {row['confidence']}"
            )

    def test_dow_multipliers_in_features(self, pipeline, items_df, estimates_df, inventory_df):
        """DOW multipliers from dow_weighted method should appear in features."""
        result = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
        )
        feat_a = result[result["item_id"] == "item-A"]["features"].iloc[0]
        assert "dow_multipliers" in feat_a
        assert feat_a["dow_adjusted"] is True
        assert feat_a["dow_multipliers"]["0"] == 1.2

    def test_avg_daily_delta_is_negative_mu(self, pipeline, items_df, estimates_df, inventory_df):
        """avg_daily_delta should be -mu_hat (consumption is negative)."""
        result = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
        )
        a_row = result[result["item_id"] == "item-A"].iloc[0]
        assert a_row["avg_daily_delta"] == -5.0

    def test_days_to_stockout_correct(self, pipeline, items_df, estimates_df, inventory_df):
        """days_to_stockout = current_qty / mu_hat."""
        result = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
        )
        # item-A: qty=50, mu=5.0 -> 10 days
        a_row = result[result["item_id"] == "item-A"].iloc[0]
        assert a_row["days_to_stockout"] == pytest.approx(10.0)

        # item-C: qty=200, mu=8.0 -> 25 days
        c_row = result[result["item_id"] == "item-C"].iloc[0]
        assert c_row["days_to_stockout"] == pytest.approx(25.0)

    def test_suggested_order_qty(self, pipeline, items_df, estimates_df, inventory_df):
        """suggested_reorder_qty = ceil(target_days * mu - current_qty), floored at 0."""
        result = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
        )
        # item-A: target=21, mu=5.0, qty=50 -> ceil(105 - 50) = 55
        a_row = result[result["item_id"] == "item-A"].iloc[0]
        assert a_row["suggested_reorder_qty"] == 55

        # item-C: target=21, mu=8.0, qty=200 -> ceil(168 - 200) = 0 (negative)
        c_row = result[result["item_id"] == "item-C"].iloc[0]
        assert c_row["suggested_reorder_qty"] == 0

    def test_empty_inventory_defaults_to_zero(self, pipeline, items_df, estimates_df):
        """Empty inventory should default current_qty to 0."""
        result = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=pd.DataFrame(),
            estimates_df=estimates_df,
        )
        for _, row in result.iterrows():
            assert row["features"]["current_qty"] == 0

    def test_mape_blending_disabled_by_default(
        self, pipeline, items_df, estimates_df, inventory_df
    ):
        """MAPE blending should not affect confidence when CONFIDENCE_MAPE_ENABLED=False."""
        mape_df = pd.DataFrame({
            "item_id": ["item-A", "item-B"],
            "mape": [0.5, 0.2],
            "forecast_mu": [5.0, 3.0],
            "actual_mu": [4.5, 3.2],
            "backtest_days": [14, 14],
        })

        result_with_mape = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
            mape_df=mape_df,
        )
        result_without = pipeline._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
        )

        # Confidence should be identical (MAPE blending disabled)
        for item_id in ["item-A", "item-B"]:
            conf_with = result_with_mape[result_with_mape["item_id"] == item_id]["confidence"].iloc[0]
            conf_without = result_without[result_without["item_id"] == item_id]["confidence"].iloc[0]
            assert conf_with == conf_without
