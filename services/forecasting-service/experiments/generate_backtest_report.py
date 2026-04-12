"""Generate backtest accuracy report with figures.

Runs the walk-forward backtest comparing old vs new pipeline at the optimal
configuration (MIN_IN_STOCK_DAYS=7, MIN_TEST_IN_STOCK_DAYS=3) and produces:
  1. Summary metrics table (console + CSV)
  2. Per-item MAE comparison chart
  3. Error distribution histogram
  4. Bias comparison bar chart
  5. Accuracy by category breakdown

Usage:
    cd services/forecasting-service
    python experiments/generate_backtest_report.py
"""

from __future__ import annotations

import sys
import warnings
from pathlib import Path

import matplotlib
matplotlib.use("Agg")  # non-interactive backend
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

warnings.filterwarnings("ignore", category=FutureWarning)

SERVICE_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SERVICE_ROOT))

from src import config
from src import features as feat
from src import forecast as fc

DATA_DIR = Path(__file__).parent / "data"
OUT_DIR = Path(__file__).parent / "report"
OUT_DIR.mkdir(exist_ok=True)


def load_snapshot():
    movements = pd.read_csv(DATA_DIR / "stock_movements.csv")
    products = pd.read_csv(DATA_DIR / "products.csv")
    return movements, products


def prepare_data(movements, products):
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
    date_min = daily_all["date"].min()
    date_max = daily_all["date"].max()
    first_origin = date_min + pd.Timedelta(days=min_train_days)
    last_origin = date_max - pd.Timedelta(days=horizon_days)
    origins = pd.date_range(first_origin, last_origin, freq=f"{horizon_days}D")

    old_preds = []
    new_preds = []

    for origin in origins:
        test_end = origin + pd.Timedelta(days=horizon_days)

        train_old = daily_all[daily_all["date"] < origin]
        test_window = daily_with_stockout[
            (daily_with_stockout["date"] >= origin) & (daily_with_stockout["date"] < test_end)
        ]
        if train_old.empty or test_window.empty:
            continue

        features_old = feat.build_stats(train_old)
        est_old = fc.estimate_mu_sigma(features_old, method="dow_weighted", min_in_stock_days=0)

        train_new = daily_with_stockout[daily_with_stockout["date"] < origin]
        features_new = feat.build_stats(train_new)
        est_new = fc.estimate_mu_sigma(
            features_new, method="dow_weighted", min_in_stock_days=min_in_stock_days,
        )
        items_with_history = set(features_new["item_id"].unique())
        est_new = fc.apply_category_fallback(est_new, category_map, items_with_history)

        for item_id in est_old["item_id"].unique():
            item_test = test_window[test_window["item_id"] == item_id]
            if item_test.empty:
                continue

            if "is_stockout" in item_test.columns:
                in_stock = item_test[~item_test["is_stockout"]]
                if len(in_stock) < min_test_in_stock_days:
                    continue
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


# ---------------------------------------------------------------------------
# Figure generators
# ---------------------------------------------------------------------------

def fig_summary_comparison(old_m, new_m):
    """Bar chart comparing old vs new on key metrics."""
    fig, axes = plt.subplots(1, 4, figsize=(16, 5))
    fig.suptitle("Old vs New Pipeline: Accuracy Comparison", fontsize=14, fontweight="bold")

    metrics = [
        ("MAE", "mae", "lower is better"),
        ("Bias", "bias", "closer to 0 is better"),
        ("Within 1 unit (%)", "within_1", "higher is better"),
        ("Within 2 units (%)", "within_2", "higher is better"),
    ]

    colors_old = "#4A90D9"
    colors_new = "#E8833A"

    for ax, (label, key, note) in zip(axes, metrics):
        vals = [old_m[key], new_m[key]]
        bars = ax.bar(["Old", "New"], vals, color=[colors_old, colors_new], width=0.5)
        ax.set_title(f"{label}\n({note})", fontsize=10)
        ax.set_ylabel(label)

        for bar, val in zip(bars, vals):
            fmt = f"{val:+.4f}" if key == "bias" else f"{val:.4f}" if "mae" in key or "rmse" in key else f"{val:.1f}%"
            ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height(),
                    fmt, ha="center", va="bottom", fontsize=9, fontweight="bold")

    plt.tight_layout()
    fig.savefig(OUT_DIR / "1_summary_comparison.png", dpi=150, bbox_inches="tight")
    plt.close(fig)


def fig_error_distribution(old_preds, new_preds):
    """Histogram of prediction errors for old vs new."""
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5), sharey=True)
    fig.suptitle("Prediction Error Distribution (predicted - actual)", fontsize=14, fontweight="bold")

    old_has = old_preds[old_preds["actual_mu"] > 0]
    new_has = new_preds[new_preds["actual_mu"] > 0]

    old_errors = old_has["predicted_mu"] - old_has["actual_mu"]
    new_errors = new_has["predicted_mu"] - new_has["actual_mu"]

    bins = np.arange(-10, 10.5, 0.5)

    ax1.hist(old_errors, bins=bins, color="#4A90D9", alpha=0.8, edgecolor="white")
    ax1.axvline(0, color="red", linestyle="--", linewidth=1.5, label="Perfect (0)")
    ax1.axvline(old_errors.mean(), color="darkblue", linestyle="-", linewidth=1.5,
                label=f"Mean bias: {old_errors.mean():.2f}")
    ax1.set_title("Old Pipeline")
    ax1.set_xlabel("Error (predicted - actual)")
    ax1.set_ylabel("Count")
    ax1.legend(fontsize=8)

    ax2.hist(new_errors, bins=bins, color="#E8833A", alpha=0.8, edgecolor="white")
    ax2.axvline(0, color="red", linestyle="--", linewidth=1.5, label="Perfect (0)")
    ax2.axvline(new_errors.mean(), color="darkred", linestyle="-", linewidth=1.5,
                label=f"Mean bias: {new_errors.mean():.2f}")
    ax2.set_title("New Pipeline (stockout-aware)")
    ax2.set_xlabel("Error (predicted - actual)")
    ax2.legend(fontsize=8)

    plt.tight_layout()
    fig.savefig(OUT_DIR / "2_error_distribution.png", dpi=150, bbox_inches="tight")
    plt.close(fig)


def fig_per_item_comparison(old_preds, new_preds, name_map):
    """Scatter plot: old MAE vs new MAE per item, with diagonal reference."""
    old_has = old_preds[old_preds["actual_mu"] > 0].copy()
    old_has["abs_err"] = (old_has["predicted_mu"] - old_has["actual_mu"]).abs()
    old_item = old_has.groupby("item_id")["abs_err"].mean().rename("mae_old")

    new_has = new_preds[new_preds["actual_mu"] > 0].copy()
    new_has["abs_err"] = (new_has["predicted_mu"] - new_has["actual_mu"]).abs()
    new_item = new_has.groupby("item_id")["abs_err"].mean().rename("mae_new")

    compare = pd.concat([old_item, new_item], axis=1).dropna()
    compare["name"] = compare.index.map(name_map)

    fig, ax = plt.subplots(figsize=(10, 10))
    ax.set_title("Per-Item MAE: Old vs New Pipeline", fontsize=14, fontweight="bold")

    improved = compare[compare["mae_new"] < compare["mae_old"]]
    worsened = compare[compare["mae_new"] > compare["mae_old"]]
    neutral = compare[compare["mae_new"] == compare["mae_old"]]

    ax.scatter(improved["mae_old"], improved["mae_new"], color="#2ECC71", alpha=0.7,
               s=40, label=f"Improved ({len(improved)})", zorder=3)
    ax.scatter(worsened["mae_old"], worsened["mae_new"], color="#E74C3C", alpha=0.7,
               s=40, label=f"Worsened ({len(worsened)})", zorder=3)
    ax.scatter(neutral["mae_old"], neutral["mae_new"], color="#95A5A6", alpha=0.5,
               s=30, label=f"Unchanged ({len(neutral)})", zorder=2)

    # Diagonal line (y=x)
    lim = max(compare["mae_old"].max(), compare["mae_new"].max()) * 1.05
    ax.plot([0, lim], [0, lim], "k--", alpha=0.3, label="Equal accuracy")

    # Label top outliers
    top_worsened = worsened.nlargest(3, "mae_new")
    top_improved = improved.nsmallest(3, "mae_new")
    for _, row in pd.concat([top_worsened, top_improved]).iterrows():
        label = str(row["name"])[:20] if pd.notna(row["name"]) else row.name[:8]
        ax.annotate(label, (row["mae_old"], row["mae_new"]),
                    fontsize=7, alpha=0.8,
                    xytext=(5, 5), textcoords="offset points")

    ax.set_xlabel("Old Pipeline MAE (per item)")
    ax.set_ylabel("New Pipeline MAE (per item)")
    ax.legend(loc="upper left")
    ax.set_xlim(0, lim)
    ax.set_ylim(0, lim)
    ax.set_aspect("equal")
    ax.grid(True, alpha=0.2)

    plt.tight_layout()
    fig.savefig(OUT_DIR / "3_per_item_scatter.png", dpi=150, bbox_inches="tight")
    plt.close(fig)
    return compare


def fig_top_movers(compare, n=15):
    """Horizontal bar chart of items with biggest accuracy changes."""
    compare = compare.copy()
    compare["delta"] = compare["mae_new"] - compare["mae_old"]
    compare["display_name"] = compare["name"].fillna(pd.Series(compare.index.str[:8], index=compare.index))
    compare["display_name"] = compare["display_name"].str[:25]

    # Top improved and worsened
    top_improved = compare.nsmallest(n // 2, "delta")
    top_worsened = compare.nlargest(n // 2 + 1, "delta")
    top = pd.concat([top_worsened, top_improved]).sort_values("delta")

    fig, ax = plt.subplots(figsize=(12, max(6, len(top) * 0.4)))
    ax.set_title("Biggest Accuracy Changes (New - Old MAE)", fontsize=14, fontweight="bold")

    colors = ["#2ECC71" if d < 0 else "#E74C3C" for d in top["delta"]]
    ax.barh(top["display_name"], top["delta"], color=colors, edgecolor="white")
    ax.axvline(0, color="black", linewidth=0.5)
    ax.set_xlabel("MAE Delta (negative = improved)")

    for i, (_, row) in enumerate(top.iterrows()):
        ax.text(row["delta"], i, f" {row['delta']:+.2f}", va="center",
                fontsize=8, fontweight="bold",
                ha="left" if row["delta"] >= 0 else "right")

    plt.tight_layout()
    fig.savefig(OUT_DIR / "4_top_movers.png", dpi=150, bbox_inches="tight")
    plt.close(fig)


def fig_category_breakdown(old_preds, new_preds, category_map):
    """MAE comparison by product category."""
    old_has = old_preds[old_preds["actual_mu"] > 0].copy()
    old_has["category"] = old_has["item_id"].map(category_map).fillna("Unknown")
    old_has["abs_err"] = (old_has["predicted_mu"] - old_has["actual_mu"]).abs()

    new_has = new_preds[new_preds["actual_mu"] > 0].copy()
    new_has["category"] = new_has["item_id"].map(category_map).fillna("Unknown")
    new_has["abs_err"] = (new_has["predicted_mu"] - new_has["actual_mu"]).abs()

    old_cat = old_has.groupby("category")["abs_err"].mean().rename("mae_old")
    new_cat = new_has.groupby("category")["abs_err"].mean().rename("mae_new")
    counts = new_has.groupby("category").size().rename("n_predictions")

    cat_compare = pd.concat([old_cat, new_cat, counts], axis=1).dropna()
    cat_compare = cat_compare[cat_compare["n_predictions"] >= 5]
    cat_compare = cat_compare.sort_values("mae_old", ascending=True)

    if cat_compare.empty:
        return

    fig, ax = plt.subplots(figsize=(12, max(5, len(cat_compare) * 0.5)))
    ax.set_title("MAE by Product Category", fontsize=14, fontweight="bold")

    y = np.arange(len(cat_compare))
    height = 0.35

    bars_old = ax.barh(y - height / 2, cat_compare["mae_old"], height,
                       color="#4A90D9", alpha=0.8, label="Old Pipeline")
    bars_new = ax.barh(y + height / 2, cat_compare["mae_new"], height,
                       color="#E8833A", alpha=0.8, label="New Pipeline")

    labels = [f"{cat} (n={int(n)})" for cat, n in
              zip(cat_compare.index, cat_compare["n_predictions"])]
    ax.set_yticks(y)
    ax.set_yticklabels(labels, fontsize=9)
    ax.set_xlabel("MAE")
    ax.legend()
    ax.grid(True, axis="x", alpha=0.2)

    plt.tight_layout()
    fig.savefig(OUT_DIR / "5_category_breakdown.png", dpi=150, bbox_inches="tight")
    plt.close(fig)


def fig_within_accuracy(old_preds, new_preds):
    """Cumulative accuracy chart: % of predictions within X units."""
    old_has = old_preds[old_preds["actual_mu"] > 0]
    new_has = new_preds[new_preds["actual_mu"] > 0]

    old_abs = (old_has["predicted_mu"] - old_has["actual_mu"]).abs()
    new_abs = (new_has["predicted_mu"] - new_has["actual_mu"]).abs()

    thresholds = np.arange(0, 10.1, 0.25)
    old_pcts = [float((old_abs <= t).mean()) * 100 for t in thresholds]
    new_pcts = [float((new_abs <= t).mean()) * 100 for t in thresholds]

    fig, ax = plt.subplots(figsize=(10, 6))
    ax.set_title("Cumulative Accuracy: % Predictions Within X Units",
                 fontsize=14, fontweight="bold")

    ax.plot(thresholds, old_pcts, color="#4A90D9", linewidth=2, label="Old Pipeline")
    ax.plot(thresholds, new_pcts, color="#E8833A", linewidth=2, label="New Pipeline")

    # Reference lines
    for pct in [50, 75, 90]:
        ax.axhline(pct, color="gray", linestyle=":", alpha=0.3)
        ax.text(9.8, pct + 0.5, f"{pct}%", fontsize=8, color="gray", ha="right")

    ax.axvline(1.0, color="green", linestyle="--", alpha=0.4, label="1 unit threshold")
    ax.axvline(2.0, color="orange", linestyle="--", alpha=0.4, label="2 unit threshold")

    ax.set_xlabel("Error Threshold (units/day)")
    ax.set_ylabel("% of Predictions Within Threshold")
    ax.legend(loc="lower right")
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 102)
    ax.grid(True, alpha=0.2)

    plt.tight_layout()
    fig.savefig(OUT_DIR / "6_cumulative_accuracy.png", dpi=150, bbox_inches="tight")
    plt.close(fig)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    print("=" * 70)
    print("BACKTEST ACCURACY REPORT")
    print("=" * 70)

    movements, products = load_snapshot()
    print(f"Data: {len(movements)} movements, {len(products)} products")

    daily_all, daily_with_stockout, category_map, name_map = prepare_data(movements, products)

    # Stockout stats
    if "is_stockout" in daily_with_stockout.columns:
        total_days = len(daily_with_stockout)
        stockout_days = int(daily_with_stockout["is_stockout"].sum())
        print(f"Stockout rate: {stockout_days}/{total_days} item-days ({stockout_days/total_days*100:.1f}%)")

    n_items = daily_all["item_id"].nunique()
    date_range = f"{daily_all['date'].min().date()} to {daily_all['date'].max().date()}"
    print(f"Items with sales: {n_items}, Date range: {date_range}")

    print(f"\nConfig: MIN_IN_STOCK_DAYS={config.MIN_IN_STOCK_DAYS}, "
          f"MIN_TEST_IN_STOCK_DAYS={config.MIN_TEST_IN_STOCK_DAYS}")

    print("\nRunning walk-forward backtest...", flush=True)
    old_preds, new_preds = walk_forward(
        daily_all, daily_with_stockout, category_map,
        min_in_stock_days=config.MIN_IN_STOCK_DAYS,
        min_test_in_stock_days=config.MIN_TEST_IN_STOCK_DAYS,
    )

    old_m = compute_metrics(old_preds)
    new_m = compute_metrics(new_preds)

    # --- Console summary ---
    print("\n" + "=" * 70)
    print("RESULTS SUMMARY")
    print("=" * 70)
    print(f"\n{'Metric':<25} {'Old Pipeline':>15} {'New Pipeline':>15} {'Change':>15}")
    print("-" * 70)
    print(f"{'MAE':<25} {old_m['mae']:>15.4f} {new_m['mae']:>15.4f} {new_m['mae']-old_m['mae']:>+15.4f}")
    print(f"{'RMSE':<25} {old_m['rmse']:>15.4f} {new_m['rmse']:>15.4f} {new_m['rmse']-old_m['rmse']:>+15.4f}")
    print(f"{'Bias':<25} {old_m['bias']:>+15.4f} {new_m['bias']:>+15.4f} {abs(new_m['bias'])-abs(old_m['bias']):>+15.4f}")
    print(f"{'Within 1 unit (%)':<25} {old_m['within_1']:>14.1f}% {new_m['within_1']:>14.1f}% {new_m['within_1']-old_m['within_1']:>+14.1f}%")
    print(f"{'Within 2 units (%)':<25} {old_m['within_2']:>14.1f}% {new_m['within_2']:>14.1f}% {new_m['within_2']-old_m['within_2']:>+14.1f}%")
    print(f"{'Predictions evaluated':<25} {old_m['n']:>15} {new_m['n']:>15}")

    # Per-item breakdown
    old_has = old_preds[old_preds["actual_mu"] > 0].copy()
    old_has["abs_err"] = (old_has["predicted_mu"] - old_has["actual_mu"]).abs()
    old_item = old_has.groupby("item_id")["abs_err"].mean().rename("mae_old")

    new_has = new_preds[new_preds["actual_mu"] > 0].copy()
    new_has["abs_err"] = (new_has["predicted_mu"] - new_has["actual_mu"]).abs()
    new_item = new_has.groupby("item_id")["abs_err"].mean().rename("mae_new")

    compare = pd.concat([old_item, new_item], axis=1).dropna()
    compare["delta"] = compare["mae_new"] - compare["mae_old"]
    improved = (compare["delta"] < -0.01).sum()
    worsened = (compare["delta"] > 0.01).sum()
    neutral = len(compare) - improved - worsened

    print(f"\n{'ITEM BREAKDOWN'}")
    print(f"  Total items evaluated: {len(compare)}")
    print(f"  Improved (MAE decreased): {improved} ({improved/len(compare)*100:.0f}%)")
    print(f"  Worsened (MAE increased): {worsened} ({worsened/len(compare)*100:.0f}%)")
    print(f"  Neutral: {neutral} ({neutral/len(compare)*100:.0f}%)")

    # --- Generate figures ---
    print(f"\nGenerating figures in {OUT_DIR}/...")

    fig_summary_comparison(old_m, new_m)
    print("  [1/6] Summary comparison chart")

    fig_error_distribution(old_preds, new_preds)
    print("  [2/6] Error distribution histogram")

    item_compare = fig_per_item_comparison(old_preds, new_preds, name_map)
    print("  [3/6] Per-item scatter plot")

    fig_top_movers(item_compare)
    print("  [4/6] Top movers bar chart")

    fig_category_breakdown(old_preds, new_preds, category_map)
    print("  [5/6] Category breakdown")

    fig_within_accuracy(old_preds, new_preds)
    print("  [6/6] Cumulative accuracy curve")

    # --- Save CSV ---
    summary_df = pd.DataFrame([
        {"pipeline": "old", **old_m},
        {"pipeline": "new", **new_m},
    ])
    summary_df.to_csv(OUT_DIR / "summary_metrics.csv", index=False)

    item_compare["name"] = item_compare.index.map(name_map)
    item_compare.to_csv(OUT_DIR / "per_item_comparison.csv")

    print(f"\nAll outputs saved to: {OUT_DIR}/")
    print("  - summary_metrics.csv")
    print("  - per_item_comparison.csv")
    print("  - 1_summary_comparison.png")
    print("  - 2_error_distribution.png")
    print("  - 3_per_item_scatter.png")
    print("  - 4_top_movers.png")
    print("  - 5_category_breakdown.png")
    print("  - 6_cumulative_accuracy.png")


if __name__ == "__main__":
    main()
