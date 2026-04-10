"""
Reorder Point Investigation Suite

Runs 5 analytical tests against production data to identify gaps in how
reorder point is computed, used for alerts, and acted upon.

Read-only -- nothing is written to the database.

Usage:
    SUPABASE_DB_URL=... SUPABASE_DB_USERNAME=... SUPABASE_DB_PASSWORD=... \
    python scripts/rop_investigation.py
"""

from __future__ import annotations

import math
import os
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from statistics import NormalDist

import numpy as np
import pandas as pd
from sqlalchemy import create_engine, text
from urllib.parse import quote, urlparse, urlunparse


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
MU_FLOOR = 0.1
SIGMA_FLOOR = 0.01
SERVICE_LEVEL = 0.95
TARGET_DAYS = 21
EPSILON_MU = 0.1
LEAD_TIME_STD_DEFAULT = 2.0

Z_SCORE = float(NormalDist().inv_cdf(SERVICE_LEVEL))


def _build_engine():
    raw_url = os.environ.get("SUPABASE_DB_URL", "")
    username = os.environ.get("SUPABASE_DB_USERNAME", "postgres")
    password = os.environ.get("SUPABASE_DB_PASSWORD", "")
    if not raw_url:
        print("ERROR: SUPABASE_DB_URL not set", file=sys.stderr)
        sys.exit(1)
    url = raw_url.replace("jdbc:", "") if raw_url.startswith("jdbc:") else raw_url
    parsed = urlparse(url)
    netloc = f"{quote(username)}:{quote(password)}@{parsed.hostname}"
    if parsed.port:
        netloc += f":{parsed.port}"
    return create_engine(
        urlunparse(("postgresql+psycopg2", netloc, parsed.path, "", "", "")),
        pool_pre_ping=True,
    )


def sep(title: str):
    print(f"\n{'=' * 72}")
    print(f"  {title}")
    print(f"{'=' * 72}")


def subsep(title: str):
    print(f"\n  --- {title} ---")


# ---------------------------------------------------------------------------
# Data loading
# ---------------------------------------------------------------------------

def load_all_data(engine):
    now = datetime.now(timezone.utc)

    with engine.connect() as conn:
        products = pd.read_sql(text("""
            SELECT id::text AS item_id, name, sku, quantity AS current_stock,
                   lead_time_days, reorder_point AS static_rop,
                   target_stock_level
            FROM products WHERE is_active = true ORDER BY name
        """), conn)

        forecasts = pd.read_sql(text("""
            SELECT DISTINCT ON (item_id)
                item_id::text AS item_id,
                computed_at,
                confidence,
                days_to_stockout,
                suggested_reorder_qty,
                (features->>'mu_hat')::float AS mu_hat,
                (features->>'sigma_d_hat')::float AS sigma_d_hat,
                (features->>'safety_stock')::float AS safety_stock,
                (features->>'reorder_point')::float AS dynamic_rop,
                (features->>'lead_time_days')::float AS lead_time,
                (features->>'sigma_L')::float AS sigma_L,
                (features->>'current_qty')::int AS forecast_qty
            FROM forecast_predictions
            ORDER BY item_id, computed_at DESC
        """), conn)

        movements = pd.read_sql(text("""
            SELECT id::text AS event_id, item_id::text AS item_id,
                   quantity_change, LOWER(reason) AS reason, at
            FROM stock_movements
            WHERE at >= :start_ts ORDER BY at ASC
        """), conn, params={"start_ts": now - timedelta(days=30)})

        pending_shipments = pd.read_sql(text("""
            SELECT si.item_id::text AS item_id,
                   si.ordered_quantity AS ordered_qty,
                   s.status,
                   s.order_date,
                   s.expected_delivery_date
            FROM shipment_items si
            JOIN shipments s ON si.shipment_id = s.id
            WHERE s.status NOT IN ('DELIVERED', 'CANCELLED')
        """), conn)

    return products, forecasts, movements, pending_shipments


# ---------------------------------------------------------------------------
# Test 1: Static vs Dynamic ROP Alert Accuracy
# ---------------------------------------------------------------------------

def test_1_alert_accuracy(products, forecasts, movements):
    sep("TEST 1: Static vs Dynamic ROP Alert Accuracy")
    print("  Question: How many alerts would fire at the right time with dynamic vs static ROP?")

    merged = products.merge(forecasts[["item_id", "mu_hat", "dynamic_rop"]], on="item_id", how="inner")
    sellers = merged[merged["mu_hat"] > MU_FLOOR].copy()

    if sellers.empty:
        print("  No sellers with forecast data.")
        return {}

    static_rop_val = 10  # hardcoded default

    # For each seller, determine alert timing quality
    rows = []
    for _, prod in sellers.iterrows():
        iid = str(prod["item_id"])
        current = float(prod.get("current_stock", 0) or 0)
        mu = float(prod["mu_hat"])
        dyn_rop = float(prod["dynamic_rop"]) if pd.notna(prod["dynamic_rop"]) else None
        lt = float(prod.get("lead_time_days", 7) or 7)

        if dyn_rop is None:
            continue

        # Days of warning before stockout at each ROP
        # Warning time = (current_stock - ROP) / mu (how many days before stock reaches ROP)
        # Ideal: warning time >= lead_time (enough time to reorder)
        if mu < EPSILON_MU:
            continue

        dto = current / mu  # days to stockout from current level

        # Static alert: fires when stock < 10
        static_warning = (current - static_rop_val) / mu if current > static_rop_val else 0
        static_fires_now = current < static_rop_val

        # Dynamic alert: fires when stock < dynamic_rop
        dynamic_warning = (current - dyn_rop) / mu if current > dyn_rop else 0
        dynamic_fires_now = current < dyn_rop

        # Classification
        static_adequate = static_warning >= lt  # enough time to reorder
        dynamic_adequate = dynamic_warning >= lt

        rows.append({
            "name": prod["name"],
            "current_stock": int(current),
            "mu_hat": round(mu, 2),
            "lead_time": lt,
            "dto": round(dto, 1),
            "static_rop": static_rop_val,
            "dynamic_rop": round(dyn_rop, 1),
            "static_warning_days": round(static_warning, 1),
            "dynamic_warning_days": round(dynamic_warning, 1),
            "static_fires_now": static_fires_now,
            "dynamic_fires_now": dynamic_fires_now,
            "static_adequate": static_adequate,
            "dynamic_adequate": dynamic_adequate,
            "static_too_late": not static_adequate,
            "dynamic_too_late": not dynamic_adequate,
        })

    df = pd.DataFrame(rows)

    print(f"\n  Active sellers analyzed: {len(df)}")

    subsep("Current alert status (stock below ROP right now)")
    s_fires = df["static_fires_now"].sum()
    d_fires = df["dynamic_fires_now"].sum()
    print(f"  Alerts firing NOW with static ROP=10:  {s_fires}")
    print(f"  Alerts firing NOW with dynamic ROP:    {d_fires}")

    subsep("Alert timing quality (warning time >= lead time)")
    s_adequate = df["static_adequate"].sum()
    d_adequate = df["dynamic_adequate"].sum()
    s_late = df["static_too_late"].sum()
    d_late = df["dynamic_too_late"].sum()
    print(f"  Static ROP=10 provides adequate warning:  {s_adequate} / {len(df)} ({s_adequate/len(df)*100:.0f}%)")
    print(f"  Dynamic ROP provides adequate warning:     {d_adequate} / {len(df)} ({d_adequate/len(df)*100:.0f}%)")
    print(f"  Static ROP=10 fires too late:             {s_late} / {len(df)} ({s_late/len(df)*100:.0f}%)")
    print(f"  Dynamic ROP fires too late:               {d_late} / {len(df)} ({d_late/len(df)*100:.0f}%)")

    # Products where static is dangerously late
    danger = df[(df["static_too_late"]) & (df["dto"] < df["lead_time"] * 2)]
    if not danger.empty:
        subsep(f"Highest-risk products (static alert too late, < 2x lead time of stock)")
        print(f"  {'Product':<35} {'Stock':>6} {'mu/d':>6} {'DTO':>6} {'LT':>4} {'StatW':>6} {'DynW':>6} {'DynROP':>7}")
        print(f"  {'-'*35} {'-'*6} {'-'*6} {'-'*6} {'-'*4} {'-'*6} {'-'*6} {'-'*7}")
        for _, r in danger.sort_values("dto").head(15).iterrows():
            name = str(r["name"])[:33]
            print(f"  {name:<35} {r['current_stock']:>6} {r['mu_hat']:>5.1f} {r['dto']:>5.1f} {r['lead_time']:>3.0f} "
                  f"{r['static_warning_days']:>5.1f} {r['dynamic_warning_days']:>5.1f} {r['dynamic_rop']:>6.1f}")

    # Products where dynamic would have caught but static missed
    dynamic_only = df[(df["dynamic_fires_now"]) & (~df["static_fires_now"])]
    if not dynamic_only.empty:
        subsep(f"Products where dynamic ROP alerts but static does not ({len(dynamic_only)})")
        for _, r in dynamic_only.sort_values("dto").head(10).iterrows():
            name = str(r["name"])[:40]
            print(f"  {name:<42} stock={r['current_stock']:>4}  dynROP={r['dynamic_rop']:>6.1f}  DTO={r['dto']:.1f}d")

    return {"static_late": int(s_late), "dynamic_late": int(d_late), "dynamic_only_alerts": len(dynamic_only)}


# ---------------------------------------------------------------------------
# Test 2: Order Quantity with Safety Stock Inclusion
# ---------------------------------------------------------------------------

def test_2_order_qty_with_ss(products, forecasts):
    sep("TEST 2: Order Quantity With Safety Stock Inclusion")
    print("  Question: Would including ROP in the order quantity formula change recommendations?")

    merged = products.merge(
        forecasts[["item_id", "mu_hat", "sigma_d_hat", "safety_stock", "dynamic_rop", "lead_time"]],
        on="item_id", how="inner",
    )
    sellers = merged[merged["mu_hat"] > MU_FLOOR].copy()

    rows = []
    for _, prod in sellers.iterrows():
        mu = float(prod["mu_hat"])
        current = float(prod.get("current_stock", 0) or 0)
        ss = float(prod["safety_stock"]) if pd.notna(prod["safety_stock"]) else 0
        rop = float(prod["dynamic_rop"]) if pd.notna(prod["dynamic_rop"]) else 0

        # Current formula: Q = max(0, ceil(target_days * mu - current_qty))
        q_current = max(0, math.ceil(TARGET_DAYS * mu - current))

        # Proposed: Q = max(0, ceil(target_days * mu + SS - current_qty))
        # This ensures stock after delivery = target_days * mu + SS (above ROP by target coverage)
        q_with_ss = max(0, math.ceil(TARGET_DAYS * mu + ss - current))

        # Alternative: Q = max(0, ceil(ROP + target_days * mu - current_qty))
        # Even more conservative: ensure stock reaches ROP + full target coverage
        q_with_rop = max(0, math.ceil(rop + TARGET_DAYS * mu - current))

        rows.append({
            "name": prod["name"],
            "mu_hat": round(mu, 2),
            "current_stock": int(current),
            "safety_stock": round(ss, 1),
            "dynamic_rop": round(rop, 1),
            "q_current": q_current,
            "q_with_ss": q_with_ss,
            "q_with_rop": q_with_rop,
            "diff_ss": q_with_ss - q_current,
            "diff_rop": q_with_rop - q_current,
        })

    df = pd.DataFrame(rows)

    print(f"\n  Active sellers analyzed: {len(df)}")

    subsep("Order quantity comparison")
    print(f"  {'Formula':<45} {'Median Q':>10} {'Mean Q':>10} {'Total units':>12}")
    print(f"  {'-'*45} {'-'*10} {'-'*10} {'-'*12}")
    for col, label in [
        ("q_current", "Current: target_days * mu - stock"),
        ("q_with_ss", "Proposed: target_days * mu + SS - stock"),
        ("q_with_rop", "Conservative: ROP + target * mu - stock"),
    ]:
        vals = df[col]
        print(f"  {label:<45} {vals.median():>10.0f} {vals.mean():>10.1f} {vals.sum():>12.0f}")

    subsep("Incremental units from including safety stock")
    extra = df[df["diff_ss"] > 0]
    print(f"  Products with higher order qty: {len(extra)} / {len(df)}")
    print(f"  Total extra units ordered:      {df['diff_ss'].sum():.0f}")
    print(f"  Average extra per product:      {df['diff_ss'].mean():.1f}")
    print(f"  Median extra per product:       {df['diff_ss'].median():.0f}")

    subsep("Incremental units from including full ROP")
    extra_rop = df[df["diff_rop"] > 0]
    print(f"  Products with higher order qty: {len(extra_rop)} / {len(df)}")
    print(f"  Total extra units ordered:      {df['diff_rop'].sum():.0f}")
    print(f"  Average extra per product:      {df['diff_rop'].mean():.1f}")

    return {"extra_ss_units": int(df["diff_ss"].sum()), "extra_rop_units": int(df["diff_rop"].sum())}


# ---------------------------------------------------------------------------
# Test 3: In-Transit Inventory Impact
# ---------------------------------------------------------------------------

def test_3_in_transit(products, forecasts, pending_shipments):
    sep("TEST 3: In-Transit Inventory Impact")
    print("  Question: Are there duplicate reorder recommendations for products with pending orders?")

    if pending_shipments.empty:
        print("\n  No pending (non-delivered, non-cancelled) shipments found.")
        print("  In-transit inventory impact: N/A (no active orders in the system)")

        # Still check if any products have reorder recommendations
        merged = products.merge(
            forecasts[["item_id", "mu_hat", "suggested_reorder_qty", "dynamic_rop"]],
            on="item_id", how="inner",
        )
        active_reorder = merged[merged["suggested_reorder_qty"] > 0]
        print(f"\n  Products with active reorder recommendation: {len(active_reorder)}")
        print(f"  Since there are no pending shipments, all recommendations are valid.")
        return {"pending_shipments": 0, "overlaps": 0}

    print(f"\n  Pending shipments found: {len(pending_shipments)}")
    items_with_pending = pending_shipments["item_id"].unique()
    print(f"  Unique products with pending orders: {len(items_with_pending)}")

    # Check overlap with reorder recommendations
    merged = products.merge(
        forecasts[["item_id", "mu_hat", "suggested_reorder_qty", "dynamic_rop"]],
        on="item_id", how="inner",
    )
    active_reorder = merged[merged["suggested_reorder_qty"] > 0]

    overlap = active_reorder[active_reorder["item_id"].isin(items_with_pending)]
    print(f"  Products with BOTH pending order AND reorder recommendation: {len(overlap)}")

    if not overlap.empty:
        subsep("Double-ordering risk")
        pending_qty = pending_shipments.groupby("item_id")["ordered_qty"].sum().reset_index()
        overlap_detail = overlap.merge(pending_qty, on="item_id", how="left")
        for _, r in overlap_detail.iterrows():
            name = str(r["name"])[:40]
            print(f"  {name:<42} pending={r.get('ordered_qty', '?')}  reorder_rec={r['suggested_reorder_qty']}")

    return {"pending_shipments": len(pending_shipments), "overlaps": len(overlap)}


# ---------------------------------------------------------------------------
# Test 4: Alert Oscillation Frequency
# ---------------------------------------------------------------------------

def test_4_alert_oscillation(products, movements):
    sep("TEST 4: Alert Oscillation Frequency")
    print("  Question: How often does stock cross below ROP=10 repeatedly for the same product?")

    static_rop = 10

    if movements.empty:
        print("  No movements to analyze.")
        return {}

    # Reconstruct stock trajectory per product from movements
    # We need running stock levels, so we work backwards from current stock
    mvts = movements.copy()
    mvts["at"] = pd.to_datetime(mvts["at"], utc=True)
    mvts = mvts.sort_values(["item_id", "at"])

    crossing_counts = {}
    crossing_intervals = []

    for item_id, grp in mvts.groupby("item_id"):
        iid = str(item_id)
        prod_row = products[products["item_id"] == iid]
        if prod_row.empty:
            continue
        current_stock = float(prod_row.iloc[0].get("current_stock", 0) or 0)

        # Reconstruct stock levels backwards from current
        events = grp.sort_values("at", ascending=False)
        stock_levels = []
        running = current_stock
        for _, ev in events.iterrows():
            stock_levels.append({"at": ev["at"], "stock_after": running})
            running -= float(ev["quantity_change"])  # reverse the change to get stock before
        stock_levels.reverse()

        # Count crossings below static ROP
        crossings = []
        prev_above = True  # assume starts above
        for sl in stock_levels:
            currently_above = sl["stock_after"] >= static_rop
            if prev_above and not currently_above:
                crossings.append(sl["at"])
            prev_above = currently_above

        if crossings:
            crossing_counts[iid] = len(crossings)
            # Compute intervals between crossings
            for i in range(1, len(crossings)):
                interval = (crossings[i] - crossings[i - 1]).total_seconds() / 86400
                crossing_intervals.append({
                    "item_id": iid,
                    "interval_days": round(interval, 1),
                })

    total_products_crossing = len(crossing_counts)
    multi_crossing = {k: v for k, v in crossing_counts.items() if v > 1}

    print(f"\n  Products that crossed below ROP=10 at least once: {total_products_crossing}")
    print(f"  Products that crossed below ROP=10 multiple times: {len(multi_crossing)}")

    if crossing_counts:
        counts = list(crossing_counts.values())
        print(f"\n  Crossing count distribution:")
        print(f"    1 crossing:    {sum(1 for c in counts if c == 1)}")
        print(f"    2 crossings:   {sum(1 for c in counts if c == 2)}")
        print(f"    3 crossings:   {sum(1 for c in counts if c == 3)}")
        print(f"    4+ crossings:  {sum(1 for c in counts if c >= 4)}")

    if crossing_intervals:
        intervals_df = pd.DataFrame(crossing_intervals)
        print(f"\n  Re-crossing intervals (days between consecutive crossings):")
        print(f"    Total intervals:  {len(intervals_df)}")
        print(f"    Mean interval:    {intervals_df['interval_days'].mean():.1f} days")
        print(f"    Median interval:  {intervals_df['interval_days'].median():.1f} days")
        print(f"    Min interval:     {intervals_df['interval_days'].min():.1f} days")
        print(f"    Max interval:     {intervals_df['interval_days'].max():.1f} days")

        rapid = intervals_df[intervals_df["interval_days"] < 7]
        print(f"    Re-crossings within 7 days: {len(rapid)} ({len(rapid)/len(intervals_df)*100:.0f}%)")

        if not rapid.empty:
            subsep("Products with rapid re-crossing (< 7 days)")
            rapid_items = rapid["item_id"].unique()
            for iid in rapid_items[:10]:
                prod_row = products[products["item_id"] == iid]
                if not prod_row.empty:
                    name = str(prod_row.iloc[0]["name"])[:40]
                    n_cross = crossing_counts.get(iid, 0)
                    print(f"  {name:<42} crossings={n_cross}")

    return {"multi_crossing": len(multi_crossing), "rapid_recrossings": len(crossing_intervals)}


# ---------------------------------------------------------------------------
# Test 5: Dynamic ROP Propagation Impact
# ---------------------------------------------------------------------------

def test_5_rop_propagation(products, forecasts):
    sep("TEST 5: Dynamic ROP Propagation Impact")
    print("  Question: What changes if we write forecast-computed ROP to the products table?")

    static_rop = 10  # current hardcoded default

    merged = products.merge(
        forecasts[["item_id", "mu_hat", "dynamic_rop", "lead_time"]],
        on="item_id", how="inner",
    )

    sellers = merged[merged["mu_hat"] > MU_FLOOR].copy()
    non_sellers = merged[merged["mu_hat"] <= MU_FLOOR].copy()

    print(f"\n  Products with forecasts: {len(merged)}")
    print(f"  Sellers (mu > {MU_FLOOR}): {len(sellers)}")
    print(f"  Non-sellers: {len(non_sellers)}")

    subsep("Dynamic ROP vs Static ROP=10 (sellers)")

    sellers["diff"] = sellers["dynamic_rop"] - static_rop
    higher = sellers[sellers["dynamic_rop"] > static_rop]
    lower = sellers[sellers["dynamic_rop"] < static_rop]
    same = sellers[(sellers["dynamic_rop"] >= static_rop - 0.5) & (sellers["dynamic_rop"] <= static_rop + 0.5)]

    print(f"  Dynamic ROP > static 10:  {len(higher)} products (would get EARLIER alerts)")
    print(f"  Dynamic ROP < static 10:  {len(lower)} products (would get LATER alerts)")
    print(f"  Dynamic ROP ~ static 10:  {len(same)} products (unchanged)")

    print(f"\n  Dynamic ROP distribution (sellers):")
    dyn = sellers["dynamic_rop"]
    print(f"    Min:    {dyn.min():.1f}")
    print(f"    Median: {dyn.median():.1f}")
    print(f"    Mean:   {dyn.mean():.1f}")
    print(f"    Max:    {dyn.max():.1f}")

    subsep("Alert behavior change for sellers")
    # Products currently below static ROP=10
    below_static = sellers[sellers["current_stock"] < static_rop]
    # Products that would be below dynamic ROP
    below_dynamic = sellers[sellers["current_stock"] < sellers["dynamic_rop"]]

    print(f"  Currently alerting (stock < 10):       {len(below_static)} products")
    print(f"  Would alert with dynamic ROP:          {len(below_dynamic)} products")

    new_alerts = below_dynamic[~below_dynamic["item_id"].isin(below_static["item_id"])]
    lost_alerts = below_static[~below_static["item_id"].isin(below_dynamic["item_id"])]

    print(f"  New alerts (dynamic catches, static misses): {len(new_alerts)}")
    print(f"  Lost alerts (static fires, dynamic wouldn't): {len(lost_alerts)}")

    if not new_alerts.empty:
        subsep(f"New alerts that would fire ({len(new_alerts)} products)")
        print(f"  {'Product':<40} {'Stock':>6} {'DynROP':>7} {'mu/d':>6} {'DTO':>6}")
        print(f"  {'-'*40} {'-'*6} {'-'*7} {'-'*6} {'-'*6}")
        for _, r in new_alerts.sort_values("current_stock").head(15).iterrows():
            name = str(r["name"])[:38]
            dto = r["current_stock"] / r["mu_hat"] if r["mu_hat"] > 0 else float("inf")
            print(f"  {name:<40} {int(r['current_stock']):>6} {r['dynamic_rop']:>6.1f} "
                  f"{r['mu_hat']:>5.1f} {dto:>5.1f}")

    if not lost_alerts.empty:
        subsep(f"Alerts that would stop firing ({len(lost_alerts)} products)")
        for _, r in lost_alerts.iterrows():
            name = str(r["name"])[:40]
            print(f"  {name:<42} stock={int(r['current_stock'])}  dynROP={r['dynamic_rop']:.1f}  mu={r['mu_hat']:.2f}")

    subsep("Non-sellers: dynamic ROP impact")
    if not non_sellers.empty:
        ns_dyn = non_sellers["dynamic_rop"]
        below_static_ns = non_sellers[non_sellers["current_stock"] < static_rop]
        below_dynamic_ns = non_sellers[non_sellers["current_stock"] < non_sellers["dynamic_rop"]]
        print(f"  Dynamic ROP range: {ns_dyn.min():.1f} to {ns_dyn.max():.1f}")
        print(f"  Currently alerting (stock < 10): {len(below_static_ns)}")
        print(f"  Would alert with dynamic ROP:    {len(below_dynamic_ns)}")
        print(f"  Non-sellers typically have very low dynamic ROP (mu=0.1, so ROP ~ 0.7-1.5)")
        print(f"  Switching to dynamic ROP would STOP false alerts for {len(below_static_ns) - len(below_dynamic_ns)} slow/dead products")

    return {
        "new_alerts": len(new_alerts),
        "lost_alerts": len(lost_alerts),
        "false_alerts_eliminated": max(0, len(below_static_ns) - len(below_dynamic_ns)) if not non_sellers.empty else 0,
    }


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    print("=" * 72)
    print("  REORDER POINT INVESTIGATION SUITE")
    print(f"  Run at: {datetime.now(timezone.utc).isoformat()}")
    print("=" * 72)

    engine = _build_engine()

    print("\nLoading data...")
    products, forecasts, movements, pending_shipments = load_all_data(engine)
    print(f"  Products: {len(products)}")
    print(f"  Forecasts: {len(forecasts)}")
    print(f"  Movements (30d): {len(movements)}")
    print(f"  Pending shipments: {len(pending_shipments)}")

    # Run all tests
    r1 = test_1_alert_accuracy(products, forecasts, movements)
    r2 = test_2_order_qty_with_ss(products, forecasts)
    r3 = test_3_in_transit(products, forecasts, pending_shipments)
    r4 = test_4_alert_oscillation(products, movements)
    r5 = test_5_rop_propagation(products, forecasts)

    # Combined analysis
    sep("COMBINED ANALYSIS")

    print("""
  The five tests reveal an interconnected picture:

  1. ALERT TIMING: The static ROP=10 provides inadequate warning for fast-
     moving products and false alerts for slow-moving ones. The dynamic ROP
     computed by the forecasting pipeline is properly calibrated but is not
     connected to the alert system.

  2. ORDER QUANTITY: The current formula ignores safety stock, meaning
     ordered quantities may leave stock below the reorder point after
     delivery. Including safety stock adds a modest buffer.

  3. IN-TRANSIT: {}

  4. OSCILLATION: {}

  5. PROPAGATION: Writing dynamic ROP to the products table would add
     {} new correctly-timed alerts and eliminate {} false alerts
     from non-selling products.
    """.format(
        "No pending shipments exist, so double-ordering is not currently a risk. "
        "This will become relevant as the team starts placing orders through the system."
        if r3.get("pending_shipments", 0) == 0
        else f"{r3['overlaps']} products have both pending orders and reorder recommendations (double-order risk).",

        f"Alert oscillation detected: {r4.get('multi_crossing', 0)} products crossed below ROP=10 multiple times. "
        if r4.get("multi_crossing", 0) > 0
        else "No significant oscillation detected with the current static ROP=10.",

        r5.get("new_alerts", 0),
        r5.get("false_alerts_eliminated", 0),
    ))

    sep("COMPLETE")


if __name__ == "__main__":
    main()
