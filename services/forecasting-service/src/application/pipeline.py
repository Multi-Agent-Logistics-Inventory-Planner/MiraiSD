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


def _resolve_regimes(
    item_ids: list[str],
    cv_map: dict[str, float],
    prior_regimes: dict[str, str],
) -> dict[str, str]:
    """Apply deadband regime routing per SKU.

    Move to ``bursty`` only when CV crosses ``CV_THRESHOLD_HIGH``; move to
    ``steady`` only when it drops below ``CV_THRESHOLD_LOW``. SKUs whose CV
    sits inside the deadband keep their previous regime. New SKUs (no prior
    state, no measurable CV) default to ``CV_DEFAULT_REGIME``.
    """
    resolved: dict[str, str] = {}
    for item_id in item_ids:
        prior = prior_regimes.get(item_id)
        cv = cv_map.get(item_id)
        if cv is None:
            # No sale-day data this window -- preserve prior or default.
            resolved[item_id] = prior or config.CV_DEFAULT_REGIME
            continue
        if cv > config.CV_THRESHOLD_HIGH:
            resolved[item_id] = policy.REGIME_BURSTY
        elif cv < config.CV_THRESHOLD_LOW:
            resolved[item_id] = policy.REGIME_STEADY
        else:
            resolved[item_id] = prior or config.CV_DEFAULT_REGIME
    return resolved


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

        # Step 3: Load recent stock movements (lookback window for feature building).
        # Use the larger of 2x rolling window or the CV window so the per-SKU
        # CV used for safety-stock regime routing has enough history.
        lookback_days = max(config.ROLLING_WINDOW * 2, config.CV_WINDOW_DAYS)
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
            features_df = self._create_zero_demand_features(items_df)
        else:
            daily_df = feat.build_daily_usage(movements_df)
            # Stockout awareness: opt-in via config. With <60 days of data,
            # excluding stockout days hurts accuracy (sample-size penalty > bias correction).
            if config.STOCKOUT_FILTER_ENABLED:
                stockout_df = feat.detect_stockout_days(movements_df)
                if not stockout_df.empty:
                    daily_df = daily_df.merge(stockout_df, on=["item_id", "date"], how="left")
                    daily_df["is_stockout"] = daily_df["is_stockout"].fillna(False).astype(bool)
                    stockout_pct = daily_df["is_stockout"].mean() * 100
                    logger.debug("Stockout filter ON: %.1f%% of item-days are stockout", stockout_pct)
            features_df = feat.build_stats(daily_df)
            logger.debug("Built features: %d rows", len(features_df))

        # Step 5: Estimate demand parameters
        if features_df.empty:
            logger.warning("No features built, skipping forecast")
            return 0

        # Use stockout-aware min_in_stock_days only when filter is enabled
        min_stock_days = config.MIN_IN_STOCK_DAYS if config.STOCKOUT_FILTER_ENABLED else 0
        forecast_method = config.FORECAST_METHOD
        estimates_df = fc.estimate_mu_sigma(
            features_df, method=forecast_method, min_in_stock_days=min_stock_days,
        )
        logger.debug("Estimated demand for %d items (method=%s)", len(estimates_df), forecast_method)

        # Step 5a: Category-pooled fallback for cold-start items only
        cat_col = items_df["category_name"] if "category_name" in items_df.columns else pd.Series("Unknown", index=items_df.index)
        category_map = dict(zip(items_df["item_id"], cat_col))
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

        # Step 5c: Compute per-SKU CV and apply demand-regime routing with
        # hysteresis. The regime selects the safety-stock distribution
        # (steady -> Poisson, bursty -> NegBin) in _compute_forecasts.
        cv_map = feat.compute_per_sku_cv(daily_df) if not movements_df.empty else {}
        prior_regimes = self._repo.get_latest_demand_regimes(item_list)
        regime_map = _resolve_regimes(item_list, cv_map, prior_regimes)

        # Step 5d: Phase 4 event multipliers. When the active method is the
        # events-aware variant, learn global SHIPMENT_RECEIPT / DISPLAY_SET
        # uplift multipliers from history and compute per-SKU "days since
        # last event" so the policy layer can activate the multiplier on
        # lead-time days inside the lookback window.
        event_multipliers: dict[str, float] = {}
        event_days_since: dict[str, dict[str, float]] = {}
        event_window_days = 7
        ship_col = f"recent_shipment_{event_window_days}d"
        disp_col = f"recent_display_{event_window_days}d"
        if forecast_method == "dow_weighted_events" and not movements_df.empty:
            # Learn multipliers from the GLOBAL movement stream (all items),
            # not just this Kafka batch -- a single-item batch carries far too
            # few sale-with-event observations to clear the min_n=30 threshold,
            # so multipliers would default to 1.0 and the events path would be
            # a no-op in production. The per-item daily features still use the
            # batched movements_df.
            global_movements = self._repo.get_stock_movements(
                start=start_ts, end=end_ts, item_ids=None,
            )
            if not global_movements.empty:
                global_daily = feat.build_daily_usage(global_movements)
                global_event_features = feat.build_event_features(
                    global_movements, window_days=event_window_days,
                )
                if not global_event_features.empty and not global_daily.empty:
                    global_daily["item_id"] = global_daily["item_id"].astype(str)
                    global_daily["date"] = pd.to_datetime(global_daily["date"]).dt.floor("D")
                    global_event_features["date"] = pd.to_datetime(
                        global_event_features["date"]
                    ).dt.floor("D")
                    train = global_daily.merge(
                        global_event_features, on=["item_id", "date"], how="left",
                    )
                    train[ship_col] = train[ship_col].fillna(0).astype(int)
                    train[disp_col] = train[disp_col].fillna(0).astype(int)
                    event_multipliers = feat.compute_global_event_multipliers(
                        train, event_cols=[ship_col, disp_col],
                    )
                # Recency for the batched items uses the full movement window
                # so we don't miss a shipment that arrived for the SKU before
                # this batch fired.
                event_days_since = self._compute_event_days_since(
                    global_movements, item_list,
                )

        # Step 6: Merge all data and compute policy
        forecasts_df = self._compute_forecasts(
            items_df=items_df,
            inventory_df=inventory_df,
            estimates_df=estimates_df,
            lead_time_stats_df=lead_time_stats_df,
            mape_df=mape_df,
            regime_map=regime_map,
            cv_map=cv_map,
            event_multipliers=event_multipliers,
            event_days_since=event_days_since,
            event_window_days=event_window_days,
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

    @staticmethod
    def _compute_event_days_since(
        movements_df: pd.DataFrame,
        item_ids: list[str],
    ) -> dict[str, dict[str, float]]:
        """For each event type, return {item_id: days_since_last_event_today}.

        NaN means "never observed in the lookback window" -- the policy layer
        skips the multiplier for those SKUs.
        """
        if movements_df.empty or "reason" not in movements_df.columns:
            return {}

        df = movements_df[["item_id", "reason", "at"]].copy()
        df["at"] = pd.to_datetime(df["at"], utc=True)
        df["item_id"] = df["item_id"].astype(str)
        today = pd.Timestamp(datetime.now(timezone.utc)).floor("D")

        groups = {
            "recent_shipment_7d": feat.SHIPMENT_REASONS,
            "recent_display_7d": feat.DISPLAY_REASONS,
        }
        out: dict[str, dict[str, float]] = {}
        for col, reasons in groups.items():
            subset = df[df["reason"].isin(reasons)]
            if subset.empty:
                out[col] = {iid: float("nan") for iid in item_ids}
                continue
            last_per_item = subset.groupby("item_id")["at"].max()
            per_item: dict[str, float] = {}
            for iid in item_ids:
                iid_s = str(iid)
                last_dt = last_per_item.get(iid_s)
                if last_dt is None or pd.isna(last_dt):
                    per_item[iid_s] = float("nan")
                else:
                    per_item[iid_s] = float((today - last_dt).days)
            out[col] = per_item
        return out

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
        regime_map: dict[str, str] | None = None,
        cv_map: dict[str, float] | None = None,
        event_multipliers: dict[str, float] | None = None,
        event_days_since: dict[str, dict[str, float]] | None = None,
        event_window_days: int = 7,
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

        # Build the regime series (steady -> Poisson buffer, bursty -> NegBin)
        # in the same index order as the rest of the merged DataFrame.
        regime_series: pd.Series | None = None
        if regime_map:
            regime_series = merged["item_id"].astype(str).map(regime_map).fillna(
                config.CV_DEFAULT_REGIME
            )

        # DOW multipliers from the estimator land in merged as a column of dicts
        # when method is dow_weighted or tsb. We feed them into the policy layer
        # so the lead-time demand sum tracks weekday/weekend cadence instead of
        # using a flat mu * L. Today is the start of the lead-time window.
        dow_mult_series: pd.Series | None = None
        if "dow_multipliers" in merged.columns:
            dow_mult_series = merged["dow_multipliers"]
        start_date = now.date()

        # Phase 4: event_days_since maps each event_col to a per-item dict.
        # Project to Series aligned with the merged index for the policy layer.
        event_days_since_series: dict[str, pd.Series] | None = None
        if event_multipliers and event_days_since:
            event_days_since_series = {}
            id_series = merged["item_id"].astype(str)
            for col, per_item in event_days_since.items():
                event_days_since_series[col] = id_series.map(per_item).astype(float)

        # Vectorized policy computations. When the new regime mapping is
        # present, the safety stock switches from the legacy Normal-quantile
        # formula to Poisson (steady) / NegBin (bursty).
        if regime_series is not None:
            ss = policy.compute_safety_stock_vectorized(
                mu_hat=mu_hat,
                sigma_d_hat=sigma_d_hat,
                L=lead_time,
                alpha=service_level,
                regime=regime_series,
                dow_multipliers=dow_mult_series,
                start_date=start_date,
                event_multipliers=event_multipliers or None,
                event_days_since=event_days_since_series,
                event_window_days=event_window_days,
            )
        else:
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
            dow_multipliers=dow_mult_series,
            start_date=start_date,
            event_multipliers=event_multipliers or None,
            event_days_since=event_days_since_series,
            event_window_days=event_window_days,
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
        confidence = np.clip(np.round(1.0 / (1.0 + cv), 3), 0.01, 1.0)

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

            # Add TSB state (p = sale probability, z = sale-day demand).
            # Persisted for the "why this number" drawer; the next pipeline run
            # re-derives both from the 60-day window rather than reading them.
            if "method" in merged.columns:
                feat_dict["method"] = str(merged["method"].iloc[i])
            if "p" in merged.columns and pd.notna(merged["p"].iloc[i]):
                feat_dict["tsb_p"] = round(float(merged["p"].iloc[i]), 4)
            if "z" in merged.columns and pd.notna(merged["z"].iloc[i]):
                feat_dict["tsb_z"] = round(float(merged["z"].iloc[i]), 4)

            # Add demand-regime tag and the CV that informed it. Used by the
            # next pipeline run for hysteresis and by the UI drawer to
            # explain "we're treating this SKU as bursty because its CV is X."
            item_id_str = str(merged["item_id"].iloc[i])
            if regime_map is not None:
                feat_dict["demand_regime"] = regime_map.get(
                    item_id_str, config.CV_DEFAULT_REGIME
                )
            if cv_map and item_id_str in cv_map:
                feat_dict["cv"] = round(float(cv_map[item_id_str]), 4)

            # Phase 4 event-feature state: persist the learned global multipliers
            # plus per-SKU days-since-event so the accuracy SQL can replay the
            # per-day prediction and the UI drawer can explain "predicted is +X%
            # because a shipment arrived 2 days ago".
            if event_multipliers:
                feat_dict["event_multipliers"] = {
                    k: round(float(v), 4) for k, v in event_multipliers.items()
                }
                feat_dict["event_window_days"] = event_window_days
            if event_days_since:
                ds: dict[str, float | None] = {}
                for col, per_item in event_days_since.items():
                    val = per_item.get(item_id_str)
                    ds[col] = None if val is None or val != val else float(val)
                feat_dict["event_days_since"] = ds

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

            # Confidence: 1/(1+CV) -- matches vectorized path, floored at 0.01
            cv = sigma_d_hat / max(mu_hat, 0.1)
            confidence = max(1.0 / (1.0 + cv), 0.01)

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
