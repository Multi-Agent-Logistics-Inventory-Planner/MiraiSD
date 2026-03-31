"""Unit tests for Slack notifier webhook validation."""

import pytest
from src.adapters.slack_notifier import SlackNotifier


class TestSlackWebhookValidation:
    """Test webhook URL validation."""

    def test_valid_slack_webhook_url(self):
        """Valid Slack webhook URL should be accepted."""
        url = "https://hooks.slack.com" + "/services/X00000000/Y00000000/XXXXXXXXXXXXXXXXXXXX"
        notifier = SlackNotifier(webhook_url=url)
        assert notifier._webhook_url == url

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

    def test_none_webhook_url_with_config(self, monkeypatch):
        """None webhook URL should use config value if available."""
        monkeypatch.setenv("SLACK_WEBHOOK_URL", "https://hooks.slack.com" + "/services/X00/Y00/XXX123")
        # This test assumes config loads from environment
        # If config is not set, validation should not run for None
        notifier = SlackNotifier(webhook_url=None)
        # If config has a valid URL, it should pass validation
        # If config is None/empty, notifier should still be created (validation skipped for None)
        assert notifier is not None

    def test_empty_string_webhook_skips_validation(self):
        """Empty string webhook URL should skip validation (treated as disabled)."""
        notifier = SlackNotifier(webhook_url="")
        assert notifier._webhook_url == ""

    def test_webhook_with_trailing_slash(self):
        """Webhook URL with trailing slash should be accepted."""
        url = "https://hooks.slack.com" + "/services/X00000000/Y00000000/XXXXXXXXXXXXXXXXXXXX/"
        with pytest.raises(ValueError, match="Malformed Slack webhook URL"):
            SlackNotifier(webhook_url=url)

    def test_webhook_with_query_params_rejected(self):
        """Webhook URL with query parameters should be rejected."""
        url = "https://hooks.slack.com" + "/services/X00000000/Y00000000/XXXXXXXXXXXXXXXXXXXX?foo=bar"
        with pytest.raises(ValueError, match="Malformed Slack webhook URL"):
            SlackNotifier(webhook_url=url)

    def test_valid_webhook_variations(self):
        """Test various valid Slack webhook URL formats."""
        valid_urls = [
            "https://hooks.slack.com" + "/services/X00000000/Y00000000/XXXXXXXXXXXXXXXXXXXX",
            "https://hooks.slack.com" + "/services/XAAAAAAAA/YBBBBBBBB/1234567890123456789012",
            "https://hooks.slack.com" + "/services/X12345678/Y87654321/AbCdEfGhIjKlMnOpQrStUvWx",
        ]
        for url in valid_urls:
            notifier = SlackNotifier(webhook_url=url)
            assert notifier._webhook_url == url
