"""Slack notifier for sending alerts."""

from __future__ import annotations

import logging
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

    def _format_message(self, alert: AlertMessage) -> dict:
        """Format alert message for Slack.

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

