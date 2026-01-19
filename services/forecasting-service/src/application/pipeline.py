"""Forecasting pipeline orchestrating domain logic with Supabase data."""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import TYPE_CHECKING

import pandas as pd

from .. import config
from .. import features as feat
from .. import forecast as fc
from .. import policy
from ..adapters.supabase_repo import SupabaseRepo

if TYPE_CHECKING:
    pass

logger = logging.getLogger(__name__)


class ForecastingPipeline:
    """Orchestrates the forecasting pipeline for affected items.

    Pipeline steps:
    1. Load item metadata and current inventory from Supabase
    2. Load recent stock movements for the affected items
    3. Build daily usage features from movements
    4. Estimate demand parameters (mu, sigma)
    5. Compute reorder policy (safety stock, reorder point, suggested order)
    6. Save forecast predictions back to Supabase
    """

    def __init__(self, repo: SupabaseRepo | None = None):
        self._repo = repo or SupabaseRepo()

    def run_for_items(self, item_ids: set[str]) -> int:
        """Run the forecasting pipeline for specific items.

        Args:
            item_ids: Set of item UUIDs to process.

        Returns:
            Number of forecast predictions saved.
        """
        if not item_ids:
            logger.info("No items to process")
            return 0

        item_list = list(item_ids)
        logger.info("Running pipeline for %d items", len(item_list))

        # Step 1: Load item metadata
        items_df = self._repo.get_items(item_ids=item_list)
        if items_df.empty:
            logger.warning("No active items found for IDs: %s", item_list[:5])
            return 0

        logger.debug("Loaded %d items", len(items_df))

        # Step 2: Load current inventory
        inventory_df = self._repo.get_current_inventory(item_ids=item_list)
        logger.debug("Loaded inventory for %d items", len(inventory_df))

        # Step 3: Load recent stock movements (lookback window for feature building)
        lookback_days = config.ROLLING_WINDOW * 2  # 2x rolling window for stability
        end_ts = datetime.now(timezone.utc)
        start_ts = end_ts - timedelta(days=lookback_days)

        movements_df = self._repo.get_stock_movements(
            start=start_ts,
            end=end_ts,
            item_ids=item_list,
        )
        logger.debug(
            "Loaded %d movements in %d-day window",
            len(movements_df),
            lookback_days,
        )

        # Step 4: Build features
        if movements_df.empty:
            logger.info("No movements found, using zero-demand baseline")
            # Create zero-demand features for items with no history
            features_df = self._create_zero_demand_features(items_df)
        else:
            # Build daily usage from movements
            daily_df = feat.build_daily_usage(movements_df)
            features_df = feat.build_stats(daily_df)
            logger.debug("Built features: %d rows", len(features_df))

        # Step 5: Estimate demand parameters
        if features_df.empty:
            logger.warning("No features built, skipping forecast")
            return 0

        estimates_df = fc.estimate_mu_sigma(features_df, method="ma14")
        logger.debug("Estimated demand for %d items", len(estimates_df))

        # Step 6: Merge all data and compute policy
        forecasts_df = self._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
        )

        if forecasts_df.empty:
            logger.info("No forecasts computed")
            return 0

        # Step 7: Save to database
        saved = self._repo.upsert_forecasts(forecasts_df)
        logger.info("Saved %d forecast predictions", saved)
        return saved

    def run_all(self) -> int:
        """Run the forecasting pipeline for all active items.

        Returns:
            Number of forecast predictions saved.
        """
        logger.info("Running pipeline for all active items")

        # Load all active items
        items_df = self._repo.get_items()
        if items_df.empty:
            logger.warning("No active items found")
            return 0

        item_ids = set(items_df["item_id"].tolist())
        return self.run_for_items(item_ids)

    def _create_zero_demand_features(self, items_df: pd.DataFrame) -> pd.DataFrame:
        """Create baseline features for items with no movement history."""
        today = datetime.now(timezone.utc).date()
        rows = []
        for _, item in items_df.iterrows():
            rows.append(
                {
                    "date": today,
                    "item_id": item["item_id"],
                    "consumption": 0.0,
                    "ma7": 0.0,
                    "ma14": 0.0,
                    "std14": 0.0,
                    "dow": today.weekday(),
                    "is_weekend": today.weekday() >= 5,
                }
            )
        return pd.DataFrame(rows)

    def _compute_forecasts(
        self,
        items_df: pd.DataFrame,
        inventory_df: pd.DataFrame,
        estimates_df: pd.DataFrame,
    ) -> pd.DataFrame:
        """Compute forecast predictions by merging data and applying policy."""
        now = datetime.now(timezone.utc)

        # Merge items with estimates
        merged = items_df.merge(estimates_df, on="item_id", how="inner")

        # Merge with inventory (left join - some items may not have inventory yet)
        if not inventory_df.empty:
            merged = merged.merge(
                inventory_df[["item_id", "current_qty"]],
                on="item_id",
                how="left",
            )
            merged["current_qty"] = merged["current_qty"].fillna(0).astype(int)
        else:
            merged["current_qty"] = 0

        if merged.empty:
            return pd.DataFrame()

        forecasts = []
        for _, row in merged.iterrows():
            item_id = row["item_id"]
            mu_hat = float(row["mu_hat"])
            sigma_d_hat = float(row["sigma_d_hat"])
            current_qty = int(row["current_qty"])
            lead_time = int(row.get("lead_time_days", 7))
            service_level = config.SERVICE_LEVEL_DEFAULT

            # Compute safety stock
            ss = policy.compute_safety_stock(
                mu_hat=mu_hat,
                sigma_d_hat=sigma_d_hat,
                L=lead_time,
                alpha=service_level,
            )

            # Compute reorder point
            rop = policy.reorder_point(mu_hat=mu_hat, safety_stock=ss, L=lead_time)

            # Compute days to stockout
            days_out = policy.days_to_stockout(
                current_qty=current_qty,
                mu_hat=mu_hat,
                epsilon=config.EPSILON_MU,
            )

            # Suggest order quantity (target days of cover)
            suggested_qty = policy.suggest_order(
                current_qty=current_qty,
                mu_hat=mu_hat,
                L=lead_time,
                safety_stock=ss,
                target_days_of_cover=config.TARGET_DAYS,
            )

            # Compute suggested order date (when to place order to avoid stockout)
            if days_out < float("inf") and days_out > lead_time:
                order_date = (now + timedelta(days=int(days_out - lead_time))).date()
            elif days_out <= lead_time:
                order_date = now.date()  # Order immediately
            else:
                order_date = None  # No urgency

            # Confidence based on data quality (simple heuristic)
            confidence = min(1.0, sigma_d_hat / max(mu_hat, 0.1))
            confidence = 1.0 - confidence  # Higher sigma = lower confidence

            forecasts.append(
                {
                    "item_id": item_id,
                    "computed_at": now,
                    "horizon_days": config.TARGET_DAYS,
                    "avg_daily_delta": -mu_hat,  # Negative = consumption
                    "days_to_stockout": days_out if days_out < float("inf") else None,
                    "suggested_reorder_qty": suggested_qty,
                    "suggested_order_date": order_date,
                    "confidence": round(confidence, 3),
                    "features": {
                        "mu_hat": round(mu_hat, 4),
                        "sigma_d_hat": round(sigma_d_hat, 4),
                        "safety_stock": round(ss, 2),
                        "reorder_point": round(rop, 2),
                        "current_qty": current_qty,
                        "lead_time_days": lead_time,
                        "service_level": service_level,
                    },
                }
            )

        return pd.DataFrame(forecasts)
