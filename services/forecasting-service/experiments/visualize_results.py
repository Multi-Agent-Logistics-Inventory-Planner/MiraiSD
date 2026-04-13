"""
Generate presentation-ready charts from backtest results.

Reads from experiments/results/, outputs PNGs to the same directory.

Usage:
    cd services/forecasting-service
    python experiments/visualize_results.py
"""

from __future__ import annotations

import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


RESULTS_DIR = Path(__file__).parent / "results"
COLORS = {
    "ma7": "#2196F3",
    "ma14": "#4CAF50",
    "exp_smooth": "#FF9800",
    "dow_weighted": "#9C27B0",
    "category_pooled": "#E91E63",
    "median_based": "#00BCD4",
    "weekday_weekend": "#795548",
    "exp_smooth_0.1": "#FFB74D",
    "exp_smooth_0.5": "#FF7043",
}


def _color(method: str) -> str:
    return COLORS.get(method, "#666666")


def _save(fig, name: str):
    path = RESULTS_DIR / name
    fig.savefig(path, dpi=150, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print(f"  Saved {name}")


# ---------------------------------------------------------------------------
# Chart 1: Method comparison bar chart
# ---------------------------------------------------------------------------

def plot_method_comparison(method_summary: pd.DataFrame):
    summary = method_summary.sort_values("mae")
    methods = summary["method"].tolist()
    bar_colors = [_color(m) for m in methods]

    fig, axes = plt.subplots(1, 4, figsize=(18, 5))
    fig.suptitle("Forecasting Method Comparison (Walk-Forward Backtest)", fontsize=14)

    metrics = ["mae", "rmse", "mape", "bias"]
    labels = ["MAE (units/day)", "RMSE (units/day)", "MAPE", "Bias (units/day)"]

    for ax, metric, label in zip(axes, metrics, labels):
        values = summary[metric].values
        bars = ax.bar(range(len(methods)), values, color=bar_colors, edgecolor="white", linewidth=0.5)
        ax.set_ylabel(label)
        ax.set_title(label)
        ax.set_xticks(range(len(methods)))
        ax.set_xticklabels(methods, rotation=45, ha="right", fontsize=8)

        for bar, val in zip(bars, values):
            y = bar.get_height()
            offset = 0.01 if y >= 0 else -0.05
            ax.text(
                bar.get_x() + bar.get_width() / 2, y + offset,
                f"{val:.3f}", ha="center", va="bottom", fontsize=7,
            )

    fig.tight_layout()
    _save(fig, "method_comparison.png")


# ---------------------------------------------------------------------------
# Chart 2: Practical accuracy (within N units)
# ---------------------------------------------------------------------------

def plot_practical_accuracy(method_summary: pd.DataFrame):
    summary = method_summary.sort_values("mae")
    methods = summary["method"].tolist()

    fig, ax = plt.subplots(figsize=(12, 5))
    x = np.arange(len(methods))
    width = 0.35

    bars1 = ax.bar(x - width / 2, summary["within_1_unit"].values * 100,
                   width, label="Within 1 unit", color="#4CAF50", alpha=0.8)
    bars2 = ax.bar(x + width / 2, summary["within_2_units"].values * 100,
                   width, label="Within 2 units", color="#2196F3", alpha=0.8)

    ax.set_ylabel("% of predictions")
    ax.set_title("Practical Accuracy: % of Predictions Within N Units of Actual")
    ax.set_xticks(x)
    ax.set_xticklabels(methods, rotation=45, ha="right", fontsize=9)
    ax.legend()
    ax.set_ylim(0, 100)

    for bars in [bars1, bars2]:
        for bar in bars:
            ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 1,
                    f"{bar.get_height():.1f}%", ha="center", va="bottom", fontsize=7)

    fig.tight_layout()
    _save(fig, "practical_accuracy.png")


# ---------------------------------------------------------------------------
# Chart 3: Confidence formula comparison (4 formulas)
# ---------------------------------------------------------------------------

def plot_confidence_comparison(predictions: pd.DataFrame):
    has_demand = predictions[predictions["actual_mu_corrected"] > 0].copy()

    fig, axes = plt.subplots(1, 2, figsize=(14, 5))
    fig.suptitle("Confidence Formula Comparison", fontsize=14)

    # Left: theoretical curves
    ax = axes[0]
    cv_range = np.linspace(0, 5, 200)
    current_curve = np.maximum(0, 1 - cv_range)
    proposed_curve = 1.0 / (1.0 + cv_range)

    ax.plot(cv_range, current_curve, "r-", linewidth=2, label="Current: 1 - CV")
    ax.plot(cv_range, proposed_curve, "b-", linewidth=2, label="Proposed: 1/(1+CV)")
    ax.set_xlabel("Coefficient of Variation (CV)")
    ax.set_ylabel("Confidence")
    ax.set_title("Variability Score vs CV")
    ax.legend(fontsize=9)
    ax.set_xlim(0, 5)
    ax.set_ylim(-0.05, 1.05)

    # Right: histogram of all 4 formulas for dow_weighted method
    ax = axes[1]
    dow = has_demand[has_demand["method"] == "dow_weighted"]
    bins = np.linspace(0, 1, 21)

    formula_cols = {
        "conf_current": ("Current (no MAPE)", "red", 0.5),
        "conf_proposed": ("Proposed (no MAPE)", "blue", 0.5),
        "conf_current_mape": ("Current + MAPE", "salmon", 0.4),
        "conf_proposed_mape": ("Proposed + MAPE", "skyblue", 0.4),
    }
    for col, (label, color, alpha) in formula_cols.items():
        if col in dow.columns:
            ax.hist(dow[col], bins=bins, alpha=alpha, color=color,
                    label=label, edgecolor="white")

    ax.set_xlabel("Confidence")
    ax.set_ylabel("Count")
    ax.set_title("Distribution (dow_weighted method)")
    ax.legend(fontsize=8)

    fig.tight_layout()
    _save(fig, "confidence_comparison.png")


# ---------------------------------------------------------------------------
# Chart 4: Error distributions by method
# ---------------------------------------------------------------------------

def plot_error_distributions(predictions: pd.DataFrame):
    has_demand = predictions[predictions["actual_mu_corrected"] > 0]
    methods = sorted(has_demand["method"].unique())
    n = len(methods)

    cols = min(n, 3)
    rows = (n + cols - 1) // cols
    fig, axes = plt.subplots(rows, cols, figsize=(5 * cols, 4 * rows), squeeze=False)
    fig.suptitle("Prediction Error Distribution by Method", fontsize=14, y=1.02)

    for idx, method in enumerate(methods):
        r, c = divmod(idx, cols)
        ax = axes[r][c]
        subset = has_demand[has_demand["method"] == method]
        errors = subset["predicted_mu"] - subset["actual_mu_corrected"]
        color = _color(method)
        ax.hist(errors, bins=30, color=color, alpha=0.7, edgecolor="white")
        ax.axvline(0, color="black", linestyle="--", linewidth=0.8)
        ax.axvline(errors.mean(), color="red", linestyle="-", linewidth=1.2,
                   label=f"Bias: {errors.mean():.3f}")
        ax.set_xlabel("Error (predicted - actual)")
        ax.set_title(method, fontsize=10)
        ax.legend(fontsize=7)

    # Hide unused subplots
    for idx in range(n, rows * cols):
        r, c = divmod(idx, cols)
        axes[r][c].set_visible(False)

    if n > 0:
        axes[0][0].set_ylabel("Count")
    fig.tight_layout()
    _save(fig, "error_distributions.png")


# ---------------------------------------------------------------------------
# Chart 5: Top items MAPE by method
# ---------------------------------------------------------------------------

def plot_top_items(predictions: pd.DataFrame, products: pd.DataFrame, top_n: int = 15):
    has_demand = predictions[predictions["actual_mu_corrected"] > 0].copy()

    # Find top items by total actual consumption
    item_totals = has_demand.groupby("item_id")["actual_total_corrected"].sum().nlargest(top_n)
    top_items = item_totals.index.tolist()
    subset = has_demand[has_demand["item_id"].isin(top_items)]

    item_mape = (
        subset
        .assign(abs_pct_err=lambda df: (
            (df["predicted_mu"] - df["actual_mu_corrected"]).abs() /
            df["actual_mu_corrected"].clip(lower=0.1)
        ))
        .groupby(["item_id", "method"])["abs_pct_err"]
        .mean()
        .reset_index()
        .rename(columns={"abs_pct_err": "mape"})
    )

    if "item_id" in products.columns:
        name_map = products.set_index("item_id")["name"].to_dict()
        item_mape["name"] = item_mape["item_id"].map(name_map).fillna(item_mape["item_id"])
    else:
        item_mape["name"] = item_mape["item_id"]

    item_mape["name"] = item_mape["name"].str[:30]

    # Only show a subset of methods to keep readable
    show_methods = ["dow_weighted", "category_pooled", "median_based", "weekday_weekend", "ma14"]
    item_mape = item_mape[item_mape["method"].isin(show_methods)]

    pivot = item_mape.pivot(index="name", columns="method", values="mape").fillna(0)
    pivot = pivot.loc[pivot.mean(axis=1).sort_values().index]

    fig, ax = plt.subplots(figsize=(13, max(6, top_n * 0.45)))
    pivot.plot.barh(ax=ax, color=[_color(c) for c in pivot.columns], edgecolor="white")
    ax.set_xlabel("MAPE")
    ax.set_title(f"MAPE by Method - Top {top_n} Items by Volume")
    ax.legend(title="Method", fontsize=8, loc="lower right")
    fig.tight_layout()
    _save(fig, "item_mape_by_method.png")


# ---------------------------------------------------------------------------
# Chart 6: Stability over time
# ---------------------------------------------------------------------------

def plot_stability(predictions: pd.DataFrame):
    has_demand = predictions[predictions["actual_mu_corrected"] > 0].copy()
    has_demand["origin_date"] = pd.to_datetime(has_demand["origin_date"])

    stability = (
        has_demand
        .assign(abs_err=lambda df: (df["predicted_mu"] - df["actual_mu_corrected"]).abs())
        .groupby(["origin_date", "method"])["abs_err"]
        .mean()
        .reset_index()
        .rename(columns={"abs_err": "mae"})
    )

    fig, ax = plt.subplots(figsize=(12, 5))
    for method, group in stability.groupby("method"):
        group = group.sort_values("origin_date")
        color = _color(method)
        ax.plot(group["origin_date"], group["mae"], marker="o", markersize=3,
                color=color, label=method, linewidth=1.2, alpha=0.8)

    ax.set_xlabel("Forecast Origin Date")
    ax.set_ylabel("MAE (units/day)")
    ax.set_title("Forecast Accuracy Over Time (Walk-Forward)")
    ax.legend(fontsize=7, ncol=3)
    ax.tick_params(axis="x", rotation=30)
    fig.tight_layout()
    _save(fig, "stability_over_time.png")


# ---------------------------------------------------------------------------
# Chart 7: Confidence vs actual accuracy scatter
# ---------------------------------------------------------------------------

def plot_confidence_vs_accuracy(predictions: pd.DataFrame):
    """Does higher confidence actually correlate with better predictions?"""
    has_demand = predictions[predictions["actual_mu_corrected"] > 0].copy()
    dow = has_demand[has_demand["method"] == "dow_weighted"].copy()

    dow["abs_pct_err"] = (
        (dow["predicted_mu"] - dow["actual_mu_corrected"]).abs()
        / dow["actual_mu_corrected"].clip(lower=0.1)
    )

    fig, axes = plt.subplots(1, 2, figsize=(13, 5))
    fig.suptitle("Does Confidence Predict Accuracy? (dow_weighted)", fontsize=14)

    for ax, col, title in [
        (axes[0], "conf_current", "Current Formula"),
        (axes[1], "conf_proposed", "Proposed Formula"),
    ]:
        # Bin by confidence and show average error
        dow["conf_bin"] = pd.cut(dow[col], bins=10, labels=False)
        binned = dow.groupby("conf_bin").agg(
            avg_conf=(col, "mean"),
            avg_error=("abs_pct_err", "mean"),
            count=(col, "count"),
        ).dropna()

        scatter = ax.scatter(binned["avg_conf"], binned["avg_error"],
                             s=binned["count"] * 2, alpha=0.7, c="steelblue")
        ax.set_xlabel("Confidence")
        ax.set_ylabel("Average MAPE")
        ax.set_title(title)

        # Trend line
        if len(binned) > 2:
            z = np.polyfit(binned["avg_conf"], binned["avg_error"], 1)
            p = np.poly1d(z)
            x_line = np.linspace(binned["avg_conf"].min(), binned["avg_conf"].max(), 50)
            ax.plot(x_line, p(x_line), "r--", alpha=0.5, label=f"Trend (slope={z[0]:.2f})")
            ax.legend(fontsize=8)

    fig.tight_layout()
    _save(fig, "confidence_vs_accuracy.png")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    if not (RESULTS_DIR / "backtest_predictions.csv").exists():
        print("ERROR: No backtest results found. Run run_backtest.py first.")
        sys.exit(1)

    print("Loading results...")
    predictions = pd.read_csv(RESULTS_DIR / "backtest_predictions.csv")
    method_summary = pd.read_csv(RESULTS_DIR / "method_summary.csv")

    data_dir = Path(__file__).parent / "data"
    products = pd.DataFrame()
    if (data_dir / "products.csv").exists():
        products = pd.read_csv(data_dir / "products.csv")

    print(f"  {len(predictions)} predictions, {len(method_summary)} methods\n")

    print("Generating charts...")
    plot_method_comparison(method_summary)
    plot_practical_accuracy(method_summary)
    plot_confidence_comparison(predictions)
    plot_error_distributions(predictions)
    plot_top_items(predictions, products)
    plot_stability(predictions)
    plot_confidence_vs_accuracy(predictions)

    print(f"\nAll charts saved to {RESULTS_DIR.resolve()}")


if __name__ == "__main__":
    main()
