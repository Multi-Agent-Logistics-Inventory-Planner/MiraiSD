"""Slack notifier for sending alerts."""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from typing import Optional

import requests

from .. import config

logger = logging.getLogger(__name__)


@dataclass
class AlertMessage:
    """Alert message data model."""

    product_name: str
    product_sku: str | None
    current_quantity: int
    reorder_point: int
    product_id: str


class SlackNotifier:
    """Slack notifier for sending inventory alerts."""

    def __init__(self, webhook_url: str | None = None, channel: str | None = None):
        self._webhook_url = webhook_url or config.SLACK_WEBHOOK_URL
        self._channel = channel or config.SLACK_CHANNEL
        self._enabled = config.SLACK_ENABLED

        # Validate webhook URL if provided and not empty
        if self._webhook_url:
            self._validate_webhook_url(self._webhook_url)

    def _validate_webhook_url(self, url: str) -> None:
        """Validate that webhook URL is a legitimate Slack webhook.

        Args:
            url: The webhook URL to validate

        Raises:
            ValueError: If the URL is not a valid Slack webhook URL
        """
        # Check if URL starts with Slack webhook prefix
        if not url.startswith('https://hooks.slack.com/services/'):
            raise ValueError(
                "Invalid Slack webhook URL. Must start with "
                "'https://hooks.slack.com/services/'"
            )

        # Validate URL format: https://hooks.slack.com/services/{T-ID}/{B-ID}/{TOKEN}
        # T-ID and B-ID are 9+ chars, TOKEN is 24+ chars
        pattern = r'^https://hooks\.slack\.com/services/[A-Z0-9]{9,}/[A-Z0-9]{9,}/[A-Za-z0-9]{24,}$'
        if not re.match(pattern, url):
            raise ValueError("Malformed Slack webhook URL")

    def send_alert(self, alert: AlertMessage) -> bool:
        """Send an alert to Slack.

        Args:
            alert: Alert message data

        Returns:
            True if sent successfully, False otherwise
        """
        if not self._enabled:
            logger.debug("Slack notifications are disabled")
            return False

        if not self._webhook_url:
            logger.warning("SLACK_WEBHOOK_URL is not set, cannot send alert")
            return False

        message = self._format_message(alert)

        try:
            response = requests.post(
                self._webhook_url,
                json=message,
                timeout=10,
            )
            response.raise_for_status()
            logger.info("Sent Slack alert for product %s (%s)", alert.product_name, alert.product_sku)
            return True
        except requests.RequestException as e:
            logger.error("Failed to send Slack alert: %s", e)
            return False

    def send_notification(self, notification: dict) -> bool:
        """Send any notification type to Slack.

        Generic method for delivering notifications from the database,
        including those created by Java (UNASSIGNED_ITEM, etc.).

        Args:
            notification: Dict with type, severity, message, item_id, metadata

        Returns:
            True if sent successfully, False otherwise
        """
        if not self._enabled:
            logger.debug("Slack notifications are disabled")
            return False

        if not self._webhook_url:
            logger.warning("SLACK_WEBHOOK_URL is not set, cannot send notification")
            return False

        message = self._format_notification(notification)

        try:
            response = requests.post(
                self._webhook_url,
                json=message,
                timeout=10,
            )
            response.raise_for_status()
            logger.info(
                "Sent Slack notification: type=%s, id=%s",
                notification.get("type"),
                notification.get("id"),
            )
            return True
        except requests.RequestException as e:
            logger.error("Failed to send Slack notification: %s", e)
            return False

    def _format_notification(self, notification: dict) -> dict:
        """Format any notification for Slack.

        Args:
            notification: Dict with type, severity, message, item_id, metadata

        Returns:
            Formatted Slack message payload
        """
        notif_type = notification.get("type", "ALERT")
        severity = notification.get("severity", "INFO")
        message = notification.get("message", "")
        item_id = notification.get("item_id")
        metadata = notification.get("metadata") or {}

        # Color by severity
        color_map = {
            "CRITICAL": "#DC2626",  # Red
            "WARNING": "#F59E0B",   # Amber
            "INFO": "#3B82F6",      # Blue
        }
        color = color_map.get(severity, "#6B7280")  # Gray default

        # Header text by type
        header_map = {
            "UNASSIGNED_ITEM": "Unassigned Item Alert",
            "LOW_STOCK": "Low Stock Alert",
            "OUT_OF_STOCK": "Out of Stock Alert",
            "REORDER_SUGGESTION": "Reorder Suggestion",
            "EXPIRY_WARNING": "Expiry Warning",
            "SYSTEM_ALERT": "System Alert",
        }
        header = header_map.get(notif_type, f"{notif_type} Alert")

        # Build fields from metadata
        fields = []
        if item_id:
            fields.append({
                "type": "mrkdwn",
                "text": f"*Item ID:*\n`{item_id}`",
            })

        # Add useful metadata fields
        for key in ["product_name", "sku", "location_code", "quantity", "threshold"]:
            if key in metadata:
                display_key = key.replace("_", " ").title()
                fields.append({
                    "type": "mrkdwn",
                    "text": f"*{display_key}:*\n{metadata[key]}",
                })

        blocks = [
            {
                "type": "header",
                "text": {
                    "type": "plain_text",
                    "text": header,
                },
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": message,
                },
            },
        ]

        if fields:
            blocks.append({
                "type": "section",
                "fields": fields[:8],  # Slack limit
            })

        return {
            "channel": self._channel,
            "text": f"{header}: {message[:100]}",
            "attachments": [{"color": color, "blocks": blocks}],
        }

    def _format_message(self, alert: AlertMessage) -> dict:
        """Format alert message for Slack (legacy method).

        Args:
            alert: Alert message data

        Returns:
            Formatted Slack message payload
        """
        sku_text = f" (SKU: {alert.product_sku})" if alert.product_sku else ""
        quantity_diff = alert.current_quantity - alert.reorder_point

        blocks = [
            {
                "type": "header",
                "text": {
                    "type": "plain_text",
                    "text": "Inventory Reorder Alert",
                },
            },
            {
                "type": "section",
                "fields": [
                    {
                        "type": "mrkdwn",
                        "text": f"*Product:*\n{alert.product_name}{sku_text}",
                    },
                    {
                        "type": "mrkdwn",
                        "text": f"*Product ID:*\n`{alert.product_id}`",
                    },
                    {
                        "type": "mrkdwn",
                        "text": f"*Current Quantity:*\n{alert.current_quantity}",
                    },
                    {
                        "type": "mrkdwn",
                        "text": f"*Reorder Point:*\n{alert.reorder_point}",
                    },
                ],
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"*Status:* Quantity is {abs(quantity_diff)} units below reorder point. Please restock immediately.",
                },
            },
        ]

        return {
            "channel": self._channel,
            "text": f"Inventory Alert: {alert.product_name} - Quantity below reorder point",
            "blocks": blocks,
        }

