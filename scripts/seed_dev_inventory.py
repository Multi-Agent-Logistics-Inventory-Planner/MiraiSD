#!/usr/bin/env python3
"""Seed realistic inventory quantities for existing DEV products in postgres-dev.

Reads the actual SALE movement history to compute each product's real average
daily demand, then sets current stock quantities to produce a spread of risk bands:
  - 2 products: Critical  (~1-3 days)
  - 3 products: Warning   (~4-7 days)
  - 5 products: Healthy   (~8-30 days)
  - 4 products: Safe/Over (>30 days)

Usage:
    python scripts/seed_dev_inventory.py           # Set inventory + run pipeline
    python scripts/seed_dev_inventory.py --cleanup # Remove inserted inventory rows
"""

from __future__ import annotations

import argparse
import math
import sys
import uuid
from datetime import datetime, timedelta, timezone

import psycopg2
import psycopg2.extras

DB_CONFIG = {
    "host": "localhost",
    "port": 5433,
    "dbname": "mirai_inventory",
    "user": "postgres",
    "password": "postgres",
}

LOOKBACK_DAYS = 28
MU_FLOOR = 0.1

# Target risk band -> days of stock to set
RISK_TARGETS = [
    "critical",   # ~2 days
    "critical",   # ~2 days
    "warning",    # ~5 days
    "warning",    # ~5 days
    "warning",    # ~6 days
    "healthy",    # ~12 days
    "healthy",    # ~15 days
    "healthy",    # ~20 days
    "healthy",    # ~25 days
    "healthy",    # ~28 days
    "safe",       # ~40 days
    "safe",       # ~50 days
    "overstocked", # ~70 days
    "overstocked", # ~90 days
]

BAND_DAYS = {
    "critical": 2,
    "warning": 5,
    "healthy": 18,
    "safe": 45,
    "overstocked": 80,
}


def get_connection():
    return psycopg2.connect(**DB_CONFIG)


def compute_daily_demand(conn) -> list[dict]:
    """Compute average daily SALE demand per product from last 28 days."""
    end_ts = datetime.now(timezone.utc)
    start_ts = end_ts - timedelta(days=LOOKBACK_DAYS)

    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            """
            SELECT
                p.id as product_id,
                p.sku,
                p.name,
                p.lead_time_days,
                p.target_stock_level,
                COALESCE(
                    ABS(SUM(sm.quantity_change) FILTER (
                        WHERE sm.reason = 'SALE'
                        AND sm.at >= %s AND sm.at <= %s
                    )) * 1.0 / %s,
                    0
                ) as avg_daily_demand
            FROM products p
            LEFT JOIN stock_movements sm ON sm.item_id = p.id
            WHERE p.is_active = true
              AND p.sku NOT LIKE 'TEST-%%'
              AND p.sku LIKE 'DEV-%%'
            GROUP BY p.id, p.sku, p.name, p.lead_time_days, p.target_stock_level
            ORDER BY avg_daily_demand DESC
            """,
            (start_ts, end_ts, LOOKBACK_DAYS),
        )
        return [dict(row) for row in cur.fetchall()]


def cleanup(conn) -> None:
    """Remove not_assigned_inventory rows for DEV products."""
    with conn.cursor() as cur:
        cur.execute(
            """
            DELETE FROM not_assigned_inventory
            WHERE item_id IN (
                SELECT id FROM products WHERE sku LIKE 'DEV-%%'
            )
            """
        )
        count = cur.rowcount
    conn.commit()
    print(f"Removed {count} inventory rows for DEV products.")


def seed(conn) -> None:
    """Compute demand rates and insert inventory quantities."""
    # Remove existing inventory for DEV products first
    cleanup(conn)

    products = compute_daily_demand(conn)

    if not products:
        print("No DEV products found.")
        return

    print(f"\nFound {len(products)} DEV products. Computing demand rates...\n")
    print(f"{'SKU':<12} {'Name':<18} {'Demand/day':>12} {'Target Band':<14} {'Stock Set':>10}")
    print("-" * 70)

    inventory_rows = []
    # Assign risk targets in demand-sorted order (highest demand first)
    targets = RISK_TARGETS[:len(products)]
    # Pad with "healthy" if fewer products than targets
    while len(targets) < len(products):
        targets.append("healthy")

    for product, target_band in zip(products, targets):
        mu = max(float(product["avg_daily_demand"]), MU_FLOOR)
        target_days = BAND_DAYS[target_band]
        stock = max(1, math.ceil(mu * target_days))

        print(
            f"{product['sku']:<12} {product['name'][:17]:<18} "
            f"{mu:>12.2f} {target_band:<14} {stock:>10}"
        )

        inventory_rows.append((str(uuid.uuid4()), str(product["product_id"]), stock))

    print()

    with conn.cursor() as cur:
        psycopg2.extras.execute_values(
            cur,
            "INSERT INTO not_assigned_inventory (id, item_id, quantity) VALUES %s",
            inventory_rows,
            template="(%s::uuid, %s::uuid, %s)",
        )
    conn.commit()
    print(f"Inserted {len(inventory_rows)} inventory rows.")
    print("\nNext: run the pipeline to generate forecasts:")
    print("  python scripts/seed_dev_inventory.py --run-pipeline")


def run_pipeline() -> None:
    """Run the forecasting pipeline against postgres-dev."""
    import os
    import sys

    forecasting_dir = "/Users/khoinguyen/Documents/SeniorDesign/MiraiSD/services/forecasting-service"
    sys.path.insert(0, forecasting_dir)

    os.environ["SUPABASE_DB_URL"] = "postgresql://postgres:postgres@localhost:5433/mirai_inventory"
    os.environ["SUPABASE_DB_USERNAME"] = "postgres"
    os.environ["SUPABASE_DB_PASSWORD"] = "postgres"

    import importlib.util

    spec = importlib.util.spec_from_file_location(
        "pipeline",
        f"{forecasting_dir}/src/application/pipeline.py",
    )

    from src.application.pipeline import ForecastingPipeline
    from src.adapters.supabase_repo import SupabaseRepo

    import logging
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")

    repo = SupabaseRepo()
    pipeline = ForecastingPipeline(repo)
    saved = pipeline.run_all()
    print(f"\nPipeline complete: saved {saved} forecast predictions.")


def show_results(conn) -> None:
    """Print forecast results for DEV products."""
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            """
            SELECT DISTINCT ON (p.id)
                p.sku,
                p.name,
                ni.quantity as current_stock,
                fp.avg_daily_delta,
                fp.days_to_stockout,
                fp.suggested_reorder_qty,
                fp.suggested_order_date,
                fp.confidence,
                fp.computed_at
            FROM forecast_predictions fp
            JOIN products p ON p.id = fp.item_id
            LEFT JOIN not_assigned_inventory ni ON ni.item_id = p.id
            WHERE p.sku LIKE 'DEV-%%'
            ORDER BY p.id, fp.computed_at DESC
            """,
        )
        rows = cur.fetchall()

    if not rows:
        print("No forecasts found for DEV products. Run the pipeline first.")
        return

    print(f"\n{'SKU':<12} {'Name':<18} {'Stock':>6} {'Demand/d':>9} {'Days Out':>9} {'Reorder':>8} {'Band':<12}")
    print("=" * 80)

    for row in rows:
        days = row["days_to_stockout"]
        demand = abs(float(row["avg_daily_delta"])) if row["avg_daily_delta"] else 0

        if days is None:
            band = "N/A"
            days_str = "NULL"
        elif days <= 3:
            band = "CRITICAL"
            days_str = f"{float(days):.1f}"
        elif days <= 7:
            band = "WARNING"
            days_str = f"{float(days):.1f}"
        elif days <= 14:
            band = "HEALTHY"
            days_str = f"{float(days):.1f}"
        elif days <= 60:
            band = "SAFE"
            days_str = f"{float(days):.1f}"
        else:
            band = "OVERSTOCKED"
            days_str = f"{float(days):.1f}"

        print(
            f"{row['sku']:<12} {row['name'][:17]:<18} "
            f"{(row['current_stock'] or 0):>6} "
            f"{demand:>9.2f} "
            f"{days_str:>9} "
            f"{row['suggested_reorder_qty']:>8} "
            f"{band:<12}"
        )


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed dev inventory for DEV products")
    parser.add_argument("--cleanup", action="store_true", help="Remove DEV inventory rows")
    parser.add_argument("--run-pipeline", action="store_true", help="Run forecasting pipeline after seeding")
    parser.add_argument("--results", action="store_true", help="Show current forecast results")
    args = parser.parse_args()

    try:
        conn = get_connection()
    except psycopg2.OperationalError as e:
        print(f"Failed to connect: {e}")
        sys.exit(1)

    try:
        if args.cleanup:
            cleanup(conn)
        elif args.results:
            show_results(conn)
        else:
            seed(conn)
            if args.run_pipeline:
                conn.close()
                run_pipeline()
                conn = get_connection()
                show_results(conn)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
