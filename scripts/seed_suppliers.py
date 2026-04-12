#!/usr/bin/env python3
"""Seed supplier data for testing the supplier-based lead time feature.

Creates suppliers, links shipments to suppliers, sets preferred suppliers for products,
and refreshes the materialized view.

Usage:
    python scripts/seed_suppliers.py           # Create suppliers and link data
    python scripts/seed_suppliers.py --cleanup # Remove all supplier data
"""

from __future__ import annotations

import argparse
import random
import sys
import uuid
from datetime import datetime, timezone

import psycopg2
import psycopg2.extras

DB_CONFIG = {
    "host": "localhost",
    "port": 5433,
    "dbname": "mirai_inventory",
    "user": "postgres",
    "password": "postgres",
}

# Sample suppliers with different lead time characteristics
SAMPLE_SUPPLIERS = [
    {"name": "Tokyo Direct", "email": "orders@tokyodirect.jp", "lead_time_range": (4, 6)},
    {"name": "Osaka Wholesale", "email": "sales@osakawholesale.co.jp", "lead_time_range": (5, 7)},
    {"name": "Anime Goods Japan", "email": "info@animegoods.jp", "lead_time_range": (5, 8)},
    {"name": "Prize Factory", "email": "orders@prizefactory.com", "lead_time_range": (6, 10)},
    {"name": "Global Imports Ltd", "email": "purchasing@globalimports.com", "lead_time_range": (45, 60)},
    {"name": "Bandai Namco Direct", "email": None, "lead_time_range": (10, 14)},
    {"name": "Sega Prizes", "email": "wholesale@sega.co.jp", "lead_time_range": (7, 12)},
    {"name": "Taito Distribution", "email": "dist@taito.co.jp", "lead_time_range": (6, 9)},
]


def get_connection():
    return psycopg2.connect(**DB_CONFIG)


def cleanup(conn):
    """Remove all supplier-related data."""
    print("Cleaning up supplier data...")
    with conn.cursor() as cur:
        # Clear preferred supplier references from products
        cur.execute("UPDATE products SET preferred_supplier_id = NULL, preferred_supplier_auto = false")
        print(f"  Cleared preferred_supplier_id from {cur.rowcount} products")

        # Clear supplier references from shipments
        cur.execute("UPDATE shipments SET supplier_id = NULL")
        print(f"  Cleared supplier_id from {cur.rowcount} shipments")

        # Delete all suppliers
        cur.execute("DELETE FROM suppliers")
        print(f"  Deleted {cur.rowcount} suppliers")

        # Refresh MV
        cur.execute("REFRESH MATERIALIZED VIEW mv_lead_time_stats")
        print("  Refreshed materialized view")

    conn.commit()
    print("Cleanup complete!")


def create_suppliers(conn) -> dict[str, str]:
    """Create sample suppliers and return mapping of name -> id."""
    print("Creating suppliers...")
    supplier_ids = {}

    with conn.cursor() as cur:
        for supplier in SAMPLE_SUPPLIERS:
            supplier_id = str(uuid.uuid4())
            cur.execute(
                """
                INSERT INTO suppliers (id, display_name, contact_email, is_active, created_at, updated_at)
                VALUES (%s, %s, %s, true, NOW(), NOW())
                ON CONFLICT (canonical_name) DO UPDATE SET updated_at = NOW()
                RETURNING id
                """,
                (supplier_id, supplier["name"], supplier["email"]),
            )
            result = cur.fetchone()
            supplier_ids[supplier["name"]] = str(result[0])
            print(f"  Created supplier: {supplier['name']}")

    conn.commit()
    return supplier_ids


def link_shipments_to_suppliers(conn, supplier_ids: dict[str, str]):
    """Link existing shipments to suppliers."""
    print("Linking shipments to suppliers...")

    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        # Get all shipments
        cur.execute("SELECT id, supplier_name FROM shipments WHERE supplier_id IS NULL")
        shipments = cur.fetchall()
        print(f"  Found {len(shipments)} shipments to link")

        if not shipments:
            return

        supplier_names = list(supplier_ids.keys())

        for shipment in shipments:
            # Try to match by existing supplier_name, otherwise assign randomly
            matched_supplier = None
            if shipment["supplier_name"]:
                for name in supplier_names:
                    if name.lower() in shipment["supplier_name"].lower() or \
                       shipment["supplier_name"].lower() in name.lower():
                        matched_supplier = name
                        break

            if not matched_supplier:
                # Randomly assign, with higher weight for domestic suppliers
                weights = [3, 3, 3, 2, 1, 2, 2, 2]  # Lower weight for Global Imports
                matched_supplier = random.choices(supplier_names, weights=weights)[0]

            cur.execute(
                "UPDATE shipments SET supplier_id = %s WHERE id = %s",
                (supplier_ids[matched_supplier], shipment["id"]),
            )

        print(f"  Linked {len(shipments)} shipments")

    conn.commit()


def set_preferred_suppliers(conn, supplier_ids: dict[str, str]):
    """Set preferred suppliers for products based on shipment history."""
    print("Setting preferred suppliers for products...")

    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        # Get products that have been in delivered shipments
        cur.execute(
            """
            SELECT DISTINCT ON (si.item_id)
                si.item_id as product_id,
                s.supplier_id
            FROM shipment_items si
            JOIN shipments s ON s.id = si.shipment_id
            WHERE s.status = 'DELIVERED'
              AND s.supplier_id IS NOT NULL
            ORDER BY si.item_id, s.actual_delivery_date DESC NULLS LAST
            """
        )
        product_suppliers = cur.fetchall()
        print(f"  Found {len(product_suppliers)} products with shipment history")

        for ps in product_suppliers:
            cur.execute(
                """
                UPDATE products
                SET preferred_supplier_id = %s,
                    preferred_supplier_auto = true,
                    updated_at = NOW()
                WHERE id = %s
                """,
                (ps["supplier_id"], ps["product_id"]),
            )

        # For products without shipment history, randomly assign some preferred suppliers
        cur.execute(
            """
            SELECT id FROM products
            WHERE preferred_supplier_id IS NULL
              AND is_active = true
              AND parent_id IS NULL
            LIMIT 10
            """
        )
        products_without_supplier = cur.fetchall()

        supplier_id_list = list(supplier_ids.values())
        for product in products_without_supplier:
            if random.random() < 0.5:  # 50% chance to assign
                cur.execute(
                    """
                    UPDATE products
                    SET preferred_supplier_id = %s,
                        preferred_supplier_auto = false,
                        updated_at = NOW()
                    WHERE id = %s
                    """,
                    (random.choice(supplier_id_list), product["id"]),
                )

        print(f"  Set preferred suppliers for products")

    conn.commit()


def simulate_lead_times(conn, supplier_ids: dict[str, str]):
    """Update shipment dates to simulate realistic lead times per supplier."""
    print("Simulating lead times for shipments...")

    supplier_lead_times = {s["name"]: s["lead_time_range"] for s in SAMPLE_SUPPLIERS}

    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        # Get all delivered shipments with suppliers
        cur.execute(
            """
            SELECT s.id, s.order_date, s.actual_delivery_date, sup.display_name
            FROM shipments s
            JOIN suppliers sup ON sup.id = s.supplier_id
            WHERE s.status = 'DELIVERED'
              AND s.order_date IS NOT NULL
            """
        )
        shipments = cur.fetchall()

        updated = 0
        for shipment in shipments:
            supplier_name = shipment["display_name"]
            if supplier_name not in supplier_lead_times:
                continue

            min_lt, max_lt = supplier_lead_times[supplier_name]
            # Generate a lead time with some variance
            lead_time = random.randint(min_lt, max_lt)

            # Calculate new delivery date
            from datetime import timedelta
            order_date = shipment["order_date"]
            new_delivery_date = order_date + timedelta(days=lead_time)

            cur.execute(
                """
                UPDATE shipments
                SET actual_delivery_date = %s
                WHERE id = %s
                """,
                (new_delivery_date, shipment["id"]),
            )
            updated += 1

        print(f"  Updated lead times for {updated} shipments")

    conn.commit()


def refresh_materialized_view(conn):
    """Refresh the lead time stats materialized view."""
    print("Refreshing materialized view...")
    with conn.cursor() as cur:
        # Use non-concurrent refresh for dev seeding (simpler, doesn't require unique index)
        cur.execute("REFRESH MATERIALIZED VIEW mv_lead_time_stats")
    conn.commit()
    print("  MV refreshed")


def print_stats(conn):
    """Print supplier statistics."""
    print("\n--- Supplier Statistics ---")
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            """
            SELECT
                s.display_name,
                COUNT(DISTINCT sh.id) as shipment_count,
                ROUND(AVG(sh.actual_delivery_date - sh.order_date)::numeric, 1) as avg_lead_time,
                ROUND(STDDEV(sh.actual_delivery_date - sh.order_date)::numeric, 1) as sigma_l
            FROM suppliers s
            LEFT JOIN shipments sh ON sh.supplier_id = s.id AND sh.status = 'DELIVERED'
            GROUP BY s.id, s.display_name
            ORDER BY shipment_count DESC
            """
        )
        suppliers = cur.fetchall()

        print(f"\n{'Supplier':<25} {'Shipments':>10} {'Avg LT':>10} {'Sigma L':>10}")
        print("-" * 60)
        for s in suppliers:
            avg_lt = f"{s['avg_lead_time']:.1f}" if s['avg_lead_time'] else "-"
            sigma = f"{s['sigma_l']:.1f}" if s['sigma_l'] else "-"
            print(f"{s['display_name']:<25} {s['shipment_count']:>10} {avg_lt:>10} {sigma:>10}")

        # Products with preferred suppliers
        cur.execute(
            """
            SELECT COUNT(*) as total,
                   COUNT(preferred_supplier_id) as with_supplier
            FROM products
            WHERE is_active = true AND parent_id IS NULL
            """
        )
        result = cur.fetchone()
        print(f"\nProducts with preferred supplier: {result['with_supplier']}/{result['total']}")

        # MV stats
        cur.execute("SELECT COUNT(*) as count FROM mv_lead_time_stats")
        mv_count = cur.fetchone()["count"]
        print(f"MV lead time stats rows: {mv_count}")


def main():
    parser = argparse.ArgumentParser(description="Seed supplier data for testing")
    parser.add_argument("--cleanup", action="store_true", help="Remove all supplier data")
    args = parser.parse_args()

    try:
        conn = get_connection()
        print(f"Connected to database at {DB_CONFIG['host']}:{DB_CONFIG['port']}")
    except Exception as e:
        print(f"Failed to connect to database: {e}")
        print("\nMake sure the dev database is running:")
        print("  docker compose -f infra/docker-compose.dev.yml up -d postgres-dev")
        sys.exit(1)

    try:
        if args.cleanup:
            cleanup(conn)
        else:
            supplier_ids = create_suppliers(conn)
            link_shipments_to_suppliers(conn, supplier_ids)
            simulate_lead_times(conn, supplier_ids)
            set_preferred_suppliers(conn, supplier_ids)
            refresh_materialized_view(conn)
            print_stats(conn)
            print("\nSeeding complete!")

    finally:
        conn.close()


if __name__ == "__main__":
    main()
