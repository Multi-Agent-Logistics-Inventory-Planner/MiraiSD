#!/usr/bin/env python3

import argparse
import os
import sys
from pathlib import Path
from dotenv import load_dotenv

env_path = Path(__file__).parent.parent.parent / ".env"
env_loaded = load_dotenv(env_path)
env_path_str = str(env_path.resolve())

sys.path.insert(0, str(Path(__file__).parent / "src"))

from src.adapters.slack_notifier import AlertMessage, SlackNotifier
from src import config


def main():
    # Get webhook URL from env or config (check both)
    env_webhook = os.getenv("SLACK_WEBHOOK_URL", "")
    config_webhook = getattr(config, "SLACK_WEBHOOK_URL", "")
    default_webhook = env_webhook or config_webhook
    
    parser = argparse.ArgumentParser(description="Test Slack notifications")
    parser.add_argument(
        "--webhook-url",
        type=str,
        default=default_webhook,
        help="Slack webhook URL (or set SLACK_WEBHOOK_URL env var)",
    )
    parser.add_argument(
        "--channel",
        type=str,
        default=os.getenv("SLACK_CHANNEL") or getattr(config, "SLACK_CHANNEL", "#testing-wiggly"),
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

    if not args.webhook_url:
        env_webhook = os.getenv("SLACK_WEBHOOK_URL", "")
        config_webhook = getattr(config, "SLACK_WEBHOOK_URL", "")
        args.webhook_url = env_webhook or config_webhook

    if not args.webhook_url:
        print("Error: SLACK_WEBHOOK_URL not set!")
        print("\nDebug info:")
        print(f"  Looking for .env file at: {env_path_str}")
        print(f"  .env file exists: {env_path.exists()}")
        print(f"  .env file loaded: {env_loaded}")
        print(f"  Environment variable: {repr(os.getenv('SLACK_WEBHOOK_URL', 'NOT SET'))}")
        print(f"  Config value: {repr(getattr(config, 'SLACK_WEBHOOK_URL', 'NOT FOUND'))}")
        print("\nTo fix this:")
        print("  1. Create a .env file at the project root with:")
        print("     SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/WEBHOOK/URL")
        print("     SLACK_CHANNEL=#inventory-alerts")
        print(f"\n     File location: {env_path_str}")
        print("\n  2. Or set it as an environment variable:")
        print("     export SLACK_WEBHOOK_URL='https://hooks.slack.com/services/YOUR/WEBHOOK/URL'")
        print("\n  3. Or pass it as an argument:")
        print("     python test_slack.py --webhook-url 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'")
        sys.exit(1)

    print("Test Configuration")
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

