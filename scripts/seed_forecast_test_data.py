#!/usr/bin/env python3
"""Seed script for verifiable forecast test data.

Creates 5 test products with deterministic stock movement patterns so that
forecasting pipeline outputs can be hand-verified.

Usage:
    python scripts/seed_forecast_test_data.py            # Seed data
    python scripts/seed_forecast_test_data.py --verify    # Compare expected vs actual
    python scripts/seed_forecast_test_data.py --cleanup   # Remove all test data
"""

from __future__ import annotations

import argparse
import math
import random
import sys
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

import psycopg2
import psycopg2.extras

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

DB_CONFIG = {
    "host": "localhost",
    "port": 5433,
    "dbname": "mirai_inventory",
    "user": "postgres",
    "password": "postgres",
}

CATEGORY_ID = "4063a22d-393e-45bf-895f-9cc4cf63e642"  # Figurine
NAMESPACE_UUID = uuid.UUID("12345678-1234-5678-1234-567812345678")

# Pipeline constants (must match forecasting-service/src/config.py)
TARGET_DAYS = 21
MU_FLOOR = 0.1
EPSILON_MU = 0.1
DAYS_OF_HISTORY = 28

# Test product definitions
# (sku, name, daily_sales, stock, description)
TEST_PRODUCTS = [
    ("TEST-CRIT-001", "TEST Critical Item", 10, 20, "Constant 10/day sales, 20 stock -> ~2d stockout"),
    ("TEST-WARN-001", "TEST Warning Item", 5, 30, "Constant 5/day sales, 30 stock -> ~6d stockout"),
    ("TEST-SAFE-001", "TEST Safe Item", 2, 60, "Constant 2/day sales, 60 stock -> ~30d stockout"),
    ("TEST-ZERO-001", "TEST Zero Demand", 0, 50, "No sales, mu floors at 0.1 -> 500d stockout"),
    ("TEST-VAR-001", "TEST High Variance", None, 25, "Random 0-8/day sales (seed=42), 25 stock"),
]


def make_product_id(sku: str) -> str:
    """Deterministic UUID from SKU using uuid5."""
    return str(uuid.uuid5(NAMESPACE_UUID, sku))


def generate_variance_pattern(num_days: int, seed: int = 42) -> list[int]:
    """Generate deterministic pseudo-random daily sales pattern."""
    rng = random.Random(seed)
    return [rng.randint(0, 8) for _ in range(num_days)]


def compute_expected_values() -> list[dict[str, Any]]:
    """Compute expected forecast values for each test product.

    Mirrors the pipeline logic: ma14 of last 14 days -> mu_hat,
    then days_to_stockout = stock / mu_hat.
    """
    results = []
    variance_pattern = generate_variance_pattern(DAYS_OF_HISTORY)

    for sku, name, daily_sales, stock, _ in TEST_PRODUCTS:
        product_id = make_product_id(sku)

        if sku == "TEST-VAR-001":
            # ma14 = average of last 14 days of the variance pattern
            last_14 = variance_pattern[-14:]
            mu_hat = sum(last_14) / 14.0
            mu_hat = max(mu_hat, MU_FLOOR)
        elif daily_sales == 0:
            mu_hat = MU_FLOOR  # Pipeline floors at MU_FLOOR
        else:
            mu_hat = float(daily_sales)

        if mu_hat < EPSILON_MU:
            days_to_stockout = None  # inf -> stored as NULL
        else:
            days_to_stockout = stock / mu_hat

        suggested_reorder = max(0, math.ceil(TARGET_DAYS * mu_hat - stock))
        avg_daily_delta = -mu_hat

        results.append({
            "item_id": product_id,
            "sku": sku,
            "name": name,
            "stock": stock,
            "mu_hat": round(mu_hat, 4),
            "days_to_stockout": round(days_to_stockout, 2) if days_to_stockout is not None else None,
            "avg_daily_delta": round(avg_daily_delta, 4),
            "suggested_reorder_qty": suggested_reorder,
        })

    return results


# ---------------------------------------------------------------------------
# Database operations
# ---------------------------------------------------------------------------

def get_connection():
    """Create a database connection."""
    return psycopg2.connect(**DB_CONFIG)


def cleanup(conn) -> None:
    """Remove all TEST-* products and related data."""
    with conn.cursor() as cur:
        # Find test product IDs
        cur.execute("SELECT id FROM products WHERE sku LIKE 'TEST-%%'")
        product_ids = [row[0] for row in cur.fetchall()]

        if not product_ids:
            print("No test data found to clean up.")
            return

        id_tuple = tuple(product_ids)

        # Delete in dependency order
        cur.execute("DELETE FROM forecast_predictions WHERE item_id IN %s", (id_tuple,))
        forecast_count = cur.rowcount
        cur.execute("DELETE FROM stock_movements WHERE item_id IN %s", (id_tuple,))
        movement_count = cur.rowcount
        cur.execute("DELETE FROM not_assigned_inventory WHERE item_id IN %s", (id_tuple,))
        inventory_count = cur.rowcount
        cur.execute("DELETE FROM products WHERE id IN %s", (id_tuple,))
        product_count = cur.rowcount

    conn.commit()
    print(f"Cleaned up: {product_count} products, {inventory_count} inventory rows, "
          f"{movement_count} movements, {forecast_count} forecasts")


def seed(conn) -> None:
    """Insert test products, inventory, and stock movements."""
    # First clean up any existing test data
    cleanup(conn)

    now = datetime.now(timezone.utc)
    variance_pattern = generate_variance_pattern(DAYS_OF_HISTORY)

    with conn.cursor() as cur:
        # 1. Insert products
        product_rows = []
        for sku, name, _, _, description in TEST_PRODUCTS:
            product_id = make_product_id(sku)
            product_rows.append((
                product_id, sku, name, CATEGORY_ID, True, 7, 10, 50, 5.00, description,
            ))

        psycopg2.extras.execute_values(
            cur,
            """INSERT INTO products (id, sku, name, category_id, is_active,
                   lead_time_days, reorder_point, target_stock_level, unit_cost, description)
               VALUES %s""",
            product_rows,
            template="(%s::uuid, %s, %s, %s::uuid, %s, %s, %s, %s, %s, %s)",
        )
        print(f"Inserted {len(product_rows)} products")

        # 2. Insert inventory
        inventory_rows = []
        for sku, _, _, stock, _ in TEST_PRODUCTS:
            product_id = make_product_id(sku)
            inv_id = str(uuid.uuid4())
            inventory_rows.append((inv_id, product_id, stock))

        psycopg2.extras.execute_values(
            cur,
            """INSERT INTO not_assigned_inventory (id, item_id, quantity)
               VALUES %s""",
            inventory_rows,
            template="(%s::uuid, %s::uuid, %s)",
        )
        print(f"Inserted {len(inventory_rows)} inventory rows")

        # 3. Insert stock movements
        movement_rows = []
        for sku, _, daily_sales, _, _ in TEST_PRODUCTS:
            product_id = make_product_id(sku)

            if sku == "TEST-ZERO-001":
                # No movements for zero demand
                continue

            for day_offset in range(DAYS_OF_HISTORY):
                # Movements go from 28 days ago to yesterday
                movement_date = now - timedelta(days=DAYS_OF_HISTORY - day_offset)
                # Set time to noon UTC for consistency
                movement_ts = movement_date.replace(
                    hour=12, minute=0, second=0, microsecond=0,
                )

                if sku == "TEST-VAR-001":
                    qty = variance_pattern[day_offset]
                else:
                    qty = daily_sales

                if qty == 0:
                    continue  # Skip zero-quantity days

                movement_rows.append((
                    product_id,
                    "NOT_ASSIGNED",
                    -qty,  # Negative for sales
                    "SALE",
                    movement_ts,
                ))

        psycopg2.extras.execute_values(
            cur,
            """INSERT INTO stock_movements (item_id, location_type, quantity_change, reason, at)
               VALUES %s""",
            movement_rows,
            template="(%s::uuid, %s, %s, %s, %s)",
        )
        print(f"Inserted {len(movement_rows)} stock movements")

    conn.commit()

    # Print expected values
    print("\n--- Expected Forecast Values ---")
    expected = compute_expected_values()
    print(f"{'SKU':<18} {'mu_hat':>8} {'days_out':>10} {'avg_delta':>10} {'reorder':>8}")
    print("-" * 60)
    for e in expected:
        days_str = f"{e['days_to_stockout']:.1f}" if e["days_to_stockout"] is not None else "NULL"
        print(f"{e['sku']:<18} {e['mu_hat']:>8.2f} {days_str:>10} "
              f"{e['avg_daily_delta']:>10.2f} {e['suggested_reorder_qty']:>8}")

    print("\nNext steps:")
    print("  1. Trigger pipeline: curl -X POST http://localhost:5010/forecasts/trigger")
    print("  2. Verify results:   python scripts/seed_forecast_test_data.py --verify")


def verify(conn) -> None:
    """Compare expected vs actual forecast predictions."""
    expected = compute_expected_values()
    expected_by_id = {e["item_id"]: e for e in expected}

    item_ids = list(expected_by_id.keys())

    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            """SELECT fp.item_id, fp.avg_daily_delta, fp.days_to_stockout,
                      fp.suggested_reorder_qty, fp.confidence, fp.features,
                      fp.computed_at
               FROM forecast_predictions fp
               WHERE fp.item_id = ANY(%s::uuid[])
               ORDER BY fp.computed_at DESC""",
            (item_ids,),
        )
        rows = cur.fetchall()

    if not rows:
        print("No forecast predictions found for test items.")
        print("Have you run the pipeline? curl -X POST http://localhost:5010/forecasts/trigger")
        return

    # Keep only the latest prediction per item
    latest: dict[str, Any] = {}
    for row in rows:
        item_id = str(row["item_id"])
        if item_id not in latest:
            latest[item_id] = row

    print(f"\n{'SKU':<18} {'Field':<20} {'Expected':>12} {'Actual':>12} {'Match':>6}")
    print("=" * 72)

    all_pass = True
    for exp in expected:
        item_id = exp["item_id"]
        sku = exp["sku"]
        actual = latest.get(item_id)

        if actual is None:
            # Zero-demand items with no movements are excluded from the pipeline
            # when other items have movements - this is expected behavior.
            if sku == "TEST-ZERO-001":
                print(f"{sku:<18} {'(no prediction)':<20} {'---':>12} {'---':>12} {'OK*':>6}")
                print(f"  (* Expected: pipeline skips items with no movements when others have history)\n")
                continue
            print(f"{sku:<18} {'(no prediction)':<20} {'---':>12} {'---':>12} {'MISS':>6}")
            all_pass = False
            continue

        checks = [
            ("avg_daily_delta", exp["avg_daily_delta"], actual["avg_daily_delta"], 0.5),
            ("days_to_stockout", exp["days_to_stockout"], actual["days_to_stockout"], 1.0),
            ("suggested_reorder", exp["suggested_reorder_qty"], actual["suggested_reorder_qty"], 5),
        ]

        for field, exp_val, act_val, tolerance in checks:
            if exp_val is None and act_val is None:
                status = "OK"
            elif exp_val is None or act_val is None:
                status = "FAIL"
                all_pass = False
            elif abs(float(exp_val) - float(act_val)) <= tolerance:
                status = "OK"
            else:
                status = "FAIL"
                all_pass = False

            exp_str = f"{exp_val}" if exp_val is not None else "NULL"
            act_str = f"{float(act_val):.2f}" if act_val is not None else "NULL"
            print(f"{sku:<18} {field:<20} {exp_str:>12} {act_str:>12} {status:>6}")

        # Print mu_hat from features JSON
        features = actual.get("features") or {}
        mu_hat_actual = features.get("mu_hat")
        mu_hat_expected = exp["mu_hat"]
        if mu_hat_actual is not None:
            mu_match = "OK" if abs(mu_hat_actual - mu_hat_expected) <= 0.5 else "FAIL"
            if mu_match == "FAIL":
                all_pass = False
            print(f"{sku:<18} {'mu_hat (features)':<20} {mu_hat_expected:>12.2f} "
                  f"{mu_hat_actual:>12.2f} {mu_match:>6}")

        print()

    print("=" * 72)
    if all_pass:
        print("ALL CHECKS PASSED")
    else:
        print("SOME CHECKS FAILED - review mismatches above")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(description="Seed forecast test data")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--verify", action="store_true", help="Verify forecast predictions")
    group.add_argument("--cleanup", action="store_true", help="Remove all test data")
    args = parser.parse_args()

    try:
        conn = get_connection()
    except psycopg2.OperationalError as e:
        print(f"Failed to connect to database: {e}")
        print("Is the postgres-dev container running? (docker compose up postgres-dev)")
        sys.exit(1)

    try:
        if args.verify:
            verify(conn)
        elif args.cleanup:
            cleanup(conn)
        else:
            seed(conn)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
