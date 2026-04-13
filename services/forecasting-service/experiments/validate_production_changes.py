"""Validate production code changes against snapshot data.

Sweeps min_in_stock_days (training guard) and min_test_in_stock_days
(evaluation guard) thresholds to find the optimal combination for accuracy.

Usage:
    cd services/forecasting-service
    python experiments/validate_production_changes.py
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pandas as pd

SERVICE_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SERVICE_ROOT))

from src import config
from src import features as feat
from src import forecast as fc

DATA_DIR = Path(__file__).parent / "data"


def load_snapshot():
    movements = pd.read_csv(DATA_DIR / "stock_movements.csv")
    products = pd.read_csv(DATA_DIR / "products.csv")
    return movements, products


def prepare_data(movements, products):
    """Build daily usage with stockout flags (done once, shared by all sweeps)."""
    daily_all = feat.build_daily_usage(movements)
    stockout_df = feat.detect_stockout_days(movements)

    daily_with_stockout = daily_all.copy()
    if not stockout_df.empty:
        daily_with_stockout = daily_with_stockout.merge(
            stockout_df, on=["item_id", "date"], how="left"
        )
        daily_with_stockout["is_stockout"] = daily_with_stockout["is_stockout"].fillna(False)

    daily_all["date"] = pd.to_datetime(daily_all["date"])
    daily_with_stockout["date"] = pd.to_datetime(daily_with_stockout["date"])

    category_map = dict(zip(products["item_id"], products["category_name"]))
    name_map = dict(zip(products["item_id"], products["name"]))

    return daily_all, daily_with_stockout, category_map, name_map


def walk_forward(
    daily_all,
    daily_with_stockout,
    category_map,
    min_in_stock_days,
    min_test_in_stock_days,
    min_train_days=14,
    horizon_days=7,
):
    """Run walk-forward backtest for both old and new pipelines.

    Returns (old_preds, new_preds) DataFrames.
    """
    date_min = daily_all["date"].min()
    date_max = daily_all["date"].max()
    first_origin = date_min + pd.Timedelta(days=min_train_days)
    last_origin = date_max - pd.Timedelta(days=horizon_days)
    origins = pd.date_range(first_origin, last_origin, freq=f"{horizon_days}D")

    old_preds = []
    new_preds = []

    for origin in origins:
        test_end = origin + pd.Timedelta(days=horizon_days)

        # OLD pipeline (no stockout awareness, min_in_stock_days=0 disables guard)
        train_old = daily_all[daily_all["date"] < origin]
        test_window = daily_with_stockout[
            (daily_with_stockout["date"] >= origin) & (daily_with_stockout["date"] < test_end)
        ]
        if train_old.empty or test_window.empty:
            continue

        features_old = feat.build_stats(train_old)
        est_old = fc.estimate_mu_sigma(features_old, method="dow_weighted", min_in_stock_days=0)

        # NEW pipeline (stockout-aware + category fallback + guard)
        train_new = daily_with_stockout[daily_with_stockout["date"] < origin]
        features_new = feat.build_stats(train_new)
        est_new = fc.estimate_mu_sigma(
            features_new, method="dow_weighted", min_in_stock_days=min_in_stock_days,
        )
        items_with_history = set(features_new["item_id"].unique())
        est_new = fc.apply_category_fallback(est_new, category_map, items_with_history)

        # Evaluate both against stockout-corrected actuals
        for item_id in est_old["item_id"].unique():
            item_test = test_window[test_window["item_id"] == item_id]
            if item_test.empty:
                continue

            # Compute actual with evaluation guard
            if "is_stockout" in item_test.columns:
                in_stock = item_test[~item_test["is_stockout"]]
                if len(in_stock) < min_test_in_stock_days:
                    continue  # Not enough ground truth
                in_stock_days = max(len(in_stock), 1)
                actual_mu = float(in_stock["consumption"].sum()) / in_stock_days
            else:
                actual_mu = float(item_test["consumption"].mean())

            old_mu = float(est_old.loc[est_old["item_id"] == item_id, "mu_hat"].iloc[0])
            old_preds.append({
                "origin": origin, "item_id": item_id,
                "predicted_mu": old_mu, "actual_mu": actual_mu,
            })

            if item_id in est_new["item_id"].values:
                new_mu = float(est_new.loc[est_new["item_id"] == item_id, "mu_hat"].iloc[0])
                new_preds.append({
                    "origin": origin, "item_id": item_id,
                    "predicted_mu": new_mu, "actual_mu": actual_mu,
                })

    return pd.DataFrame(old_preds), pd.DataFrame(new_preds)


def compute_metrics(pred_df):
    has_demand = pred_df[pred_df["actual_mu"] > 0].copy()
    if has_demand.empty:
        return {"n": 0, "mae": 0, "rmse": 0, "bias": 0, "within_1": 0, "within_2": 0}
    errors = has_demand["predicted_mu"] - has_demand["actual_mu"]
    abs_errors = errors.abs()
    return {
        "n": len(has_demand),
        "mae": round(float(abs_errors.mean()), 4),
        "rmse": round(float(np.sqrt((errors ** 2).mean())), 4),
        "bias": round(float(errors.mean()), 4),
        "within_1": round(float((abs_errors <= 1.0).mean()) * 100, 1),
        "within_2": round(float((abs_errors <= 2.0).mean()) * 100, 1),
    }


def main():
    print("=" * 70)
    print("THRESHOLD SWEEP: Finding optimal stockout correction parameters")
    print("=" * 70)

    movements, products = load_snapshot()
    print(f"Loaded {len(movements)} movements, {len(products)} products\n")

    daily_all, daily_with_stockout, category_map, name_map = prepare_data(movements, products)

    # Threshold combinations to sweep
    train_thresholds = [0, 3, 5, 7, 10]
    test_thresholds = [0, 3]

    results = []

    for min_test in test_thresholds:
        for min_train in train_thresholds:
            label = f"train={min_train:2d}, test={min_test}"
            print(f"  Running: {label} ...", end="", flush=True)

            old_preds, new_preds = walk_forward(
                daily_all, daily_with_stockout, category_map,
                min_in_stock_days=min_train,
                min_test_in_stock_days=min_test,
            )

            old_m = compute_metrics(old_preds)
            new_m = compute_metrics(new_preds)

            results.append({
                "min_train": min_train,
                "min_test": min_test,
                "old_n": old_m["n"],
                "old_mae": old_m["mae"],
                "old_bias": old_m["bias"],
                "old_within_1": old_m["within_1"],
                "old_within_2": old_m["within_2"],
                "new_n": new_m["n"],
                "new_mae": new_m["mae"],
                "new_bias": new_m["bias"],
                "new_within_1": new_m["within_1"],
                "new_within_2": new_m["within_2"],
            })
            print(f" done (old MAE={old_m['mae']:.4f}, new MAE={new_m['mae']:.4f})")

    # Display results
    print("\n" + "=" * 70)
    print("SWEEP RESULTS")
    print("=" * 70)

    header = (
        f"{'min_train':>9} {'min_test':>8} | "
        f"{'Old MAE':>8} {'Old Bias':>9} {'Old W1%':>7} | "
        f"{'New MAE':>8} {'New Bias':>9} {'New W1%':>7} {'New W2%':>7} | "
        f"{'dMAE':>7} {'dBias':>7} {'N':>5}"
    )
    print(header)
    print("-" * len(header))

    for r in results:
        d_mae = r["new_mae"] - r["old_mae"]
        d_bias = abs(r["new_bias"]) - abs(r["old_bias"])
        print(
            f"{r['min_train']:>9} {r['min_test']:>8} | "
            f"{r['old_mae']:>8.4f} {r['old_bias']:>+9.4f} {r['old_within_1']:>6.1f}% | "
            f"{r['new_mae']:>8.4f} {r['new_bias']:>+9.4f} {r['new_within_1']:>6.1f}% {r['new_within_2']:>6.1f}% | "
            f"{d_mae:>+7.4f} {d_bias:>+7.4f} {r['new_n']:>5}"
        )

    # Find best configuration (minimize MAE while improving bias)
    print("\n" + "=" * 70)
    print("ANALYSIS")
    print("=" * 70)

    best = None
    for r in results:
        # Criteria: new MAE <= old MAE AND new bias closer to 0 than old bias
        mae_ok = r["new_mae"] <= r["old_mae"]
        bias_improved = abs(r["new_bias"]) < abs(r["old_bias"])
        if mae_ok and bias_improved:
            if best is None or r["new_mae"] < best["new_mae"]:
                best = r

    if best:
        print(f"\nBest configuration: min_train={best['min_train']}, min_test={best['min_test']}")
        print(f"  New MAE:  {best['new_mae']:.4f} (old: {best['old_mae']:.4f}, delta: {best['new_mae'] - best['old_mae']:+.4f})")
        print(f"  New Bias: {best['new_bias']:+.4f} (old: {best['old_bias']:+.4f})")
        print(f"  Within 1: {best['new_within_1']:.1f}% (old: {best['old_within_1']:.1f}%)")
        print(f"  Within 2: {best['new_within_2']:.1f}% (old: {best['old_within_2']:.1f}%)")
        print(f"  Predictions evaluated: {best['new_n']}")
    else:
        print("\nNo configuration beats baseline on BOTH MAE and bias.")
        print("Showing best MAE among configurations with improved bias:")
        bias_improved = [r for r in results if abs(r["new_bias"]) < abs(r["old_bias"])]
        if bias_improved:
            best_bias = min(bias_improved, key=lambda r: r["new_mae"])
            print(f"  min_train={best_bias['min_train']}, min_test={best_bias['min_test']}")
            print(f"  New MAE: {best_bias['new_mae']:.4f} (old: {best_bias['old_mae']:.4f})")
            print(f"  New Bias: {best_bias['new_bias']:+.4f} (old: {best_bias['old_bias']:+.4f})")
            print(f"  Within 1: {best_bias['new_within_1']:.1f}%")

    # Show per-item detail for the best config
    if best:
        print(f"\n--- Running detailed comparison for best config ---")
        old_preds, new_preds = walk_forward(
            daily_all, daily_with_stockout, category_map,
            min_in_stock_days=best["min_train"],
            min_test_in_stock_days=best["min_test"],
        )

        if not old_preds.empty and not new_preds.empty:
            old_item = old_preds[old_preds["actual_mu"] > 0].groupby("item_id").agg(
                mae_old=("predicted_mu", lambda g: (g - old_preds.loc[g.index, "actual_mu"]).abs().mean())
            )
            # Simpler approach
            old_has = old_preds[old_preds["actual_mu"] > 0].copy()
            old_has["abs_err"] = (old_has["predicted_mu"] - old_has["actual_mu"]).abs()
            old_item = old_has.groupby("item_id")["abs_err"].mean().rename("mae_old")

            new_has = new_preds[new_preds["actual_mu"] > 0].copy()
            new_has["abs_err"] = (new_has["predicted_mu"] - new_has["actual_mu"]).abs()
            new_item = new_has.groupby("item_id")["abs_err"].mean().rename("mae_new")

            item_compare = pd.concat([old_item, new_item], axis=1).dropna()
            item_compare["mae_delta"] = item_compare["mae_new"] - item_compare["mae_old"]
            item_compare["name"] = item_compare.index.map(name_map)

            improved = item_compare[item_compare["mae_delta"] < -0.1].sort_values("mae_delta")
            worsened = item_compare[item_compare["mae_delta"] > 0.1].sort_values("mae_delta", ascending=False)

            print(f"\nItems improved (MAE decreased >0.1): {len(improved)}")
            if not improved.empty:
                print(improved[["name", "mae_old", "mae_new", "mae_delta"]].head(10).to_string())

            print(f"\nItems worsened (MAE increased >0.1): {len(worsened)}")
            if not worsened.empty:
                print(worsened[["name", "mae_old", "mae_new", "mae_delta"]].head(10).to_string())

    print(f"\nRecommended config.py settings:")
    if best:
        print(f"  MIN_IN_STOCK_DAYS = {best['min_train']}")
        print(f"  MIN_TEST_IN_STOCK_DAYS = {best['min_test']}")
    else:
        print("  (manual review needed -- see table above)")


if __name__ == "__main__":
    main()
