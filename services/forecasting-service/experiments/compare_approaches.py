"""Head-to-head comparison of forecasting approaches.

Tests 4 strategies against production data using walk-forward backtesting:

  A. Main-only: DOW-weighted, NO stockout awareness, NO category fallback
  B. Branch-only: DOW-weighted + stockout exclusion + category fallback
  C. Combined: B + stockout demand imputation (DOW-based)
  D. Combined + recency: C with exponential recency weighting

Loads data directly from Supabase (or from cached CSVs in experiments/data/).

Usage:
    cd services/forecasting-service
    python experiments/compare_approaches.py          # uses DB
    python experiments/compare_approaches.py --cached  # uses experiments/data/

Output: prints comparison table and per-item analysis.
"""

from __future__ import annotations

import sys
from pathlib import Path

SERVICE_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SERVICE_ROOT))

import numpy as np
import pandas as pd

from src import config
from src import features as feat
from src import forecast as fc

# ---------------------------------------------------------------------------
# Data loading
# ---------------------------------------------------------------------------

def load_from_db():
    """Load data from Supabase."""
    from src.adapters.supabase_repo import SupabaseRepo
    from datetime import datetime, timedelta, timezone

    repo = SupabaseRepo()
    end_ts = datetime.now(timezone.utc)
    start_ts = end_ts - timedelta(days=45)

    movements = repo.get_stock_movements(start=start_ts, end=end_ts)
    items = repo.get_items()
    return movements, items


def load_from_cache():
    """Load from cached CSVs."""
    data_dir = Path(__file__).parent / "data"
    movements = pd.read_csv(data_dir / "stock_movements.csv")
    items = pd.read_csv(data_dir / "products.csv")
    return movements, items


def prepare_data(movements, items):
    """Build daily usage with and without stockout flags."""
    daily_all = feat.build_daily_usage(movements)
    stockout_df = feat.detect_stockout_days(movements)

    daily_with_stockout = daily_all.copy()
    if not stockout_df.empty:
        daily_with_stockout = daily_with_stockout.merge(
            stockout_df, on=["item_id", "date"], how="left"
        )
        daily_with_stockout["is_stockout"] = (
            daily_with_stockout["is_stockout"].fillna(False).astype(bool)
        )

    daily_all["date"] = pd.to_datetime(daily_all["date"])
    daily_with_stockout["date"] = pd.to_datetime(daily_with_stockout["date"])

    category_map = {}
    if "category_name" in items.columns:
        category_map = dict(zip(items["item_id"], items["category_name"]))
    elif "category" in items.columns:
        category_map = dict(zip(items["item_id"], items["category"]))

    return daily_all, daily_with_stockout, category_map


# ---------------------------------------------------------------------------
# DOW-weighted estimate WITH imputation (the new approach)
# ---------------------------------------------------------------------------

def _dow_weighted_with_imputation(group, min_in_stock_days=7):
    """DOW-weighted estimate with stockout demand imputation.

    1. Filter to in-stock days
    2. Learn per-DOW demand pattern from in-stock days
    3. For each stockout day, impute demand = DOW mean from in-stock data
    4. Compute mu from combined (actual + imputed) data
    """
    # Filter stockout days
    if "is_stockout" in group.columns:
        in_stock = group[~group["is_stockout"]].copy()
        if len(in_stock) < min_in_stock_days:
            in_stock = group.copy()  # fallback to all data
    else:
        in_stock = group.copy()

    if in_stock.empty:
        return config.MU_FLOOR, config.SIGMA_FLOOR, {d: 1.0 for d in range(7)}

    in_stock["dow"] = pd.to_datetime(in_stock["date"]).dt.dayofweek
    dow_means_in_stock = in_stock.groupby("dow")["consumption"].mean()
    global_in_stock_mean = float(in_stock["consumption"].mean())

    # Impute stockout days
    combined = in_stock[["date", "dow", "consumption"]].copy()
    if "is_stockout" in group.columns:
        stockout_days = group[group["is_stockout"]].copy()
        if not stockout_days.empty and len(in_stock) >= min_in_stock_days:
            stockout_days["dow"] = pd.to_datetime(stockout_days["date"]).dt.dayofweek
            stockout_days["consumption"] = (
                stockout_days["dow"]
                .map(dow_means_in_stock)
                .fillna(global_in_stock_mean)
            )
            combined = pd.concat([
                combined,
                stockout_days[["date", "dow", "consumption"]],
            ], ignore_index=True)

    overall_mean = max(float(combined["consumption"].mean()), config.MU_FLOOR)

    # DOW multipliers from combined data
    dow_means = combined.groupby("dow")["consumption"].mean()
    dow_multipliers = (dow_means / max(overall_mean, config.MU_FLOOR)).to_dict()
    for d in range(7):
        if d not in dow_multipliers:
            dow_multipliers[d] = 1.0
    dow_multipliers = {d: round(float(v), 4) for d, v in dow_multipliers.items()}

    expected = combined["dow"].map(dow_means).fillna(overall_mean)
    residuals = combined["consumption"] - expected
    sigma_d_hat = max(float(residuals.std(ddof=0)), config.SIGMA_FLOOR)

    return overall_mean, sigma_d_hat, dow_multipliers


# ---------------------------------------------------------------------------
# Walk-forward backtest engine
# ---------------------------------------------------------------------------

def run_walk_forward(
    daily_data,
    category_map,
    approach,
    min_train_days=14,
    horizon_days=7,
    min_test_in_stock_days=3,
):
    """Walk-forward backtest for a given approach.

    Approaches:
      "main": No stockout awareness, standard DOW-weighted
      "branch": Stockout exclusion + category fallback (current branch behavior)
      "imputed": Stockout exclusion + imputation + category fallback
    """
    date_min = daily_data["date"].min()
    date_max = daily_data["date"].max()
    first_origin = date_min + pd.Timedelta(days=min_train_days)
    last_origin = date_max - pd.Timedelta(days=horizon_days)

    if first_origin > last_origin:
        return pd.DataFrame()

    origins = pd.date_range(first_origin, last_origin, freq=f"{horizon_days}D")
    predictions = []

    for origin in origins:
        train = daily_data[daily_data["date"] < origin].copy()
        test_end = origin + pd.Timedelta(days=horizon_days)
        test = daily_data[
            (daily_data["date"] >= origin) & (daily_data["date"] < test_end)
        ].copy()

        if train.empty or test.empty:
            continue

        # --- TRAIN: estimate mu per item ---
        if approach == "main":
            # No stockout awareness: strip is_stockout, use raw data
            train_clean = train.drop(columns=["is_stockout"], errors="ignore")
            train_features = feat.build_stats(train_clean)
            estimates = fc.estimate_mu_sigma(
                train_features, method="dow_weighted", min_in_stock_days=0
            )

        elif approach == "branch":
            # Stockout exclusion + category fallback
            train_features = feat.build_stats(train)
            estimates = fc.estimate_mu_sigma(
                train_features,
                method="dow_weighted",
                min_in_stock_days=config.MIN_IN_STOCK_DAYS,
            )
            items_with_history = set(train_features["item_id"].unique())
            estimates = fc.apply_category_fallback(
                estimates, category_map, items_with_history
            )

        elif approach == "imputed":
            # Stockout exclusion + DOW imputation + category fallback
            train_features = feat.build_stats(train)
            # Custom per-item estimation with imputation
            results = []
            for item_id, group in train.groupby("item_id", sort=False):
                group = group.sort_values("date")
                mu, sigma, dow_mults = _dow_weighted_with_imputation(
                    group, min_in_stock_days=config.MIN_IN_STOCK_DAYS
                )
                results.append({
                    "item_id": str(item_id),
                    "mu_hat": mu,
                    "sigma_d_hat": sigma,
                    "method": "dow_weighted_imputed",
                })
            estimates = pd.DataFrame(results)
            items_with_history = set(train["item_id"].unique())
            estimates = fc.apply_category_fallback(
                estimates, category_map, items_with_history
            )

        else:
            raise ValueError(f"Unknown approach: {approach}")

        # --- EVALUATE: compare against stockout-corrected actuals ---
        for item_id in estimates["item_id"].unique():
            item_test = test[test["item_id"] == item_id]
            if item_test.empty:
                continue

            est_row = estimates[estimates["item_id"] == item_id].iloc[0]
            predicted_mu = float(est_row["mu_hat"])

            # Stockout-corrected actual
            if "is_stockout" in item_test.columns:
                in_stock = item_test[~item_test["is_stockout"]]
                if len(in_stock) < min_test_in_stock_days:
                    continue
                actual_mu = float(in_stock["consumption"].sum()) / max(
                    len(in_stock), 1
                )
            else:
                actual_mu = float(item_test["consumption"].mean())

            predictions.append({
                "approach": approach,
                "origin": origin,
                "item_id": item_id,
                "predicted_mu": predicted_mu,
                "actual_mu": actual_mu,
            })

    return pd.DataFrame(predictions)


# ---------------------------------------------------------------------------
# Metrics
# ---------------------------------------------------------------------------

def compute_metrics(pred_df):
    has_demand = pred_df[pred_df["actual_mu"] > 0].copy()
    if has_demand.empty:
        return {
            "n": 0, "mae": 0, "rmse": 0, "bias": 0,
            "within_1": 0, "within_2": 0, "mape": 0,
        }
    errors = has_demand["predicted_mu"] - has_demand["actual_mu"]
    abs_errors = errors.abs()
    pct_errors = abs_errors / has_demand["actual_mu"].clip(lower=0.1)
    return {
        "n": len(has_demand),
        "mae": round(float(abs_errors.mean()), 4),
        "rmse": round(float(np.sqrt((errors**2).mean())), 4),
        "bias": round(float(errors.mean()), 4),
        "within_1": round(float((abs_errors <= 1.0).mean()) * 100, 1),
        "within_2": round(float((abs_errors <= 2.0).mean()) * 100, 1),
        "mape": round(float(pct_errors.mean()) * 100, 1),
    }


def per_item_comparison(all_preds):
    """Per-item MAE comparison across approaches."""
    has_demand = all_preds[all_preds["actual_mu"] > 0].copy()
    has_demand["abs_error"] = (
        has_demand["predicted_mu"] - has_demand["actual_mu"]
    ).abs()

    item_metrics = (
        has_demand.groupby(["approach", "item_id"])["abs_error"]
        .mean()
        .unstack(level=0)
    )
    return item_metrics


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    use_cache = "--cached" in sys.argv

    print("=" * 75)
    print("FORECASTING APPROACH COMPARISON")
    print("Walk-forward backtest, stockout-corrected evaluation")
    print("=" * 75)

    if use_cache:
        print("Loading from cached CSVs...")
        movements, items = load_from_cache()
    else:
        print("Loading from Supabase...")
        movements, items = load_from_db()

    print(f"  {len(movements)} movements, {len(items)} products")

    daily_all, daily_with_stockout, category_map = prepare_data(movements, items)

    stockout_pct = 0.0
    if "is_stockout" in daily_with_stockout.columns:
        stockout_pct = daily_with_stockout["is_stockout"].mean() * 100
    print(f"  Stockout rate: {stockout_pct:.1f}% of item-days")
    print(f"  Date range: {daily_with_stockout['date'].min().date()} to {daily_with_stockout['date'].max().date()}")

    approaches = {
        "main": ("A. Main (no stockout awareness)", daily_all),
        "branch": ("B. Branch (stockout exclusion + fallback)", daily_with_stockout),
        "imputed": ("C. Combined (exclusion + DOW imputation + fallback)", daily_with_stockout),
    }

    all_preds = []
    for key, (label, data) in approaches.items():
        print(f"\n  Running: {label} ...", end="", flush=True)
        preds = run_walk_forward(data, category_map, approach=key)
        if not preds.empty:
            all_preds.append(preds)
        m = compute_metrics(preds) if not preds.empty else {"n": 0, "mae": 0}
        print(f" done (n={m['n']}, MAE={m['mae']:.4f})")

    if not all_preds:
        print("\nNo predictions generated. Check data.")
        return

    all_preds_df = pd.concat(all_preds, ignore_index=True)

    # --- Summary table ---
    print("\n" + "=" * 75)
    print("RESULTS SUMMARY")
    print("=" * 75)

    header = f"{'Approach':<55} {'N':>5} {'MAE':>7} {'RMSE':>7} {'Bias':>8} {'W1%':>6} {'W2%':>6} {'MAPE%':>7}"
    print(header)
    print("-" * len(header))

    results = {}
    for key, (label, _) in approaches.items():
        preds = all_preds_df[all_preds_df["approach"] == key]
        m = compute_metrics(preds)
        results[key] = m
        print(
            f"{label:<55} {m['n']:>5} {m['mae']:>7.4f} {m['rmse']:>7.4f} "
            f"{m['bias']:>+8.4f} {m['within_1']:>5.1f}% {m['within_2']:>5.1f}% {m['mape']:>6.1f}%"
        )

    # --- Deltas vs main ---
    print("\n" + "=" * 75)
    print("IMPROVEMENT vs MAIN BASELINE")
    print("=" * 75)

    baseline = results.get("main", {})
    for key in ["branch", "imputed"]:
        if key not in results:
            continue
        m = results[key]
        label = approaches[key][0]
        d_mae = m["mae"] - baseline.get("mae", 0)
        d_bias = abs(m["bias"]) - abs(baseline.get("bias", 0))
        d_w1 = m["within_1"] - baseline.get("within_1", 0)
        d_w2 = m["within_2"] - baseline.get("within_2", 0)
        print(f"\n  {label}:")
        print(f"    MAE:      {m['mae']:.4f} (delta: {d_mae:+.4f})")
        print(f"    Bias:     {m['bias']:+.4f} (|bias| delta: {d_bias:+.4f})")
        print(f"    Within 1: {m['within_1']:.1f}% (delta: {d_w1:+.1f}pp)")
        print(f"    Within 2: {m['within_2']:.1f}% (delta: {d_w2:+.1f}pp)")

    # --- Per-item analysis ---
    print("\n" + "=" * 75)
    print("PER-ITEM WINNERS (imputed vs branch)")
    print("=" * 75)

    item_metrics = per_item_comparison(all_preds_df)
    if "imputed" in item_metrics.columns and "branch" in item_metrics.columns:
        item_metrics["delta"] = item_metrics["imputed"] - item_metrics["branch"]
        item_metrics = item_metrics.dropna(subset=["delta"])

        improved = item_metrics[item_metrics["delta"] < -0.1].sort_values("delta")
        worsened = item_metrics[item_metrics["delta"] > 0.1].sort_values(
            "delta", ascending=False
        )
        neutral = item_metrics[item_metrics["delta"].abs() <= 0.1]

        print(f"  Improved:  {len(improved)} items (imputation helped)")
        print(f"  Worsened:  {len(worsened)} items (imputation hurt)")
        print(f"  Neutral:   {len(neutral)} items (< 0.1 difference)")

        if not improved.empty:
            print("\n  Top 5 most improved:")
            for item_id, row in improved.head(5).iterrows():
                print(
                    f"    {item_id[:12]:>12}: branch MAE={row['branch']:.2f} -> imputed MAE={row['imputed']:.2f} (delta={row['delta']:+.2f})"
                )

        if not worsened.empty:
            print("\n  Top 5 most worsened:")
            for item_id, row in worsened.head(5).iterrows():
                print(
                    f"    {item_id[:12]:>12}: branch MAE={row['branch']:.2f} -> imputed MAE={row['imputed']:.2f} (delta={row['delta']:+.2f})"
                )

    # --- Stockout-heavy items analysis ---
    print("\n" + "=" * 75)
    print("STOCKOUT-HEAVY ITEMS (>30% stockout rate)")
    print("=" * 75)

    if "is_stockout" in daily_with_stockout.columns:
        item_stockout = (
            daily_with_stockout.groupby("item_id")["is_stockout"]
            .mean()
            .rename("stockout_pct")
        )
        heavy_items = set(item_stockout[item_stockout > 0.3].index)

        for key, (label, _) in approaches.items():
            preds = all_preds_df[
                (all_preds_df["approach"] == key)
                & (all_preds_df["item_id"].isin(heavy_items))
            ]
            m = compute_metrics(preds)
            print(f"  {label}: MAE={m['mae']:.4f}, bias={m['bias']:+.4f}, n={m['n']}")

    print("\n" + "=" * 75)
    print("DONE")
    print("=" * 75)


if __name__ == "__main__":
    main()
