"""Unit tests for Slack notifier webhook validation."""

from unittest.mock import patch

import pytest
from src.adapters.slack_notifier import SlackNotifier

# Valid token must be 24+ alphanumeric chars to match the regex in _validate_webhook_url
VALID_URL = "https://hooks.slack.com" + "/services/X00000000/Y00000000/AbCdEfGhIjKlMnOpQrStUvWx"


class TestSlackWebhookValidation:
    """Test webhook URL validation."""

    def test_valid_slack_webhook_url(self):
        """Valid Slack webhook URL should be accepted."""
        notifier = SlackNotifier(webhook_url=VALID_URL)
        assert notifier._webhook_url == VALID_URL

    def test_invalid_webhook_url_raises_error(self):
        """Non-Slack URLs should raise ValueError."""
        with pytest.raises(ValueError, match="Invalid Slack webhook URL"):
            SlackNotifier(webhook_url="https://evil.com/webhook")

    def test_malformed_slack_webhook_raises_error(self):
        """Malformed Slack webhook should raise ValueError."""
        with pytest.raises(ValueError, match="Malformed Slack webhook URL"):
            SlackNotifier(webhook_url="https://hooks.slack.com" + "/services/short")

    def test_non_https_webhook_raises_error(self):
        """HTTP (non-HTTPS) webhook should be rejected."""
        with pytest.raises(ValueError, match="Invalid Slack webhook URL"):
            SlackNotifier(webhook_url="http://hooks.slack.com/services/X00/Y00/XXX")

    def test_none_webhook_url_with_empty_config(self):
        """None webhook URL with empty config should skip validation."""
        with patch("src.adapters.slack_notifier.config") as mock_config:
            mock_config.SLACK_WEBHOOK_URL = ""
            mock_config.SLACK_CHANNEL = "#test"
            mock_config.REVIEW_SLACK_WEBHOOK_URL = ""
            mock_config.SLACK_ENABLED = False
            notifier = SlackNotifier(webhook_url=None)
            assert notifier._webhook_url == ""

    def test_empty_string_webhook_skips_validation(self):
        """Empty string webhook URL should skip validation (treated as disabled)."""
        with patch("src.adapters.slack_notifier.config") as mock_config:
            mock_config.SLACK_WEBHOOK_URL = ""
            mock_config.SLACK_CHANNEL = "#test"
            mock_config.REVIEW_SLACK_WEBHOOK_URL = ""
            mock_config.SLACK_ENABLED = False
            notifier = SlackNotifier(webhook_url="")
            assert notifier._webhook_url == ""

    def test_webhook_with_trailing_slash(self):
        """Webhook URL with trailing slash should be rejected."""
        with pytest.raises(ValueError, match="Malformed Slack webhook URL"):
            SlackNotifier(webhook_url=VALID_URL + "/")

    def test_webhook_with_query_params_rejected(self):
        """Webhook URL with query parameters should be rejected."""
        with pytest.raises(ValueError, match="Malformed Slack webhook URL"):
            SlackNotifier(webhook_url=VALID_URL + "?foo=bar")

    def test_valid_webhook_variations(self):
        """Test various valid Slack webhook URL formats."""
        valid_urls = [
            "https://hooks.slack.com" + "/services/X00000000/Y00000000/AbCdEfGhIjKlMnOpQrStUvWx",
            "https://hooks.slack.com" + "/services/XAAAAAAAA/YBBBBBBBB/123456789012345678901234",
            "https://hooks.slack.com" + "/services/X12345678/Y87654321/AbCdEfGhIjKlMnOpQrStUvXxYz",
        ]
        for url in valid_urls:
            notifier = SlackNotifier(webhook_url=url)
            assert notifier._webhook_url == url

    def test_short_token_rejected(self):
        """Token shorter than 24 chars should be rejected."""
        with pytest.raises(ValueError, match="Malformed Slack webhook URL"):
            SlackNotifier(webhook_url="https://hooks.slack.com" + "/services/X00000000/Y00000000/ShortToken123")
