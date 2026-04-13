"""Fair head-to-head: evaluate ALL approaches on the SAME prediction set.

The previous comparison was unfair because the branch's min_test_in_stock_days=3
filter excluded ~95 predictions from evaluation (the hardest ones).

This script:
1. Generates predictions from all approaches for every (origin, item) pair
2. Evaluates ALL of them against the SAME actual demand (raw, no stockout correction)
3. Also evaluates with stockout correction but reports N so we can verify fairness

Usage:
    cd services/forecasting-service
    python experiments/compare_fair.py --cached
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


def load_from_cache():
    data_dir = Path(__file__).parent / "data"
    movements = pd.read_csv(data_dir / "stock_movements.csv")
    items = pd.read_csv(data_dir / "products.csv")
    return movements, items


def prepare_data(movements, items):
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

    return daily_all, daily_with_stockout, category_map


def _dow_weighted_with_imputation(group, min_in_stock_days=7):
    if "is_stockout" in group.columns:
        in_stock = group[~group["is_stockout"]].copy()
        if len(in_stock) < min_in_stock_days:
            in_stock = group.copy()
    else:
        in_stock = group.copy()

    if in_stock.empty:
        return config.MU_FLOOR, config.SIGMA_FLOOR

    in_stock["dow"] = pd.to_datetime(in_stock["date"]).dt.dayofweek
    dow_means_in_stock = in_stock.groupby("dow")["consumption"].mean()
    global_in_stock_mean = float(in_stock["consumption"].mean())

    combined = in_stock[["date", "dow", "consumption"]].copy()
    if "is_stockout" in group.columns:
        stockout_days = group[group["is_stockout"]].copy()
        if not stockout_days.empty and len(in_stock) >= min_in_stock_days:
            stockout_days["dow"] = pd.to_datetime(stockout_days["date"]).dt.dayofweek
            stockout_days["consumption"] = (
                stockout_days["dow"].map(dow_means_in_stock).fillna(global_in_stock_mean)
            )
            combined = pd.concat([
                combined, stockout_days[["date", "dow", "consumption"]]
            ], ignore_index=True)

    overall_mean = max(float(combined["consumption"].mean()), config.MU_FLOOR)
    dow_means = combined.groupby("dow")["consumption"].mean()
    expected = combined["dow"].map(dow_means).fillna(overall_mean)
    residuals = combined["consumption"] - expected
    sigma = max(float(residuals.std(ddof=0)), config.SIGMA_FLOOR)
    return overall_mean, sigma


def estimate_all_approaches(train_all, train_stockout, category_map):
    """Return mu_hat per item for each approach."""
    results = {}

    # A: Main (no stockout awareness)
    train_clean = train_all.drop(columns=["is_stockout"], errors="ignore")
    features_a = feat.build_stats(train_clean)
    est_a = fc.estimate_mu_sigma(features_a, method="dow_weighted", min_in_stock_days=0)
    results["main"] = dict(zip(est_a["item_id"], est_a["mu_hat"]))

    # B: Branch (stockout exclusion)
    features_b = feat.build_stats(train_stockout)
    est_b = fc.estimate_mu_sigma(
        features_b, method="dow_weighted",
        min_in_stock_days=config.MIN_IN_STOCK_DAYS,
    )
    items_hist = set(features_b["item_id"].unique())
    est_b = fc.apply_category_fallback(est_b, category_map, items_hist)
    results["branch"] = dict(zip(est_b["item_id"], est_b["mu_hat"]))

    # C: Imputation
    imputed_rows = []
    for item_id, group in train_stockout.groupby("item_id", sort=False):
        group = group.sort_values("date")
        mu, sigma = _dow_weighted_with_imputation(
            group, min_in_stock_days=config.MIN_IN_STOCK_DAYS
        )
        imputed_rows.append({"item_id": str(item_id), "mu_hat": mu, "sigma_d_hat": sigma, "method": "imputed"})
    est_c = pd.DataFrame(imputed_rows)
    est_c = fc.apply_category_fallback(est_c, category_map, items_hist)
    results["imputed"] = dict(zip(est_c["item_id"], est_c["mu_hat"]))

    # D: Main + category fallback (best of main + cold-start handling)
    est_d = fc.estimate_mu_sigma(features_a, method="dow_weighted", min_in_stock_days=0)
    est_d = fc.apply_category_fallback(est_d, category_map, items_hist)
    results["main_plus_fallback"] = dict(zip(est_d["item_id"], est_d["mu_hat"]))

    # E: Imputation with lower guard (min_in_stock=3 instead of 7)
    imputed_low = []
    for item_id, group in train_stockout.groupby("item_id", sort=False):
        group = group.sort_values("date")
        mu, sigma = _dow_weighted_with_imputation(group, min_in_stock_days=3)
        imputed_low.append({"item_id": str(item_id), "mu_hat": mu, "sigma_d_hat": sigma, "method": "imputed_low"})
    est_e = pd.DataFrame(imputed_low)
    est_e = fc.apply_category_fallback(est_e, category_map, items_hist)
    results["imputed_low_guard"] = dict(zip(est_e["item_id"], est_e["mu_hat"]))

    return results


def main():
    print("=" * 80)
    print("FAIR COMPARISON: Same prediction set, multiple estimation approaches")
    print("=" * 80)

    movements, items = load_from_cache()
    daily_all, daily_with_stockout, category_map = prepare_data(movements, items)

    stockout_pct = 0.0
    if "is_stockout" in daily_with_stockout.columns:
        stockout_pct = daily_with_stockout["is_stockout"].mean() * 100
    print(f"  {len(movements)} movements, {len(items)} products, stockout={stockout_pct:.1f}%")

    min_train_days = 14
    horizon_days = 7
    date_min = daily_all["date"].min()
    date_max = daily_all["date"].max()
    first_origin = date_min + pd.Timedelta(days=min_train_days)
    last_origin = date_max - pd.Timedelta(days=horizon_days)
    origins = pd.date_range(first_origin, last_origin, freq=f"{horizon_days}D")

    print(f"  {len(origins)} forecast origins, horizon={horizon_days}d")

    all_predictions = []

    for origin in origins:
        train_all = daily_all[daily_all["date"] < origin]
        train_stockout = daily_with_stockout[daily_with_stockout["date"] < origin]
        test_end = origin + pd.Timedelta(days=horizon_days)
        test = daily_with_stockout[
            (daily_with_stockout["date"] >= origin) & (daily_with_stockout["date"] < test_end)
        ]

        if train_all.empty or test.empty:
            continue

        estimates = estimate_all_approaches(train_all, train_stockout, category_map)

        # Evaluate on SAME test set for all approaches
        common_items = set.intersection(*[set(v.keys()) for v in estimates.values()])

        for item_id in common_items:
            item_test = test[test["item_id"] == item_id]
            if item_test.empty:
                continue

            # Raw actual (no stockout correction)
            actual_mu_raw = float(item_test["consumption"].mean())

            # Stockout-corrected actual
            if "is_stockout" in item_test.columns:
                in_stock = item_test[~item_test["is_stockout"]]
                in_stock_days = len(in_stock)
                if in_stock_days > 0:
                    actual_mu_corrected = float(in_stock["consumption"].sum()) / in_stock_days
                else:
                    actual_mu_corrected = actual_mu_raw
                has_stockout_in_test = item_test["is_stockout"].any()
            else:
                actual_mu_corrected = actual_mu_raw
                in_stock_days = len(item_test)
                has_stockout_in_test = False

            for approach, mu_map in estimates.items():
                predicted_mu = mu_map.get(item_id)
                if predicted_mu is None:
                    continue
                all_predictions.append({
                    "approach": approach,
                    "origin": origin,
                    "item_id": item_id,
                    "predicted_mu": predicted_mu,
                    "actual_mu_raw": actual_mu_raw,
                    "actual_mu_corrected": actual_mu_corrected,
                    "in_stock_test_days": in_stock_days,
                    "has_stockout_in_test": has_stockout_in_test,
                })

    pred_df = pd.DataFrame(all_predictions)

    # --- Report 1: Using RAW actuals (no correction) ---
    print("\n" + "=" * 80)
    print("REPORT 1: vs RAW actual demand (includes stockout zeros)")
    print("=" * 80)
    _print_metrics(pred_df, actual_col="actual_mu_raw")

    # --- Report 2: Using corrected actuals (in-stock only) ---
    print("\n" + "=" * 80)
    print("REPORT 2: vs STOCKOUT-CORRECTED actual demand (in-stock days only)")
    print("=" * 80)
    _print_metrics(pred_df, actual_col="actual_mu_corrected")

    # --- Report 3: Corrected actuals, only test windows with 3+ in-stock days ---
    print("\n" + "=" * 80)
    print("REPORT 3: Corrected actuals, test windows with 3+ in-stock days")
    print("=" * 80)
    filtered = pred_df[pred_df["in_stock_test_days"] >= 3]
    _print_metrics(filtered, actual_col="actual_mu_corrected")

    # --- Report 4: Stockout-heavy items only ---
    print("\n" + "=" * 80)
    print("REPORT 4: Only items with stockout in test window")
    print("=" * 80)
    stockout_test = pred_df[pred_df["has_stockout_in_test"]]
    _print_metrics(stockout_test, actual_col="actual_mu_corrected")

    # --- Report 5: Clean items only (no stockout in test) ---
    print("\n" + "=" * 80)
    print("REPORT 5: Only items with NO stockout in test window")
    print("=" * 80)
    clean_test = pred_df[~pred_df["has_stockout_in_test"]]
    _print_metrics(clean_test, actual_col="actual_mu_corrected")


def _print_metrics(pred_df, actual_col):
    approaches = sorted(pred_df["approach"].unique())
    has_demand = pred_df[pred_df[actual_col] > 0]

    header = f"{'Approach':<30} {'N':>6} {'MAE':>7} {'RMSE':>7} {'Bias':>8} {'W1%':>6} {'W2%':>6}"
    print(header)
    print("-" * len(header))

    for approach in approaches:
        group = has_demand[has_demand["approach"] == approach]
        if group.empty:
            print(f"{approach:<30} {'(no data)':>6}")
            continue
        errors = group["predicted_mu"] - group[actual_col]
        abs_errors = errors.abs()
        n = len(group)
        mae = float(abs_errors.mean())
        rmse = float(np.sqrt((errors**2).mean()))
        bias = float(errors.mean())
        w1 = float((abs_errors <= 1.0).mean()) * 100
        w2 = float((abs_errors <= 2.0).mean()) * 100
        print(f"{approach:<30} {n:>6} {mae:>7.4f} {rmse:>7.4f} {bias:>+8.4f} {w1:>5.1f}% {w2:>5.1f}%")


if __name__ == "__main__":
    main()
