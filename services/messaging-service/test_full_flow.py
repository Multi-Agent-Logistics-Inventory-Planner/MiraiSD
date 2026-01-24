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

# Add src to path
sys.path.insert(0, str(Path(__file__).parent / "src"))

from src.adapters.supabase_repo import SupabaseRepo
from src.adapters.slack_notifier import AlertMessage, SlackNotifier
from src import config


def main():
    parser = argparse.ArgumentParser(description="Test full notification flow (database + Slack)")
    parser.add_argument(
        "--item-id",
        type=str,
        default=None,
        help="Product/item UUID (default: auto-fetches from database)",
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
        help="Current quantity (should be below reorder point)",
    )
    parser.add_argument(
        "--reorder-point",
        type=int,
        default=10,
        help="Reorder point threshold",
    )
    parser.add_argument(
        "--skip-slack",
        action="store_true",
        help="Skip Slack notification (test database only)",
    )
    parser.add_argument(
        "--skip-database",
        action="store_true",
        help="Skip database notification (test Slack only)",
    )

    args = parser.parse_args()

    # Determine notification type and severity
    notification_type = "OUT_OF_STOCK" if args.quantity == 0 else "LOW_STOCK"
    severity = "CRITICAL" if args.quantity == 0 else "WARNING"

    # Create notification message
    if args.quantity == 0:
        message = f"{args.product_name} is now out of stock"
    else:
        quantity_diff = args.reorder_point - args.quantity
        message = f"{args.product_name} stock is low ({args.quantity} units, {quantity_diff} below reorder point)"

    if args.product_sku:
        message += f" (SKU: {args.product_sku})"

    # If item_id not provided and we're testing database, fetch a real product ID
    repo = None
    if not args.skip_database:
        if args.item_id is None:
            print("\nFetching a real product ID from database...")
            try:
                from src.adapters.supabase_repo import SupabaseRepo
                repo = SupabaseRepo()
                from sqlalchemy import text as sql_text
                query = sql_text("SELECT id, name, sku FROM products LIMIT 1")
                with repo._engine.connect() as conn:
                    result = conn.execute(query)
                    row = result.fetchone()
                    if row:
                        args.item_id = str(row.id)
                        if args.product_name == "Test Product":
                            args.product_name = row.name or args.product_name
                        if args.product_sku == "TEST-001" and row.sku:
                            args.product_sku = row.sku
                        print(f"✓ Using product: {args.product_name} (ID: {args.item_id})")
                    else:
                        print("\n✗ No products found in database!")
                        print("  Please provide --item-id with an existing product UUID")
                        sys.exit(1)
            except Exception as e:
                print(f"\n✗ Failed to fetch product: {e}")
                print("  Please provide --item-id with an existing product UUID")
                sys.exit(1)
        else:
            # Validate that the provided item_id exists
            try:
                uuid.UUID(args.item_id)
                from src.adapters.supabase_repo import SupabaseRepo
                repo = SupabaseRepo()
                from sqlalchemy import text as sql_text
                query = sql_text("SELECT id, name, sku FROM products WHERE id = :product_id")
                with repo._engine.connect() as conn:
                    result = conn.execute(query, {"product_id": uuid.UUID(args.item_id)})
                    row = result.fetchone()
                    if row:
                        if args.product_name == "Test Product":
                            args.product_name = row.name or args.product_name
                        if args.product_sku == "TEST-001" and row.sku:
                            args.product_sku = row.sku
                        print(f"✓ Found product: {args.product_name} (ID: {args.item_id})")
                    else:
                        print(f"\n✗ Product with ID {args.item_id} not found!")
                        sys.exit(1)
            except ValueError:
                print(f"\n✗ Invalid UUID format: {args.item_id}")
                sys.exit(1)
            except Exception as e:
                print(f"\n✗ Failed to validate product: {e}")
                sys.exit(1)

    print("\nFull Flow Test")
    print("=" * 60)
    print(f"Product: {args.product_name} ({args.product_sku})")
    print(f"Item ID: {args.item_id}")
    print(f"Quantity: {args.quantity}")
    print(f"Reorder Point: {args.reorder_point}")
    print(f"Notification Type: {notification_type}")
    print(f"Severity: {severity}")
    print(f"Message: {message}")
    print("=" * 60)

    results = {"database": None, "slack": None}

    # Test database notification
    if not args.skip_database:
        print("\n[1/2] Testing database notification...")
        db_url = os.getenv("SUPABASE_DB_URL") or getattr(config, "SUPABASE_DB_URL", "")
        if not db_url:
            print("✗ SUPABASE_DB_URL not set, skipping database test")
            results["database"] = False
        else:
            try:
                if repo is None:
                    from src.adapters.supabase_repo import SupabaseRepo
                    repo = SupabaseRepo()
                metadata = {
                    "product_name": args.product_name,
                    "product_sku": args.product_sku,
                    "current_quantity": args.quantity,
                    "reorder_point": args.reorder_point,
                    "quantity_diff": args.reorder_point - args.quantity,
                }
                notification_id = repo.create_notification(
                    notification_type=notification_type,
                    severity=severity,
                    message=message,
                    item_id=args.item_id,
                    metadata=metadata,
                )
                if notification_id:
                    print(f"✓ Database notification created: {notification_id}")
                    results["database"] = True
                else:
                    print("✗ Failed to create database notification")
                    results["database"] = False
            except Exception as e:
                print(f"✗ Database notification failed: {e}")
                results["database"] = False
    else:
        print("\n[1/2] Skipping database test")
        results["database"] = None

    # Test Slack notification
    if not args.skip_slack:
        print("\n[2/2] Testing Slack notification...")
        webhook_url = os.getenv("SLACK_WEBHOOK_URL") or getattr(config, "SLACK_WEBHOOK_URL", "")
        if not webhook_url:
            print("✗ SLACK_WEBHOOK_URL not set, skipping Slack test")
            results["slack"] = False
        else:
            try:
                notifier = SlackNotifier()
                alert_message = AlertMessage(
                    product_name=args.product_name,
                    product_sku=args.product_sku,
                    current_quantity=args.quantity,
                    reorder_point=args.reorder_point,
                    product_id=args.item_id,
                )
                success = notifier.send_alert(alert_message)
                if success:
                    print("✓ Slack notification sent")
                    results["slack"] = True
                else:
                    print("✗ Failed to send Slack notification")
                    results["slack"] = False
            except Exception as e:
                print(f"✗ Slack notification failed: {e}")
                results["slack"] = False
    else:
        print("\n[2/2] Skipping Slack test")
        results["slack"] = None

    # Summary
    print("\n" + "=" * 60)
    print("Test Summary")
    print("=" * 60)
    if results["database"] is not None:
        status = "✓ PASS" if results["database"] else "✗ FAIL"
        print(f"Database: {status}")
    if results["slack"] is not None:
        status = "✓ PASS" if results["slack"] else "✗ FAIL"
        print(f"Slack: {status}")

    # Exit code
    all_passed = all(
        v for v in results.values() if v is not None
    )
    sys.exit(0 if all_passed else 1)


if __name__ == "__main__":
    main()

