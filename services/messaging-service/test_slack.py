#!/usr/bin/env python3
"""Quick test script for Slack notifications.

Usage:
    # Set webhook URL as environment variable
    export SLACK_WEBHOOK_URL="https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
    export SLACK_CHANNEL="#inventory-alerts"
    
    # Run the test
    python test_slack.py
    
    # Or test with specific values
    python test_slack.py --product-name "Test Product" --quantity 5 --reorder-point 10
"""

import argparse
import os
import sys
from pathlib import Path

# Add src to path
sys.path.insert(0, str(Path(__file__).parent / "src"))

from src.adapters.slack_notifier import AlertMessage, SlackNotifier


def main():
    parser = argparse.ArgumentParser(description="Test Slack notifications")
    parser.add_argument(
        "--webhook-url",
        type=str,
        default=os.getenv("SLACK_WEBHOOK_URL", ""),
        help="Slack webhook URL (or set SLACK_WEBHOOK_URL env var)",
    )
    parser.add_argument(
        "--channel",
        type=str,
        default=os.getenv("SLACK_CHANNEL", "#inventory-alerts"),
        help="Slack channel (or set SLACK_CHANNEL env var)",
    )
    parser.add_argument(
        "--product-name",
        type=str,
        default="Test Product",
        help="Product name for test alert",
    )
    parser.add_argument(
        "--product-sku",
        type=str,
        default="TEST-001",
        help="Product SKU for test alert",
    )
    parser.add_argument(
        "--product-id",
        type=str,
        default="test-prod-123",
        help="Product ID for test alert",
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

    args = parser.parse_args()

    # Check webhook URL
    if not args.webhook_url:
        print("Error: SLACK_WEBHOOK_URL not set!")
        print("\nSet it as an environment variable:")
        print("  export SLACK_WEBHOOK_URL='https://hooks.slack.com/services/YOUR/WEBHOOK/URL'")
        print("\nOr pass it as an argument:")
        print("  python test_slack.py --webhook-url 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'")
        sys.exit(1)

    print("test")
    print("=" * 60)
    print(f"Webhook URL: {args.webhook_url[:50]}...")
    print(f"Channel: {args.channel}")
    print(f"Product: {args.product_name} ({args.product_sku})")
    print(f"Quantity: {args.quantity}")
    print(f"Reorder Point: {args.reorder_point}")
    print("=" * 60)

    # Create notifier
    notifier = SlackNotifier(webhook_url=args.webhook_url, channel=args.channel)

    # Create test alert
    alert = AlertMessage(
        product_name=args.product_name,
        product_sku=args.product_sku,
        current_quantity=args.quantity,
        reorder_point=args.reorder_point,
        product_id=args.product_id,
    )

    # Send alert
    print("\nSending test alert...")
    success = notifier.send_alert(alert)

    if success:
        print("Success.")
        sys.exit(0)
    else:
        print("Failed to send alert.")
        sys.exit(1)


if __name__ == "__main__":
    main()

