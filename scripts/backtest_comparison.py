"""
Read-only backtest comparison: 7-day vs 14-day MAPE and confidence.

Connects to the database, recalculates MAPE at both horizons for every
active product, and writes a CSV comparison. Nothing is written to the DB.

Usage:
    # Set env vars (or export them):
    SUPABASE_DB_URL=postgresql://host:port/db \
    SUPABASE_DB_USERNAME=postgres \
    SUPABASE_DB_PASSWORD=xxx \
    python scripts/backtest_comparison.py

Output: scripts/backtest_comparison_output.csv
"""

from __future__ import annotations

import os
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np
import pandas as pd
from sqlalchemy import create_engine, text
from urllib.parse import quote, urlparse, urlunparse

# ---------------------------------------------------------------------------
# Database connection (mirrors supabase_repo.py logic, read-only)
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

    final = urlunparse((scheme, netloc, parsed.path, "", "", ""))
    return create_engine(final, pool_pre_ping=True)


# ---------------------------------------------------------------------------
# Data loading (read-only queries)
# ---------------------------------------------------------------------------

def load_products(engine) -> pd.DataFrame:
    query = """
        SELECT id::text AS item_id, name, sku, quantity AS current_stock
        FROM products
        WHERE is_active = true
        ORDER BY name
    """
    with engine.connect() as conn:
        return pd.read_sql(text(query), conn)


def load_movements(engine, lookback_days: int) -> pd.DataFrame:
    end_ts = datetime.now(timezone.utc)
    start_ts = end_ts - timedelta(days=lookback_days)
    query = """
        SELECT
            id::text AS event_id,
            item_id::text AS item_id,
            quantity_change,
            LOWER(reason) AS reason,
            at
        FROM stock_movements
        WHERE at >= :start_ts AND at <= :end_ts
        ORDER BY at ASC
    """
    with engine.connect() as conn:
        return pd.read_sql(text(query), conn, params={"start_ts": start_ts, "end_ts": end_ts})


def load_historical_forecasts(engine, item_ids: list[str], target_date: datetime) -> pd.DataFrame:
    target_start = target_date - timedelta(hours=24)
    target_end = target_date + timedelta(hours=24)
    query = """
        SELECT DISTINCT ON (item_id)
            item_id::text AS item_id,
            computed_at,
            (features->>'mu_hat')::float AS mu_hat
        FROM forecast_predictions
        WHERE item_id = ANY(:item_ids)
          AND computed_at BETWEEN :target_start AND :target_end
        ORDER BY item_id, ABS(EXTRACT(EPOCH FROM (computed_at - :target_date)))
    """
    import uuid as _uuid
    params = {
        "item_ids": [_uuid.UUID(iid) for iid in item_ids],
        "target_start": target_start,
        "target_end": target_end,
        "target_date": target_date,
    }
    with engine.connect() as conn:
        return pd.read_sql(text(query), conn, params=params)


# ---------------------------------------------------------------------------
# Reuse forecasting-service logic inline (to keep script self-contained)
# ---------------------------------------------------------------------------

MU_FLOOR = 0.1
SIGMA_FLOOR = 0.01
MAPE_EPSILON = 0.1


def build_daily_usage(events_df: pd.DataFrame) -> pd.DataFrame:
    """Mirrors features.build_daily_usage — sales-only consumption grid."""
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
            [all_items, all_dates], names=["item_id", "date"]
        ).to_frame(index=False)
        out["consumption"] = 0.0
        return out.sort_values(["item_id", "date"]).reset_index(drop=True)

    sales["consumption"] = (-sales["quantity_change"]).astype(float)
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
        actual_mu=("consumption", "mean"),
        backtest_days=("date", "nunique"),
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


def compute_confidence(mu_hat, sigma_d_hat, mape_value=None):
    variability_score = 1.0 - min(1.0, sigma_d_hat / max(mu_hat, 0.1))
    if mape_value is not None and not np.isnan(mape_value):
        mape_score = max(0.0, 1.0 - mape_value)
        return round(0.4 * variability_score + 0.6 * mape_score, 3)
    return round(variability_score, 3)


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

    # Build daily usage from all movements
    daily_usage = build_daily_usage(movements_df) if not movements_df.empty else pd.DataFrame()

    # Compute demand estimates (mu, sigma) per product using full history
    estimates = {}
    if not daily_usage.empty:
        for item_id, group in daily_usage.groupby("item_id"):
            group = group.sort_values("date")
            mu, sigma = dow_weighted_estimate(group)
            estimates[str(item_id)] = {"mu_hat": mu, "sigma_d_hat": sigma}

    # Run backtest at both horizons
    horizons = [14, 7]
    mape_results = {}

    for h in horizons:
        target = now - timedelta(days=h)
        print(f"\nBacktest horizon = {h} days (target date: {target.strftime('%Y-%m-%d')})...")

        hist_fc = load_historical_forecasts(engine, item_ids, target)
        print(f"  Found {len(hist_fc)} historical forecasts near target date")

        if hist_fc.empty or movements_df.empty:
            mape_results[h] = pd.DataFrame(columns=["item_id", "mape", "forecast_mu", "actual_mu", "backtest_days"])
            continue

        # Filter movements to the backtest window
        backtest_start = pd.to_datetime(target, utc=True)
        backtest_end = pd.to_datetime(now, utc=True)
        window_movements = movements_df[
            (movements_df["at"] >= backtest_start) & (movements_df["at"] <= backtest_end)
        ]

        if window_movements.empty:
            mape_results[h] = pd.DataFrame(columns=["item_id", "mape", "forecast_mu", "actual_mu", "backtest_days"])
            continue

        backtest_daily = build_daily_usage(window_movements)
        mape_df = compute_mape(hist_fc, backtest_daily)
        mape_results[h] = mape_df
        print(f"  Computed MAPE for {len(mape_df)} products")

    # Build output table
    rows = []
    for _, prod in products_df.iterrows():
        iid = str(prod["item_id"])
        est = estimates.get(iid, {"mu_hat": MU_FLOOR, "sigma_d_hat": SIGMA_FLOOR})
        mu = est["mu_hat"]
        sigma = est["sigma_d_hat"]
        variability = round(1.0 - min(1.0, sigma / max(mu, 0.1)), 3)

        row = {
            "product_name": prod["name"],
            "sku": prod.get("sku", ""),
            "current_stock": prod.get("current_stock", ""),
            "mu_hat": round(mu, 4),
            "sigma_d_hat": round(sigma, 4),
            "variability_score": variability,
        }

        for h in horizons:
            mape_df = mape_results[h]
            match = mape_df[mape_df["item_id"] == iid]
            if not match.empty:
                mape_val = float(match.iloc[0]["mape"])
                forecast_mu = float(match.iloc[0]["forecast_mu"])
                actual_mu = float(match.iloc[0]["actual_mu"])
                bt_days = int(match.iloc[0]["backtest_days"])
                conf = compute_confidence(mu, sigma, mape_val)
                row[f"mape_{h}d"] = round(mape_val, 4)
                row[f"forecast_mu_{h}d"] = round(forecast_mu, 4)
                row[f"actual_mu_{h}d"] = round(actual_mu, 4)
                row[f"backtest_days_{h}d"] = bt_days
                row[f"confidence_{h}d"] = conf
                row[f"confidence_{h}d_pct"] = f"{conf * 100:.1f}%"
            else:
                row[f"mape_{h}d"] = None
                row[f"forecast_mu_{h}d"] = None
                row[f"actual_mu_{h}d"] = None
                row[f"backtest_days_{h}d"] = None
                row[f"confidence_{h}d"] = variability
                row[f"confidence_{h}d_pct"] = f"{variability * 100:.1f}% (no MAPE)"

        # Delta: how much confidence changes
        c14 = row.get("confidence_14d", variability)
        c7 = row.get("confidence_7d", variability)
        row["confidence_delta"] = round(c7 - c14, 3)
        row["confidence_delta_pct"] = f"{(c7 - c14) * 100:+.1f}pp"

        rows.append(row)

    result_df = pd.DataFrame(rows)

    # Sort by confidence delta (biggest improvement first)
    result_df = result_df.sort_values("confidence_delta", ascending=False)

    out_path = Path(__file__).parent / "backtest_comparison_output.csv"
    result_df.to_csv(out_path, index=False)

    # Print summary
    has_both = result_df[result_df["mape_14d"].notna() & result_df["mape_7d"].notna()]
    print(f"\n{'='*70}")
    print(f"Results written to: {out_path}")
    print(f"{'='*70}")
    print(f"Total products: {len(result_df)}")
    print(f"Products with 14-day MAPE: {result_df['mape_14d'].notna().sum()}")
    print(f"Products with  7-day MAPE: {result_df['mape_7d'].notna().sum()}")
    print(f"Products with both:        {len(has_both)}")

    if not has_both.empty:
        avg_c14 = has_both["confidence_14d"].mean()
        avg_c7 = has_both["confidence_7d"].mean()
        avg_m14 = has_both["mape_14d"].mean()
        avg_m7 = has_both["mape_7d"].mean()
        print(f"\n--- Averages (products with both horizons) ---")
        print(f"  Avg MAPE  14d: {avg_m14:.4f} ({avg_m14*100:.1f}%)")
        print(f"  Avg MAPE   7d: {avg_m7:.4f} ({avg_m7*100:.1f}%)")
        print(f"  Avg Confidence 14d: {avg_c14:.3f} ({avg_c14*100:.1f}%)")
        print(f"  Avg Confidence  7d: {avg_c7:.3f} ({avg_c7*100:.1f}%)")
        print(f"  Avg delta (7d - 14d): {(avg_c7 - avg_c14)*100:+.1f}pp")

    # Products where confidence improves vs worsens
    if not has_both.empty:
        improves = (has_both["confidence_delta"] > 0).sum()
        worsens = (has_both["confidence_delta"] < 0).sum()
        same = (has_both["confidence_delta"] == 0).sum()
        print(f"\n  Products where 7d is better: {improves}")
        print(f"  Products where 7d is worse:  {worsens}")
        print(f"  Products unchanged:          {same}")


if __name__ == "__main__":
    main()
