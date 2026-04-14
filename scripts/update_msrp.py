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
        reader = csv.DictReader(f, skipinitialspace=True)
        for row in reader:
            # Strip whitespace from keys in case of inconsistent formatting
            row = {k.strip(): v for k, v in row.items()}
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
    """Load all parent products from database (excludes child products)."""
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute("""
            SELECT id, name, sku, msrp
            FROM products
            WHERE parent_id IS NULL
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


def prompt_match(match: Match, index: int, total: int) -> str:
    """Prompt user to approve/reject a match. Returns 'y', 'n', 'a' (all), or 'q' (quit)."""
    current = f"${match.db_product.msrp}" if match.db_product.msrp else "null"
    new = f"${match.csv_product.price}"

    print(f"\n[{index}/{total}] {match.match_type.upper()} match (confidence: {match.confidence:.0%})")
    print(f"  DB:  {match.db_product.name}")
    print(f"  CSV: {match.csv_product.name}")
    print(f"  SKU: {match.db_product.sku or 'N/A'}  |  MSRP: {current} -> {new}")

    while True:
        response = input("  Apply? [y]es / [n]o / [a]ll remaining / [q]uit: ").strip().lower()
        if response in ("y", "yes", ""):
            return "y"
        elif response in ("n", "no"):
            return "n"
        elif response in ("a", "all"):
            return "a"
        elif response in ("q", "quit"):
            return "q"
        print("  Invalid input. Enter y, n, a, or q.")


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
        "--interactive", "-i",
        action="store_true",
        help="Interactively approve each match before applying (use with --apply)",
    )
    parser.add_argument(
        "--review-exact",
        action="store_true",
        help="Also review exact matches in interactive mode (default: auto-approve exact)",
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

    # Write match report as formatted text file
    report_path = args.output.with_suffix(".txt")
    print(f"\nWriting match report to: {report_path}")

    # Group matches by type
    exact_matches = [m for m in matches if m.match_type == "exact"]
    substring_matches = [m for m in matches if m.match_type == "substring"]
    fuzzy_matches = [m for m in matches if m.match_type == "fuzzy"]

    with open(report_path, "w", encoding="utf-8") as f:
        f.write("=" * 80 + "\n")
        f.write("MSRP UPDATE MATCH REPORT\n")
        f.write("=" * 80 + "\n\n")

        f.write(f"Total DB products:     {len(db_products)}\n")
        f.write(f"Total CSV products:    {len(csv_products)}\n")
        f.write(f"Matched:               {len(matches)}\n")
        f.write(f"Unmatched (DB):        {len(unmatched_db)}\n")
        f.write(f"Unmatched (CSV):       {len(unmatched_csv)}\n\n")

        # Exact matches section
        f.write("=" * 80 + "\n")
        f.write(f"EXACT MATCHES ({len(exact_matches)})\n")
        f.write("These are 100% name matches - safe to update automatically\n")
        f.write("=" * 80 + "\n\n")
        for m in sorted(exact_matches, key=lambda x: x.db_product.name):
            current = f"${m.db_product.msrp}" if m.db_product.msrp else "null"
            new = f"${m.csv_product.price}"
            change = "" if m.db_product.msrp == m.csv_product.price else " [CHANGE]"
            f.write(f"  {m.db_product.name}\n")
            f.write(f"    SKU: {m.db_product.sku or 'N/A'}  |  MSRP: {current} -> {new}{change}\n\n")

        # Substring matches section
        f.write("\n" + "=" * 80 + "\n")
        f.write(f"SUBSTRING MATCHES ({len(substring_matches)})\n")
        f.write("One name contains the other - review recommended\n")
        f.write("=" * 80 + "\n\n")
        for m in sorted(substring_matches, key=lambda x: -x.confidence):
            current = f"${m.db_product.msrp}" if m.db_product.msrp else "null"
            new = f"${m.csv_product.price}"
            change = "" if m.db_product.msrp == m.csv_product.price else " [CHANGE]"
            f.write(f"  DB:  {m.db_product.name}\n")
            f.write(f"  CSV: {m.csv_product.name}\n")
            f.write(f"    Confidence: {m.confidence:.0%}  |  SKU: {m.db_product.sku or 'N/A'}  |  MSRP: {current} -> {new}{change}\n\n")

        # Fuzzy matches section
        f.write("\n" + "=" * 80 + "\n")
        f.write(f"FUZZY MATCHES ({len(fuzzy_matches)})\n")
        f.write("Similar names but not exact - careful review required\n")
        f.write("=" * 80 + "\n\n")
        for m in sorted(fuzzy_matches, key=lambda x: -x.confidence):
            current = f"${m.db_product.msrp}" if m.db_product.msrp else "null"
            new = f"${m.csv_product.price}"
            change = "" if m.db_product.msrp == m.csv_product.price else " [CHANGE]"
            f.write(f"  DB:  {m.db_product.name}\n")
            f.write(f"  CSV: {m.csv_product.name}\n")
            f.write(f"    Confidence: {m.confidence:.0%}  |  SKU: {m.db_product.sku or 'N/A'}  |  MSRP: {current} -> {new}{change}\n\n")

        # Unmatched DB products section
        f.write("\n" + "=" * 80 + "\n")
        f.write(f"UNMATCHED DATABASE PRODUCTS ({len(unmatched_db)})\n")
        f.write("These products have no matching entry in the CSV\n")
        f.write("=" * 80 + "\n\n")
        for p in sorted(unmatched_db, key=lambda x: x.name):
            current = f"${p.msrp}" if p.msrp else "null"
            f.write(f"  {p.name}  |  SKU: {p.sku or 'N/A'}  |  Current MSRP: {current}\n")

        # Unmatched CSV products section
        if unmatched_csv:
            f.write("\n\n" + "=" * 80 + "\n")
            f.write(f"UNMATCHED CSV PRODUCTS ({len(unmatched_csv)})\n")
            f.write("These CSV entries have no matching product in the database\n")
            f.write("=" * 80 + "\n\n")
            for p in sorted(unmatched_csv, key=lambda x: x.name):
                f.write(f"  {p.name}  |  Price: ${p.price}  |  Group: {p.group}\n")

    # Also write CSV for data processing
    print(f"Writing CSV data to: {args.output}")
    with open(args.output, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow([
            "Match Type", "Confidence", "DB Name", "CSV Name",
            "Current MSRP", "New MSRP", "Will Change", "DB ID", "DB SKU", "CSV Group"
        ])
        for m in sorted(matches, key=lambda x: (x.match_type, -x.confidence, x.db_product.name)):
            will_change = "YES" if m.db_product.msrp != m.csv_product.price else "NO"
            writer.writerow([
                m.match_type.upper(),
                f"{m.confidence:.0%}",
                m.db_product.name,
                m.csv_product.name,
                str(m.db_product.msrp) if m.db_product.msrp else "",
                str(m.csv_product.price),
                will_change,
                m.db_product.id,
                m.db_product.sku or "",
                m.csv_product.group,
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

        # Filter matches that need updating
        pending_updates = [m for m in matches if m.db_product.msrp != m.csv_product.price]
        already_correct = len(matches) - len(pending_updates)

        if not pending_updates:
            print("No updates needed - all MSRPs already match.")
        elif args.interactive:
            # Interactive mode
            print(f"\n{len(pending_updates)} products need MSRP updates.")
            print("Exact matches will be auto-approved unless --review-exact is set.")
            print("Press Enter to approve, or type your choice.\n")

            updated = 0
            rejected = 0
            auto_approved = 0
            approve_all = False

            # Sort: exact first, then substring, then fuzzy (by confidence desc)
            sorted_updates = sorted(
                pending_updates,
                key=lambda x: (
                    0 if x.match_type == "exact" else 1 if x.match_type == "substring" else 2,
                    -x.confidence
                )
            )

            for i, m in enumerate(sorted_updates, 1):
                # Auto-approve exact matches unless --review-exact
                if m.match_type == "exact" and not args.review_exact:
                    update_msrp(conn, m.db_product.id, m.csv_product.price)
                    auto_approved += 1
                    continue

                # Auto-approve if user selected "all"
                if approve_all:
                    update_msrp(conn, m.db_product.id, m.csv_product.price)
                    updated += 1
                    continue

                # Prompt user
                response = prompt_match(m, i, len(sorted_updates))

                if response == "y":
                    update_msrp(conn, m.db_product.id, m.csv_product.price)
                    updated += 1
                elif response == "n":
                    rejected += 1
                elif response == "a":
                    update_msrp(conn, m.db_product.id, m.csv_product.price)
                    updated += 1
                    approve_all = True
                elif response == "q":
                    print("\nQuitting. Committing approved changes so far...")
                    break

            conn.commit()
            print(f"\n{'='*60}")
            print("SUMMARY")
            print(f"{'='*60}")
            print(f"Auto-approved (exact): {auto_approved}")
            print(f"Manually approved:     {updated}")
            print(f"Rejected:              {rejected}")
            print(f"Already correct:       {already_correct}")
        else:
            # Non-interactive mode - apply all
            updated = 0
            for m in pending_updates:
                update_msrp(conn, m.db_product.id, m.csv_product.price)
                updated += 1

            conn.commit()
            print(f"Updated: {updated} products")
            print(f"Skipped (already correct): {already_correct} products")
    else:
        print(f"\n{'='*60}")
        print("DRY RUN - No changes made")
        print("Run with --apply to update the database")
        print("Run with --apply --interactive to review each match")
        print(f"{'='*60}")

    conn.close()
    print("\nDone!")


if __name__ == "__main__":
    main()
