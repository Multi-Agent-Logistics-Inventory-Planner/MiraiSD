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
            mock_config.SWAP_SLACK_WEBHOOK_URL = ""
            mock_config.SLACK_ENABLED = False
            notifier = SlackNotifier(webhook_url=None)
            assert notifier._webhook_url == ""

    def test_empty_string_webhook_skips_validation(self):
        """Empty string webhook URL should skip validation (treated as disabled)."""
        with patch("src.adapters.slack_notifier.config") as mock_config:
            mock_config.SLACK_WEBHOOK_URL = ""
            mock_config.SLACK_CHANNEL = "#test"
            mock_config.REVIEW_SLACK_WEBHOOK_URL = ""
            mock_config.SWAP_SLACK_WEBHOOK_URL = ""
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


# ---------------------------------------------------------------------------
# Display change notifications
# ---------------------------------------------------------------------------

SWAP_URL = "https://hooks.slack.com" + "/services/SWAP00000/SWAP11111/AbCdEfGhIjKlMnOpQrStUvWx"
MAIN_URL = "https://hooks.slack.com" + "/services/MAIN00000/MAIN11111/AbCdEfGhIjKlMnOpQrStUvWx"


def _build_notifier(monkeypatch):
    """Construct a SlackNotifier with predictable webhooks and channels."""
    from src.adapters import slack_notifier as mod

    monkeypatch.setattr(mod.config, "SLACK_WEBHOOK_URL", MAIN_URL)
    monkeypatch.setattr(mod.config, "SLACK_CHANNEL", "#inventory-alerts")
    monkeypatch.setattr(mod.config, "REVIEW_SLACK_WEBHOOK_URL", "")
    monkeypatch.setattr(mod.config, "REVIEW_SLACK_CHANNEL", "#piggly-review")
    monkeypatch.setattr(mod.config, "SWAP_SLACK_WEBHOOK_URL", SWAP_URL)
    monkeypatch.setattr(mod.config, "SWAP_SLACK_CHANNEL", "#machine-swap")
    monkeypatch.setattr(mod.config, "SLACK_ENABLED", True)
    return mod.SlackNotifier()


class TestDisplayNotificationRouting:
    """Display change notification types must route to the swap webhook + channel."""

    @pytest.mark.parametrize(
        "notif_type",
        ["DISPLAY_SET", "DISPLAY_REMOVED", "DISPLAY_SWAP", "DISPLAY_RENEWED"],
    )
    def test_routes_to_swap_webhook(self, monkeypatch, notif_type):
        notifier = _build_notifier(monkeypatch)

        with patch("src.adapters.slack_notifier.requests.post") as mock_post:
            mock_post.return_value.raise_for_status = lambda: None

            ok = notifier.send_notification({
                "type": notif_type,
                "severity": "INFO",
                "message": "test",
                "metadata": {
                    "occurred_at": "2026-05-04T18:44:00Z",
                    "actor_name": "Amy Lam",
                    "machines": [
                        {"code": "R2", "previously": ["A"], "currently": ["B"]},
                    ],
                },
            })

        assert ok is True
        call_url, kwargs = mock_post.call_args.args[0], mock_post.call_args.kwargs
        assert call_url == SWAP_URL
        assert kwargs["json"]["channel"] == "#machine-swap"

    def test_low_stock_still_routes_to_main(self, monkeypatch):
        """Regression: existing notification routing must not change."""
        notifier = _build_notifier(monkeypatch)

        with patch("src.adapters.slack_notifier.requests.post") as mock_post:
            mock_post.return_value.raise_for_status = lambda: None
            notifier.send_notification({
                "type": "LOW_STOCK",
                "severity": "WARNING",
                "message": "low",
                "metadata": {},
            })
        assert mock_post.call_args.args[0] == MAIN_URL


class TestDisplayNotificationFormatting:
    """The display formatter renders per-machine Previously/Currently blocks."""

    def test_swap_two_machines_shows_arrow_and_both_blocks(self, monkeypatch):
        notifier = _build_notifier(monkeypatch)
        payload = notifier._format_display_notification({
            "type": "DISPLAY_SWAP",
            "metadata": {
                "occurred_at": "2026-05-04T18:44:00Z",
                "actor_name": "Amy Lam",
                "machines": [
                    {"code": "R2", "previously": ["Sonny V1", "Sonny V2"], "currently": ["Sonny V3"]},
                    {"code": "S5", "previously": [], "currently": ["Sonny V1", "Sonny V2"]},
                ],
            },
        }, channel="#machine-swap")

        text = payload["attachments"][0]["blocks"][1]["text"]["text"]
        assert "R2 <-> S5" in text
        assert "*R2*" in text and "*S5*" in text
        assert "Sonny V1, Sonny V2" in text
        assert "*Previously:*" in text and "*Currently:*" in text
        assert payload["channel"] == "#machine-swap"

    def test_renew_uses_renewed_line_not_previously_currently(self, monkeypatch):
        notifier = _build_notifier(monkeypatch)
        payload = notifier._format_display_notification({
            "type": "DISPLAY_RENEWED",
            "metadata": {
                "occurred_at": "2026-05-04T18:44:00Z",
                "actor_name": "Amy Lam",
                "machines": [
                    {"code": "R2", "previously": ["X"], "currently": ["X"]},
                ],
            },
        }, channel="#machine-swap")

        text = payload["attachments"][0]["blocks"][1]["text"]["text"]
        assert "*Renewed:* X" in text
        assert "Previously" not in text
        assert "Currently" not in text

    def test_empty_product_lists_render_as_empty_marker(self, monkeypatch):
        notifier = _build_notifier(monkeypatch)
        payload = notifier._format_display_notification({
            "type": "DISPLAY_SET",
            "metadata": {
                "occurred_at": "2026-05-04T18:44:00Z",
                "actor_name": "Amy Lam",
                "machines": [
                    {"code": "R2", "previously": [], "currently": ["X"]},
                ],
            },
        }, channel="#machine-swap")

        text = payload["attachments"][0]["blocks"][1]["text"]["text"]
        assert "*Previously:* _(empty)_" in text
        assert "*Currently:* X" in text

    def test_single_machine_has_no_arrow_in_machine_line(self, monkeypatch):
        notifier = _build_notifier(monkeypatch)
        payload = notifier._format_display_notification({
            "type": "DISPLAY_SET",
            "metadata": {
                "occurred_at": "2026-05-04T18:44:00Z",
                "actor_name": "Amy Lam",
                "machines": [
                    {"code": "R2", "previously": [], "currently": ["X"]},
                ],
            },
        }, channel="#machine-swap")

        text = payload["attachments"][0]["blocks"][1]["text"]["text"]
        assert "*Machine:* R2\n" in text or "*Machine:* R2" == text.split("\n")[1]
        assert "<->" not in text.split("\n")[1]
