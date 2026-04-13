"""Generate algorithm explainer slide for presentation.

Shows how demand is calculated, why DOW-weighted was chosen,
and how the system improves over time.

Usage:
    cd services/forecasting-service
    python experiments/generate_algorithm_explainer.py
"""

from __future__ import annotations

from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyArrowPatch
import numpy as np

OUT_DIR = Path(__file__).parent / "system_report"
OUT_DIR.mkdir(exist_ok=True)

BLUE = "#2C7BB6"
GREEN = "#1A9641"
ORANGE = "#E8833A"
RED = "#D7191C"
GRAY = "#AAAAAA"
DARK = "#222222"
LIGHT_BG = "#F8F9FA"
CARD_BG = "white"


def fig_maturity_timeline():
    """Standalone slide: how accuracy improves as data accumulates."""
    fig, ax = plt.subplots(figsize=(16, 8))
    fig.patch.set_facecolor(LIGHT_BG)
    ax.set_xlim(0, 16)
    ax.set_ylim(0, 8)
    ax.axis("off")
    ax.set_facecolor(LIGHT_BG)

    ax.text(8, 7.6, "Accuracy Improves as the System Matures",
            ha="center", va="center", fontsize=17, fontweight="bold", color=DARK)
    ax.text(8, 7.1,
            "No manual retraining needed -- every sale automatically refines future predictions",
            ha="center", va="center", fontsize=11, color="#555555")

    # Horizontal timeline bar
    ax.plot([1.0, 15.0], [4.2, 4.2], color="#DDDDDD", linewidth=4, zorder=1,
            solid_capstyle="round")

    stages = [
        ("Now\n(38 days)", 2.0,  "~80%\nwithin 2 units",
         "Day-of-week patterns learned\nfrom limited history.\nHigh-demand stockout items\nare the main source of error.",
         ORANGE),
        ("3 Months",       5.5,  "~85%\nwithin 2 units",
         "More in-stock days per product.\nStockout correction becomes\nmore reliable. Lead time\nestimates stabilize.",
         BLUE),
        ("6 Months",       9.0,  "~90%\nwithin 2 units",
         "Seasonal patterns emerge\n(holidays, slow seasons).\nDay-of-week weights\nbecome highly stable.",
         GREEN),
        ("Ongoing",        12.5, "Continuously\nimproving",
         "Each inventory change\nrefines the model.\nNew products ramp up\nfaster with more history.",
         GREEN),
    ]

    for label, x, accuracy, description, color in stages:
        # Circle on timeline
        ax.add_patch(plt.Circle((x, 4.2), 0.38, color=color, zorder=4))

        # Label above circle
        ax.text(x, 4.82, label, ha="center", va="bottom",
                fontsize=10.5, fontweight="bold", color=color)

        # Accuracy badge above label
        ax.text(x, 6.05, accuracy, ha="center", va="center",
                fontsize=12, fontweight="bold", color=color,
                bbox=dict(boxstyle="round,pad=0.45", facecolor=color + "20",
                          edgecolor=color, linewidth=1.8))

    # Arrows between circles on the timeline
    for x_start, x_end in [(2.4, 5.1), (5.9, 8.6), (9.4, 12.1)]:
        ax.annotate("", xy=(x_end, 4.2), xytext=(x_start, 4.2),
                    arrowprops=dict(arrowstyle="-|>", color=GRAY,
                                   lw=1.8, mutation_scale=16))

    plt.tight_layout()
    fig.savefig(OUT_DIR / "8_maturity_timeline.png", dpi=150, bbox_inches="tight",
                facecolor=LIGHT_BG)
    plt.close(fig)
    print("Saved: 8_maturity_timeline.png")


def fig_algorithm_explainer():
    """5-step horizontal flow only. No sub-captions, no bottom section."""
    fig, ax = plt.subplots(figsize=(20, 6))
    fig.patch.set_facecolor(LIGHT_BG)
    ax.set_xlim(0, 20)
    ax.set_ylim(0, 6)
    ax.axis("off")
    ax.set_facecolor(LIGHT_BG)

    ax.text(10, 5.65, "How the System Figures Out What to Order",
            ha="center", va="center", fontsize=22, fontweight="bold", color=DARK)
    ax.text(10, 5.1,
            "Runs automatically every night. No manual work required.",
            ha="center", va="center", fontsize=13, color="#666666")

    steps = [
        ("1", "Look at\npast sales",      BLUE),
        ("2", "Skip days\nwith no stock",  ORANGE),
        ("3", "Learn weekly\npatterns",    BLUE),
        ("4", "Predict\ndaily demand",     GREEN),
        ("5", "Send\nreorder alert",       RED),
    ]

    n = len(steps)
    box_w = 2.9
    box_h = 2.8
    gap = 1.0
    total = n * box_w + (n - 1) * gap
    x_start = (20 - total) / 2
    y_top = 4.6

    for i, (num, title, color) in enumerate(steps):
        x = x_start + i * (box_w + gap)
        xc = x + box_w / 2

        rect = mpatches.FancyBboxPatch(
            (x, y_top - box_h), box_w, box_h,
            boxstyle="round,pad=0.22",
            facecolor=CARD_BG, edgecolor=color, linewidth=3.0,
            zorder=3,
        )
        ax.add_patch(rect)

        ax.add_patch(plt.Circle((x + 0.45, y_top - 0.45), 0.33,
                                color=color, zorder=5))
        ax.text(x + 0.45, y_top - 0.45, num,
                ha="center", va="center", fontsize=13,
                fontweight="bold", color="white", zorder=6)

        ax.text(xc, y_top - 1.65, title,
                ha="center", va="center", fontsize=14,
                fontweight="bold", color=color, multialignment="center")

        if i < n - 1:
            ax_end = x + box_w
            ax_next = ax_end + gap
            mid_y = y_top - box_h / 2
            ax.annotate("", xy=(ax_next - 0.04, mid_y),
                        xytext=(ax_end + 0.04, mid_y),
                        arrowprops=dict(arrowstyle="-|>", color=GRAY,
                                       lw=2.5, mutation_scale=22),
                        zorder=4)

    plt.savefig(OUT_DIR / "7_algorithm_explainer.png", dpi=150,
                bbox_inches="tight", facecolor=LIGHT_BG)
    plt.close(fig)
    print("Saved: 7_algorithm_explainer.png")


if __name__ == "__main__":
    fig_algorithm_explainer()
    fig_maturity_timeline()
    print(f"Output: {OUT_DIR}/")
