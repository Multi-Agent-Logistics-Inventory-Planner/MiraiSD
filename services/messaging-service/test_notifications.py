#!/usr/bin/env python3

import argparse
import os
import sys
import uuid
from pathlib import Path
from dotenv import load_dotenv

# Load .env file from the project root (MiraiSD/)
env_path = Path(__file__).parent.parent.parent / ".env"
env_loaded = load_dotenv(env_path)
env_path_str = str(env_path.resolve())

# Add src to path
sys.path.insert(0, str(Path(__file__).parent / "src"))

from src.adapters.supabase_repo import SupabaseRepo
from src import config


def main():
    parser = argparse.ArgumentParser(description="Test notification creation in database")
    parser.add_argument(
        "--item-id",
        type=str,
        default=None,
        help="Product/item UUID (default: auto-fetches from database)",
    )
    parser.add_argument(
        "--type",
        type=str,
        default="LOW_STOCK",
        choices=["LOW_STOCK", "OUT_OF_STOCK", "REORDER_SUGGESTION", "EXPIRY_WARNING", "SYSTEM_ALERT"],
        help="Notification type",
    )
    parser.add_argument(
        "--severity",
        type=str,
        default="WARNING",
        choices=["INFO", "WARNING", "CRITICAL"],
        help="Severity level",
    )
    parser.add_argument(
        "--message",
        type=str,
        default=None,
        help="Notification message (default: auto-generated)",
    )
    parser.add_argument(
        "--product-name",
        type=str,
        default="Test Product",
        help="Product name for test notification",
    )
    parser.add_argument(
        "--product-sku",
        type=str,
        default="TEST-001",
        help="Product SKU for test notification",
    )
    parser.add_argument(
        "--quantity",
        type=int,
        default=5,
        help="Current quantity for metadata",
    )
    parser.add_argument(
        "--reorder-point",
        type=int,
        default=10,
        help="Reorder point for metadata",
    )
    parser.add_argument(
        "--recipient-id",
        type=str,
        default=None,
        help="Recipient user UUID (optional)",
    )

    args = parser.parse_args()

    # Check database configuration
    db_url = os.getenv("SUPABASE_DB_URL") or getattr(config, "SUPABASE_DB_URL", "")
    if not db_url:
        print("Error: SUPABASE_DB_URL not set!")
        print("\nDebug info:")
        print(f"  Looking for .env file at: {env_path_str}")
        print(f"  .env file exists: {env_path.exists()}")
        print(f"  .env file loaded: {env_loaded}")
        print(f"  Environment variable: {repr(os.getenv('SUPABASE_DB_URL', 'NOT SET'))}")
        print(f"  Config value: {repr(getattr(config, 'SUPABASE_DB_URL', 'NOT FOUND'))}")
        print("\nTo fix this:")
        print("  1. Create a .env file at the project root with:")
        print("     SUPABASE_DB_URL=postgresql://db.xxxxx.supabase.co:5432/postgres")
        print("     SUPABASE_DB_USERNAME=postgres")
        print("     SUPABASE_DB_PASSWORD=your-password")
        print(f"\n     File location: {env_path_str}")
        print("\n  2. Or set as environment variables:")
        print("     export SUPABASE_DB_URL='postgresql://...'")
        print("     export SUPABASE_DB_USERNAME='postgres'")
        print("     export SUPABASE_DB_PASSWORD='...'")
        sys.exit(1)

    # Generate message if not provided
    if args.message is None:
        if args.type == "OUT_OF_STOCK":
            args.message = f"{args.product_name} is now out of stock"
        elif args.type == "LOW_STOCK":
            quantity_diff = args.reorder_point - args.quantity
            args.message = f"{args.product_name} stock is low ({args.quantity} units, {quantity_diff} below reorder point)"
        else:
            args.message = f"Test notification for {args.product_name}"

        if args.product_sku:
            args.message += f" (SKU: {args.product_sku})"

    print("Test Configuration")
    print("=" * 60)
    print(f"Database URL: {db_url[:50]}...")
    print(f"Item ID: {args.item_id}")
    print(f"Type: {args.type}")
    print(f"Severity: {args.severity}")
    print(f"Message: {args.message}")
    print(f"Recipient ID: {args.recipient_id or 'None (all users)'}")
    print("=" * 60)

    # Create repository
    try:
        repo = SupabaseRepo()
        print("\n✓ Connected to database")
    except Exception as e:
        print(f"\n✗ Failed to connect to database: {e}")
        sys.exit(1)

    # If item_id not provided, fetch a real product ID from database
    if args.item_id is None:
        print("\nFetching a real product ID from database...")
        try:
            from sqlalchemy import text as sql_text
            query = sql_text("SELECT id, name, sku FROM products LIMIT 1")
            with repo._engine.connect() as conn:
                result = conn.execute(query)
                row = result.fetchone()
                if row:
                    args.item_id = str(row.id)
                    # Update product info if using real product
                    if args.product_name == "Test Product":
                        args.product_name = row.name or args.product_name
                    if args.product_sku == "TEST-001" and row.sku:
                        args.product_sku = row.sku
                    print(f"✓ Using product: {args.product_name} (ID: {args.item_id})")
                else:
                    print("\n✗ No products found in database!")
                    print("  You need to either:")
                    print("  1. Create a product first, or")
                    print("  2. Use --item-id with an existing product UUID")
                    sys.exit(1)
        except Exception as e:
            print(f"\n✗ Failed to fetch product: {e}")
            print("  Please provide --item-id with an existing product UUID")
            sys.exit(1)
    else:
        # Validate that the provided item_id exists
        try:
            uuid.UUID(args.item_id)  # Validate UUID format
            from sqlalchemy import text as sql_text
            query = sql_text("SELECT id, name, sku FROM products WHERE id = :product_id")
            with repo._engine.connect() as conn:
                result = conn.execute(query, {"product_id": uuid.UUID(args.item_id)})
                row = result.fetchone()
                if row:
                    # Update product info if using real product
                    if args.product_name == "Test Product":
                        args.product_name = row.name or args.product_name
                    if args.product_sku == "TEST-001" and row.sku:
                        args.product_sku = row.sku
                    print(f"✓ Found product: {args.product_name} (ID: {args.item_id})")
                else:
                    print(f"\n✗ Product with ID {args.item_id} not found in database!")
                    print("  Please use an existing product UUID")
                    sys.exit(1)
        except ValueError:
            print(f"\n✗ Invalid UUID format: {args.item_id}")
            sys.exit(1)
        except Exception as e:
            print(f"\n✗ Failed to validate product ID: {e}")
            sys.exit(1)

    # Prepare metadata
    metadata = {
        "product_name": args.product_name,
        "product_sku": args.product_sku,
        "current_quantity": args.quantity,
        "reorder_point": args.reorder_point,
        "quantity_diff": args.reorder_point - args.quantity,
        "test": True,  # Mark as test notification
    }

    # Create notification
    print("\nCreating notification in database...")
    try:
        notification_id = repo.create_notification(
            notification_type=args.type,
            severity=args.severity,
            message=args.message,
            item_id=args.item_id,
            recipient_id=args.recipient_id,
            metadata=metadata,
        )

        if notification_id:
            print(f"✓ Success! Created notification with ID: {notification_id}")
            print(f"\nYou can verify this in your database:")
            print(f"  SELECT * FROM notifications WHERE id = '{notification_id}';")
            sys.exit(0)
        else:
            print("✗ Failed to create notification (no ID returned)")
            sys.exit(1)
    except Exception as e:
        print(f"✗ Failed to create notification: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()

