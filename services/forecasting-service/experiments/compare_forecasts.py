"""Compare NEW forecasts (this branch) vs CURRENT production forecasts.

Loads current forecast_predictions from Supabase, runs the new pipeline
offline against the same items, and produces a side-by-side comparison.

Usage:
    cd services/forecasting-service
    SUPABASE_DB_URL=... SUPABASE_DB_USERNAME=... SUPABASE_DB_PASSWORD=... \
    python experiments/compare_forecasts.py
"""

from __future__ import annotations

import json
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

SERVICE_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SERVICE_ROOT))

from unittest.mock import MagicMock
sys.modules["kafka"] = MagicMock()
sys.modules["kafka.errors"] = MagicMock()

import pydantic
if not hasattr(pydantic, "field_validator"):
    pydantic.field_validator = lambda *a, **kw: lambda f: f

import numpy as np
import pandas as pd

from src import config
from src import features as feat
from src import forecast as fc
from src import policy
from src.adapters.supabase_repo import SupabaseRepo


def load_current_forecasts(repo: SupabaseRepo) -> pd.DataFrame:
    """Load current forecast_predictions from production."""
    query = """
        SELECT DISTINCT ON (fp.item_id)
            fp.item_id::text AS item_id,
            p.name AS product_name,
            fp.computed_at,
            fp.avg_daily_delta,
            fp.days_to_stockout,
            fp.suggested_reorder_qty,
            fp.confidence,
            fp.features
        FROM forecast_predictions fp
        JOIN products p ON p.id = fp.item_id
        ORDER BY fp.item_id, fp.computed_at DESC
    """
    from sqlalchemy import text
    with repo._engine.connect() as conn:
        df = pd.read_sql(text(query), conn)

    if df.empty:
        return df

    df["item_id"] = df["item_id"].astype(str)

    # Parse features JSON
    def parse_features(f):
        if isinstance(f, dict):
            return f
        if isinstance(f, str):
            try:
                return json.loads(f)
            except (json.JSONDecodeError, TypeError):
                return {}
        return {}

    df["features_parsed"] = df["features"].apply(parse_features)
    df["mu_hat_current"] = df["features_parsed"].apply(lambda f: f.get("mu_hat", 0))
    df["sigma_current"] = df["features_parsed"].apply(lambda f: f.get("sigma_d_hat", 0))
    df["safety_stock_current"] = df["features_parsed"].apply(lambda f: f.get("safety_stock", 0))
    df["rop_current"] = df["features_parsed"].apply(lambda f: f.get("reorder_point", 0))
    df["confidence_current"] = df["confidence"]

    return df


def run_new_forecasts(repo: SupabaseRepo) -> pd.DataFrame:
    """Run the new pipeline logic offline and return forecast DataFrame."""
    items_df = repo.get_items()
    if items_df.empty:
        return pd.DataFrame()

    item_list = list(items_df["item_id"])

    # Load inventory
    inventory_df = repo.get_current_inventory(item_ids=item_list)

    # Load movements
    lookback_days = config.ROLLING_WINDOW * 2
    end_ts = datetime.now(timezone.utc)
    start_ts = end_ts - timedelta(days=lookback_days)
    movements_df = repo.get_stock_movements(start=start_ts, end=end_ts, item_ids=item_list)

    # Build features (no stockout filter -- STOCKOUT_FILTER_ENABLED=false default)
    if movements_df.empty:
        return pd.DataFrame()

    daily_df = feat.build_daily_usage(movements_df)
    features_df = feat.build_stats(daily_df)

    if features_df.empty:
        return pd.DataFrame()

    # Estimate demand (no stockout filter)
    estimates_df = fc.estimate_mu_sigma(features_df, method="dow_weighted", min_in_stock_days=0)

    # Category fallback
    cat_col = items_df["category_name"] if "category_name" in items_df.columns else pd.Series("Unknown", index=items_df.index)
    category_map = dict(zip(items_df["item_id"], cat_col))
    items_with_history = set(features_df["item_id"].unique())
    estimates_df = fc.apply_category_fallback(estimates_df, category_map, items_with_history)

    # Merge items + estimates + inventory
    merged = items_df.merge(estimates_df, on="item_id", how="inner")
    if not inventory_df.empty:
        merged = merged.merge(inventory_df[["item_id", "current_qty"]], on="item_id", how="left")
        merged["current_qty"] = merged["current_qty"].fillna(0).astype(int)
    else:
        merged["current_qty"] = 0

    # Lead time (use static from products since MV may not exist)
    if "lead_time_days" in merged.columns:
        lead_time = merged["lead_time_days"].fillna(14).astype(float)
    else:
        lead_time = pd.Series([14.0] * len(merged))

    mu_hat = merged["mu_hat"].astype(float)
    sigma_d_hat = merged["sigma_d_hat"].astype(float)
    current_qty = merged["current_qty"].astype(int)

    # Policy computations
    ss = policy.compute_safety_stock_vectorized(
        mu_hat=mu_hat, sigma_d_hat=sigma_d_hat, L=lead_time,
        alpha=config.SERVICE_LEVEL_DEFAULT,
    )
    rop = policy.reorder_point_vectorized(mu_hat=mu_hat, safety_stock=ss, L=lead_time)
    days_out = policy.days_to_stockout_vectorized(
        current_qty=current_qty.astype(float), mu_hat=mu_hat, epsilon=config.EPSILON_MU,
    )
    suggested_qty = policy.suggest_order_vectorized(
        current_qty=current_qty, mu_hat=mu_hat, L=lead_time,
        safety_stock=ss, target_days=config.TARGET_DAYS,
    )

    # Confidence with floor
    cv = sigma_d_hat / np.maximum(mu_hat, config.EPSILON_MU)
    confidence = np.clip(np.round(1.0 / (1.0 + cv), 3), 0.01, 1.0)

    result = pd.DataFrame({
        "item_id": merged["item_id"].values,
        "name": merged["name"].values if "name" in merged.columns else "",
        "mu_hat_new": mu_hat.values,
        "sigma_new": sigma_d_hat.values,
        "safety_stock_new": np.round(ss.values, 2),
        "rop_new": np.round(rop.values, 2),
        "days_to_stockout_new": [float(d) if d < float("inf") else None for d in days_out],
        "suggested_qty_new": suggested_qty.values,
        "confidence_new": confidence.values if hasattr(confidence, 'values') else confidence,
        "current_qty": current_qty.values,
    })

    return result


def main():
    print("=" * 90)
    print("FORECAST COMPARISON: Current Production vs New Pipeline")
    print("=" * 90)

    repo = SupabaseRepo()

    print("\nLoading current production forecasts...")
    current_df = load_current_forecasts(repo)
    print(f"  Found {len(current_df)} current forecasts")

    print("Running new pipeline forecasts...")
    new_df = run_new_forecasts(repo)
    print(f"  Generated {len(new_df)} new forecasts")

    if current_df.empty or new_df.empty:
        print("Cannot compare -- one or both datasets empty.")
        return

    # Merge on item_id
    comparison = current_df[
        ["item_id", "product_name", "mu_hat_current", "sigma_current",
         "safety_stock_current", "rop_current", "confidence_current",
         "avg_daily_delta", "days_to_stockout", "suggested_reorder_qty"]
    ].merge(
        new_df[["item_id", "mu_hat_new", "sigma_new", "safety_stock_new",
                "rop_new", "confidence_new", "days_to_stockout_new",
                "suggested_qty_new", "current_qty"]],
        on="item_id",
        how="inner",
    )

    print(f"\n  Matched {len(comparison)} items for comparison")

    # Compute deltas
    comparison["mu_delta"] = comparison["mu_hat_new"] - comparison["mu_hat_current"]
    comparison["mu_delta_pct"] = (
        comparison["mu_delta"] / comparison["mu_hat_current"].clip(lower=0.01) * 100
    )
    comparison["confidence_delta"] = comparison["confidence_new"] - comparison["confidence_current"]
    comparison["rop_delta"] = comparison["rop_new"] - comparison["rop_current"]
    comparison["ss_delta"] = comparison["safety_stock_new"] - comparison["safety_stock_current"]

    # --- Summary stats ---
    print("\n" + "=" * 90)
    print("AGGREGATE SUMMARY")
    print("=" * 90)

    has_sales = comparison[comparison["mu_hat_current"] > config.MU_FLOOR]
    no_sales = comparison[comparison["mu_hat_current"] <= config.MU_FLOOR]

    print(f"\n  Total items compared: {len(comparison)}")
    print(f"  Items with sales history: {len(has_sales)}")
    print(f"  Items without sales (at MU_FLOOR): {len(no_sales)}")

    if not has_sales.empty:
        print(f"\n  --- Items WITH sales ---")
        print(f"  Avg mu_hat current: {has_sales['mu_hat_current'].mean():.4f}")
        print(f"  Avg mu_hat new:     {has_sales['mu_hat_new'].mean():.4f}")
        print(f"  Avg mu delta:       {has_sales['mu_delta'].mean():+.4f}")
        print(f"  Avg confidence current: {has_sales['confidence_current'].mean():.3f}")
        print(f"  Avg confidence new:     {has_sales['confidence_new'].mean():.3f}")
        print(f"  Avg confidence delta:   {has_sales['confidence_delta'].mean():+.3f}")
        print(f"  Avg ROP current: {has_sales['rop_current'].mean():.1f}")
        print(f"  Avg ROP new:     {has_sales['rop_new'].mean():.1f}")

    # --- Distribution of changes ---
    print("\n" + "=" * 90)
    print("DISTRIBUTION OF CHANGES (items with sales)")
    print("=" * 90)

    if not has_sales.empty:
        mu_change = has_sales["mu_delta"].abs()
        print(f"\n  mu_hat change magnitude:")
        print(f"    No change (<0.01):  {(mu_change < 0.01).sum()} items")
        print(f"    Small (0.01-0.5):   {((mu_change >= 0.01) & (mu_change < 0.5)).sum()} items")
        print(f"    Medium (0.5-2.0):   {((mu_change >= 0.5) & (mu_change < 2.0)).sum()} items")
        print(f"    Large (>2.0):       {(mu_change >= 2.0).sum()} items")

        conf_change = has_sales["confidence_delta"]
        print(f"\n  Confidence change:")
        print(f"    Increased:  {(conf_change > 0.01).sum()} items")
        print(f"    Decreased:  {(conf_change < -0.01).sum()} items")
        print(f"    Unchanged:  {(conf_change.abs() <= 0.01).sum()} items")

    # --- Top movers ---
    print("\n" + "=" * 90)
    print("TOP 15 LARGEST MU CHANGES (items with sales)")
    print("=" * 90)

    if not has_sales.empty:
        top = has_sales.nlargest(15, "mu_delta", keep="first")
        header = f"{'Product':<45} {'mu_cur':>7} {'mu_new':>7} {'delta':>7} {'conf_cur':>8} {'conf_new':>8}"
        print(header)
        print("-" * len(header))
        for _, r in top.iterrows():
            name = str(r["product_name"])[:44]
            print(
                f"{name:<45} {r['mu_hat_current']:>7.2f} {r['mu_hat_new']:>7.2f} "
                f"{r['mu_delta']:>+7.2f} {r['confidence_current']:>8.3f} {r['confidence_new']:>8.3f}"
            )

    # --- Bottom movers ---
    print("\n" + "=" * 90)
    print("TOP 15 LARGEST MU DECREASES (items with sales)")
    print("=" * 90)

    if not has_sales.empty:
        bottom = has_sales.nsmallest(15, "mu_delta", keep="first")
        header = f"{'Product':<45} {'mu_cur':>7} {'mu_new':>7} {'delta':>7} {'conf_cur':>8} {'conf_new':>8}"
        print(header)
        print("-" * len(header))
        for _, r in bottom.iterrows():
            name = str(r["product_name"])[:44]
            print(
                f"{name:<45} {r['mu_hat_current']:>7.2f} {r['mu_hat_new']:>7.2f} "
                f"{r['mu_delta']:>+7.2f} {r['confidence_current']:>8.3f} {r['confidence_new']:>8.3f}"
            )

    # --- Safety stock / ROP changes ---
    print("\n" + "=" * 90)
    print("SAFETY STOCK & REORDER POINT IMPACT")
    print("=" * 90)

    if not has_sales.empty:
        print(f"\n  Avg safety stock current: {has_sales['safety_stock_current'].mean():.1f}")
        print(f"  Avg safety stock new:     {has_sales['safety_stock_new'].mean():.1f}")
        print(f"  Avg SS delta:             {has_sales['ss_delta'].mean():+.1f}")
        print(f"\n  Avg ROP current: {has_sales['rop_current'].mean():.1f}")
        print(f"  Avg ROP new:     {has_sales['rop_new'].mean():.1f}")
        print(f"  Avg ROP delta:   {has_sales['rop_delta'].mean():+.1f}")

        rop_increased = (has_sales["rop_delta"] > 1).sum()
        rop_decreased = (has_sales["rop_delta"] < -1).sum()
        rop_stable = len(has_sales) - rop_increased - rop_decreased
        print(f"\n  ROP increased (>1): {rop_increased} items")
        print(f"  ROP decreased (<-1): {rop_decreased} items")
        print(f"  ROP stable: {rop_stable} items")

    # --- Export full comparison ---
    out_path = Path(__file__).parent / "forecast_comparison.csv"
    export_cols = [
        "item_id", "product_name", "current_qty",
        "mu_hat_current", "mu_hat_new", "mu_delta", "mu_delta_pct",
        "sigma_current", "sigma_new",
        "safety_stock_current", "safety_stock_new", "ss_delta",
        "rop_current", "rop_new", "rop_delta",
        "confidence_current", "confidence_new", "confidence_delta",
    ]
    comparison[export_cols].to_csv(out_path, index=False)
    print(f"\n  Full comparison exported to: {out_path}")

    print("\n" + "=" * 90)
    print("DONE")
    print("=" * 90)


if __name__ == "__main__":
    main()
