#!/usr/bin/env python3
"""Update product MSRP values from CSV file.

Reads productsList.csv and updates MSRP for matching products in the database.
Uses fuzzy matching to find products by name.

Usage:
    # Preview matches without updating (default)
    python scripts/update_msrp.py

    # Apply updates to database
    python scripts/update_msrp.py --apply

    # Use custom CSV path
    python scripts/update_msrp.py --csv /path/to/file.csv

Environment Variables:
    SUPABASE_DB_URL - PostgreSQL connection string for Supabase
                      Format: postgresql://user:password@host:port/database

    Or set individual variables:
    SUPABASE_DB_HOST - Database host (e.g., db.xxx.supabase.co)
    SUPABASE_DB_PORT - Database port (default: 5432)
    SUPABASE_DB_NAME - Database name (default: postgres)
    SUPABASE_DB_USER - Database user (default: postgres)
    SUPABASE_DB_PASSWORD - Database password
"""

from __future__ import annotations

import argparse
import csv
import os
import sys
from dataclasses import dataclass
from decimal import Decimal
from difflib import SequenceMatcher
from pathlib import Path

import psycopg2
import psycopg2.extras

# Default CSV path relative to project root
DEFAULT_CSV_PATH = Path(__file__).parent.parent / "refs" / "productsList.csv"

# Fuzzy match threshold (0.0 to 1.0)
FUZZY_THRESHOLD = 0.85


@dataclass
class CsvProduct:
    """Product data from CSV."""
    name: str
    price: Decimal
    group: str


@dataclass
class DbProduct:
    """Product data from database."""
    id: str
    name: str
    sku: str | None
    msrp: Decimal | None


@dataclass
class Match:
    """A matched product pair."""
    db_product: DbProduct
    csv_product: CsvProduct
    match_type: str  # "exact", "substring", "fuzzy"
    confidence: float


def normalize(name: str) -> str:
    """Normalize a product name for comparison."""
    return name.lower().strip()


def get_connection():
    """Create database connection from environment variables."""
    # Try full connection URL first
    db_url = os.environ.get("SUPABASE_DB_URL")
    if db_url:
        return psycopg2.connect(db_url)

    # Fall back to individual variables
    host = os.environ.get("SUPABASE_DB_HOST")
    if not host:
        print("Error: Set SUPABASE_DB_URL or SUPABASE_DB_HOST environment variable")
        print("\nExample:")
        print("  export SUPABASE_DB_URL='postgresql://postgres:password@db.xxx.supabase.co:5432/postgres'")
        sys.exit(1)

    return psycopg2.connect(
        host=host,
        port=os.environ.get("SUPABASE_DB_PORT", "5432"),
        dbname=os.environ.get("SUPABASE_DB_NAME", "postgres"),
        user=os.environ.get("SUPABASE_DB_USER", "postgres"),
        password=os.environ.get("SUPABASE_DB_PASSWORD", ""),
    )


def load_csv(csv_path: Path) -> dict[str, CsvProduct]:
    """Load CSV file into a dict keyed by normalized name."""
    products = {}
    with open(csv_path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            name = row.get("Name", "").strip()
            price_str = row.get("Price", "0").strip()
            group = row.get("Group", "").strip()

            if not name:
                continue

            try:
                price = Decimal(price_str)
            except Exception:
                print(f"Warning: Invalid price '{price_str}' for '{name}', skipping")
                continue

            normalized = normalize(name)
            products[normalized] = CsvProduct(name=name, price=price, group=group)

    return products


def load_db_products(conn) -> list[DbProduct]:
    """Load all products from database."""
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute("""
            SELECT id, name, sku, msrp
            FROM products
            WHERE is_active = true
            ORDER BY name
        """)
        return [
            DbProduct(
                id=str(row["id"]),
                name=row["name"],
                sku=row["sku"],
                msrp=row["msrp"],
            )
            for row in cur.fetchall()
        ]


def find_match(
    db_product: DbProduct,
    csv_products: dict[str, CsvProduct],
) -> Match | None:
    """Find the best matching CSV product for a database product."""
    db_normalized = normalize(db_product.name)

    # 1. Try exact match
    if db_normalized in csv_products:
        return Match(
            db_product=db_product,
            csv_product=csv_products[db_normalized],
            match_type="exact",
            confidence=1.0,
        )

    # 2. Try substring match (CSV name in DB name, or DB name in CSV name)
    for csv_normalized, csv_product in csv_products.items():
        if csv_normalized in db_normalized or db_normalized in csv_normalized:
            # Calculate confidence based on length ratio
            shorter = min(len(csv_normalized), len(db_normalized))
            longer = max(len(csv_normalized), len(db_normalized))
            confidence = shorter / longer if longer > 0 else 0
            return Match(
                db_product=db_product,
                csv_product=csv_product,
                match_type="substring",
                confidence=confidence,
            )

    # 3. Try fuzzy match
    best_match = None
    best_score = 0.0

    for csv_normalized, csv_product in csv_products.items():
        score = SequenceMatcher(None, db_normalized, csv_normalized).ratio()
        if score > best_score and score >= FUZZY_THRESHOLD:
            best_score = score
            best_match = Match(
                db_product=db_product,
                csv_product=csv_product,
                match_type="fuzzy",
                confidence=score,
            )

    return best_match


def update_msrp(conn, product_id: str, msrp: Decimal) -> None:
    """Update MSRP for a single product."""
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE products SET msrp = %s, updated_at = NOW() WHERE id = %s",
            (msrp, product_id),
        )


def main():
    parser = argparse.ArgumentParser(
        description="Update product MSRP from CSV file",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Apply updates to database (default: dry-run only)",
    )
    parser.add_argument(
        "--csv",
        type=Path,
        default=DEFAULT_CSV_PATH,
        help=f"Path to CSV file (default: {DEFAULT_CSV_PATH})",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).parent / "msrp_matches.csv",
        help="Path to output match report CSV",
    )
    args = parser.parse_args()

    # Validate CSV exists
    if not args.csv.exists():
        print(f"Error: CSV file not found: {args.csv}")
        sys.exit(1)

    print(f"Loading CSV from: {args.csv}")
    csv_products = load_csv(args.csv)
    print(f"Loaded {len(csv_products)} products from CSV")

    print("\nConnecting to database...")
    conn = get_connection()
    print("Connected successfully")

    print("\nLoading products from database...")
    db_products = load_db_products(conn)
    print(f"Loaded {len(db_products)} active products from database")

    # Find matches
    matches: list[Match] = []
    unmatched_db: list[DbProduct] = []

    print("\nMatching products...")
    for db_product in db_products:
        match = find_match(db_product, csv_products)
        if match:
            matches.append(match)
        else:
            unmatched_db.append(db_product)

    # Track which CSV products were matched
    matched_csv_names = {normalize(m.csv_product.name) for m in matches}
    unmatched_csv = [
        p for norm, p in csv_products.items() if norm not in matched_csv_names
    ]

    # Print summary
    print(f"\n{'='*60}")
    print("MATCH SUMMARY")
    print(f"{'='*60}")
    print(f"Total DB products:     {len(db_products)}")
    print(f"Total CSV products:    {len(csv_products)}")
    print(f"Matched:               {len(matches)}")
    print(f"Unmatched (DB):        {len(unmatched_db)}")
    print(f"Unmatched (CSV):       {len(unmatched_csv)}")

    # Count by match type
    exact_count = sum(1 for m in matches if m.match_type == "exact")
    substring_count = sum(1 for m in matches if m.match_type == "substring")
    fuzzy_count = sum(1 for m in matches if m.match_type == "fuzzy")
    print(f"\nMatch types:")
    print(f"  Exact:      {exact_count}")
    print(f"  Substring:  {substring_count}")
    print(f"  Fuzzy:      {fuzzy_count}")

    # Write match report
    print(f"\nWriting match report to: {args.output}")
    with open(args.output, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow([
            "DB ID", "DB Name", "DB SKU", "Current MSRP",
            "CSV Name", "CSV Price", "CSV Group",
            "Match Type", "Confidence"
        ])
        for m in sorted(matches, key=lambda x: x.db_product.name):
            writer.writerow([
                m.db_product.id,
                m.db_product.name,
                m.db_product.sku or "",
                str(m.db_product.msrp) if m.db_product.msrp else "",
                m.csv_product.name,
                str(m.csv_product.price),
                m.csv_product.group,
                m.match_type,
                f"{m.confidence:.2f}",
            ])

    # Print some example matches
    print(f"\n{'='*60}")
    print("SAMPLE MATCHES (first 10)")
    print(f"{'='*60}")
    for m in matches[:10]:
        current = f"${m.db_product.msrp}" if m.db_product.msrp else "null"
        new = f"${m.csv_product.price}"
        print(f"  [{m.match_type:9}] {m.db_product.name[:40]:<40}")
        print(f"              -> {m.csv_product.name[:40]:<40} {current} -> {new}")

    # Print unmatched DB products
    if unmatched_db:
        print(f"\n{'='*60}")
        print("UNMATCHED DATABASE PRODUCTS (first 20)")
        print(f"{'='*60}")
        for p in unmatched_db[:20]:
            print(f"  {p.name}")
        if len(unmatched_db) > 20:
            print(f"  ... and {len(unmatched_db) - 20} more")

    # Apply updates if requested
    if args.apply:
        print(f"\n{'='*60}")
        print("APPLYING UPDATES")
        print(f"{'='*60}")

        updated = 0
        skipped = 0
        for m in matches:
            # Skip if MSRP already matches
            if m.db_product.msrp == m.csv_product.price:
                skipped += 1
                continue

            update_msrp(conn, m.db_product.id, m.csv_product.price)
            updated += 1

        conn.commit()
        print(f"Updated: {updated} products")
        print(f"Skipped (already correct): {skipped} products")
    else:
        print(f"\n{'='*60}")
        print("DRY RUN - No changes made")
        print("Run with --apply to update the database")
        print(f"{'='*60}")

    conn.close()
    print("\nDone!")


if __name__ == "__main__":
    main()
