"""
Forecasting Service Investigation Suite

Runs 8 analytical tests against production data to identify gaps in the
forecasting pipeline and validate/invalidate assumptions about mu_hat,
sigma_d_hat, lead time, confidence, and MAPE.

Read-only -- nothing is written to the database.

Usage:
    source .env && python scripts/forecasting_investigation.py

Output: scripts/forecasting_investigation_output.csv (per-product detail)
        stdout: structured test results
"""

from __future__ import annotations

import math
import os
import sys
import uuid as _uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np
import pandas as pd
from statistics import NormalDist
from sqlalchemy import create_engine, text
from urllib.parse import quote, urlparse, urlunparse


# ---------------------------------------------------------------------------
# Constants (match forecasting-service/src/config.py defaults)
# ---------------------------------------------------------------------------
MU_FLOOR = 0.1
SIGMA_FLOOR = 0.01
MAPE_EPSILON = 0.1
ROLLING_WINDOW = 14
SERVICE_LEVEL = 0.95
TARGET_DAYS = 21
EPSILON_MU = 0.1
LEAD_TIME_DEFAULT = 14
LEAD_TIME_STD_DEFAULT = 0.0
LEAD_TIME_MIN_SHIPMENTS = 2

Z_SCORE = float(NormalDist().inv_cdf(SERVICE_LEVEL))


# ---------------------------------------------------------------------------
# Database connection (same pattern as backtest_comparison_v2.py)
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
# Data loading
# ---------------------------------------------------------------------------

def load_products(engine) -> pd.DataFrame:
    query = """
        SELECT id::text AS item_id, name, sku, quantity AS current_stock,
               lead_time_days
        FROM products WHERE is_active = true ORDER BY name
    """
    with engine.connect() as conn:
        return pd.read_sql(text(query), conn)


def load_movements(engine, lookback_days: int) -> pd.DataFrame:
    end_ts = datetime.now(timezone.utc)
    start_ts = end_ts - timedelta(days=lookback_days)
    query = """
        SELECT id::text AS event_id, item_id::text AS item_id,
               quantity_change, LOWER(reason) AS reason, at
        FROM stock_movements
        WHERE at >= :start_ts AND at <= :end_ts ORDER BY at ASC
    """
    with engine.connect() as conn:
        return pd.read_sql(
            text(query), conn,
            params={"start_ts": start_ts, "end_ts": end_ts},
        )


def load_shipments(engine) -> pd.DataFrame:
    query = """
        SELECT si.item_id::text AS item_id,
               s.order_date,
               s.actual_delivery_date,
               s.status,
               (s.actual_delivery_date::date - s.order_date::date)::int AS lead_time_days
        FROM shipment_items si
        JOIN shipments s ON si.shipment_id = s.id
        WHERE s.status = 'DELIVERED'
          AND s.actual_delivery_date IS NOT NULL
          AND s.order_date IS NOT NULL
          AND s.actual_delivery_date > s.order_date
        ORDER BY s.actual_delivery_date DESC
    """
    with engine.connect() as conn:
        return pd.read_sql(text(query), conn)


def load_historical_forecasts(engine, item_ids, target_date) -> pd.DataFrame:
    target_start = target_date - timedelta(hours=24)
    target_end = target_date + timedelta(hours=24)
    query = """
        SELECT DISTINCT ON (item_id)
            item_id::text AS item_id, computed_at,
            (features->>'mu_hat')::float AS mu_hat,
            (features->>'sigma_d_hat')::float AS sigma_d_hat
        FROM forecast_predictions
        WHERE item_id = ANY(:item_ids)
          AND computed_at BETWEEN :target_start AND :target_end
        ORDER BY item_id, ABS(EXTRACT(EPOCH FROM (computed_at - :target_date)))
    """
    params = {
        "item_ids": [_uuid.UUID(iid) for iid in item_ids],
        "target_start": target_start,
        "target_end": target_end,
        "target_date": target_date,
    }
    with engine.connect() as conn:
        return pd.read_sql(text(query), conn, params=params)


def load_latest_forecasts(engine) -> pd.DataFrame:
    query = """
        SELECT DISTINCT ON (item_id)
            item_id::text AS item_id,
            computed_at,
            confidence,
            (features->>'mu_hat')::float AS mu_hat,
            (features->>'sigma_d_hat')::float AS sigma_d_hat,
            (features->>'safety_stock')::float AS safety_stock,
            (features->>'reorder_point')::float AS reorder_point,
            (features->>'lead_time_days')::float AS forecast_lead_time,
            (features->>'sigma_L')::float AS sigma_L,
            (features->>'lead_time_source')::text AS lead_time_source,
            (features->>'mape')::float AS mape,
            days_to_stockout,
            suggested_reorder_qty
        FROM forecast_predictions
        ORDER BY item_id, computed_at DESC
    """
    with engine.connect() as conn:
        return pd.read_sql(text(query), conn)


# ---------------------------------------------------------------------------
# Feature helpers
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
    dow_multipliers = (dow_means / max(overall_mean, MU_FLOOR)).to_dict()
    for d in range(7):
        if d not in dow_multipliers:
            dow_multipliers[d] = 1.0
    expected = g["dow"].map(dow_means).fillna(overall_mean)
    residuals = g["consumption"] - expected
    sigma_d0 = max(float(residuals.std(ddof=0)), SIGMA_FLOOR)
    sigma_d1 = max(float(residuals.std(ddof=1)), SIGMA_FLOOR)
    return overall_mean, sigma_d0, sigma_d1, dow_multipliers


# ---------------------------------------------------------------------------
# Formatting helpers
# ---------------------------------------------------------------------------

def sep(title: str):
    print(f"\n{'=' * 72}")
    print(f"  {title}")
    print(f"{'=' * 72}")


def subsep(title: str):
    print(f"\n  --- {title} ---")


# ---------------------------------------------------------------------------
# Test 3: Lead time coverage audit (EASIEST, RUN FIRST)
# ---------------------------------------------------------------------------

def test_3_lead_time_coverage(shipments_df, products_df, forecasts_df):
    sep("TEST 3: Lead Time Coverage Audit")
    print("  Question: How many products have meaningful lead time data?")

    total_products = len(products_df)
    items_with_shipments = shipments_df["item_id"].nunique() if not shipments_df.empty else 0

    # Count shipments per product
    if not shipments_df.empty:
        ship_counts = shipments_df.groupby("item_id").size().reset_index(name="shipment_count")
    else:
        ship_counts = pd.DataFrame(columns=["item_id", "shipment_count"])

    bucket_0 = total_products - items_with_shipments
    bucket_1 = len(ship_counts[ship_counts["shipment_count"] == 1]) if not ship_counts.empty else 0
    bucket_2 = len(ship_counts[ship_counts["shipment_count"] == 2]) if not ship_counts.empty else 0
    bucket_3plus = len(ship_counts[ship_counts["shipment_count"] >= 3]) if not ship_counts.empty else 0

    print(f"\n  Products with 0 delivered shipments:  {bucket_0:>4} ({bucket_0/total_products*100:.1f}%)")
    print(f"  Products with 1 delivered shipment:   {bucket_1:>4} ({bucket_1/total_products*100:.1f}%)")
    print(f"  Products with 2 delivered shipments:  {bucket_2:>4} ({bucket_2/total_products*100:.1f}%)")
    print(f"  Products with 3+ delivered shipments: {bucket_3plus:>4} ({bucket_3plus/total_products*100:.1f}%)")
    print(f"  Min shipments for sigma_L:            {LEAD_TIME_MIN_SHIPMENTS}")

    usable = bucket_2 + bucket_3plus
    print(f"\n  Products with usable sigma_L (>= {LEAD_TIME_MIN_SHIPMENTS} shipments): {usable} ({usable/total_products*100:.1f}%)")
    print(f"  Products using default sigma_L=0.0:   {total_products - usable} ({(total_products - usable)/total_products*100:.1f}%)")

    # Compare computed lead times vs product defaults
    if not ship_counts.empty and not shipments_df.empty:
        multi = ship_counts[ship_counts["shipment_count"] >= 2]
        if not multi.empty:
            subsep("Computed vs Default Lead Times (products with 2+ shipments)")
            lt_agg = shipments_df[shipments_df["item_id"].isin(multi["item_id"])].groupby("item_id").agg(
                avg_lt=("lead_time_days", "mean"),
                std_lt=("lead_time_days", lambda x: x.std(ddof=1) if len(x) > 1 else 0.0),
                count=("lead_time_days", "count"),
            ).reset_index()
            lt_merged = lt_agg.merge(
                products_df[["item_id", "lead_time_days", "name"]],
                on="item_id", how="left",
            )
            lt_merged["default_lt"] = lt_merged["lead_time_days"].fillna(LEAD_TIME_DEFAULT)
            lt_merged["diff"] = lt_merged["avg_lt"] - lt_merged["default_lt"]
            print(f"  Count: {len(lt_merged)}")
            print(f"  Avg computed lead time:  {lt_merged['avg_lt'].mean():.1f} days")
            print(f"  Avg default lead time:   {lt_merged['default_lt'].mean():.1f} days")
            print(f"  Avg sigma_L:             {lt_merged['std_lt'].mean():.1f} days")
            print(f"  Max sigma_L:             {lt_merged['std_lt'].max():.1f} days")
            print(f"  Avg diff (computed-default): {lt_merged['diff'].mean():+.1f} days")

            outliers = lt_merged[lt_merged["diff"].abs() > 5]
            if not outliers.empty:
                print(f"\n  Products where computed differs from default by > 5 days:")
                for _, r in outliers.iterrows():
                    print(f"    {r['name'][:40]:<42} computed={r['avg_lt']:.0f}d  default={r['default_lt']:.0f}d  sigma_L={r['std_lt']:.1f}d")

    # Check forecast lead_time_source distribution
    if not forecasts_df.empty and "lead_time_source" in forecasts_df.columns:
        subsep("Lead Time Source in Latest Forecasts")
        src_dist = forecasts_df["lead_time_source"].value_counts(dropna=False)
        for src, count in src_dist.items():
            label = src if src else "NULL/missing"
            print(f"  {label}: {count}")

    return {"usable_sigma_L": usable, "total": total_products, "using_default": total_products - usable}


# ---------------------------------------------------------------------------
# Test 6: Floor value impact analysis (EASY, HIGH IMPACT)
# ---------------------------------------------------------------------------

def test_6_floor_value_impact(products_df, estimates, forecasts_df):
    sep("TEST 6: Floor Value Impact Analysis")
    print("  Question: Do MU_FLOOR and SIGMA_FLOOR create misleading outputs for zero-demand products?")

    zero_sellers = []
    phantom_units = 0

    for _, prod in products_df.iterrows():
        iid = str(prod["item_id"])
        est = estimates.get(iid)
        if est is None:
            continue
        mu = est["mu_hat"]
        has_real_sales = est.get("has_sales", False)

        if has_real_sales:
            continue

        current_qty = float(prod.get("current_stock", 0) or 0)
        target_cycle = TARGET_DAYS * mu  # 21 * 0.1 = 2.1
        suggested_qty = max(0, math.ceil(target_cycle - current_qty))

        # Safety stock with default lead time
        ss = Z_SCORE * est["sigma_d0"] * math.sqrt(LEAD_TIME_DEFAULT)
        rop = mu * LEAD_TIME_DEFAULT + ss

        zero_sellers.append({
            "name": prod["name"],
            "current_stock": current_qty,
            "mu_hat": mu,
            "sigma_d_hat": est["sigma_d0"],
            "suggested_reorder": suggested_qty,
            "safety_stock": round(ss, 2),
            "reorder_point": round(rop, 2),
        })
        phantom_units += suggested_qty

    total_zero = len(zero_sellers)
    ordering_for_zero = sum(1 for z in zero_sellers if z["suggested_reorder"] > 0)

    print(f"\n  Zero-sales products analyzed:     {total_zero}")
    print(f"  Of those, system recommends ordering: {ordering_for_zero} ({ordering_for_zero/max(total_zero,1)*100:.1f}%)")
    print(f"  Total phantom reorder units:      {phantom_units}")
    print(f"\n  Breakdown of phantom demand source:")
    print(f"    MU_FLOOR = {MU_FLOOR} units/day")
    print(f"    TARGET_DAYS = {TARGET_DAYS} days")
    print(f"    target_cycle_stock = {TARGET_DAYS * MU_FLOOR:.1f} units per product")
    print(f"    Suggests ordering for any product with stock < {TARGET_DAYS * MU_FLOOR:.1f}")

    # Check how many zero-sellers have stock below the phantom threshold
    below_threshold = sum(1 for z in zero_sellers if z["current_stock"] < TARGET_DAYS * MU_FLOOR)
    print(f"\n  Zero-sellers with stock < {TARGET_DAYS * MU_FLOOR:.1f}: {below_threshold}")
    print(f"  Zero-sellers with stock = 0:       {sum(1 for z in zero_sellers if z['current_stock'] == 0)}")

    # Check actual forecast recommendations
    if not forecasts_df.empty:
        subsep("Actual Forecast Recommendations for Zero-Sellers")
        zero_ids = [z["name"] for z in zero_sellers[:5]]  # Just names for display
        fc_zero = forecasts_df[
            forecasts_df["item_id"].isin(
                products_df[~products_df["item_id"].isin(
                    [k for k, v in estimates.items() if v.get("has_sales", False)]
                )]["item_id"]
            )
        ]
        if not fc_zero.empty:
            recommending = fc_zero[fc_zero["suggested_reorder_qty"] > 0]
            print(f"  Zero-sellers with active reorder recommendation: {len(recommending)} / {len(fc_zero)}")
            if not recommending.empty:
                print(f"  Total units recommended: {recommending['suggested_reorder_qty'].sum():.0f}")

    return {"phantom_units": phantom_units, "ordering_for_zero": ordering_for_zero}


# ---------------------------------------------------------------------------
# Test 2: sigma_d_hat ddof sensitivity
# ---------------------------------------------------------------------------

def test_2_ddof_sensitivity(products_df, estimates):
    sep("TEST 2: sigma_d_hat ddof Sensitivity (ddof=0 vs ddof=1)")
    print("  Question: Does the ddof choice materially change safety stock?")

    rows = []
    for iid, est in estimates.items():
        if not est.get("has_sales", False):
            continue
        mu = est["mu_hat"]
        s0 = est["sigma_d0"]
        s1 = est["sigma_d1"]

        ss0 = Z_SCORE * s0 * math.sqrt(LEAD_TIME_DEFAULT)
        ss1 = Z_SCORE * s1 * math.sqrt(LEAD_TIME_DEFAULT)

        rows.append({
            "item_id": iid,
            "mu_hat": mu,
            "sigma_ddof0": s0,
            "sigma_ddof1": s1,
            "sigma_pct_diff": (s1 - s0) / max(s0, 0.001) * 100,
            "ss_ddof0": ss0,
            "ss_ddof1": ss1,
            "ss_diff": ss1 - ss0,
            "ss_pct_diff": (ss1 - ss0) / max(ss0, 0.001) * 100,
        })

    if not rows:
        print("  No selling products to analyze.")
        return {}

    df = pd.DataFrame(rows)

    print(f"\n  Selling products analyzed: {len(df)}")
    print(f"\n  sigma_d_hat comparison:")
    print(f"    Median sigma (ddof=0): {df['sigma_ddof0'].median():.4f}")
    print(f"    Median sigma (ddof=1): {df['sigma_ddof1'].median():.4f}")
    print(f"    Median % increase:     {df['sigma_pct_diff'].median():.2f}%")
    print(f"    Max % increase:        {df['sigma_pct_diff'].max():.2f}%")

    print(f"\n  Safety stock comparison:")
    print(f"    Median SS (ddof=0):    {df['ss_ddof0'].median():.2f} units")
    print(f"    Median SS (ddof=1):    {df['ss_ddof1'].median():.2f} units")
    print(f"    Median SS diff:        {df['ss_diff'].median():.2f} units")
    print(f"    Median SS % change:    {df['ss_pct_diff'].median():.2f}%")

    material = df[df["ss_pct_diff"].abs() > 5]
    print(f"\n  Products where SS changes by > 5%: {len(material)} / {len(df)}")

    verdict = "IMMATERIAL" if df["ss_pct_diff"].median() < 5 else "MATERIAL"
    print(f"\n  Verdict: ddof choice is {verdict} at current data volumes")
    print(f"  (Threshold: 5% median SS change)")

    return {"median_ss_pct_diff": df["ss_pct_diff"].median(), "verdict": verdict}


# ---------------------------------------------------------------------------
# Test 1: DOW multiplier impact on ROP accuracy
# ---------------------------------------------------------------------------

def test_1_dow_multiplier_impact(estimates):
    sep("TEST 1: DOW Multiplier Impact on ROP Accuracy")
    print("  Question: Does ignoring DOW patterns in ROP lead to systematic over/under-ordering?")

    rows = []
    for iid, est in estimates.items():
        if not est.get("has_sales", False):
            continue
        mults = est.get("dow_multipliers", {})
        if not mults:
            continue

        mu = est["mu_hat"]
        mult_values = list(mults.values())
        mult_range = max(mult_values) - min(mult_values)
        mult_max = max(mult_values)
        mult_min = min(mult_values)
        mult_ratio = mult_max / max(mult_min, 0.01)

        # Simulate lead-time demand with flat vs DOW-weighted
        # Lead time = 14 days, starting from each DOW
        flat_demand_14d = mu * LEAD_TIME_DEFAULT

        # DOW-weighted: sum mu * multiplier[d] for each day in a 14-day window
        dow_demands = []
        for start_dow in range(7):
            total = 0.0
            for day_offset in range(LEAD_TIME_DEFAULT):
                dow = (start_dow + day_offset) % 7
                total += mu * mults.get(dow, 1.0)
            dow_demands.append(total)

        dow_mean = np.mean(dow_demands)
        dow_max = max(dow_demands)
        dow_min = min(dow_demands)

        pct_err = abs(dow_mean - flat_demand_14d) / max(flat_demand_14d, 0.01) * 100
        worst_pct = abs(dow_max - flat_demand_14d) / max(flat_demand_14d, 0.01) * 100

        rows.append({
            "item_id": iid,
            "mu_hat": mu,
            "mult_range": round(mult_range, 3),
            "mult_ratio": round(mult_ratio, 2),
            "flat_demand_14d": round(flat_demand_14d, 2),
            "dow_mean_14d": round(dow_mean, 2),
            "dow_max_14d": round(dow_max, 2),
            "dow_min_14d": round(dow_min, 2),
            "mean_pct_err": round(pct_err, 2),
            "worst_case_pct_err": round(worst_pct, 2),
        })

    if not rows:
        print("  No selling products with DOW multipliers to analyze.")
        return {}

    df = pd.DataFrame(rows)

    # Products with strong DOW patterns (multiplier range > 2x)
    strong_dow = df[df["mult_ratio"] > 2.0]

    print(f"\n  Products analyzed: {len(df)}")
    print(f"  Products with strong DOW pattern (max/min ratio > 2x): {len(strong_dow)}")

    print(f"\n  All sellers:")
    print(f"    Median multiplier range:     {df['mult_range'].median():.3f}")
    print(f"    Median max/min ratio:        {df['mult_ratio'].median():.2f}x")
    print(f"    Median mean % error:         {df['mean_pct_err'].median():.2f}%")
    print(f"    Median worst-case % error:   {df['worst_case_pct_err'].median():.2f}%")

    if not strong_dow.empty:
        print(f"\n  Strong DOW pattern products ({len(strong_dow)}):")
        print(f"    Median mean % error:         {strong_dow['mean_pct_err'].median():.2f}%")
        print(f"    Median worst-case % error:   {strong_dow['worst_case_pct_err'].median():.2f}%")
        print(f"    Max worst-case % error:      {strong_dow['worst_case_pct_err'].max():.2f}%")

    ss_threshold = 10.0
    affected = df[df["worst_case_pct_err"] > ss_threshold]
    print(f"\n  Products where worst-case DOW error exceeds {ss_threshold}% of lead-time demand: {len(affected)}")

    # Over a 14-day window the DOW effects average out, so the mean error should be near 0
    print(f"\n  Note: Over a {LEAD_TIME_DEFAULT}-day lead time (2 full weeks), DOW patterns")
    print(f"  partially cancel out. The mean error reflects this averaging effect.")

    return {"strong_dow_count": len(strong_dow), "median_worst_pct": df["worst_case_pct_err"].median()}


# ---------------------------------------------------------------------------
# Test 5: Confidence formula comparison
# ---------------------------------------------------------------------------

def test_5_confidence_comparison(estimates, mape_map):
    sep("TEST 5: Confidence Formula Comparison (Current vs Proposed vs Bayesian)")
    print("  Question: Which formula correlates best with actual forecast quality?")

    rows = []
    for iid, est in estimates.items():
        if not est.get("has_sales", False):
            continue
        mu = est["mu_hat"]
        sigma = est["sigma_d0"]
        cv = sigma / max(mu, 0.1)
        n_days = est.get("n_days", 28)
        mape_val = mape_map.get(iid)

        # Current: 1 - CV
        conf_current = max(0.0, 1.0 - min(1.0, cv))

        # Proposed: 1 / (1 + CV)
        conf_proposed = 1.0 / (1.0 + cv)

        # Bayesian (sample-size weighted): proposed * min(1, n/28)
        conf_bayesian = conf_proposed * min(1.0, n_days / 28.0)

        rows.append({
            "item_id": iid,
            "mu_hat": mu,
            "cv": cv,
            "n_days": n_days,
            "mape": mape_val,
            "conf_current": round(conf_current, 4),
            "conf_proposed": round(conf_proposed, 4),
            "conf_bayesian": round(conf_bayesian, 4),
        })

    df = pd.DataFrame(rows)

    print(f"\n  Products with sales: {len(df)}")
    print(f"  Products with MAPE data: {df['mape'].notna().sum()}")

    subsep("Confidence Score Distributions (all sellers)")
    for col, label in [
        ("conf_current", "Current (1-CV)"),
        ("conf_proposed", "Proposed 1/(1+CV)"),
        ("conf_bayesian", "Bayesian (size-weighted)"),
    ]:
        vals = df[col]
        print(f"  {label:30s}  mean={vals.mean():.3f}  median={vals.median():.3f}  "
              f"min={vals.min():.3f}  max={vals.max():.3f}  at_zero={int((vals == 0).sum())}")

    # Correlation with MAPE (only for products with MAPE data)
    with_mape = df[df["mape"].notna()].copy()
    if len(with_mape) >= 5:
        subsep(f"Correlation with MAPE ({len(with_mape)} products)")
        print("  (Negative correlation = higher confidence correlates with lower error = GOOD)")

        for col, label in [
            ("conf_current", "Current (1-CV)"),
            ("conf_proposed", "Proposed 1/(1+CV)"),
            ("conf_bayesian", "Bayesian (size-weighted)"),
        ]:
            if with_mape[col].std() < 1e-10:
                print(f"  {label:30s}  r = N/A (no variance in confidence scores)")
                continue
            corr = with_mape[col].corr(with_mape["mape"])
            # Spearman rank correlation (manual, no scipy)
            ranks_x = with_mape[col].rank()
            ranks_y = with_mape["mape"].rank()
            spearman = ranks_x.corr(ranks_y)
            print(f"  {label:30s}  pearson r={corr:+.3f}  spearman rho={spearman:+.3f}")

        # Which formula best ranks forecast quality?
        best_col = None
        best_rho = 0.0
        for col in ["conf_current", "conf_proposed", "conf_bayesian"]:
            if with_mape[col].std() < 1e-10:
                continue
            rho = abs(with_mape[col].rank().corr(with_mape["mape"].rank()))
            if rho > best_rho:
                best_rho = rho
                best_col = col
        if best_col:
            labels = {
                "conf_current": "Current (1-CV)",
                "conf_proposed": "Proposed 1/(1+CV)",
                "conf_bayesian": "Bayesian (size-weighted)",
            }
            print(f"\n  Strongest rank correlation: {labels[best_col]} (|rho| = {best_rho:.3f})")
    else:
        print(f"\n  Insufficient MAPE data for correlation analysis (need >= 5, have {len(with_mape)})")

    return df


# ---------------------------------------------------------------------------
# Test 4: MAPE vs alternative accuracy metrics
# ---------------------------------------------------------------------------

def test_4_mape_alternatives(daily_usage, mape_map, estimates):
    sep("TEST 4: MAPE vs Alternative Accuracy Metrics for Intermittent Demand")
    print("  Question: Is MAPE the right accuracy metric for this demand profile?")

    items_with_mape = [iid for iid in mape_map.keys() if iid in estimates and estimates[iid].get("has_sales")]
    if len(items_with_mape) < 3:
        print(f"  Insufficient products with MAPE data ({len(items_with_mape)}). Skipping.")
        return {}

    rows = []
    for iid in items_with_mape:
        est = estimates[iid]
        mu = est["mu_hat"]
        mape_val = mape_map[iid]

        # Get daily consumption for this item
        item_daily = daily_usage[daily_usage["item_id"] == iid].sort_values("date")
        if item_daily.empty:
            continue

        actuals = item_daily["consumption"].values
        n = len(actuals)

        # MAE: mean absolute error (forecast is mu for each day)
        mae = float(np.mean(np.abs(actuals - mu)))

        # RMSE: root mean squared error
        rmse = float(np.sqrt(np.mean((actuals - mu) ** 2)))

        # Scaled MAE: MAE / mean(|diff of actuals|) -- similar to MASE
        naive_errors = np.abs(np.diff(actuals))
        if naive_errors.mean() > 0:
            mase = mae / naive_errors.mean()
        else:
            mase = float("inf") if mae > 0 else 0.0

        # Percent of days where forecast is within 1 unit of actual
        within_1 = float(np.mean(np.abs(actuals - mu) <= 1.0)) * 100

        rows.append({
            "item_id": iid,
            "mu_hat": mu,
            "actual_mean": float(actuals.mean()),
            "mape": mape_val,
            "mae": round(mae, 4),
            "rmse": round(rmse, 4),
            "mase": round(mase, 4) if mase != float("inf") else None,
            "within_1_unit_pct": round(within_1, 1),
            "n_days": n,
        })

    df = pd.DataFrame(rows)

    print(f"\n  Products analyzed: {len(df)}")
    print(f"\n  Metric distributions:")
    for col, label, fmt in [
        ("mape", "MAPE", ".1%"),
        ("mae", "MAE (units)", ".2f"),
        ("rmse", "RMSE (units)", ".2f"),
        ("mase", "MASE", ".2f"),
        ("within_1_unit_pct", "Within 1 unit (%)", ".1f"),
    ]:
        vals = df[col].dropna()
        if vals.empty:
            continue
        print(f"    {label:22s}  mean={vals.mean():{fmt}}  median={vals.median():{fmt}}  "
              f"min={vals.min():{fmt}}  max={vals.max():{fmt}}")

    # Rank agreement analysis
    subsep("Rank Agreement Between Metrics")
    print("  (Do different metrics agree on which forecasts are 'good' vs 'bad'?)")

    rank_cols = ["mape", "mae", "rmse"]
    available = [c for c in rank_cols if c in df.columns and df[c].notna().sum() >= 3]
    if len(available) >= 2:
        for i, c1 in enumerate(available):
            for c2 in available[i + 1:]:
                valid = df[[c1, c2]].dropna()
                if len(valid) >= 3:
                    rho = valid[c1].rank().corr(valid[c2].rank())
                    print(f"    {c1} vs {c2}: spearman rho = {rho:.3f}")

    # Check if MAPE is misleading for low-demand products
    if "actual_mean" in df.columns:
        low_demand = df[df["actual_mean"] < 0.5]
        high_demand = df[df["actual_mean"] >= 0.5]
        if not low_demand.empty and not high_demand.empty:
            subsep("MAPE Behavior by Demand Level")
            print(f"  Low demand (< 0.5 units/day):  n={len(low_demand)}  avg MAPE={low_demand['mape'].mean():.1%}  avg MAE={low_demand['mae'].mean():.2f}")
            print(f"  High demand (>= 0.5 units/day): n={len(high_demand)}  avg MAPE={high_demand['mape'].mean():.1%}  avg MAE={high_demand['mae'].mean():.2f}")

    return {"n_analyzed": len(df)}


# ---------------------------------------------------------------------------
# Test 8: Outlier resilience
# ---------------------------------------------------------------------------

def test_8_outlier_resilience(daily_usage, estimates, products_df):
    sep("TEST 8: Outlier Resilience")
    print("  Question: How sensitive is mu_hat to single-day demand spikes?")

    # Top sellers by mu_hat
    sellers = [(iid, est) for iid, est in estimates.items() if est.get("has_sales")]
    sellers.sort(key=lambda x: x[1]["mu_hat"], reverse=True)
    top_n = min(10, len(sellers))
    top_sellers = sellers[:top_n]

    if not top_sellers:
        print("  No selling products to analyze.")
        return {}

    rows = []
    for iid, est in top_sellers:
        mu_original = est["mu_hat"]
        sigma_original = est["sigma_d0"]
        item_daily = daily_usage[daily_usage["item_id"] == iid].sort_values("date")
        if item_daily.empty:
            continue

        # Inject a 10x spike on the most recent day
        spiked = item_daily.copy()
        max_normal = float(spiked["consumption"].max())
        spike_value = max(10.0 * mu_original, max_normal * 3)
        spiked.iloc[-1, spiked.columns.get_loc("consumption")] = spike_value

        # Recompute mu_hat and sigma_d_hat with spiked data
        mu_spiked, sigma_spiked, _, _ = dow_weighted_estimate(spiked)

        mu_pct_change = (mu_spiked - mu_original) / max(mu_original, 0.01) * 100
        sigma_pct_change = (sigma_spiked - sigma_original) / max(sigma_original, 0.01) * 100

        # Compute ROP change
        rop_original = mu_original * LEAD_TIME_DEFAULT + Z_SCORE * sigma_original * math.sqrt(LEAD_TIME_DEFAULT)
        rop_spiked = mu_spiked * LEAD_TIME_DEFAULT + Z_SCORE * sigma_spiked * math.sqrt(LEAD_TIME_DEFAULT)
        rop_pct_change = (rop_spiked - rop_original) / max(rop_original, 0.01) * 100

        # How many days until spike washes out (rolling window = 14)
        n_days = len(item_daily)
        washout_days = min(ROLLING_WINDOW, n_days)

        name = ""
        for _, prod in products_df.iterrows():
            if str(prod["item_id"]) == iid:
                name = prod["name"]
                break

        rows.append({
            "name": name[:35],
            "mu_original": round(mu_original, 3),
            "mu_spiked": round(mu_spiked, 3),
            "mu_pct_change": round(mu_pct_change, 1),
            "sigma_pct_change": round(sigma_pct_change, 1),
            "rop_pct_change": round(rop_pct_change, 1),
            "spike_value": round(spike_value, 1),
            "washout_days": washout_days,
        })

    df = pd.DataFrame(rows)

    print(f"\n  Top {len(df)} sellers analyzed with 10x demand spike injection:")
    print(f"\n  {'Product':<37} {'mu_orig':>8} {'mu_spike':>9} {'mu_%chg':>8} {'ROP_%chg':>9} {'washout':>8}")
    print(f"  {'-'*37} {'-'*8} {'-'*9} {'-'*8} {'-'*9} {'-'*8}")
    for _, r in df.iterrows():
        print(f"  {r['name']:<37} {r['mu_original']:>8.3f} {r['mu_spiked']:>9.3f} "
              f"{r['mu_pct_change']:>+7.1f}% {r['rop_pct_change']:>+8.1f}% {r['washout_days']:>6}d")

    rop_above_20 = (df["rop_pct_change"].abs() > 20).sum()
    print(f"\n  Products where spike shifts ROP by > 20%: {rop_above_20} / {len(df)}")
    if rop_above_20 > 0:
        print("  Consider: Winsorization at 95th percentile or trimmed mean")
    else:
        print("  Single-day spikes are adequately dampened by the rolling window")

    return {"rop_above_20pct": rop_above_20}


# ---------------------------------------------------------------------------
# Test 7: Data age sensitivity
# ---------------------------------------------------------------------------

def test_7_data_age_sensitivity(daily_usage, estimates):
    sep("TEST 7: Data Age Sensitivity")
    print("  Question: How much does forecast quality improve as data accumulates?")

    sellers = [(iid, est) for iid, est in estimates.items() if est.get("has_sales")]
    if not sellers:
        print("  No selling products to analyze.")
        return {}

    # Simulate pipeline at weekly snapshots: day 7, 14, 21, 28
    snapshots = [7, 14, 21, 28]

    # Get the date range from daily usage
    all_dates = sorted(daily_usage["date"].unique())
    if len(all_dates) < 7:
        print(f"  Insufficient data ({len(all_dates)} days). Need at least 7.")
        return {}

    max_date = all_dates[-1]
    min_date = all_dates[0]
    total_days = (max_date - min_date).days + 1

    results = {}
    for snap_days in snapshots:
        if snap_days > total_days:
            continue
        cutoff = min_date + pd.Timedelta(days=snap_days)
        windowed = daily_usage[daily_usage["date"] <= cutoff]

        snap_estimates = {}
        for item_id, group in windowed.groupby("item_id"):
            iid = str(item_id)
            if iid not in dict(sellers):
                continue
            g = group.sort_values("date")
            if g["consumption"].sum() == 0:
                continue
            mu, s0, _, _ = dow_weighted_estimate(g)
            snap_estimates[iid] = mu

        results[snap_days] = snap_estimates

    # Compare stability: how much does mu_hat change between snapshots?
    print(f"\n  Data range: {min_date.date()} to {max_date.date()} ({total_days} days)")
    print(f"\n  Snapshot analysis:")
    print(f"  {'Window':>8} {'Products':>10} {'Avg mu_hat':>12} {'Median mu':>10}")
    print(f"  {'-'*8} {'-'*10} {'-'*12} {'-'*10}")

    for snap_days in sorted(results.keys()):
        ests = results[snap_days]
        if not ests:
            continue
        mus = list(ests.values())
        print(f"  {snap_days:>6}d {len(mus):>10} {np.mean(mus):>12.4f} {np.median(mus):>10.4f}")

    # Convergence: compare each snapshot's mu to the final (28-day) mu
    if 28 in results and len(results) > 1:
        final = results[max(results.keys())]
        subsep("Convergence toward final estimate")
        print(f"  {'Window':>8} {'Avg |diff|':>12} {'Avg %diff':>10} {'Products':>10}")
        for snap_days in sorted(results.keys()):
            if snap_days == max(results.keys()):
                continue
            snap = results[snap_days]
            common = set(snap.keys()) & set(final.keys())
            if not common:
                continue
            diffs = [abs(snap[k] - final[k]) for k in common]
            pct_diffs = [abs(snap[k] - final[k]) / max(final[k], 0.01) * 100 for k in common]
            print(f"  {snap_days:>6}d {np.mean(diffs):>12.4f} {np.mean(pct_diffs):>9.1f}% {len(common):>10}")

    return {"snapshots_computed": len(results)}


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    print("=" * 72)
    print("  FORECASTING SERVICE INVESTIGATION SUITE")
    print("  Read-only analysis against production data")
    print(f"  Run at: {datetime.now(timezone.utc).isoformat()}")
    print("=" * 72)

    engine = _build_engine()
    now = datetime.now(timezone.utc)

    # Load all data upfront
    print("\nLoading data...")
    products_df = load_products(engine)
    print(f"  Products: {len(products_df)}")

    movements_df = load_movements(engine, lookback_days=30)
    print(f"  Stock movements (30d): {len(movements_df)}")

    shipments_df = load_shipments(engine)
    print(f"  Delivered shipments: {len(shipments_df)}")

    forecasts_df = load_latest_forecasts(engine)
    print(f"  Latest forecasts: {len(forecasts_df)}")

    # Build daily usage and estimates
    daily_usage = build_daily_usage(movements_df) if not movements_df.empty else pd.DataFrame()

    estimates = {}
    if not daily_usage.empty:
        for item_id, group in daily_usage.groupby("item_id"):
            iid = str(item_id)
            g = group.sort_values("date")
            mu, s0, s1, mults = dow_weighted_estimate(g)
            has_sales = float(g["consumption"].sum()) > 0
            estimates[iid] = {
                "mu_hat": mu,
                "sigma_d0": s0,
                "sigma_d1": s1,
                "dow_multipliers": mults,
                "has_sales": has_sales,
                "n_days": len(g),
            }

    # Fill in products not in movements
    for _, prod in products_df.iterrows():
        iid = str(prod["item_id"])
        if iid not in estimates:
            estimates[iid] = {
                "mu_hat": MU_FLOOR,
                "sigma_d0": SIGMA_FLOOR,
                "sigma_d1": SIGMA_FLOOR,
                "dow_multipliers": {d: 1.0 for d in range(7)},
                "has_sales": False,
                "n_days": 0,
            }

    sellers = sum(1 for e in estimates.values() if e.get("has_sales"))
    print(f"  Products with sales: {sellers}")
    print(f"  Products without sales: {len(estimates) - sellers}")

    # Load MAPE data
    backtest_target = now - timedelta(days=14)
    item_ids = [str(p["item_id"]) for _, p in products_df.iterrows()]
    hist_fc = load_historical_forecasts(engine, item_ids, backtest_target)
    mape_map = _compute_mape_map(hist_fc, movements_df, backtest_target, now)
    print(f"  Products with MAPE data: {len(mape_map)}")

    # -----------------------------------------------------------------------
    # Run tests in order of impact/ease
    # -----------------------------------------------------------------------

    # TIER 1: Easiest, highest impact
    test_3_lead_time_coverage(shipments_df, products_df, forecasts_df)
    test_6_floor_value_impact(products_df, estimates, forecasts_df)

    # TIER 2: Medium complexity
    test_2_ddof_sensitivity(products_df, estimates)
    test_1_dow_multiplier_impact(estimates)

    # TIER 3: Requires MAPE data
    test_5_confidence_comparison(estimates, mape_map)
    test_4_mape_alternatives(daily_usage, mape_map, estimates)

    # TIER 4: Simulation-based
    test_8_outlier_resilience(daily_usage, estimates, products_df)
    test_7_data_age_sensitivity(daily_usage, estimates)

    # -----------------------------------------------------------------------
    # Export per-product detail
    # -----------------------------------------------------------------------
    export_rows = []
    for _, prod in products_df.iterrows():
        iid = str(prod["item_id"])
        est = estimates.get(iid, {})
        mu = est.get("mu_hat", MU_FLOOR)
        s0 = est.get("sigma_d0", SIGMA_FLOOR)
        s1 = est.get("sigma_d1", SIGMA_FLOOR)
        cv = s0 / max(mu, 0.1)
        mape_val = mape_map.get(iid)
        has_sales = est.get("has_sales", False)

        export_rows.append({
            "product_name": prod["name"],
            "sku": prod.get("sku", ""),
            "current_stock": prod.get("current_stock", ""),
            "has_sales": has_sales,
            "mu_hat": round(mu, 4),
            "sigma_d_ddof0": round(s0, 4),
            "sigma_d_ddof1": round(s1, 4),
            "cv_ratio": round(cv, 4),
            "mape_14d": round(mape_val, 4) if mape_val is not None else None,
            "conf_current": round(max(0, 1 - min(1, cv)), 4),
            "conf_proposed": round(1 / (1 + cv), 4),
            "conf_bayesian": round(1 / (1 + cv) * min(1, est.get("n_days", 0) / 28), 4),
            "n_data_days": est.get("n_days", 0),
        })

    export_df = pd.DataFrame(export_rows).sort_values("mu_hat", ascending=False)
    out_path = Path(__file__).parent / "forecasting_investigation_output.csv"
    export_df.to_csv(out_path, index=False)

    sep("COMPLETE")
    print(f"  Per-product detail exported to: {out_path}")
    print(f"  Total products: {len(export_df)}")


def _compute_mape_map(hist_fc, movements_df, backtest_target, now):
    mape_map = {}
    if hist_fc.empty or movements_df.empty:
        return mape_map

    backtest_start = pd.to_datetime(backtest_target, utc=True)
    backtest_end = pd.to_datetime(now, utc=True)
    window_mvts = movements_df[
        (movements_df["at"] >= backtest_start) & (movements_df["at"] <= backtest_end)
    ]
    if window_mvts.empty:
        return mape_map

    backtest_daily = build_daily_usage(window_mvts)
    if backtest_daily.empty:
        return mape_map

    # Inline MAPE computation
    usage = backtest_daily.copy()
    usage["item_id"] = usage["item_id"].astype(str)
    actual_agg = usage.groupby("item_id", as_index=False).agg(
        actual_mu=("consumption", "mean"),
        backtest_days=("date", "nunique"),
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


if __name__ == "__main__":
    main()
