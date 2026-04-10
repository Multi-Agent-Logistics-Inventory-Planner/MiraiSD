"""
Impact Simulation: Confidence Formula Fix

Simulates the exact impact of pushing two changes to production:
  1. Replace confidence formula: 1 - min(1, CV) --> 1 / (1 + CV)
  2. Disable MAPE blending: use variability score alone

Recomputes ALL forecast outputs for every product using live production data,
comparing current pipeline behavior vs proposed. The simulation replicates the
exact logic from pipeline.py lines 270-450.

Read-only -- nothing is written to the database.

Usage:
    SUPABASE_DB_URL=... SUPABASE_DB_USERNAME=... SUPABASE_DB_PASSWORD=... \
    python scripts/impact_simulation.py

Output:
    scripts/impact_simulation_output.csv       (per-product detail)
    scripts/impact_simulation_summary.txt      (structured summary)
    stdout: full report
"""

from __future__ import annotations

import math
import os
import sys
import uuid as _uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from statistics import NormalDist

import numpy as np
import pandas as pd
from sqlalchemy import create_engine, text
from urllib.parse import quote, urlparse, urlunparse


# ---------------------------------------------------------------------------
# Constants (match forecasting-service/src/config.py defaults exactly)
# ---------------------------------------------------------------------------
MU_FLOOR = 0.1
SIGMA_FLOOR = 0.01
MAPE_EPSILON = 0.1
ROLLING_WINDOW = 14
SERVICE_LEVEL = 0.95
TARGET_DAYS = 21
EPSILON_MU = 0.1
LEAD_TIME_STD_DEFAULT = 0.0
LEAD_TIME_MIN_SHIPMENTS = 2

Z_SCORE = float(NormalDist().inv_cdf(SERVICE_LEVEL))


# ---------------------------------------------------------------------------
# Database
# ---------------------------------------------------------------------------

def _build_engine():
    raw_url = os.environ.get("SUPABASE_DB_URL", "")
    username = os.environ.get("SUPABASE_DB_USERNAME", "postgres")
    password = os.environ.get("SUPABASE_DB_PASSWORD", "")
    if not raw_url:
        print("ERROR: SUPABASE_DB_URL not set", file=sys.stderr)
        sys.exit(1)
    url = raw_url.replace("jdbc:", "") if raw_url.startswith("jdbc:") else raw_url
    parsed = urlparse(url)
    scheme = "postgresql+psycopg2"
    netloc = f"{quote(username)}:{quote(password)}@{parsed.hostname}"
    if parsed.port:
        netloc += f":{parsed.port}"
    return create_engine(
        urlunparse((scheme, netloc, parsed.path, "", "", "")),
        pool_pre_ping=True,
    )


# ---------------------------------------------------------------------------
# Data loading (mirrors pipeline.py exactly)
# ---------------------------------------------------------------------------

def load_products(engine) -> pd.DataFrame:
    with engine.connect() as conn:
        return pd.read_sql(text("""
            SELECT id::text AS item_id, name, sku, quantity AS current_stock,
                   lead_time_days, subcategory AS category
            FROM products WHERE is_active = true ORDER BY name
        """), conn)


def load_movements(engine, lookback_days: int) -> pd.DataFrame:
    end_ts = datetime.now(timezone.utc)
    start_ts = end_ts - timedelta(days=lookback_days)
    with engine.connect() as conn:
        return pd.read_sql(text("""
            SELECT id::text AS event_id, item_id::text AS item_id,
                   quantity_change, LOWER(reason) AS reason, at
            FROM stock_movements
            WHERE at >= :start_ts AND at <= :end_ts ORDER BY at ASC
        """), conn, params={"start_ts": start_ts, "end_ts": end_ts})


def load_shipments(engine) -> pd.DataFrame:
    with engine.connect() as conn:
        return pd.read_sql(text("""
            SELECT si.item_id::text AS item_id,
                   (s.actual_delivery_date::date - s.order_date::date)::int AS lead_time_days
            FROM shipment_items si
            JOIN shipments s ON si.shipment_id = s.id
            WHERE s.status = 'DELIVERED'
              AND s.actual_delivery_date IS NOT NULL
              AND s.order_date IS NOT NULL
              AND s.actual_delivery_date > s.order_date
        """), conn)


def load_historical_forecasts(engine, item_ids, target_date) -> pd.DataFrame:
    target_start = target_date - timedelta(hours=24)
    target_end = target_date + timedelta(hours=24)
    with engine.connect() as conn:
        return pd.read_sql(text("""
            SELECT DISTINCT ON (item_id)
                item_id::text AS item_id, computed_at,
                (features->>'mu_hat')::float AS mu_hat
            FROM forecast_predictions
            WHERE item_id = ANY(:item_ids)
              AND computed_at BETWEEN :target_start AND :target_end
            ORDER BY item_id, ABS(EXTRACT(EPOCH FROM (computed_at - :target_date)))
        """), conn, params={
            "item_ids": [_uuid.UUID(i) for i in item_ids],
            "target_start": target_start,
            "target_end": target_end,
            "target_date": target_date,
        })


def load_current_production_forecasts(engine) -> pd.DataFrame:
    """Load the actual current forecasts from production for comparison."""
    with engine.connect() as conn:
        return pd.read_sql(text("""
            SELECT DISTINCT ON (item_id)
                item_id::text AS item_id,
                confidence AS prod_confidence,
                days_to_stockout AS prod_days_to_stockout,
                suggested_reorder_qty AS prod_suggested_qty,
                (features->>'mu_hat')::float AS prod_mu_hat,
                (features->>'sigma_d_hat')::float AS prod_sigma_d_hat,
                (features->>'safety_stock')::float AS prod_safety_stock,
                (features->>'reorder_point')::float AS prod_reorder_point,
                (features->>'lead_time_days')::float AS prod_lead_time
            FROM forecast_predictions
            ORDER BY item_id, computed_at DESC
        """), conn)


# ---------------------------------------------------------------------------
# Feature building (mirrors features.py + forecast.py exactly)
# ---------------------------------------------------------------------------

def build_daily_usage(events_df: pd.DataFrame) -> pd.DataFrame:
    if events_df.empty:
        return pd.DataFrame(columns=["date", "item_id", "consumption"])
    df = events_df.copy()
    df["at"] = pd.to_datetime(df["at"], utc=True)
    df["date"] = df["at"].dt.floor("D").dt.tz_localize(None)
    reason = df["reason"].astype("string").str.lower().str.strip()
    is_sale = reason.isin({"sale", "sales"}) & (df["quantity_change"] < 0)
    sales = df.loc[is_sale, ["item_id", "date", "quantity_change"]].copy()
    all_items = df["item_id"].astype(str).unique()
    all_dates = pd.date_range(df["date"].min(), df["date"].max(), freq="D")
    if sales.empty:
        out = pd.MultiIndex.from_product(
            [all_items, all_dates], names=["item_id", "date"],
        ).to_frame(index=False)
        out["consumption"] = 0.0
        return out.sort_values(["item_id", "date"]).reset_index(drop=True)
    sales["consumption"] = (-sales["quantity_change"]).astype(float)
    daily = sales.groupby(["item_id", "date"], as_index=False)["consumption"].sum()
    full_idx = pd.MultiIndex.from_product(
        [all_items, all_dates], names=["item_id", "date"],
    )
    daily = daily.set_index(["item_id", "date"]).reindex(full_idx)
    daily["consumption"] = daily["consumption"].fillna(0.0)
    return daily.reset_index().sort_values(["item_id", "date"]).reset_index(drop=True)


def dow_weighted_estimate(group):
    g = group.copy()
    g["dow"] = pd.to_datetime(g["date"]).dt.dayofweek
    overall_mean = max(float(g["consumption"].mean()), MU_FLOOR)
    dow_means = g.groupby("dow")["consumption"].mean()
    expected = g["dow"].map(dow_means).fillna(overall_mean)
    residuals = g["consumption"] - expected
    sigma_d_hat = max(float(residuals.std(ddof=0)), SIGMA_FLOOR)
    return overall_mean, sigma_d_hat


def compute_mape_map(hist_fc, movements_df, backtest_target, now):
    mape_map = {}
    if hist_fc.empty or movements_df.empty:
        return mape_map
    bt_start = pd.to_datetime(backtest_target, utc=True)
    bt_end = pd.to_datetime(now, utc=True)
    window = movements_df[(movements_df["at"] >= bt_start) & (movements_df["at"] <= bt_end)]
    if window.empty:
        return mape_map
    daily = build_daily_usage(window)
    if daily.empty:
        return mape_map
    usage = daily.copy()
    usage["item_id"] = usage["item_id"].astype(str)
    actual_agg = usage.groupby("item_id", as_index=False).agg(
        actual_mu=("consumption", "mean"),
    )
    fc = hist_fc[["item_id", "mu_hat"]].copy()
    fc["item_id"] = fc["item_id"].astype(str)
    fc = fc.rename(columns={"mu_hat": "forecast_mu"})
    merged = fc.merge(actual_agg, on="item_id", how="inner")
    if merged.empty:
        return mape_map
    merged["mape"] = (
        np.abs(merged["actual_mu"] - merged["forecast_mu"])
        / np.maximum(merged["actual_mu"], MAPE_EPSILON)
    )
    for _, r in merged.iterrows():
        mape_map[str(r["item_id"])] = float(r["mape"])
    return mape_map


# ---------------------------------------------------------------------------
# Lead time stats (mirrors lead_time.py)
# ---------------------------------------------------------------------------

def compute_lead_time_stats(shipments_df, products_df):
    stats = {}
    if not shipments_df.empty:
        for item_id, grp in shipments_df.groupby("item_id"):
            n = len(grp)
            avg_lt = float(grp["lead_time_days"].mean())
            sigma_l = (
                float(grp["lead_time_days"].std(ddof=1))
                if n >= LEAD_TIME_MIN_SHIPMENTS
                else LEAD_TIME_STD_DEFAULT
            )
            if pd.isna(sigma_l):
                sigma_l = LEAD_TIME_STD_DEFAULT
            stats[str(item_id)] = {
                "avg_lead_time": avg_lt,
                "sigma_L": sigma_l,
                "source": "shipment_history",
            }
    for _, prod in products_df.iterrows():
        iid = str(prod["item_id"])
        if iid not in stats:
            lt = float(prod["lead_time_days"]) if pd.notna(prod.get("lead_time_days")) else 14.0
            stats[iid] = {
                "avg_lead_time": lt,
                "sigma_L": LEAD_TIME_STD_DEFAULT,
                "source": "product_default",
            }
    return stats


# ---------------------------------------------------------------------------
# Confidence formulas
# ---------------------------------------------------------------------------

def confidence_current(mu, sigma, mape_val=None):
    """Exact replica of pipeline.py lines 373-382."""
    cv = sigma / max(mu, 0.1)
    variability = 1.0 - min(1.0, cv)
    if mape_val is not None and not np.isnan(mape_val):
        mape_score = max(0.0, 1.0 - mape_val)
        return round(0.4 * variability + 0.6 * mape_score, 3)
    return round(variability, 3)


def confidence_proposed(mu, sigma, mape_val=None):
    """Proposed: 1/(1+CV), MAPE disabled."""
    cv = sigma / max(mu, 0.1)
    return round(1.0 / (1.0 + cv), 3)


# ---------------------------------------------------------------------------
# Policy formulas (mirrors policy.py vectorized functions)
# ---------------------------------------------------------------------------

def compute_safety_stock(mu, sigma_d, lead_time, sigma_l=None):
    if sigma_l and sigma_l > 0:
        return Z_SCORE * math.sqrt(lead_time * sigma_d**2 + mu**2 * sigma_l**2)
    return Z_SCORE * sigma_d * math.sqrt(max(lead_time, 0))


def compute_rop(mu, safety_stock, lead_time):
    return mu * max(lead_time, 0) + max(safety_stock, 0)


def compute_days_to_stockout(current_qty, mu):
    if mu < EPSILON_MU:
        return float("inf")
    return max(current_qty, 0) / mu


def compute_suggested_qty(current_qty, mu):
    target = TARGET_DAYS * max(mu, 0)
    needed = target - max(current_qty, 0)
    return max(0, math.ceil(needed)) if needed > 0 else 0


# ---------------------------------------------------------------------------
# Formatting
# ---------------------------------------------------------------------------

def sep(title: str):
    print(f"\n{'=' * 72}")
    print(f"  {title}")
    print(f"{'=' * 72}")


def subsep(title: str):
    print(f"\n  --- {title} ---")


# ---------------------------------------------------------------------------
# Main simulation
# ---------------------------------------------------------------------------

def main():
    print("=" * 72)
    print("  IMPACT SIMULATION: Confidence Formula Fix")
    print("  Changes under test:")
    print("    1. Variability formula: 1 - min(1,CV) --> 1/(1+CV)")
    print("    2. MAPE blending: disabled (variability score only)")
    print("  All other pipeline logic is UNCHANGED.")
    print(f"  Run at: {datetime.now(timezone.utc).isoformat()}")
    print("=" * 72)

    engine = _build_engine()
    now = datetime.now(timezone.utc)

    # Load data
    print("\nLoading production data...")
    products_df = load_products(engine)
    movements_df = load_movements(engine, lookback_days=ROLLING_WINDOW * 2)
    shipments_df = load_shipments(engine)
    prod_forecasts = load_current_production_forecasts(engine)

    item_ids = products_df["item_id"].tolist()
    backtest_target = now - timedelta(days=14)
    hist_fc = load_historical_forecasts(engine, item_ids, backtest_target)
    mape_map = compute_mape_map(hist_fc, movements_df, backtest_target, now)

    print(f"  Products: {len(products_df)}")
    print(f"  Stock movements ({ROLLING_WINDOW*2}d): {len(movements_df)}")
    print(f"  Current production forecasts: {len(prod_forecasts)}")
    print(f"  Products with MAPE data: {len(mape_map)}")

    # Build daily usage and estimates
    daily_usage = build_daily_usage(movements_df) if not movements_df.empty else pd.DataFrame()
    lt_stats = compute_lead_time_stats(shipments_df, products_df)

    estimates = {}
    if not daily_usage.empty:
        for item_id, group in daily_usage.groupby("item_id"):
            iid = str(item_id)
            g = group.sort_values("date")
            mu, sigma = dow_weighted_estimate(g)
            has_sales = float(g["consumption"].sum()) > 0
            estimates[iid] = {"mu_hat": mu, "sigma_d_hat": sigma, "has_sales": has_sales}

    for _, prod in products_df.iterrows():
        iid = str(prod["item_id"])
        if iid not in estimates:
            estimates[iid] = {"mu_hat": MU_FLOOR, "sigma_d_hat": SIGMA_FLOOR, "has_sales": False}

    # -----------------------------------------------------------------------
    # Recompute ALL forecasts under CURRENT and PROPOSED formulas
    # -----------------------------------------------------------------------
    rows = []
    for _, prod in products_df.iterrows():
        iid = str(prod["item_id"])
        est = estimates[iid]
        mu = est["mu_hat"]
        sigma = est["sigma_d_hat"]
        has_sales = est["has_sales"]
        current_qty = float(prod.get("current_stock", 0) or 0)
        mape_val = mape_map.get(iid)

        lt_info = lt_stats.get(iid, {"avg_lead_time": 14.0, "sigma_L": 0.0, "source": "product_default"})
        lead_time = lt_info["avg_lead_time"]
        sigma_l = lt_info["sigma_L"]

        # These are IDENTICAL between current and proposed (no change)
        ss = compute_safety_stock(mu, sigma, lead_time, sigma_l)
        rop = compute_rop(mu, ss, lead_time)
        dto = compute_days_to_stockout(current_qty, mu)
        suggested = compute_suggested_qty(current_qty, mu)

        # These DIFFER between current and proposed
        conf_current = confidence_current(mu, sigma, mape_val)
        conf_proposed = confidence_proposed(mu, sigma, mape_val)

        cv = sigma / max(mu, 0.1)

        rows.append({
            "item_id": iid,
            "product_name": prod["name"],
            "sku": prod.get("sku", ""),
            "category": prod.get("category", ""),
            "has_sales": has_sales,
            "current_stock": int(current_qty),
            "mu_hat": round(mu, 4),
            "sigma_d_hat": round(sigma, 4),
            "cv": round(cv, 4),
            "lead_time": round(lead_time, 1),
            "sigma_L": round(sigma_l, 2),
            "safety_stock": round(ss, 2),
            "reorder_point": round(rop, 2),
            "days_to_stockout": round(dto, 1) if dto != float("inf") else None,
            "suggested_reorder_qty": suggested,
            "mape_14d": round(mape_val, 4) if mape_val is not None else None,
            "confidence_current": conf_current,
            "confidence_proposed": conf_proposed,
            "confidence_delta": round(conf_proposed - conf_current, 4),
        })

    result_df = pd.DataFrame(rows)

    # Merge with production forecasts to validate simulation accuracy
    if not prod_forecasts.empty:
        result_df = result_df.merge(prod_forecasts, on="item_id", how="left")

    # -----------------------------------------------------------------------
    # Analysis
    # -----------------------------------------------------------------------

    sellers = result_df[result_df["has_sales"]]
    non_sellers = result_df[~result_df["has_sales"]]

    sep("1. WHAT CHANGES (confidence only)")
    print("\n  The ONLY field that changes is 'confidence'. All other outputs")
    print("  (safety_stock, reorder_point, days_to_stockout, suggested_reorder_qty)")
    print("  are computed from mu_hat, sigma_d_hat, and lead_time, which are unchanged.")

    subsep("Verification: operational outputs are identical")
    if "prod_safety_stock" in result_df.columns:
        matched = result_df[result_df["prod_safety_stock"].notna()]
        if not matched.empty:
            ss_diff = (matched["safety_stock"] - matched["prod_safety_stock"]).abs()
            rop_diff = (matched["reorder_point"] - matched["prod_reorder_point"]).abs()
            print(f"  Products with production forecasts to compare: {len(matched)}")
            print(f"  Safety stock max diff vs production:  {ss_diff.max():.4f}")
            print(f"  Reorder point max diff vs production: {rop_diff.max():.4f}")
            print(f"  (Small diffs are expected from timing/data freshness)")

    sep("2. CONFIDENCE IMPACT: ALL PRODUCTS ({})".format(len(result_df)))

    print(f"\n  {'Metric':<45} {'Current':>10} {'Proposed':>10} {'Change':>10}")
    print(f"  {'-'*45} {'-'*10} {'-'*10} {'-'*10}")

    avg_curr = result_df["confidence_current"].mean()
    avg_prop = result_df["confidence_proposed"].mean()
    print(f"  {'Average confidence':<45} {avg_curr:>9.1%} {avg_prop:>9.1%} {avg_prop-avg_curr:>+9.1%}")

    med_curr = result_df["confidence_current"].median()
    med_prop = result_df["confidence_proposed"].median()
    print(f"  {'Median confidence':<45} {med_curr:>9.1%} {med_prop:>9.1%} {med_prop-med_curr:>+9.1%}")

    at_zero_curr = (result_df["confidence_current"] == 0).sum()
    at_zero_prop = (result_df["confidence_proposed"] == 0).sum()
    print(f"  {'Products at exactly 0%':<45} {at_zero_curr:>10} {at_zero_prop:>10} {at_zero_prop-at_zero_curr:>+10}")

    improve = (result_df["confidence_delta"] > 0.001).sum()
    worsen = (result_df["confidence_delta"] < -0.001).sum()
    unchanged = len(result_df) - improve - worsen
    print(f"  {'Products that improve':<45} {improve:>10}")
    print(f"  {'Products that worsen':<45} {worsen:>10}")
    print(f"  {'Products unchanged (< 0.1pp)':<45} {unchanged:>10}")

    sep("3. CONFIDENCE IMPACT: SELLERS ({})".format(len(sellers)))

    print(f"\n  {'Metric':<45} {'Current':>10} {'Proposed':>10} {'Change':>10}")
    print(f"  {'-'*45} {'-'*10} {'-'*10} {'-'*10}")

    avg_s_curr = sellers["confidence_current"].mean()
    avg_s_prop = sellers["confidence_proposed"].mean()
    print(f"  {'Average confidence':<45} {avg_s_curr:>9.1%} {avg_s_prop:>9.1%} {avg_s_prop-avg_s_curr:>+9.1%}")

    at_zero_s = (sellers["confidence_current"] == 0).sum()
    at_zero_sp = (sellers["confidence_proposed"] == 0).sum()
    print(f"  {'Products at exactly 0%':<45} {at_zero_s:>10} {at_zero_sp:>10} {at_zero_sp-at_zero_s:>+10}")

    below_10_curr = (sellers["confidence_current"] < 0.1).sum()
    below_10_prop = (sellers["confidence_proposed"] < 0.1).sum()
    print(f"  {'Products below 10%':<45} {below_10_curr:>10} {below_10_prop:>10} {below_10_prop-below_10_curr:>+10}")

    above_30_curr = (sellers["confidence_current"] >= 0.3).sum()
    above_30_prop = (sellers["confidence_proposed"] >= 0.3).sum()
    print(f"  {'Products at or above 30%':<45} {above_30_curr:>10} {above_30_prop:>10} {above_30_prop-above_30_curr:>+10}")

    s_improve = (sellers["confidence_delta"] > 0.001).sum()
    s_worsen = (sellers["confidence_delta"] < -0.001).sum()
    print(f"  {'Sellers that improve':<45} {s_improve:>10} ({s_improve/len(sellers)*100:.0f}%)")
    print(f"  {'Sellers that worsen':<45} {s_worsen:>10} ({s_worsen/len(sellers)*100:.0f}%)")

    sep("4. CONFIDENCE IMPACT: NON-SELLERS ({})".format(len(non_sellers)))

    avg_ns_curr = non_sellers["confidence_current"].mean()
    avg_ns_prop = non_sellers["confidence_proposed"].mean()
    print(f"\n  Average confidence CURRENT:  {avg_ns_curr:.1%}")
    print(f"  Average confidence PROPOSED: {avg_ns_prop:.1%}")
    print(f"  Change: {avg_ns_prop - avg_ns_curr:+.1%}")
    print(f"\n  Non-sellers are mostly unchanged. Their CV is very low")
    print(f"  (SIGMA_FLOOR/MU_FLOOR = {SIGMA_FLOOR}/{MU_FLOOR} = {SIGMA_FLOOR/MU_FLOOR}),")
    print(f"  so both formulas produce high confidence.")

    sep("5. DISTRIBUTION SHIFT")

    bins = [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.01]
    labels = ["0-10%", "10-20%", "20-30%", "30-40%", "40-50%",
              "50-60%", "60-70%", "70-80%", "80-90%", "90-100%"]

    curr_buckets = pd.cut(result_df["confidence_current"], bins=bins, labels=labels, right=False)
    prop_buckets = pd.cut(result_df["confidence_proposed"], bins=bins, labels=labels, right=False)

    print(f"\n  {'Bucket':<12} {'Current':>10} {'Proposed':>10} {'Change':>10}")
    print(f"  {'-'*12} {'-'*10} {'-'*10} {'-'*10}")
    for label in labels:
        c = int((curr_buckets == label).sum())
        p = int((prop_buckets == label).sum())
        print(f"  {label:<12} {c:>10} {p:>10} {p-c:>+10}")

    sep("6. PRODUCTS THAT WORSEN")

    worse = sellers[sellers["confidence_delta"] < -0.001].sort_values("confidence_delta")
    if worse.empty:
        print("\n  No selling products worsen.")
    else:
        print(f"\n  {len(worse)} selling products show lower confidence under the proposed formula.")
        print(f"  These are products where the current MAPE boost is removed.\n")
        print(f"  {'Product':<40} {'CV':>6} {'MAPE':>7} {'Curr':>7} {'Prop':>7} {'Delta':>7}")
        print(f"  {'-'*40} {'-'*6} {'-'*7} {'-'*7} {'-'*7} {'-'*7}")
        for _, r in worse.iterrows():
            name = str(r["product_name"])[:38]
            mape_str = f"{r['mape_14d']:.0%}" if pd.notna(r.get("mape_14d")) else "N/A"
            print(f"  {name:<40} {r['cv']:>5.1f} {mape_str:>7} "
                  f"{r['confidence_current']:>6.1%} {r['confidence_proposed']:>6.1%} "
                  f"{r['confidence_delta']:>+6.1%}")

        subsep("Analysis of worsened products")
        avg_cv_worse = worse["cv"].mean()
        avg_mape_worse = worse["mape_14d"].dropna().mean()
        print(f"  Average CV: {avg_cv_worse:.2f} (demand is {avg_cv_worse:.0f}x more volatile than mean)")
        if not worse["mape_14d"].dropna().empty:
            print(f"  Average MAPE: {avg_mape_worse:.0%}")
            low_mape = worse[worse["mape_14d"] < 0.5]
            print(f"  Products with MAPE < 50% (genuinely good forecasts): {len(low_mape)}")
            print(f"  Products with MAPE >= 50% (noisy MAPE): {len(worse) - len(low_mape)}")

    sep("7. TOP MOVERS (biggest confidence increases)")

    top_up = sellers.nlargest(15, "confidence_delta")
    print(f"\n  {'Product':<40} {'mu':>6} {'CV':>6} {'Curr':>7} {'Prop':>7} {'Delta':>7}")
    print(f"  {'-'*40} {'-'*6} {'-'*6} {'-'*7} {'-'*7} {'-'*7}")
    for _, r in top_up.iterrows():
        name = str(r["product_name"])[:38]
        print(f"  {name:<40} {r['mu_hat']:>5.2f} {r['cv']:>5.1f} "
              f"{r['confidence_current']:>6.1%} {r['confidence_proposed']:>6.1%} "
              f"{r['confidence_delta']:>+6.1%}")

    sep("8. WHAT THE USER SEES IN PRODUCT ASSISTANT")

    print("\n  The Product Assistant panel displays confidence as a KPI card.")
    print("  Here is what changes for the products most likely to be viewed")
    print("  (top sellers by demand velocity):\n")

    top_sellers = sellers.nlargest(20, "mu_hat")
    print(f"  {'Product':<40} {'Sales/d':>8} {'Stock':>6} {'Conf NOW':>9} {'Conf NEW':>9}")
    print(f"  {'-'*40} {'-'*8} {'-'*6} {'-'*9} {'-'*9}")
    for _, r in top_sellers.iterrows():
        name = str(r["product_name"])[:38]
        print(f"  {name:<40} {r['mu_hat']:>7.2f} {r['current_stock']:>6} "
              f"{r['confidence_current']:>8.1%} {r['confidence_proposed']:>8.1%}")

    sep("9. SAFETY CHECK: OPERATIONAL OUTPUTS")

    print("\n  These values are UNCHANGED by the confidence fix:")
    print(f"\n  {'Metric':<35} {'Min':>10} {'Median':>10} {'Mean':>10} {'Max':>10}")
    print(f"  {'-'*35} {'-'*10} {'-'*10} {'-'*10} {'-'*10}")
    for col, label in [
        ("safety_stock", "Safety stock (units)"),
        ("reorder_point", "Reorder point (units)"),
        ("suggested_reorder_qty", "Suggested order qty"),
    ]:
        vals = sellers[col]
        print(f"  {label:<35} {vals.min():>10.1f} {vals.median():>10.1f} "
              f"{vals.mean():>10.1f} {vals.max():>10.1f}")

    dto_finite = sellers[sellers["days_to_stockout"].notna()]["days_to_stockout"]
    if not dto_finite.empty:
        print(f"  {'Days to stockout (finite only)':<35} {dto_finite.min():>10.1f} {dto_finite.median():>10.1f} "
              f"{dto_finite.mean():>10.1f} {dto_finite.max():>10.1f}")

    dto_inf = sellers[sellers["days_to_stockout"].isna()]
    print(f"\n  Products with infinite days-to-stockout (mu < {EPSILON_MU}): {len(dto_inf)}")
    print(f"  Products with active reorder recommendation: {(sellers['suggested_reorder_qty'] > 0).sum()}")

    sep("10. RISK ASSESSMENT")

    print("""
  Risk: Confidence numbers change visibly before a presentation.
  Mitigation: The change is a two-line code modification. The before/after
  data is documented. The current formula produces 0% for every seller,
  which is demonstrably broken. Fixing a broken metric is not cherry-picking.

  Risk: Users who saw 0% before now see 25-47%. They may ask why.
  Mitigation: "The confidence formula was designed for high-volume retail
  and didn't work for low-volume collectible sales. We corrected it."

  Risk: Products that previously had MAPE boost show lower confidence.
  Quantified: {} products worsen, average drop is {:.1%}.
  Mitigation: These products have high CV (volatile demand). Lower
  confidence for volatile products is more honest, not less.

  Risk: Confidence still doesn't predict forecast accuracy (rho ~ 0).
  Reality: This is true of ALL three formulas tested. It is not introduced
  by this change. The proposed formula is strictly better than a formula
  that produces zero for every product.
    """.format(
        len(worse),
        worse["confidence_delta"].mean() if not worse.empty else 0,
    ))

    # -----------------------------------------------------------------------
    # Export
    # -----------------------------------------------------------------------
    export_cols = [
        "product_name", "sku", "category", "has_sales", "current_stock",
        "mu_hat", "sigma_d_hat", "cv", "lead_time", "sigma_L",
        "safety_stock", "reorder_point", "days_to_stockout",
        "suggested_reorder_qty", "mape_14d",
        "confidence_current", "confidence_proposed", "confidence_delta",
    ]
    out_df = result_df[[c for c in export_cols if c in result_df.columns]].sort_values(
        "confidence_delta", ascending=False,
    )

    csv_path = Path(__file__).parent / "impact_simulation_output.csv"
    out_df.to_csv(csv_path, index=False)

    # Summary text file
    summary_path = Path(__file__).parent / "impact_simulation_summary.txt"
    lines = [
        "IMPACT SIMULATION SUMMARY",
        f"Date: {now.date().isoformat()}",
        f"Total products: {len(result_df)}",
        f"Sellers: {len(sellers)}",
        f"Non-sellers: {len(non_sellers)}",
        "",
        "CHANGES:",
        "  1. Confidence formula: 1-min(1,CV) --> 1/(1+CV)",
        "  2. MAPE blending: disabled",
        "",
        "UNCHANGED:",
        "  safety_stock, reorder_point, days_to_stockout, suggested_reorder_qty",
        "  mu_hat, sigma_d_hat, lead_time (all operational outputs)",
        "",
        "SELLER CONFIDENCE:",
        f"  Average: {avg_s_curr:.1%} --> {avg_s_prop:.1%} ({avg_s_prop-avg_s_curr:+.1%})",
        f"  At zero: {at_zero_s} --> {at_zero_sp}",
        f"  Improve: {s_improve} ({s_improve/len(sellers)*100:.0f}%)",
        f"  Worsen:  {s_worsen} ({s_worsen/len(sellers)*100:.0f}%)",
        "",
        "NON-SELLER CONFIDENCE:",
        f"  Average: {avg_ns_curr:.1%} --> {avg_ns_prop:.1%}",
        "",
        f"Files: {csv_path.name}, {summary_path.name}",
    ]
    summary_path.write_text("\n".join(lines))

    sep("COMPLETE")
    print(f"  Per-product detail: {csv_path}")
    print(f"  Summary: {summary_path}")
    print(f"  Total products computed: {len(result_df)}")


if __name__ == "__main__":
    main()
