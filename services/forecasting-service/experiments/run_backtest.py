"""
Walk-forward backtesting harness for forecasting methods.

Tests 9 forecasting methods and 4 confidence formulas against historical data.
Includes stockout-aware demand estimation to correct for censored demand.

Imports production code directly from src/ -- no copy-paste.

Usage:
    cd services/forecasting-service
    python experiments/run_backtest.py

Output:
    experiments/results/backtest_predictions.csv
    experiments/results/method_summary.csv
    experiments/results/item_method_summary.csv
    experiments/results/confidence_comparison.csv
    experiments/results/stockout_analysis.csv
"""

from __future__ import annotations

import sys
from pathlib import Path

_service_root = str(Path(__file__).resolve().parent.parent)
if _service_root not in sys.path:
    sys.path.insert(0, _service_root)

import numpy as np
import pandas as pd

from src.features import build_daily_usage, build_stats
from src.forecast import estimate_mu_sigma
from src.config import MU_FLOOR, SIGMA_FLOOR

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
PROD_METHODS = ["ma7", "ma14", "exp_smooth", "dow_weighted"]
CUSTOM_METHODS = [
    "category_pooled",
    "median_based",
    "weekday_weekend",
    "exp_smooth_0.1",
    "exp_smooth_0.5",
]
ALL_METHODS = PROD_METHODS + CUSTOM_METHODS

MIN_TRAIN_DAYS = 14
HORIZON_DAYS = 7
MAPE_EPSILON = 0.1


# ---------------------------------------------------------------------------
# Data loading
# ---------------------------------------------------------------------------

def load_snapshot(data_dir: Path) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    products = pd.read_csv(data_dir / "products.csv")
    movements = pd.read_csv(data_dir / "stock_movements.csv")
    inventory = pd.read_csv(data_dir / "location_inventory.csv")
    return products, movements, inventory


# ---------------------------------------------------------------------------
# Stockout detection
# ---------------------------------------------------------------------------

def build_stockout_days(movements_df: pd.DataFrame) -> pd.DataFrame:
    """Identify item-days where inventory was at zero (stockout).

    Uses previous_quantity and current_quantity from stock_movements to
    reconstruct when inventory hit zero. A day is marked as a stockout
    if the last movement of the day left current_quantity = 0, OR if
    there were no movements and the prior day ended at 0.

    Returns DataFrame with columns: [item_id, date, is_stockout]
    """
    df = movements_df.copy()
    df["at"] = pd.to_datetime(df["at"], utc=True)
    df["date"] = df["at"].dt.floor("D").dt.tz_localize(None)

    # For each item-day, get the last movement's current_quantity
    # This tells us the inventory level at end of day
    last_movement = (
        df.dropna(subset=["current_quantity"])
        .sort_values("at")
        .groupby(["item_id", "date"])
        .last()
        .reset_index()
    )[["item_id", "date", "current_quantity"]]

    # Build complete date grid for all items
    all_items = df["item_id"].unique()
    all_dates = pd.date_range(df["date"].min(), df["date"].max(), freq="D")

    full_grid = pd.MultiIndex.from_product(
        [all_items, all_dates], names=["item_id", "date"]
    ).to_frame(index=False)

    # Merge end-of-day inventory onto grid
    merged = full_grid.merge(last_movement, on=["item_id", "date"], how="left")

    # Forward-fill: if no movement on a day, inventory = previous day's level
    merged = merged.sort_values(["item_id", "date"])
    merged["current_quantity"] = merged.groupby("item_id")["current_quantity"].ffill()

    # Days where inventory is 0 (or unknown before first movement) = stockout
    merged["is_stockout"] = (merged["current_quantity"] == 0) | merged["current_quantity"].isna()

    return merged[["item_id", "date", "is_stockout"]].copy()


def build_daily_usage_stockout_aware(
    movements_df: pd.DataFrame, stockout_days: pd.DataFrame
) -> pd.DataFrame:
    """Build daily usage but mark stockout days so they can be excluded.

    Returns the standard daily_usage DataFrame with an added 'is_stockout' column.
    Stockout days still show consumption = 0 but are flagged for exclusion
    from demand estimation.
    """
    daily_usage = build_daily_usage(movements_df)
    daily_usage["date"] = pd.to_datetime(daily_usage["date"]).dt.floor("D")
    stockout_days = stockout_days.copy()
    stockout_days["date"] = pd.to_datetime(stockout_days["date"]).dt.floor("D")

    merged = daily_usage.merge(
        stockout_days, on=["item_id", "date"], how="left"
    )
    merged["is_stockout"] = merged["is_stockout"].fillna(False)
    return merged


def print_stockout_summary(stockout_days: pd.DataFrame, daily_usage: pd.DataFrame):
    """Print analysis of how stockouts affect the data."""
    selling_items = daily_usage[daily_usage["consumption"] > 0]["item_id"].unique()
    seller_stockouts = stockout_days[stockout_days["item_id"].isin(selling_items)]

    total_seller_days = len(seller_stockouts)
    stockout_count = seller_stockouts["is_stockout"].sum()

    print(f"\n  === STOCKOUT ANALYSIS ===")
    print(f"  Total item-days for selling items: {total_seller_days}")
    print(f"  Days with stockout (inventory = 0): {stockout_count} ({stockout_count/max(total_seller_days,1)*100:.1f}%)")
    print(f"  Days in stock: {total_seller_days - stockout_count}")
    print(f"  Items affected by stockouts: {seller_stockouts[seller_stockouts['is_stockout']]['item_id'].nunique()}")

    # Zero-sales days that are stockouts vs genuine zero demand
    zero_sales = daily_usage[daily_usage["consumption"] == 0].copy()
    zero_sales["date"] = pd.to_datetime(zero_sales["date"]).dt.floor("D")
    stockout_lookup = stockout_days[["item_id", "date", "is_stockout"]].rename(
        columns={"is_stockout": "is_stockout_lookup"}
    )
    zero_with_stockout = zero_sales.merge(
        stockout_lookup, on=["item_id", "date"], how="left"
    )
    zero_with_stockout["is_stockout_flag"] = zero_with_stockout["is_stockout_lookup"].fillna(False)

    seller_zeros = zero_with_stockout[zero_with_stockout["item_id"].isin(selling_items)]
    zero_and_stockout = seller_zeros["is_stockout_flag"].sum()
    zero_and_instock = len(seller_zeros) - zero_and_stockout

    print(f"\n  Zero-sales days for selling items: {len(seller_zeros)}")
    print(f"    Stockout (censored demand):  {zero_and_stockout} ({zero_and_stockout/max(len(seller_zeros),1)*100:.1f}%)")
    print(f"    In stock (genuine zero):     {zero_and_instock} ({zero_and_instock/max(len(seller_zeros),1)*100:.1f}%)")


# ---------------------------------------------------------------------------
# Custom forecasting methods
# ---------------------------------------------------------------------------

def _exp_smooth_series(values: pd.Series, alpha: float) -> float:
    level = None
    for x in values.astype(float):
        if level is None:
            level = x
        else:
            level = alpha * x + (1.0 - alpha) * level
    return float(0.0 if level is None else level)


def estimate_category_pooled(
    train_features: pd.DataFrame, category_map: dict[str, str]
) -> pd.DataFrame:
    """Pool demand across items in the same category.

    New items inherit category average instead of MU_FLOOR.
    """
    item_estimates = estimate_mu_sigma(train_features, method="dow_weighted")
    item_estimates["category"] = item_estimates["item_id"].map(category_map).fillna("unknown")

    cat_mu = item_estimates.groupby("category")["mu_hat"].mean().rename("cat_mu")
    item_estimates = item_estimates.merge(cat_mu, left_on="category", right_index=True, how="left")
    item_estimates["mu_hat"] = np.maximum(
        item_estimates["mu_hat"], item_estimates["cat_mu"].fillna(MU_FLOOR)
    )

    result = item_estimates[["item_id", "mu_hat", "sigma_d_hat"]].copy()
    result["method"] = "category_pooled"
    return result.reset_index(drop=True)


def estimate_median_based(train_features: pd.DataFrame) -> pd.DataFrame:
    """Use median daily consumption instead of mean."""
    df = train_features.copy()
    df["date"] = pd.to_datetime(df["date"]).dt.floor("D")

    results = []
    for item_id, group in df.groupby("item_id", sort=False):
        group = group.sort_values("date")
        median_demand = float(group["consumption"].median())
        mu_hat = max(median_demand, MU_FLOOR)
        mad = float((group["consumption"] - median_demand).abs().median())
        sigma_d_hat = max(mad * 1.4826, SIGMA_FLOOR)

        results.append({
            "item_id": str(item_id),
            "mu_hat": mu_hat,
            "sigma_d_hat": sigma_d_hat,
            "method": "median_based",
        })
    return pd.DataFrame(results).reset_index(drop=True)


def estimate_weekday_weekend(train_features: pd.DataFrame) -> pd.DataFrame:
    """Split demand into weekday vs weekend averages."""
    df = train_features.copy()
    df["date"] = pd.to_datetime(df["date"]).dt.floor("D")
    df["is_weekend"] = df["date"].dt.dayofweek.isin([5, 6])

    results = []
    for item_id, group in df.groupby("item_id", sort=False):
        group = group.sort_values("date")
        weekday = group[~group["is_weekend"]]["consumption"]
        weekend = group[group["is_weekend"]]["consumption"]
        weekday_mu = float(weekday.mean()) if len(weekday) > 0 else 0.0
        weekend_mu = float(weekend.mean()) if len(weekend) > 0 else 0.0
        mu_hat = max((5 * weekday_mu + 2 * weekend_mu) / 7, MU_FLOOR)

        expected = group["is_weekend"].map({True: weekend_mu, False: weekday_mu})
        residuals = group["consumption"] - expected
        sigma_d_hat = max(float(residuals.std(ddof=0)), SIGMA_FLOOR)

        results.append({
            "item_id": str(item_id),
            "mu_hat": mu_hat,
            "sigma_d_hat": sigma_d_hat,
            "method": "weekday_weekend",
        })
    return pd.DataFrame(results).reset_index(drop=True)


def estimate_exp_smooth_tuned(train_features: pd.DataFrame, alpha: float) -> pd.DataFrame:
    """Exponential smoothing with configurable alpha."""
    df = train_features.copy()
    df["date"] = pd.to_datetime(df["date"]).dt.floor("D")

    results = []
    for item_id, group in df.groupby("item_id", sort=False):
        group = group.sort_values("date")
        level = _exp_smooth_series(group["consumption"], alpha=alpha)
        mu_hat = max(level, MU_FLOOR)
        if "std14" in group.columns:
            sigma = float(group["std14"].iloc[-1])
        else:
            sigma = float(group["consumption"].rolling(14, min_periods=1).std(ddof=0).iloc[-1])
        sigma_d_hat = max(sigma, SIGMA_FLOOR)

        results.append({
            "item_id": str(item_id),
            "mu_hat": mu_hat,
            "sigma_d_hat": sigma_d_hat,
            "method": f"exp_smooth_{alpha}",
        })
    return pd.DataFrame(results).reset_index(drop=True)


def run_custom_method(
    method: str, train_features: pd.DataFrame, category_map: dict[str, str]
) -> pd.DataFrame:
    if method == "category_pooled":
        return estimate_category_pooled(train_features, category_map)
    elif method == "median_based":
        return estimate_median_based(train_features)
    elif method == "weekday_weekend":
        return estimate_weekday_weekend(train_features)
    elif method == "exp_smooth_0.1":
        return estimate_exp_smooth_tuned(train_features, alpha=0.1)
    elif method == "exp_smooth_0.5":
        return estimate_exp_smooth_tuned(train_features, alpha=0.5)
    else:
        raise ValueError(f"Unknown custom method: {method}")


# ---------------------------------------------------------------------------
# Confidence formulas
# ---------------------------------------------------------------------------

def confidence_current(mu: pd.Series, sigma: pd.Series) -> pd.Series:
    cv = sigma / mu.clip(lower=MU_FLOOR)
    return (1.0 - np.minimum(1.0, cv)).round(4)


def confidence_proposed(mu: pd.Series, sigma: pd.Series) -> pd.Series:
    cv = sigma / mu.clip(lower=MU_FLOOR)
    return (1.0 / (1.0 + cv)).round(4)


def confidence_current_with_mape(
    mu: pd.Series, sigma: pd.Series, mape: pd.Series
) -> pd.Series:
    cv = sigma / mu.clip(lower=MU_FLOOR)
    variability = 1.0 - np.minimum(1.0, cv)
    mape_score = (1.0 - mape).clip(lower=0.0)
    has_mape = mape.notna() & (mape >= 0)
    result = variability.copy()
    result[has_mape] = 0.4 * variability[has_mape] + 0.6 * mape_score[has_mape]
    return result.round(4)


def confidence_proposed_with_mape(
    mu: pd.Series, sigma: pd.Series, mape: pd.Series
) -> pd.Series:
    cv = sigma / mu.clip(lower=MU_FLOOR)
    variability = 1.0 / (1.0 + cv)
    mape_score = (1.0 - mape).clip(lower=0.0)
    has_mape = mape.notna() & (mape >= 0)
    result = variability.copy()
    result[has_mape] = 0.4 * variability[has_mape] + 0.6 * mape_score[has_mape]
    return result.round(4)


# ---------------------------------------------------------------------------
# Walk-forward backtest
# ---------------------------------------------------------------------------

def walk_forward_backtest(
    daily_usage: pd.DataFrame,
    methods: list[str],
    category_map: dict[str, str],
    min_train_days: int = MIN_TRAIN_DAYS,
    horizon_days: int = HORIZON_DAYS,
) -> pd.DataFrame:
    """Run walk-forward evaluation across all methods.

    Uses stockout-aware data: training excludes stockout days,
    evaluation only counts in-stock days for actual demand.
    """
    has_stockout_col = "is_stockout" in daily_usage.columns
    dates = sorted(daily_usage["date"].unique())
    total_days = len(dates)
    max_origin = total_days - horizon_days

    print(f"  Total days in data: {total_days}")
    print(f"  Forecast origins: {min_train_days} to {max_origin - 1}")
    print(f"  Number of origins: {max_origin - min_train_days}")
    print(f"  Stockout-aware: {has_stockout_col}")
    print(f"  Methods: {len(methods)}")

    all_results = []

    for origin_idx in range(min_train_days, max_origin):
        origin_date = dates[origin_idx]
        eval_start = dates[origin_idx + 1]
        eval_end = dates[origin_idx + horizon_days]

        # --- Training window ---
        train_df = daily_usage[daily_usage["date"] <= origin_date].copy()

        # Exclude stockout days from training: the model should not learn
        # "demand = 0" from days where there was nothing to sell
        if has_stockout_col:
            train_df_clean = train_df[~train_df["is_stockout"]].copy()
            # Fall back to full data if filtering removes everything for an item
            items_with_data = train_df_clean["item_id"].unique()
            items_missing = set(train_df["item_id"].unique()) - set(items_with_data)
            if items_missing:
                fallback = train_df[train_df["item_id"].isin(items_missing)]
                train_df_clean = pd.concat([train_df_clean, fallback], ignore_index=True)
            train_df = train_df_clean

        # --- Evaluation window ---
        eval_df = daily_usage[
            (daily_usage["date"] >= eval_start) & (daily_usage["date"] <= eval_end)
        ].copy()

        if eval_df.empty:
            continue

        # For evaluation: compute actuals both ways
        # 1. Raw actuals (includes stockout days as 0 -- how production currently works)
        actuals_raw = eval_df.groupby("item_id", as_index=False).agg(
            actual_mu_raw=("consumption", "mean"),
            actual_total_raw=("consumption", "sum"),
            eval_days_raw=("date", "nunique"),
        )

        # 2. Stockout-corrected actuals (exclude stockout days)
        if has_stockout_col:
            eval_instock = eval_df[~eval_df["is_stockout"]]
            actuals_corrected = eval_instock.groupby("item_id", as_index=False).agg(
                actual_mu_corrected=("consumption", "mean"),
                actual_total_corrected=("consumption", "sum"),
                eval_days_instock=("date", "nunique"),
            )
            # Count stockout days per item in eval window
            stockout_counts = (
                eval_df[eval_df["is_stockout"]]
                .groupby("item_id", as_index=False)
                .size()
                .rename(columns={"size": "stockout_days_eval"})
            )
        else:
            actuals_corrected = actuals_raw.rename(columns={
                "actual_mu_raw": "actual_mu_corrected",
                "actual_total_raw": "actual_total_corrected",
                "eval_days_raw": "eval_days_instock",
            })
            stockout_counts = pd.DataFrame(columns=["item_id", "stockout_days_eval"])

        # Merge actuals
        actuals = actuals_raw.merge(actuals_corrected, on="item_id", how="left")
        actuals = actuals.merge(stockout_counts, on="item_id", how="left")
        actuals["stockout_days_eval"] = actuals["stockout_days_eval"].fillna(0).astype(int)
        # Fill corrected with raw where no in-stock days exist
        actuals["actual_mu_corrected"] = actuals["actual_mu_corrected"].fillna(actuals["actual_mu_raw"])
        actuals["actual_total_corrected"] = actuals["actual_total_corrected"].fillna(actuals["actual_total_raw"])
        actuals["eval_days_instock"] = actuals["eval_days_instock"].fillna(actuals["eval_days_raw"])

        # Build features for training
        train_cols = ["date", "item_id", "consumption"]
        train_features = build_stats(train_df[train_cols])

        for method in methods:
            try:
                if method in PROD_METHODS:
                    estimates = estimate_mu_sigma(train_features, method=method)
                else:
                    estimates = run_custom_method(method, train_features, category_map)
            except Exception as e:
                print(f"  WARNING: {method} failed at origin {origin_date}: {e}")
                continue

            rename_map = {}
            if "mu_hat" in estimates.columns:
                rename_map["mu_hat"] = "predicted_mu"
            if "sigma_d_hat" in estimates.columns:
                rename_map["sigma_d_hat"] = "predicted_sigma"
            preds = estimates.rename(columns=rename_map)

            merged = preds.merge(actuals, on="item_id", how="inner")
            if merged.empty:
                continue

            # MAPE using corrected actuals
            pred_mape = (
                (merged["predicted_mu"] - merged["actual_mu_corrected"]).abs()
                / merged["actual_mu_corrected"].clip(lower=MAPE_EPSILON)
            )

            merged["conf_current"] = confidence_current(merged["predicted_mu"], merged["predicted_sigma"])
            merged["conf_proposed"] = confidence_proposed(merged["predicted_mu"], merged["predicted_sigma"])
            merged["conf_current_mape"] = confidence_current_with_mape(merged["predicted_mu"], merged["predicted_sigma"], pred_mape)
            merged["conf_proposed_mape"] = confidence_proposed_with_mape(merged["predicted_mu"], merged["predicted_sigma"], pred_mape)

            merged["origin_date"] = origin_date
            merged["horizon_days"] = horizon_days
            if "method" not in merged.columns:
                merged["method"] = method

            cols = [
                "origin_date", "item_id", "method",
                "predicted_mu", "predicted_sigma",
                "actual_mu_raw", "actual_total_raw", "eval_days_raw",
                "actual_mu_corrected", "actual_total_corrected", "eval_days_instock",
                "stockout_days_eval",
                "conf_current", "conf_proposed",
                "conf_current_mape", "conf_proposed_mape",
            ]
            keep = [c for c in cols if c in merged.columns]
            all_results.append(merged[keep])

    if not all_results:
        print("  WARNING: No results produced.")
        return pd.DataFrame()

    return pd.concat(all_results, ignore_index=True)


# ---------------------------------------------------------------------------
# Metrics
# ---------------------------------------------------------------------------

def compute_metrics(
    results_df: pd.DataFrame, actual_col: str = "actual_mu_corrected"
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Compute metrics using specified actual column."""
    has_demand = results_df[results_df[actual_col] > 0].copy()
    zero_demand = results_df[results_df[actual_col] == 0]

    print(f"\n  Predictions with demand > 0 ({actual_col}): {len(has_demand)}")
    print(f"  Predictions with demand = 0: {len(zero_demand)} (excluded)")

    def _agg(group: pd.DataFrame) -> pd.Series:
        pred = group["predicted_mu"]
        actual = group[actual_col]
        err = pred - actual
        abs_err = err.abs()
        return pd.Series({
            "mae": abs_err.mean(),
            "rmse": np.sqrt((err ** 2).mean()),
            "mape": (abs_err / actual.clip(lower=MAPE_EPSILON)).mean(),
            "bias": err.mean(),
            "within_1_unit": (abs_err <= 1.0).mean(),
            "within_2_units": (abs_err <= 2.0).mean(),
            "avg_conf_current": group["conf_current"].mean(),
            "avg_conf_proposed": group["conf_proposed"].mean(),
            "avg_conf_current_mape": group["conf_current_mape"].mean(),
            "avg_conf_proposed_mape": group["conf_proposed_mape"].mean(),
            "n_predictions": len(group),
        })

    method_summary = (
        has_demand
        .groupby("method")
        .apply(_agg, include_groups=False)
        .reset_index()
    )

    item_method_summary = (
        has_demand
        .groupby(["method", "item_id"])
        .apply(_agg, include_groups=False)
        .reset_index()
    )

    return method_summary, item_method_summary


def compute_confidence_comparison(results_df: pd.DataFrame) -> pd.DataFrame:
    has_demand = results_df[results_df["actual_mu_corrected"] > 0]

    rows = []
    for method, group in has_demand.groupby("method"):
        row = {"method": method}
        for col, label in [
            ("conf_current", "current"),
            ("conf_proposed", "proposed"),
            ("conf_current_mape", "current_mape"),
            ("conf_proposed_mape", "proposed_mape"),
        ]:
            vals = group[col]
            row[f"avg_{label}"] = round(vals.mean(), 4)
            row[f"median_{label}"] = round(vals.median(), 4)
            row[f"pct_zero_{label}"] = round((vals == 0).mean(), 4)
        rows.append(row)
    return pd.DataFrame(rows)


def compute_stockout_impact(results_df: pd.DataFrame) -> pd.DataFrame:
    """Compare metrics with and without stockout correction."""
    has_demand_raw = results_df[results_df["actual_mu_raw"] > 0].copy()
    has_demand_corr = results_df[results_df["actual_mu_corrected"] > 0].copy()

    rows = []
    for method in results_df["method"].unique():
        raw = has_demand_raw[has_demand_raw["method"] == method]
        corr = has_demand_corr[has_demand_corr["method"] == method]

        if raw.empty or corr.empty:
            continue

        raw_err = (raw["predicted_mu"] - raw["actual_mu_raw"]).abs()
        corr_err = (corr["predicted_mu"] - corr["actual_mu_corrected"]).abs()

        raw_mape = (raw_err / raw["actual_mu_raw"].clip(lower=MAPE_EPSILON)).mean()
        corr_mape = (corr_err / corr["actual_mu_corrected"].clip(lower=MAPE_EPSILON)).mean()

        rows.append({
            "method": method,
            "mae_raw": raw_err.mean(),
            "mae_corrected": corr_err.mean(),
            "mae_delta": corr_err.mean() - raw_err.mean(),
            "mape_raw": raw_mape,
            "mape_corrected": corr_mape,
            "mape_delta": corr_mape - raw_mape,
            "n_raw": len(raw),
            "n_corrected": len(corr),
            "pct_with_stockouts": (results_df[results_df["method"] == method]["stockout_days_eval"] > 0).mean(),
        })

    return pd.DataFrame(rows).sort_values("mae_corrected")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    base_dir = Path(__file__).parent
    data_dir = base_dir / "data"
    results_dir = base_dir / "results"
    results_dir.mkdir(exist_ok=True)

    if not (data_dir / "stock_movements.csv").exists():
        print("ERROR: No data snapshot found. Run extract_snapshot.py first.")
        sys.exit(1)

    print("Loading snapshot...")
    products, movements, inventory = load_snapshot(data_dir)
    print(f"  Products: {len(products)}, Movements: {len(movements)}")

    category_map = dict(zip(products["item_id"], products["category_name"]))
    print(f"  Categories: {products['category_name'].nunique()} unique")

    print("\nDetecting stockouts from inventory levels...")
    stockout_days = build_stockout_days(movements)

    print("Building daily usage (stockout-aware)...")
    daily_usage = build_daily_usage_stockout_aware(movements, stockout_days)
    if daily_usage.empty:
        print("ERROR: No daily usage data produced.")
        sys.exit(1)

    sellers = daily_usage.groupby("item_id")["consumption"].sum()
    n_sellers = (sellers > 0).sum()
    print(f"  Items with sales: {n_sellers}")

    print_stockout_summary(stockout_days, daily_usage)

    print(f"\nRunning walk-forward backtest ({len(ALL_METHODS)} methods, stockout-aware)...")
    results = walk_forward_backtest(daily_usage, ALL_METHODS, category_map)
    if results.empty:
        print("ERROR: Backtest produced no results.")
        sys.exit(1)

    results.to_csv(results_dir / "backtest_predictions.csv", index=False)
    print(f"\n  Saved {len(results)} predictions")

    # Metrics with stockout-corrected actuals
    print("\nComputing metrics (stockout-corrected actuals)...")
    method_summary, item_summary = compute_metrics(results, actual_col="actual_mu_corrected")
    method_summary = method_summary.sort_values("mae")
    method_summary.to_csv(results_dir / "method_summary.csv", index=False)
    item_summary.to_csv(results_dir / "item_method_summary.csv", index=False)

    conf_comparison = compute_confidence_comparison(results)
    conf_comparison.to_csv(results_dir / "confidence_comparison.csv", index=False)

    # Stockout impact analysis
    stockout_impact = compute_stockout_impact(results)
    stockout_impact.to_csv(results_dir / "stockout_analysis.csv", index=False)

    # Print summaries
    print(f"\n{'='*90}")
    print("METHOD COMPARISON (stockout-corrected, sorted by MAE)")
    print(f"{'='*90}")
    display_cols = [
        "method", "mae", "rmse", "mape", "bias",
        "within_1_unit", "within_2_units", "n_predictions",
    ]
    print(method_summary[display_cols].to_string(index=False, float_format="%.4f"))

    print(f"\n{'='*90}")
    print("STOCKOUT IMPACT: RAW vs CORRECTED")
    print(f"{'='*90}")
    impact_cols = [
        "method", "mae_raw", "mae_corrected", "mae_delta",
        "mape_raw", "mape_corrected", "pct_with_stockouts",
    ]
    print(stockout_impact[impact_cols].to_string(index=False, float_format="%.4f"))

    print(f"\n{'='*90}")
    print("CONFIDENCE FORMULA COMPARISON")
    print(f"{'='*90}")
    conf_display = ["method", "avg_current", "avg_proposed",
                     "avg_current_mape", "avg_proposed_mape",
                     "pct_zero_current", "pct_zero_proposed"]
    print(conf_comparison[conf_display].to_string(index=False, float_format="%.4f"))

    print(f"\nResults saved to {results_dir.resolve()}")


if __name__ == "__main__":
    main()
