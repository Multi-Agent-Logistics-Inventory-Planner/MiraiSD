"""Slack notifier for sending alerts and review summaries."""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from datetime import date
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

    # -------------------------------------------------------------------------
    # Review summary methods
    # -------------------------------------------------------------------------

    def send_daily_review_summary(
        self,
        counts: list[tuple[str, int]],
        target_date: date | None = None,
        channel: str | None = None,
    ) -> bool:
        """Send daily review summary to Slack.

        Args:
            counts: List of (employee_name, review_count) tuples.
            target_date: Date of the summary. Defaults to today.
            channel: Slack channel. Defaults to REVIEW_SLACK_CHANNEL.

        Returns:
            True if sent successfully, False otherwise.
        """
        if not self._enabled:
            logger.debug("Slack notifications are disabled")
            return False

        target = target_date or date.today()
        dest_channel = channel or config.REVIEW_SLACK_CHANNEL

        if not counts:
            # Send "no reviews" message
            text = f"{target.strftime('%B %d, %Y')}: No employee mentions in reviews today."
            return self._send_simple_message(dest_channel, text)

        # Format the summary
        total = sum(c[1] for c in counts)
        date_str = target.strftime("%B %d, %Y")

        lines = [
            f"*{name}*: {count} review{'s' if count > 1 else ''}"
            for name, count in counts
        ]

        blocks = [
            {
                "type": "header",
                "text": {
                    "type": "plain_text",
                    "text": f"Daily Review Summary - {date_str}",
                },
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"*Total Reviews:* {total}",
                },
            },
            {"type": "divider"},
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": "\n".join(lines),
                },
            },
        ]

        return self._send_blocks(dest_channel, blocks, f"Daily Review Summary - {date_str}")

    def send_monthly_review_summary(
        self,
        totals: list[tuple[str, int]],
        year: int,
        month: int,
        channel: str | None = None,
    ) -> bool:
        """Send monthly review leaderboard to Slack.

        Args:
            totals: List of (employee_name, total_count) tuples, sorted by count desc.
            year: Year of the summary.
            month: Month of the summary (1-12).
            channel: Slack channel. Defaults to REVIEW_SLACK_CHANNEL.

        Returns:
            True if sent successfully, False otherwise.
        """
        if not self._enabled:
            logger.debug("Slack notifications are disabled")
            return False

        dest_channel = channel or config.REVIEW_SLACK_CHANNEL
        month_name = date(year, month, 1).strftime("%B %Y")

        if not totals:
            text = f"{month_name} Monthly Totals: No employee mentions this month."
            return self._send_simple_message(dest_channel, text)

        total = sum(t[1] for t in totals)

        # Build leaderboard with medals for top 3
        lines = []
        for i, (name, count) in enumerate(totals[:10], 1):
            medal = {1: ":first_place_medal:", 2: ":second_place_medal:", 3: ":third_place_medal:"}.get(i, "")
            prefix = f"{medal} " if medal else f"{i}. "
            lines.append(f"{prefix}*{name}*: {count} review{'s' if count > 1 else ''}")

        blocks = [
            {
                "type": "header",
                "text": {
                    "type": "plain_text",
                    "text": f"Monthly Review Leaderboard - {month_name}",
                },
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"*Total employee mentions:* {total}",
                },
            },
            {"type": "divider"},
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": "\n".join(lines),
                },
            },
        ]

        return self._send_blocks(dest_channel, blocks, f"Monthly Review Leaderboard - {month_name}")

    def _send_simple_message(self, channel: str, text: str) -> bool:
        """Send a simple text message to Slack.

        Args:
            channel: Slack channel.
            text: Message text.

        Returns:
            True if sent successfully, False otherwise.
        """
        if not self._webhook_url:
            logger.warning("SLACK_WEBHOOK_URL is not set")
            return False

        try:
            response = requests.post(
                self._webhook_url,
                json={"channel": channel, "text": text},
                timeout=10,
            )
            response.raise_for_status()
            logger.info("Sent Slack message to %s", channel)
            return True
        except requests.RequestException as e:
            logger.error("Failed to send Slack message: %s", e)
            return False

    def _send_blocks(self, channel: str, blocks: list[dict], fallback_text: str) -> bool:
        """Send a Block Kit message to Slack.

        Args:
            channel: Slack channel.
            blocks: Block Kit blocks.
            fallback_text: Fallback text for notifications.

        Returns:
            True if sent successfully, False otherwise.
        """
        if not self._webhook_url:
            logger.warning("SLACK_WEBHOOK_URL is not set")
            return False

        try:
            response = requests.post(
                self._webhook_url,
                json={
                    "channel": channel,
                    "text": fallback_text,
                    "blocks": blocks,
                },
                timeout=10,
            )
            response.raise_for_status()
            logger.info("Sent Slack blocks message to %s", channel)
            return True
        except requests.RequestException as e:
            logger.error("Failed to send Slack blocks: %s", e)
            return False

