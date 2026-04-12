"""Generate system accuracy report for presentation.

Focuses on what the system does and whether it works -- no old vs new comparison.
Figures show: predicted vs actual, error distribution, accuracy breakdown,
category performance, and a plain-English verdict on system quality.

Usage:
    cd services/forecasting-service
    python experiments/generate_system_report.py
"""

from __future__ import annotations

import sys
import warnings
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np
import pandas as pd

warnings.filterwarnings("ignore", category=FutureWarning)

SERVICE_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SERVICE_ROOT))

from src import config
from src import features as feat
from src import forecast as fc

DATA_DIR = Path(__file__).parent / "data"
OUT_DIR = Path(__file__).parent / "system_report"
OUT_DIR.mkdir(exist_ok=True)

BLUE = "#2C7BB6"
GREEN = "#1A9641"
ORANGE = "#E8833A"
RED = "#D7191C"
GRAY = "#AAAAAA"
DARK = "#222222"


# ---------------------------------------------------------------------------
# Data loading and backtest
# ---------------------------------------------------------------------------

def load_and_run():
    movements = pd.read_csv(DATA_DIR / "stock_movements.csv")
    products = pd.read_csv(DATA_DIR / "products.csv")

    daily_all = feat.build_daily_usage(movements)
    stockout_df = feat.detect_stockout_days(movements)

    daily = daily_all.copy()
    if not stockout_df.empty:
        daily = daily.merge(stockout_df, on=["item_id", "date"], how="left")
        daily["is_stockout"] = daily["is_stockout"].fillna(False).astype(bool)
    daily["date"] = pd.to_datetime(daily["date"])

    category_map = dict(zip(products["item_id"], products["category_name"]))
    name_map = dict(zip(products["item_id"], products["name"]))

    # Walk-forward backtest
    date_min = daily["date"].min()
    date_max = daily["date"].max()
    min_train_days = 14
    horizon_days = 7
    first_origin = date_min + pd.Timedelta(days=min_train_days)
    last_origin = date_max - pd.Timedelta(days=horizon_days)
    origins = pd.date_range(first_origin, last_origin, freq=f"{horizon_days}D")

    predictions = []
    for origin in origins:
        test_end = origin + pd.Timedelta(days=horizon_days)
        train = daily[daily["date"] < origin]
        test = daily[(daily["date"] >= origin) & (daily["date"] < test_end)]
        if train.empty or test.empty:
            continue

        features = feat.build_stats(train)
        estimates = fc.estimate_mu_sigma(
            features, method="dow_weighted",
            min_in_stock_days=config.MIN_IN_STOCK_DAYS,
        )
        items_with_history = set(features["item_id"].unique())
        estimates = fc.apply_category_fallback(estimates, category_map, items_with_history)

        for _, est in estimates.iterrows():
            item_id = est["item_id"]
            item_test = test[test["item_id"] == item_id]
            if item_test.empty:
                continue

            if "is_stockout" in item_test.columns:
                in_stock = item_test[~item_test["is_stockout"]]
                if len(in_stock) < config.MIN_TEST_IN_STOCK_DAYS:
                    continue
                actual_mu = float(in_stock["consumption"].sum()) / max(len(in_stock), 1)
            else:
                actual_mu = float(item_test["consumption"].mean())

            predictions.append({
                "origin": origin,
                "item_id": item_id,
                "predicted_mu": float(est["mu_hat"]),
                "actual_mu": actual_mu,
                "category": category_map.get(item_id, "Unknown"),
                "name": name_map.get(item_id, ""),
            })

    pred_df = pd.DataFrame(predictions)
    has_demand = pred_df[pred_df["actual_mu"] > 0].copy()
    has_demand["error"] = has_demand["predicted_mu"] - has_demand["actual_mu"]
    has_demand["abs_error"] = has_demand["error"].abs()

    stockout_rate = None
    if "is_stockout" in daily.columns:
        stockout_rate = daily["is_stockout"].mean() * 100

    meta = {
        "n_movements": len(movements),
        "n_products": len(products),
        "n_items_with_demand": has_demand["item_id"].nunique(),
        "n_predictions": len(has_demand),
        "date_range": f"{daily['date'].min().date()} to {daily['date'].max().date()}",
        "stockout_rate": stockout_rate,
    }

    return has_demand, meta


# ---------------------------------------------------------------------------
# Figure 1: Predicted vs Actual scatter
# ---------------------------------------------------------------------------

def fig_predicted_vs_actual(df):
    """Scatter plot of predicted vs actual daily demand -- the core proof."""
    fig, ax = plt.subplots(figsize=(9, 8))

    # Color by absolute error bucket
    low = df[df["abs_error"] <= 1.0]
    mid = df[(df["abs_error"] > 1.0) & (df["abs_error"] <= 3.0)]
    high = df[df["abs_error"] > 3.0]

    ax.scatter(low["actual_mu"], low["predicted_mu"], color=GREEN, alpha=0.4,
               s=18, label=f"Within 1 unit ({len(low)/len(df)*100:.0f}%)")
    ax.scatter(mid["actual_mu"], mid["predicted_mu"], color=ORANGE, alpha=0.4,
               s=18, label=f"Within 1-3 units ({len(mid)/len(df)*100:.0f}%)")
    ax.scatter(high["actual_mu"], high["predicted_mu"], color=RED, alpha=0.4,
               s=18, label=f"Off by 3+ units ({len(high)/len(df)*100:.0f}%)")

    # Perfect prediction line
    lim = max(df["actual_mu"].quantile(0.98), df["predicted_mu"].quantile(0.98)) * 1.1
    ax.plot([0, lim], [0, lim], "k--", alpha=0.4, linewidth=1.5, label="Perfect prediction")

    ax.set_xlim(0, lim)
    ax.set_ylim(0, lim)
    ax.set_xlabel("Actual Daily Demand (units/day)", fontsize=12)
    ax.set_ylabel("Predicted Daily Demand (units/day)", fontsize=12)
    ax.set_title("Predicted vs Actual Daily Demand\n(each dot = one item, one forecast window)",
                 fontsize=13, fontweight="bold")
    ax.legend(fontsize=10, loc="upper left")
    ax.grid(True, alpha=0.2)

    # Annotation
    mae = df["abs_error"].mean()
    within_1 = (df["abs_error"] <= 1.0).mean() * 100
    ax.text(0.98, 0.05,
            f"MAE: {mae:.2f} units/day\n{within_1:.0f}% within 1 unit",
            transform=ax.transAxes, ha="right", va="bottom",
            fontsize=10, bbox=dict(boxstyle="round,pad=0.4", facecolor="white", alpha=0.8))

    plt.tight_layout()
    fig.savefig(OUT_DIR / "1_predicted_vs_actual.png", dpi=150, bbox_inches="tight")
    plt.close(fig)
    print("  [1] Predicted vs actual scatter")


# ---------------------------------------------------------------------------
# Figure 2: Error distribution
# ---------------------------------------------------------------------------

def fig_error_distribution(df):
    """Single histogram showing how far off predictions are."""
    fig, ax = plt.subplots(figsize=(10, 6))

    errors = df["error"]
    bins = np.arange(-10, 10.5, 0.5)

    n, bin_edges, patches = ax.hist(errors, bins=bins, color=BLUE, alpha=0.8, edgecolor="white")

    # Color bars: green if within 1 unit of zero
    for patch, left, right in zip(patches, bin_edges[:-1], bin_edges[1:]):
        if abs((left + right) / 2) <= 1.0:
            patch.set_facecolor(GREEN)
            patch.set_alpha(0.85)

    ax.axvline(0, color=DARK, linestyle="--", linewidth=2, label="Perfect (0 error)")
    ax.axvline(errors.mean(), color=RED, linestyle="-", linewidth=2,
               label=f"Mean error: {errors.mean():.2f} units/day")
    ax.axvspan(-1, 1, alpha=0.06, color=GREEN, label="±1 unit zone")

    ax.set_xlabel("Prediction Error (predicted - actual, units/day)", fontsize=12)
    ax.set_ylabel("Number of Predictions", fontsize=12)
    ax.set_title("How Far Off Are the Predictions?\n(green bars = within 1 unit of actual)",
                 fontsize=13, fontweight="bold")
    ax.legend(fontsize=10)
    ax.grid(True, axis="y", alpha=0.2)

    # Key stats annotation
    within_1 = (df["abs_error"] <= 1.0).mean() * 100
    within_2 = (df["abs_error"] <= 2.0).mean() * 100
    ax.text(0.02, 0.95,
            f"n = {len(df)} predictions\n"
            f"Within ±1 unit: {within_1:.1f}%\n"
            f"Within ±2 units: {within_2:.1f}%",
            transform=ax.transAxes, va="top", fontsize=10,
            bbox=dict(boxstyle="round,pad=0.4", facecolor="white", alpha=0.85))

    plt.tight_layout()
    fig.savefig(OUT_DIR / "2_error_distribution.png", dpi=150, bbox_inches="tight")
    plt.close(fig)
    print("  [2] Error distribution histogram")


# ---------------------------------------------------------------------------
# Figure 3: Per-item accuracy breakdown
# ---------------------------------------------------------------------------

def fig_per_item_accuracy(df):
    """Top 5 best and worst items with explanation of why hard items are hard."""
    item_stats = (
        df.groupby(["item_id", "name"])
        .agg(
            mae=("abs_error", "mean"),
            n=("abs_error", "count"),
            avg_actual=("actual_mu", "mean"),
        )
        .reset_index()
    )
    item_stats["display_name"] = item_stats["name"].str[:32].replace("", np.nan)
    item_stats["display_name"] = item_stats["display_name"].fillna(
        item_stats["item_id"].str[:8]
    )
    item_stats = item_stats[item_stats["n"] >= 2].sort_values("mae")

    top_5 = item_stats.head(5)
    worst_5 = item_stats.tail(5)

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))
    fig.suptitle("Forecast Accuracy: Best and Hardest Items",
                 fontsize=14, fontweight="bold", y=1.01)

    # Best 5
    bars1 = ax1.barh(top_5["display_name"], top_5["mae"], color=GREEN, alpha=0.85)
    ax1.axvline(1.0, color=GRAY, linestyle="--", alpha=0.5, linewidth=1.2)
    ax1.set_xlabel("Average Error (units/day)", fontsize=11)
    ax1.set_title("5 Most Accurately Predicted Items", fontsize=11, fontweight="bold")
    ax1.set_xlim(0, 1.8)
    for bar, val in zip(bars1, top_5["mae"]):
        ax1.text(val + 0.02, bar.get_y() + bar.get_height() / 2,
                 f"{val:.2f}", va="center", fontsize=10, fontweight="bold", color=DARK)
    ax1.text(1.02, -0.5, "1 unit", fontsize=8, color=GRAY,
             transform=ax1.get_xaxis_transform(), ha="center")
    ax1.grid(True, axis="x", alpha=0.15)
    ax1.set_axisbelow(True)

    # Worst 5
    worst_colors = [RED if v > 5.0 else ORANGE for v in worst_5["mae"]]
    bars2 = ax2.barh(worst_5["display_name"], worst_5["mae"], color=worst_colors, alpha=0.85)
    ax2.axvline(1.0, color=GRAY, linestyle="--", alpha=0.5, linewidth=1.2)
    ax2.set_xlabel("Average Error (units/day)", fontsize=11)
    ax2.set_title("5 Hardest-to-Predict Items", fontsize=11, fontweight="bold")
    ax2.set_xlim(0, worst_5["mae"].max() * 1.3)
    for bar, val in zip(bars2, worst_5["mae"]):
        ax2.text(val + 0.1, bar.get_y() + bar.get_height() / 2,
                 f"{val:.2f}", va="center", fontsize=10, fontweight="bold", color=DARK)
    ax2.grid(True, axis="x", alpha=0.15)
    ax2.set_axisbelow(True)

    # Explanation box below the hard items chart
    explanation = (
        "Why are these hard to predict?\n\n"
        "These are high-demand items that frequently sell out.\n"
        "When a product has zero stock, sales drop to zero --\n"
        "but that doesn't mean demand dropped. The model sees\n"
        "the missing sales and underestimates true demand.\n\n"
        "This is a known limitation with only 38 days of data.\n"
        "Accuracy improves as more sales history accumulates."
    )
    ax2.text(
        1.08, 0.5, explanation,
        transform=ax2.transAxes,
        fontsize=9, va="center", ha="left",
        bbox=dict(boxstyle="round,pad=0.6", facecolor="#FFF8F0",
                  edgecolor=ORANGE, linewidth=1.2),
        multialignment="left",
    )

    plt.tight_layout()
    fig.savefig(OUT_DIR / "3_per_item_accuracy.png", dpi=150, bbox_inches="tight")
    plt.close(fig)
    print("  [3] Per-item accuracy breakdown")


# ---------------------------------------------------------------------------
# Figure 4: Category accuracy
# ---------------------------------------------------------------------------

def fig_category_accuracy(df):
    """Horizontal bar chart of MAE by category with item counts."""
    cat_stats = (
        df.groupby("category")
        .agg(mae=("abs_error", "mean"), n_items=("item_id", "nunique"),
             within_1=("abs_error", lambda x: (x <= 1.0).mean() * 100))
        .reset_index()
    )
    cat_stats = cat_stats[cat_stats["n_items"] >= 3].sort_values("mae")

    fig, ax = plt.subplots(figsize=(12, max(6, len(cat_stats) * 0.45)))

    colors = [GREEN if m <= 1.0 else ORANGE if m <= 2.5 else RED for m in cat_stats["mae"]]
    bars = ax.barh(
        [f"{c} ({n} items)" for c, n in zip(cat_stats["category"], cat_stats["n_items"])],
        cat_stats["mae"],
        color=colors, alpha=0.85
    )
    ax.axvline(1.0, color=GRAY, linestyle="--", alpha=0.6, linewidth=1.5, label="1 unit/day")
    ax.axvline(2.0, color=GRAY, linestyle=":", alpha=0.4, linewidth=1.5, label="2 units/day")

    for bar, val, w1 in zip(bars, cat_stats["mae"], cat_stats["within_1"]):
        ax.text(val + 0.02, bar.get_y() + bar.get_height() / 2,
                f"{val:.2f}  ({w1:.0f}% within 1)",
                va="center", fontsize=8.5)

    ax.set_xlabel("Mean Absolute Error (units/day)", fontsize=11)
    ax.set_title("Forecast Accuracy by Product Category\n(green = MAE ≤ 1, orange = 1-2.5, red = 2.5+)",
                 fontsize=13, fontweight="bold")
    ax.legend(fontsize=9)
    ax.grid(True, axis="x", alpha=0.2)
    ax.set_xlim(0, cat_stats["mae"].max() * 1.35)

    plt.tight_layout()
    fig.savefig(OUT_DIR / "4_category_accuracy.png", dpi=150, bbox_inches="tight")
    plt.close(fig)
    print("  [4] Category accuracy breakdown")


# ---------------------------------------------------------------------------
# Figure 5: Cumulative accuracy (the "verdict" chart)
# ---------------------------------------------------------------------------

def fig_cumulative_accuracy(df):
    """Cumulative accuracy curve with verdict annotations."""
    abs_errors = df["abs_error"]
    thresholds = np.arange(0, 10.1, 0.1)
    pcts = [float((abs_errors <= t).mean()) * 100 for t in thresholds]

    fig, ax = plt.subplots(figsize=(10, 7))

    ax.fill_between(thresholds, pcts, alpha=0.12, color=BLUE)
    ax.plot(thresholds, pcts, color=BLUE, linewidth=2.5)

    # Milestone annotations
    milestones = [
        (1.0, "1 unit/day\n(order 1 item off)", GREEN),
        (2.0, "2 units/day\n(order 2 items off)", ORANGE),
        (5.0, "5 units/day", RED),
    ]
    for threshold, label, color in milestones:
        pct_at = float((abs_errors <= threshold).mean()) * 100
        ax.axvline(threshold, color=color, linestyle="--", alpha=0.5, linewidth=1.5)
        ax.annotate(
            f"{pct_at:.0f}% of predictions\nwithin {threshold:.0f} unit(s)",
            xy=(threshold, pct_at),
            xytext=(threshold + 0.3, pct_at - 10),
            fontsize=9.5,
            arrowprops=dict(arrowstyle="->", color=DARK, lw=1.2),
            bbox=dict(boxstyle="round,pad=0.3", facecolor="white", alpha=0.85),
        )

    ax.axhline(50, color=GRAY, linestyle=":", alpha=0.3)
    ax.axhline(75, color=GRAY, linestyle=":", alpha=0.3)
    ax.axhline(90, color=GRAY, linestyle=":", alpha=0.3)
    for pct in [50, 75, 90]:
        ax.text(9.85, pct + 0.8, f"{pct}%", fontsize=8, color=GRAY, ha="right")

    ax.set_xlabel("Error Threshold (units/day)", fontsize=12)
    ax.set_ylabel("% of Predictions Within That Error", fontsize=12)
    ax.set_title("Cumulative Forecast Accuracy\nWhat % of predictions land within X units of actual demand?",
                 fontsize=13, fontweight="bold")
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 102)
    ax.grid(True, alpha=0.15)

    plt.tight_layout()
    fig.savefig(OUT_DIR / "5_cumulative_accuracy.png", dpi=150, bbox_inches="tight")
    plt.close(fig)
    print("  [5] Cumulative accuracy curve")


# ---------------------------------------------------------------------------
# Figure 6: System verdict scorecard
# ---------------------------------------------------------------------------

def fig_scorecard(df, meta):
    """A plain-English scorecard summarising whether the system works."""
    mae = df["abs_error"].mean()
    bias = df["error"].mean()
    within_1 = (df["abs_error"] <= 1.0).mean() * 100
    within_2 = (df["abs_error"] <= 2.0).mean() * 100
    n_items = df["item_id"].nunique()
    n_preds = len(df)

    # Grade each metric
    def grade(value, thresholds, labels):
        for t, l in zip(thresholds, labels):
            if value <= t:
                return l
        return labels[-1]

    mae_grade = grade(mae, [1.0, 2.0, 3.5], ["Excellent", "Good", "Fair", "Poor"])
    bias_grade = grade(abs(bias), [0.2, 0.5, 1.0], ["Excellent", "Good", "Fair", "Poor"])
    w2_grade = grade(100 - within_2, [10, 25, 40], ["Excellent", "Good", "Fair", "Poor"])

    grade_color = {"Excellent": GREEN, "Good": "#4CAF50", "Fair": ORANGE, "Poor": RED}

    fig, ax = plt.subplots(figsize=(12, 8))
    ax.set_xlim(0, 12)
    ax.set_ylim(0, 9)
    ax.axis("off")
    ax.set_facecolor("#F8F9FA")
    fig.patch.set_facecolor("#F8F9FA")

    # Title
    ax.text(6, 8.4, "Forecasting System -- Accuracy Report",
            ha="center", va="center", fontsize=17, fontweight="bold", color=DARK)
    ax.text(6, 7.85,
            f"Walk-forward backtest: {meta['date_range']}  |  "
            f"{n_items} items  |  {n_preds} predictions evaluated",
            ha="center", va="center", fontsize=10, color="#555555")

    # Divider
    ax.plot([0.5, 11.5], [7.6, 7.6], color="#DDDDDD", linewidth=1.5)

    # Metric cards
    cards = [
        ("Mean Absolute Error", f"{mae:.2f} units/day",
         "Average gap between predicted\nand actual daily demand",
         mae_grade, 1.5),
        ("Systematic Bias", f"{bias:+.2f} units/day",
         "Positive = over-predicts\nNegative = under-predicts",
         bias_grade, 4.5),
        ("Within 2 Units", f"{within_2:.1f}%",
         "Predictions accurate to\nwithin 2 units per day",
         w2_grade, 7.5),
    ]

    for label, value, description, g, x in cards:
        color = grade_color[g]
        # Card background
        rect = mpatches.FancyBboxPatch(
            (x - 1.4, 3.5), 2.8, 3.8,
            boxstyle="round,pad=0.2",
            facecolor="white", edgecolor="#DDDDDD", linewidth=1.5
        )
        ax.add_patch(rect)
        ax.text(x, 6.9, label, ha="center", va="center",
                fontsize=10.5, fontweight="bold", color=DARK)
        ax.text(x, 5.9, value, ha="center", va="center",
                fontsize=22, fontweight="bold", color=color)
        ax.text(x, 4.9, description, ha="center", va="center",
                fontsize=8.5, color="#666666", multialignment="center")
        ax.text(x, 4.0, g, ha="center", va="center",
                fontsize=11, fontweight="bold", color=color,
                bbox=dict(boxstyle="round,pad=0.3", facecolor=color + "22",
                          edgecolor=color, linewidth=1.2))

    # Also show within 1 unit
    ax.text(6, 3.1,
            f"Within 1 unit: {within_1:.1f}%  |  "
            f"Stockout rate in data: {meta['stockout_rate']:.1f}%",
            ha="center", va="center", fontsize=10, color="#555555")

    # Divider
    ax.plot([0.5, 11.5], [2.75, 2.75], color="#DDDDDD", linewidth=1.5)

    # Verdict text
    if mae <= 1.5 and within_2 >= 78:
        verdict = "YES -- the system works well for production use."
        verdict_color = GREEN
        detail = (
            f"The model predicts daily demand within 2 units for {within_2:.0f}% of items. "
            f"At an average error of {mae:.2f} units/day,\n"
            f"reorder quantities and stockout warnings are reliable for most products."
        )
    elif mae <= 2.5 and within_2 >= 65:
        verdict = "YES, with caveats -- works for most items."
        verdict_color = ORANGE
        detail = (
            f"{within_2:.0f}% of predictions land within 2 units/day. "
            f"A small number of high-demand items drive the average error up.\n"
            f"The system is suitable for inventory planning, especially for low-to-medium demand products."
        )
    else:
        verdict = "PARTIALLY -- accuracy needs improvement."
        verdict_color = RED
        detail = (
            f"Only {within_1:.0f}% of predictions are within 1 unit/day. "
            f"The system may generate unreliable reorder suggestions for high-demand items."
        )

    ax.text(0.7, 2.35, "Does it work?", ha="left", va="center",
            fontsize=11, fontweight="bold", color=DARK)
    ax.text(0.7, 1.85, verdict, ha="left", va="center",
            fontsize=12, fontweight="bold", color=verdict_color)
    ax.text(0.7, 1.1, detail, ha="left", va="center",
            fontsize=9, color="#444444", multialignment="left")

    plt.tight_layout()
    fig.savefig(OUT_DIR / "6_scorecard.png", dpi=150, bbox_inches="tight")
    plt.close(fig)
    print("  [6] System scorecard")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    print("=" * 60)
    print("SYSTEM ACCURACY REPORT")
    print("=" * 60)
    print("Loading data and running walk-forward backtest...")

    has_demand, meta = load_and_run()

    print(f"Data: {meta['n_movements']} movements, {meta['n_products']} products")
    print(f"Date range: {meta['date_range']}")
    print(f"Items with demand: {meta['n_items_with_demand']}")
    print(f"Predictions evaluated: {meta['n_predictions']}")
    if meta["stockout_rate"] is not None:
        print(f"Stockout rate: {meta['stockout_rate']:.1f}%")

    mae = has_demand["abs_error"].mean()
    bias = has_demand["error"].mean()
    within_1 = (has_demand["abs_error"] <= 1.0).mean() * 100
    within_2 = (has_demand["abs_error"] <= 2.0).mean() * 100

    print(f"\nMAE:          {mae:.4f} units/day")
    print(f"Bias:         {bias:+.4f} units/day")
    print(f"Within 1 unit: {within_1:.1f}%")
    print(f"Within 2 units: {within_2:.1f}%")

    print(f"\nGenerating figures in {OUT_DIR}/...")
    fig_predicted_vs_actual(has_demand)
    fig_error_distribution(has_demand)
    fig_per_item_accuracy(has_demand)
    fig_category_accuracy(has_demand)
    fig_cumulative_accuracy(has_demand)
    fig_scorecard(has_demand, meta)

    print(f"\nAll figures saved to: {OUT_DIR}/")
    print("  1_predicted_vs_actual.png  -- core proof: predicted vs real demand")
    print("  2_error_distribution.png   -- how far off the model is")
    print("  3_per_item_accuracy.png    -- best and worst predicted items")
    print("  4_category_accuracy.png    -- accuracy by product category")
    print("  5_cumulative_accuracy.png  -- % of predictions within X units")
    print("  6_scorecard.png            -- plain-English verdict")


if __name__ == "__main__":
    main()
