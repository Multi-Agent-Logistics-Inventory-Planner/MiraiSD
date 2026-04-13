"""
Extract a frozen snapshot of sales data from Supabase into local CSVs.

Run once, then experiment offline without hitting the database.

Usage:
    SUPABASE_DB_URL=... SUPABASE_DB_USERNAME=... SUPABASE_DB_PASSWORD=... \
    python experiments/extract_snapshot.py

Output:
    experiments/data/products.csv
    experiments/data/stock_movements.csv
    experiments/data/location_inventory.csv
"""

from __future__ import annotations

import os
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import quote, urlparse, urlunparse

import pandas as pd
from sqlalchemy import create_engine, text


LOOKBACK_DAYS = 45  # 14d warm-up for rolling stats + ~30d actual data


def _build_engine():
    """Build SQLAlchemy engine from Supabase env vars.

    Reuses the connection pattern from scripts/backtest_comparison_v2.py.
    """
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


def extract_products(engine) -> pd.DataFrame:
    query = """
        SELECT
            p.id::text AS item_id,
            p.name,
            p.sku,
            p.lead_time_days,
            COALESCE(
                p.reorder_point / NULLIF(
                    p.target_stock_level / NULLIF(p.lead_time_days, 0), 0
                ), 7
            )::int AS safety_stock_days,
            p.category_id::text AS category_id,
            c.name AS category_name
        FROM products p
        LEFT JOIN categories c ON p.category_id = c.id
        WHERE p.is_active = true
        ORDER BY p.name
    """
    with engine.connect() as conn:
        return pd.read_sql(text(query), conn)


def extract_stock_movements(engine, lookback_days: int) -> pd.DataFrame:
    end_ts = datetime.now(timezone.utc)
    start_ts = end_ts - timedelta(days=lookback_days)
    query = """
        SELECT
            id::text AS event_id,
            item_id::text AS item_id,
            quantity_change,
            LOWER(reason) AS reason,
            at,
            previous_quantity,
            current_quantity
        FROM stock_movements
        WHERE at >= :start_ts AND at <= :end_ts
        ORDER BY at ASC
    """
    with engine.connect() as conn:
        return pd.read_sql(
            text(query), conn, params={"start_ts": start_ts, "end_ts": end_ts}
        )


def extract_location_inventory(engine) -> pd.DataFrame:
    query = """
        SELECT
            product_id::text AS item_id,
            NOW() AS as_of_ts,
            COALESCE(SUM(quantity), 0)::int AS current_qty
        FROM location_inventory
        GROUP BY product_id
    """
    with engine.connect() as conn:
        return pd.read_sql(text(query), conn)


def main():
    engine = _build_engine()
    data_dir = Path(__file__).parent / "data"
    data_dir.mkdir(exist_ok=True)

    print("Extracting products...")
    products = extract_products(engine)
    products.to_csv(data_dir / "products.csv", index=False)
    print(f"  -> {len(products)} products")

    print(f"Extracting stock movements (last {LOOKBACK_DAYS} days)...")
    movements = extract_stock_movements(engine, LOOKBACK_DAYS)
    movements.to_csv(data_dir / "stock_movements.csv", index=False)
    print(f"  -> {len(movements)} movements")

    print("Extracting location inventory...")
    inventory = extract_location_inventory(engine)
    inventory.to_csv(data_dir / "location_inventory.csv", index=False)
    print(f"  -> {len(inventory)} location-product rows")

    print(f"\nSnapshot saved to {data_dir.resolve()}")


if __name__ == "__main__":
    main()
