"""
Backtest comparison v2: Simulates the proposed confidence formula fix.

Compares current formula vs proposed for all products, with and without MAPE.
Read-only — nothing written to the database.

Usage:
    SUPABASE_DB_URL=... SUPABASE_DB_USERNAME=... SUPABASE_DB_PASSWORD=... \
    python scripts/backtest_comparison_v2.py

Output: scripts/backtest_comparison_v2_output.csv
"""

from __future__ import annotations

import os
import sys
import uuid as _uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np
import pandas as pd
from sqlalchemy import create_engine, text
from urllib.parse import quote, urlparse, urlunparse


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
MU_FLOOR = 0.1
SIGMA_FLOOR = 0.01
MAPE_EPSILON = 0.1


# ---------------------------------------------------------------------------
# Database connection
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
    return create_engine(urlunparse((scheme, netloc, parsed.path, "", "", "")), pool_pre_ping=True)


# ---------------------------------------------------------------------------
# Data loading
# ---------------------------------------------------------------------------

def load_products(engine) -> pd.DataFrame:
    query = """
        SELECT id::text AS item_id, name, sku, quantity AS current_stock
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
        return pd.read_sql(text(query), conn, params={"start_ts": start_ts, "end_ts": end_ts})


def load_historical_forecasts(engine, item_ids: list[str], target_date: datetime) -> pd.DataFrame:
    target_start = target_date - timedelta(hours=24)
    target_end = target_date + timedelta(hours=24)
    query = """
        SELECT DISTINCT ON (item_id)
            item_id::text AS item_id, computed_at,
            (features->>'mu_hat')::float AS mu_hat
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


# ---------------------------------------------------------------------------
# Feature building
# ---------------------------------------------------------------------------

def build_daily_usage(events_df: pd.DataFrame) -> pd.DataFrame:
    if events_df.empty:
        return pd.DataFrame(columns=["date", "item_id", "consumption"])

    df = events_df.copy()
    df.loc[:, "at"] = pd.to_datetime(df["at"], utc=True)
    df.loc[:, "date"] = df["at"].dt.floor("D").dt.tz_localize(None)

    reason = df["reason"].astype("string").str.lower().str.strip()
    is_sale = reason.isin({"sale", "sales"}) & (df["quantity_change"] < 0)
    sales = df.loc[is_sale, ["item_id", "date", "quantity_change"]].copy()

    all_items = df["item_id"].astype(str).unique()
    all_dates = pd.date_range(df["date"].min(), df["date"].max(), freq="D")

    if sales.empty:
        out = pd.MultiIndex.from_product(
            [all_items, all_dates], names=["item_id", "date"]
        ).to_frame(index=False)
        out["consumption"] = 0.0
        return out.sort_values(["item_id", "date"]).reset_index(drop=True)

    sales.loc[:, "consumption"] = (-sales["quantity_change"]).astype(float)
    daily = sales.groupby(["item_id", "date"], as_index=False)["consumption"].sum()
    full_idx = pd.MultiIndex.from_product([all_items, all_dates], names=["item_id", "date"])
    daily = daily.set_index(["item_id", "date"]).reindex(full_idx)
    daily["consumption"] = daily["consumption"].fillna(0.0)
    return daily.reset_index().sort_values(["item_id", "date"]).reset_index(drop=True)


def compute_mape(historical_fc_df, actual_daily_df, epsilon=MAPE_EPSILON):
    if historical_fc_df.empty or actual_daily_df.empty:
        return pd.DataFrame(columns=["item_id", "mape", "forecast_mu", "actual_mu", "backtest_days"])

    usage = actual_daily_df.copy()
    usage["item_id"] = usage["item_id"].astype(str)
    actual_agg = usage.groupby("item_id", as_index=False).agg(
        actual_mu=("consumption", "mean"), backtest_days=("date", "nunique"),
    )
    fc = historical_fc_df[["item_id", "mu_hat"]].copy()
    fc["item_id"] = fc["item_id"].astype(str)
    fc = fc.rename(columns={"mu_hat": "forecast_mu"})
    merged = fc.merge(actual_agg, on="item_id", how="inner")
    if merged.empty:
        return pd.DataFrame(columns=["item_id", "mape", "forecast_mu", "actual_mu", "backtest_days"])
    merged["mape"] = (
        np.abs(merged["actual_mu"] - merged["forecast_mu"])
        / np.maximum(merged["actual_mu"], epsilon)
    )
    return merged[["item_id", "mape", "forecast_mu", "actual_mu", "backtest_days"]].reset_index(drop=True)


def dow_weighted_estimate(group):
    g = group.copy()
    g["dow"] = pd.to_datetime(g["date"]).dt.dayofweek
    overall_mean = max(float(g["consumption"].mean()), MU_FLOOR)
    dow_means = g.groupby("dow")["consumption"].mean()
    expected = g["dow"].map(dow_means).fillna(overall_mean)
    residuals = g["consumption"] - expected
    sigma_d_hat = max(float(residuals.std(ddof=0)), SIGMA_FLOOR)
    return overall_mean, sigma_d_hat


# ---------------------------------------------------------------------------
# Confidence formulas
# ---------------------------------------------------------------------------

def confidence_current(mu, sigma, mape_val=None):
    """Current formula: linear 1 - sigma/mu, with 0.4/0.6 MAPE blend."""
    variability = 1.0 - min(1.0, sigma / max(mu, 0.1))
    if mape_val is not None and not np.isnan(mape_val):
        mape_score = max(0.0, 1.0 - mape_val)
        return round(0.4 * variability + 0.6 * mape_score, 3)
    return round(variability, 3)


def confidence_proposed(mu, sigma, mape_val=None):
    """Proposed: sigmoid-like 1/(1+CV), MAPE disabled (variability only)."""
    cv = sigma / max(mu, 0.1)
    variability = 1.0 / (1.0 + cv)
    # Option 3: no MAPE blend for a young system
    return round(variability, 3)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    engine = _build_engine()
    now = datetime.now(timezone.utc)

    print("Loading products...")
    products_df = load_products(engine)
    if products_df.empty:
        print("No active products found.")
        return

    item_ids = products_df["item_id"].tolist()
    print(f"Found {len(item_ids)} active products")

    print("Loading stock movements (last 30 days)...")
    movements_df = load_movements(engine, lookback_days=30)
    print(f"Loaded {len(movements_df)} movements")

    daily_usage = build_daily_usage(movements_df) if not movements_df.empty else pd.DataFrame()

    # Demand estimates
    estimates = {}
    if not daily_usage.empty:
        for item_id, group in daily_usage.groupby("item_id"):
            mu, sigma = dow_weighted_estimate(group.sort_values("date"))
            estimates[str(item_id)] = {"mu_hat": mu, "sigma_d_hat": sigma}

    # Load MAPE at 14-day horizon (current prod setting)
    backtest_target = now - timedelta(days=14)
    hist_fc = load_historical_forecasts(engine, item_ids, backtest_target)
    mape_map = {}
    if not hist_fc.empty and not movements_df.empty:
        backtest_start = pd.to_datetime(backtest_target, utc=True)
        backtest_end = pd.to_datetime(now, utc=True)
        window_mvts = movements_df[
            (movements_df["at"] >= backtest_start) & (movements_df["at"] <= backtest_end)
        ]
        if not window_mvts.empty:
            backtest_daily = build_daily_usage(window_mvts)
            mape_df = compute_mape(hist_fc, backtest_daily)
            for _, r in mape_df.iterrows():
                mape_map[str(r["item_id"])] = float(r["mape"])

    # Build comparison
    rows = []
    for _, prod in products_df.iterrows():
        iid = str(prod["item_id"])
        est = estimates.get(iid, {"mu_hat": MU_FLOOR, "sigma_d_hat": SIGMA_FLOOR})
        mu = est["mu_hat"]
        sigma = est["sigma_d_hat"]
        mape_val = mape_map.get(iid)
        has_sales = mu > MU_FLOOR

        curr = confidence_current(mu, sigma, mape_val)
        prop = confidence_proposed(mu, sigma, mape_val)

        cv = sigma / max(mu, 0.1)
        var_current = 1.0 - min(1.0, cv)
        var_proposed = 1.0 / (1.0 + cv)

        rows.append({
            "product_name": prod["name"],
            "sku": prod.get("sku", ""),
            "current_stock": prod.get("current_stock", ""),
            "has_sales": has_sales,
            "mu_hat": round(mu, 4),
            "sigma_d_hat": round(sigma, 4),
            "cv_ratio": round(cv, 4),
            "mape_14d": round(mape_val, 4) if mape_val is not None else None,
            "variability_current": round(var_current, 3),
            "variability_proposed": round(var_proposed, 3),
            "confidence_current": curr,
            "confidence_current_pct": f"{curr * 100:.1f}%",
            "confidence_proposed": prop,
            "confidence_proposed_pct": f"{prop * 100:.1f}%",
            "delta": round(prop - curr, 3),
            "delta_pct": f"{(prop - curr) * 100:+.1f}pp",
        })

    result_df = pd.DataFrame(rows)
    result_df = result_df.sort_values("delta", ascending=False)

    out_path = Path(__file__).parent / "backtest_comparison_v2_output.csv"
    result_df.to_csv(out_path, index=False)

    # Summary
    sellers = result_df[result_df["has_sales"]]
    non_sellers = result_df[~result_df["has_sales"]]

    print(f"\n{'='*70}")
    print(f"Results written to: {out_path}")
    print(f"{'='*70}")
    print(f"Total products: {len(result_df)}")
    print(f"Products with sales: {len(sellers)}")
    print(f"Products without sales: {len(non_sellers)}")

    print(f"\n--- Products WITH sales ({len(sellers)}) ---")
    print(f"  Avg confidence CURRENT:  {sellers['confidence_current'].mean():.3f} ({sellers['confidence_current'].mean()*100:.1f}%)")
    print(f"  Avg confidence PROPOSED: {sellers['confidence_proposed'].mean():.3f} ({sellers['confidence_proposed'].mean()*100:.1f}%)")
    print(f"  Avg delta: {sellers['delta'].mean()*100:+.1f}pp")
    print(f"  Products that improve:  {(sellers['delta'] > 0).sum()}")
    print(f"  Products that worsen:   {(sellers['delta'] < 0).sum()}")
    print(f"  Products unchanged:     {(sellers['delta'] == 0).sum()}")

    print(f"\n--- Products WITHOUT sales ({len(non_sellers)}) ---")
    print(f"  Avg confidence CURRENT:  {non_sellers['confidence_current'].mean():.3f} ({non_sellers['confidence_current'].mean()*100:.1f}%)")
    print(f"  Avg confidence PROPOSED: {non_sellers['confidence_proposed'].mean():.3f} ({non_sellers['confidence_proposed'].mean()*100:.1f}%)")

    # Confidence buckets comparison
    print(f"\n--- Confidence Bucket Distribution (ALL products) ---")
    bins = [0, 0.2, 0.4, 0.6, 0.8, 1.01]
    labels = ["0-20%", "20-40%", "40-60%", "60-80%", "80-100%"]
    curr_buckets = pd.cut(result_df["confidence_current"], bins=bins, labels=labels, right=False)
    prop_buckets = pd.cut(result_df["confidence_proposed"], bins=bins, labels=labels, right=False)
    print(f"  {'Bucket':<10} {'Current':>10} {'Proposed':>10} {'Change':>10}")
    for label in labels:
        c = (curr_buckets == label).sum()
        p = (prop_buckets == label).sum()
        print(f"  {label:<10} {c:>10} {p:>10} {p-c:>+10}")

    # Show sample products with sales
    print(f"\n--- Sample: Products with sales (sorted by delta) ---")
    sample_cols = ["product_name", "mu_hat", "cv_ratio", "confidence_current_pct", "confidence_proposed_pct", "delta_pct"]
    top = sellers.head(15)
    print(top[sample_cols].to_string(index=False))

    print(f"\n--- Products where proposed is worse ---")
    worse = sellers[sellers["delta"] < 0]
    if worse.empty:
        print("  None")
    else:
        print(worse[sample_cols].to_string(index=False))


if __name__ == "__main__":
    main()
