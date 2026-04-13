"""Walk-forward backtester for validating forecast accuracy.

Runs against production data via SupabaseRepo. Implements stockout-corrected
evaluation so demand estimates are compared to true in-stock demand rather
than censored zeros.

Usage via API:
    GET /forecasts/backtest?methods=dow_weighted&horizon=7

Usage programmatically:
    from src.application.backtester import run_backtest
    from src.adapters.supabase_repo import SupabaseRepo
    results = run_backtest(SupabaseRepo(), methods=["dow_weighted"])
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone

import numpy as np
import pandas as pd

from .. import config
from .. import features as feat
from .. import forecast as fc

logger = logging.getLogger(__name__)


def run_backtest(
    repo,
    methods: list[str] | None = None,
    min_train_days: int = 14,
    horizon_days: int = 7,
    lookback_days: int = 45,
) -> dict:
    """Walk-forward backtest using production data.

    For each forecast origin date (spaced horizon_days apart), trains on
    all data before the origin and evaluates on the next horizon_days.
    Stockout days are excluded from training and corrected in evaluation.

    Args:
        repo: SupabaseRepo instance for data access.
        methods: Forecasting methods to evaluate. Defaults to ["dow_weighted"].
        min_train_days: Minimum training window before first forecast.
        horizon_days: Days ahead to forecast at each origin.
        lookback_days: Total days of history to load.

    Returns:
        Dict with keys:
            method_summary: List of dicts with per-method aggregate metrics.
            predictions_count: Total number of predictions evaluated.
            stockout_stats: Dict with stockout percentage info.
    """
    if methods is None:
        methods = ["dow_weighted"]

    end_ts = datetime.now(timezone.utc)
    start_ts = end_ts - timedelta(days=lookback_days)

    # Load data
    movements_df = repo.get_stock_movements(start=start_ts, end=end_ts)
    if movements_df.empty:
        return {"method_summary": [], "predictions_count": 0, "stockout_stats": {}}

    items_df = repo.get_items()
    category_map = dict(zip(items_df["item_id"], items_df.get("category_name", "Unknown")))

    # Build daily usage + stockout flags
    daily_df = feat.build_daily_usage(movements_df)
    stockout_df = feat.detect_stockout_days(movements_df)
    if not stockout_df.empty:
        daily_df = daily_df.merge(stockout_df, on=["item_id", "date"], how="left")
        daily_df["is_stockout"] = daily_df["is_stockout"].fillna(False).astype(bool)

    daily_df["date"] = pd.to_datetime(daily_df["date"])

    # Determine forecast origins
    date_min = daily_df["date"].min()
    date_max = daily_df["date"].max()
    first_origin = date_min + timedelta(days=min_train_days)
    last_origin = date_max - timedelta(days=horizon_days)

    if first_origin > last_origin:
        logger.warning("Not enough data for backtest (need %d+ days)", min_train_days + horizon_days)
        return {"method_summary": [], "predictions_count": 0, "stockout_stats": {}}

    origins = pd.date_range(first_origin, last_origin, freq=f"{horizon_days}D")
    logger.info("Backtest: %d origins, %d methods, horizon=%dd", len(origins), len(methods), horizon_days)

    all_predictions = []

    for origin in origins:
        train = daily_df[daily_df["date"] < origin].copy()
        test_end = origin + timedelta(days=horizon_days)
        test = daily_df[(daily_df["date"] >= origin) & (daily_df["date"] < test_end)].copy()

        if train.empty or test.empty:
            continue

        train_features = feat.build_stats(train)

        for method in methods:
            valid_methods = {"ma7", "ma14", "exp_smooth", "dow_weighted"}
            if method not in valid_methods:
                continue

            estimates = fc.estimate_mu_sigma(train_features, method=method)
            items_with_history = set(train_features["item_id"].unique())
            estimates = fc.apply_category_fallback(estimates, category_map, items_with_history)

            # Compute actual demand per item in test window
            for item_id in estimates["item_id"].unique():
                item_test = test[test["item_id"] == item_id]
                if item_test.empty:
                    continue

                est_row = estimates[estimates["item_id"] == item_id].iloc[0]
                predicted_mu = float(est_row["mu_hat"])

                actual_days = len(item_test)
                actual_total_raw = float(item_test["consumption"].sum())
                actual_mu_raw = actual_total_raw / max(actual_days, 1)

                # Stockout-corrected actual: exclude stockout days
                if "is_stockout" in item_test.columns:
                    in_stock = item_test[~item_test["is_stockout"]]
                    # Skip if not enough in-stock test days for reliable ground truth
                    if len(in_stock) < config.MIN_TEST_IN_STOCK_DAYS:
                        continue
                    in_stock_days = len(in_stock)
                    actual_total_corrected = float(in_stock["consumption"].sum())
                    actual_mu_corrected = actual_total_corrected / max(in_stock_days, 1)
                else:
                    actual_mu_corrected = actual_mu_raw
                    actual_total_corrected = actual_total_raw

                all_predictions.append({
                    "method": method,
                    "origin_date": origin,
                    "item_id": item_id,
                    "predicted_mu": predicted_mu,
                    "actual_mu_raw": actual_mu_raw,
                    "actual_mu_corrected": actual_mu_corrected,
                    "actual_days": actual_days,
                })

    if not all_predictions:
        return {"method_summary": [], "predictions_count": 0, "stockout_stats": {}}

    pred_df = pd.DataFrame(all_predictions)

    # Compute stockout stats
    stockout_stats = {}
    if "is_stockout" in daily_df.columns:
        stockout_stats = {
            "total_item_days": int(len(daily_df)),
            "stockout_item_days": int(daily_df["is_stockout"].sum()),
            "stockout_pct": round(float(daily_df["is_stockout"].mean() * 100), 1),
        }

    # Aggregate per-method metrics
    method_summary = _compute_method_summary(pred_df)

    return {
        "method_summary": method_summary,
        "predictions_count": len(pred_df),
        "stockout_stats": stockout_stats,
    }


def _compute_method_summary(pred_df: pd.DataFrame) -> list[dict]:
    """Compute aggregate accuracy metrics per method."""
    has_demand = pred_df[pred_df["actual_mu_corrected"] > 0]
    summaries = []

    for method, group in has_demand.groupby("method"):
        errors = group["predicted_mu"] - group["actual_mu_corrected"]
        abs_errors = errors.abs()
        pct_errors = abs_errors / group["actual_mu_corrected"].clip(lower=0.1)

        summaries.append({
            "method": method,
            "n_predictions": int(len(group)),
            "mae": round(float(abs_errors.mean()), 4),
            "rmse": round(float(np.sqrt((errors ** 2).mean())), 4),
            "mape": round(float(pct_errors.mean()), 4),
            "bias": round(float(errors.mean()), 4),
            "within_1_unit": round(float((abs_errors <= 1.0).mean()), 4),
            "within_2_units": round(float((abs_errors <= 2.0).mean()), 4),
        })

    return sorted(summaries, key=lambda x: x["mae"])
