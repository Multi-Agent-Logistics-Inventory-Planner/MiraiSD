"""Forecasting pipeline orchestrating domain logic with Supabase data."""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import TYPE_CHECKING

import numpy as np
import pandas as pd

from .. import config
from .. import features as feat
from .. import forecast as fc
from .. import policy
from ..backtest import compute_mape
from ..lead_time import compute_hierarchical_lead_time, compute_lead_time_stats
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

    def run_for_items(
        self,
        item_ids: set[str],
        event_inventory: dict[str, int] | None = None,
    ) -> int:
        """Run the forecasting pipeline for specific items.

        Args:
            item_ids: Set of item UUIDs to process.
            event_inventory: Optional dict of item_id -> current_qty from events.
                If provided and non-empty, uses event-carried state instead of
                querying the database for inventory.

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

        # Step 1b: Load dynamic lead times using hierarchical supplier-based computation
        mv_stats_df = self._repo.get_lead_time_mv_stats(item_ids=item_list)
        products_for_lt = items_df[["item_id"]].copy()
        if "preferred_supplier_id" in items_df.columns:
            products_for_lt["preferred_supplier_id"] = items_df["preferred_supplier_id"]
        lead_time_stats_df = compute_hierarchical_lead_time(
            mv_stats_df,
            products_for_lt,
        )
        logger.debug(
            "Computed hierarchical lead time stats for %d items (sources: %s)",
            len(lead_time_stats_df),
            lead_time_stats_df["source"].value_counts().to_dict() if not lead_time_stats_df.empty else {},
        )

        # Step 2: Load current inventory - prefer event-carried state
        if event_inventory:
            inventory_df = self._build_inventory_from_events(item_ids, event_inventory)
            logger.debug("Using event-carried inventory for %d items", len(inventory_df))
        else:
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

        # Step 4: Build features (with stockout awareness)
        if movements_df.empty:
            logger.info("No movements found, using zero-demand baseline")
            features_df = self._create_zero_demand_features(items_df)
        else:
            daily_df = feat.build_daily_usage(movements_df)
            stockout_df = feat.detect_stockout_days(movements_df)
            if not stockout_df.empty:
                daily_df = daily_df.merge(stockout_df, on=["item_id", "date"], how="left")
                daily_df["is_stockout"] = daily_df["is_stockout"].fillna(False).astype(bool)
                stockout_pct = daily_df["is_stockout"].mean() * 100
                logger.debug("Stockout rate: %.1f%% of item-days", stockout_pct)
            features_df = feat.build_stats(daily_df)
            logger.debug("Built features: %d rows", len(features_df))

        # Step 5: Estimate demand parameters
        if features_df.empty:
            logger.warning("No features built, skipping forecast")
            return 0

        estimates_df = fc.estimate_mu_sigma(features_df, method="dow_weighted")
        logger.debug("Estimated demand for %d items (DOW-weighted)", len(estimates_df))

        # Step 5a: Category-pooled fallback for cold-start items only
        category_map = dict(zip(items_df["item_id"], items_df.get("category_name", "Unknown")))
        items_with_history = set(features_df["item_id"].unique()) if not features_df.empty else set()
        estimates_df = fc.apply_category_fallback(estimates_df, category_map, items_with_history)
        logger.debug("Applied category fallback for cold-start items")

        # Step 5b: Compute backtest MAPE
        mape_df = pd.DataFrame(columns=["item_id", "mape", "forecast_mu", "actual_mu", "backtest_days"])
        backtest_target = datetime.now(timezone.utc) - timedelta(days=config.BACKTEST_HORIZON_DAYS)
        historical_fc_df = self._repo.get_historical_forecasts(
            item_ids=item_list, target_date=backtest_target,
        )
        if not historical_fc_df.empty and not movements_df.empty:
            # Build actual daily usage for the backtest window
            backtest_start = backtest_target
            backtest_end = datetime.now(timezone.utc)
            backtest_movements = movements_df[
                (movements_df["at"] >= backtest_start) & (movements_df["at"] <= backtest_end)
            ]
            if not backtest_movements.empty:
                backtest_daily = feat.build_daily_usage(backtest_movements)
                mape_df = compute_mape(
                    historical_fc_df,
                    backtest_daily,
                    horizon_days=config.BACKTEST_HORIZON_DAYS,
                    epsilon=config.MAPE_EPSILON,
                )
                logger.debug("Computed MAPE for %d items", len(mape_df))

        # Step 6: Merge all data and compute policy
        forecasts_df = self._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
            lead_time_stats_df=lead_time_stats_df,
            mape_df=mape_df,
        )

        if forecasts_df.empty:
            logger.info("No forecasts computed")
            return 0

        # Step 7: Save to database
        saved = self._repo.upsert_forecasts(forecasts_df)
        logger.info("Saved %d forecast predictions", saved)

        # Step 8: Propagate dynamic reorder points to products table.
        # Wrapped in try/except so a DB failure here does not crash the pipeline
        # after forecasts were already committed in Step 7.
        try:
            updated = self._repo.update_product_reorder_points(forecasts_df)
            logger.info("Propagated reorder points for %d products", updated)
        except Exception:
            logger.exception(
                "Failed to propagate reorder points; forecasts were saved"
            )

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

    def _build_inventory_from_events(
        self,
        item_ids: set[str],
        event_inventory: dict[str, int],
    ) -> pd.DataFrame:
        """Build inventory DataFrame from event-carried state.

        Args:
            item_ids: Set of item IDs to include.
            event_inventory: Dict mapping item_id to current_qty from events.

        Returns:
            DataFrame with columns: item_id, as_of_ts, current_qty
        """
        now = datetime.now(timezone.utc)
        rows = [
            {"item_id": item_id, "as_of_ts": now, "current_qty": event_inventory[item_id]}
            for item_id in item_ids
            if item_id in event_inventory
        ]

        if not rows:
            return pd.DataFrame(columns=["item_id", "as_of_ts", "current_qty"])

        df = pd.DataFrame(rows)
        df["item_id"] = df["item_id"].astype(str)
        df["as_of_ts"] = pd.to_datetime(df["as_of_ts"], utc=True)
        df["current_qty"] = df["current_qty"].astype(int)
        return df

    def _create_zero_demand_features(self, items_df: pd.DataFrame) -> pd.DataFrame:
        """Create baseline features for items with no movement history.

        Uses vectorized pandas operations for better performance.
        """
        if items_df.empty:
            return pd.DataFrame(
                columns=[
                    "date",
                    "item_id",
                    "consumption",
                    "ma7",
                    "ma14",
                    "std14",
                    "dow",
                    "is_weekend",
                ]
            )

        today = datetime.now(timezone.utc).date()

        return pd.DataFrame(
            {
                "date": today,
                "item_id": items_df["item_id"].values,
                "consumption": 0.0,
                "ma7": 0.0,
                "ma14": 0.0,
                "std14": 0.0,
                "dow": today.weekday(),
                "is_weekend": today.weekday() >= 5,
            }
        )

    def _create_zero_demand_features_legacy(
        self, items_df: pd.DataFrame
    ) -> pd.DataFrame:
        """Legacy iterrows version for comparison testing."""
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
        lead_time_stats_df: pd.DataFrame | None = None,
        mape_df: pd.DataFrame | None = None,
    ) -> pd.DataFrame:
        """Compute forecast predictions using vectorized operations.

        Uses vectorized policy functions for better performance on large datasets.
        """
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

        # Merge dynamic lead time stats if available
        sigma_L_series = None
        if lead_time_stats_df is not None and not lead_time_stats_df.empty:
            lt_cols = ["item_id", "avg_lead_time", "sigma_L", "shipment_count", "source"]
            if "preferred_supplier_id" in lead_time_stats_df.columns:
                lt_cols.append("preferred_supplier_id")
            merged = merged.merge(
                lead_time_stats_df[lt_cols].rename(
                    columns={"source": "lead_time_source"}
                ),
                on="item_id",
                how="left",
            )
            # Use dynamic lead time where available, fall back to static
            if "lead_time_days" in merged.columns:
                merged["lead_time_days"] = merged["avg_lead_time"].fillna(
                    merged["lead_time_days"]
                ).fillna(14)
            else:
                merged["lead_time_days"] = merged["avg_lead_time"].fillna(14)
            merged["sigma_L"] = merged["sigma_L"].fillna(config.LEAD_TIME_STD_DEFAULT_DAYS)
            sigma_L_series = merged["sigma_L"].astype(float)

        # Merge MAPE if available
        if mape_df is not None and not mape_df.empty:
            merged = merged.merge(
                mape_df[["item_id", "mape", "forecast_mu", "actual_mu", "backtest_days"]].rename(
                    columns={"forecast_mu": "backtest_forecast_mu", "actual_mu": "backtest_actual_mu"}
                ),
                on="item_id",
                how="left",
            )

        # Extract columns as Series for vectorized operations
        mu_hat = merged["mu_hat"].astype(float)
        sigma_d_hat = merged["sigma_d_hat"].astype(float)
        current_qty = merged["current_qty"].astype(int)

        # Handle lead_time_days column (may not exist)
        if "lead_time_days" in merged.columns:
            lead_time = merged["lead_time_days"].fillna(14).astype(float)
        else:
            lead_time = 14.0  # Scalar default

        service_level = config.SERVICE_LEVEL_DEFAULT

        # Vectorized policy computations (with sigma_L if available)
        ss = policy.compute_safety_stock_vectorized(
            mu_hat=mu_hat,
            sigma_d_hat=sigma_d_hat,
            L=lead_time,
            alpha=service_level,
            sigma_L=sigma_L_series,
        )

        rop = policy.reorder_point_vectorized(
            mu_hat=mu_hat,
            safety_stock=ss,
            L=lead_time,
        )

        days_out = policy.days_to_stockout_vectorized(
            current_qty=current_qty.astype(float),
            mu_hat=mu_hat,
            epsilon=config.EPSILON_MU,
        )

        suggested_qty = policy.suggest_order_vectorized(
            current_qty=current_qty,
            mu_hat=mu_hat,
            L=lead_time,
            safety_stock=ss,
            target_days=config.TARGET_DAYS,
        )

        # Confidence: 1/(1+CV) -- eliminates zero-confidence scores and
        # correlates well with actual prediction accuracy per backtest results.
        # MAPE blending disabled until system has 60-90 days of history.
        cv = sigma_d_hat / np.maximum(mu_hat, config.EPSILON_MU)
        confidence = np.round(1.0 / (1.0 + cv), 3)

        if config.CONFIDENCE_MAPE_ENABLED and "mape" in merged.columns:
            has_mape = merged["mape"].notna()
            clamped_mape = merged["mape"].fillna(0.0).clip(lower=0.0, upper=1.0)
            mape_score = 1.0 - clamped_mape
            confidence = pd.Series(confidence, index=merged.index)
            confidence[has_mape] = 0.5 * confidence[has_mape] + 0.5 * mape_score[has_mape]
            confidence = np.round(confidence, 3)

        # Vectorized order date computation
        lead_time_arr = (
            lead_time if isinstance(lead_time, pd.Series) else pd.Series([lead_time] * len(merged))
        )

        # Build the features dict for each row (must use iteration for dict creation)
        features_list = []
        for i in range(len(merged)):
            lt = float(lead_time_arr.iloc[i]) if isinstance(lead_time, pd.Series) else float(lead_time)
            feat_dict = {
                "mu_hat": round(float(mu_hat.iloc[i]), 4),
                "sigma_d_hat": round(float(sigma_d_hat.iloc[i]), 4),
                "safety_stock": round(float(ss.iloc[i]), 2),
                "reorder_point": round(float(rop.iloc[i]), 2),
                "current_qty": int(current_qty.iloc[i]),
                "lead_time_days": round(lt, 1),
                "service_level": service_level,
            }

            # Add dynamic lead time info
            if "sigma_L" in merged.columns:
                feat_dict["sigma_L"] = round(float(merged["sigma_L"].iloc[i]), 2)
            if "lead_time_source" in merged.columns:
                feat_dict["lead_time_source"] = str(merged["lead_time_source"].iloc[i])
            if "shipment_count" in merged.columns:
                feat_dict["shipment_count"] = int(merged["shipment_count"].iloc[i])
            if "preferred_supplier_id" in merged.columns and pd.notna(merged["preferred_supplier_id"].iloc[i]):
                feat_dict["preferred_supplier_id"] = str(merged["preferred_supplier_id"].iloc[i])

            # Add MAPE info
            if "mape" in merged.columns and pd.notna(merged["mape"].iloc[i]):
                feat_dict["mape"] = round(float(merged["mape"].iloc[i]), 4)
            if "backtest_forecast_mu" in merged.columns and pd.notna(merged["backtest_forecast_mu"].iloc[i]):
                feat_dict["backtest_forecast_mu"] = round(float(merged["backtest_forecast_mu"].iloc[i]), 4)
            if "backtest_actual_mu" in merged.columns and pd.notna(merged["backtest_actual_mu"].iloc[i]):
                feat_dict["backtest_actual_mu"] = round(float(merged["backtest_actual_mu"].iloc[i]), 4)

            # Add DOW multipliers
            if "dow_multipliers" in merged.columns:
                dm = merged["dow_multipliers"].iloc[i]
                if isinstance(dm, dict):
                    feat_dict["dow_multipliers"] = {str(k): v for k, v in dm.items()}
                    feat_dict["dow_adjusted"] = True

            features_list.append(feat_dict)

        # Compute order dates using extracted policy function
        order_dates = []
        for i in range(len(merged)):
            d_out = float(days_out.iloc[i])
            lt = float(lead_time_arr.iloc[i]) if isinstance(lead_time, pd.Series) else float(lead_time)
            order_dates.append(policy.compute_order_date(d_out, lt, now))

        # Convert days_to_stockout: replace inf with None
        days_to_stockout_values = [
            float(d) if d < float("inf") else None for d in days_out
        ]

        # Build final DataFrame
        return pd.DataFrame(
            {
                "item_id": merged["item_id"].values,
                "computed_at": now,
                "horizon_days": config.TARGET_DAYS,
                "avg_daily_delta": -mu_hat.values,
                "days_to_stockout": days_to_stockout_values,
                "suggested_reorder_qty": suggested_qty.values,
                "suggested_order_date": order_dates,
                "confidence": confidence.values,
                "features": features_list,
            }
        )

    def _compute_forecasts_legacy(
        self,
        items_df: pd.DataFrame,
        inventory_df: pd.DataFrame,
        estimates_df: pd.DataFrame,
    ) -> pd.DataFrame:
        """Legacy iterrows version for comparison testing."""
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
            lead_time = int(row.get("lead_time_days", 14))
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

            # Confidence: sigmoid-like variability score
            cv = sigma_d_hat / max(mu_hat, 0.1)
            confidence = 1.0 / (1.0 + cv)

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
