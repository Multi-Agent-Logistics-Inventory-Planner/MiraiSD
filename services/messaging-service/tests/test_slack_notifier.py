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
            mock_config.KUJI_SLACK_WEBHOOK_URL = ""
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
            mock_config.KUJI_SLACK_WEBHOOK_URL = ""
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
KUJI_URL = "https://hooks.slack.com" + "/services/KUJI00000/KUJI11111/AbCdEfGhIjKlMnOpQrStUvWx"
PER_KUJI_URL = "https://hooks.slack.com" + "/services/PERKUJI00/PERKUJI11/AbCdEfGhIjKlMnOpQrStUvWx"


def _build_notifier(monkeypatch, kuji_webhook_url: str = ""):
    """Construct a SlackNotifier with predictable webhooks and channels."""
    from src.adapters import slack_notifier as mod

    monkeypatch.setattr(mod.config, "SLACK_WEBHOOK_URL", MAIN_URL)
    monkeypatch.setattr(mod.config, "SLACK_CHANNEL", "#inventory-alerts")
    monkeypatch.setattr(mod.config, "REVIEW_SLACK_WEBHOOK_URL", "")
    monkeypatch.setattr(mod.config, "REVIEW_SLACK_CHANNEL", "#piggly-review")
    monkeypatch.setattr(mod.config, "SWAP_SLACK_WEBHOOK_URL", SWAP_URL)
    monkeypatch.setattr(mod.config, "SWAP_SLACK_CHANNEL", "#machine-swap")
    monkeypatch.setattr(mod.config, "KUJI_SLACK_WEBHOOK_URL", kuji_webhook_url)
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


# ---------------------------------------------------------------------------
# Kuji draw notifications
# ---------------------------------------------------------------------------


class TestKujiNotificationFormatting:
    """The kuji formatters render draw / undo notifications correctly."""

    def test_format_kuji_draw_notification_multi_tier(self, monkeypatch):
        notifier = _build_notifier(monkeypatch)
        payload = notifier._format_kuji_draw_notification({
            "type": "KUJI_PRIZE_DRAWN",
            "metadata": {
                "kuji_product_id": "kp-1",
                "kuji_product_name": "Sonny Angel Kuji V1",
                "box_id": "box-1",
                "box_label": "Box A",
                "location_name": "G1 / Gachapons",
                "location_id": "loc-1",
                "actor_name": "Amy Lam",
                "occurred_at": "2026-05-04T18:44:00Z",
                "tiers": [
                    {
                        "tier_id": "t-A",
                        "label": "A Prize",
                        "letter": "A",
                        "linked_product_name": "Big Plush",
                        "price": None,
                        "quantity": 1,
                        "count_after": 2,
                    },
                    {
                        "tier_id": "t-B",
                        "label": "B Prize",
                        "letter": "B",
                        "linked_product_name": "Mini Figure",
                        "price": None,
                        "quantity": 2,
                        "count_after": 5,
                    },
                ],
                "total_count_after": 7,
            },
        })

        text = payload["attachments"][0]["blocks"][1]["text"]["text"]
        assert "*Kuji:* Sonny Angel Kuji V1 — Box Box A" in text
        assert "*Location:* G1 / Gachapons" in text
        assert "*Prizes drawn:*" in text
        assert "1x A — A Prize → Big Plush (2 left)" in text
        assert "2x B — B Prize → Mini Figure (5 left)" in text
        assert "*Total slips remaining in box:* 7" in text
        assert "*Drawn by:* Amy Lam" in text
        assert payload["attachments"][0]["color"] == "#22C55E"
        assert payload["attachments"][0]["blocks"][0]["text"]["text"] == "Kuji Prize Drawn"

    def test_format_kuji_draw_notification_unlinked_no_letter(self, monkeypatch):
        notifier = _build_notifier(monkeypatch)
        payload = notifier._format_kuji_draw_notification({
            "type": "KUJI_PRIZE_DRAWN",
            "metadata": {
                "kuji_product_name": "Mystery Kuji",
                "box_label": None,
                "location_name": "G1 / Gachapons",
                "actor_name": "Amy Lam",
                "occurred_at": "2026-05-04T18:44:00Z",
                "tiers": [
                    {
                        "label": "Last Prize",
                        "letter": None,
                        "linked_product_name": None,
                        "price": None,
                        "quantity": 1,
                        "count_after": 0,
                    },
                ],
                "total_count_after": 0,
            },
        })

        text = payload["attachments"][0]["blocks"][1]["text"]["text"]
        # No box label means no " — Box ..." suffix
        assert "*Kuji:* Mystery Kuji\n" in text or text.startswith("*Kuji:* Mystery Kuji\n")
        assert "Box " not in text.split("\n")[0]
        # No letter -> no "L — " prefix; no linked product -> em-dash
        assert "1x Last Prize → — (0 left)" in text
        assert "*Total slips remaining in box:* 0" in text

    def test_format_kuji_draw_notification_with_price_and_notes(self, monkeypatch):
        notifier = _build_notifier(monkeypatch)
        payload = notifier._format_kuji_draw_notification({
            "type": "KUJI_PRIZE_DRAWN",
            "metadata": {
                "kuji_product_name": "Premium Kuji",
                "box_label": "Box 1",
                "location_name": "G1 / Gachapons",
                "actor_name": "Amy Lam",
                "occurred_at": "2026-05-04T18:44:00Z",
                "notes": "Customer drew during event",
                "tiers": [
                    {
                        "label": "Standard",
                        "letter": "C",
                        "linked_product_name": "Sticker Pack",
                        "price": 5,
                        "quantity": 1,
                        "count_after": 9,
                    },
                ],
                "total_count_after": 9,
            },
        })

        text = payload["attachments"][0]["blocks"][1]["text"]["text"]
        assert "@ $5.00" in text
        assert "*Note:* Customer drew during event" in text

    def test_format_kuji_undo_notification(self, monkeypatch):
        notifier = _build_notifier(monkeypatch)
        payload = notifier._format_kuji_undo_notification({
            "type": "KUJI_PRIZE_DRAW_UNDONE",
            "metadata": {
                "kuji_product_name": "Sonny Angel Kuji V1",
                "box_label": "Box A",
                "location_name": "G1 / Gachapons",
                "actor_name": "Amy Lam",
                "occurred_at": "2026-05-04T18:44:00Z",
                "tiers": [
                    {
                        "label": "A Prize",
                        "letter": "A",
                        "linked_product_name": "Big Plush",
                        "price": None,
                        "quantity": 1,
                        "count_after": 3,
                    },
                ],
                "total_count_after": 8,
            },
        })

        text = payload["attachments"][0]["blocks"][1]["text"]["text"]
        assert payload["attachments"][0]["blocks"][0]["text"]["text"] == "Kuji Draw Undone"
        assert payload["attachments"][0]["color"] == "#F59E0B"
        assert "*Restored prizes:*" in text
        assert "1x A — A Prize → Big Plush (3 left)" in text
        assert "*Total slips remaining:* 8" in text
        assert "*Undone by:* Amy Lam" in text


class TestKujiNotificationRouting:
    """Kuji notifications must use the per-kuji webhook with config / default fallback."""

    def _kuji_payload(self, per_kuji_webhook: str | None = None) -> dict:
        return {
            "type": "KUJI_PRIZE_DRAWN",
            "severity": "INFO",
            "message": "draw",
            "metadata": {
                "kuji_product_name": "Sonny Angel Kuji V1",
                "kuji_slack_webhook_url": per_kuji_webhook,
                "box_label": "Box A",
                "location_name": "G1 / Gachapons",
                "actor_name": "Amy Lam",
                "occurred_at": "2026-05-04T18:44:00Z",
                "tiers": [
                    {
                        "label": "A Prize",
                        "letter": "A",
                        "linked_product_name": "Big Plush",
                        "price": None,
                        "quantity": 1,
                        "count_after": 2,
                    },
                ],
                "total_count_after": 2,
            },
        }

    def test_kuji_routing_uses_per_kuji_webhook(self, monkeypatch):
        notifier = _build_notifier(monkeypatch, kuji_webhook_url=KUJI_URL)

        with patch("src.adapters.slack_notifier.requests.post") as mock_post:
            mock_post.return_value.raise_for_status = lambda: None
            ok = notifier.send_notification(
                self._kuji_payload(per_kuji_webhook=PER_KUJI_URL)
            )

        assert ok is True
        assert mock_post.call_args.args[0] == PER_KUJI_URL

    def test_kuji_routing_falls_back_to_env_then_default(self, monkeypatch):
        # 1) No per-kuji override but KUJI_SLACK_WEBHOOK_URL set -> kuji env webhook
        notifier = _build_notifier(monkeypatch, kuji_webhook_url=KUJI_URL)
        with patch("src.adapters.slack_notifier.requests.post") as mock_post:
            mock_post.return_value.raise_for_status = lambda: None
            notifier.send_notification(self._kuji_payload(per_kuji_webhook=None))
        assert mock_post.call_args.args[0] == KUJI_URL

        # 2) No per-kuji override AND no KUJI_SLACK_WEBHOOK_URL -> falls back to default
        notifier2 = _build_notifier(monkeypatch, kuji_webhook_url="")
        with patch("src.adapters.slack_notifier.requests.post") as mock_post:
            mock_post.return_value.raise_for_status = lambda: None
            notifier2.send_notification(self._kuji_payload(per_kuji_webhook=None))
        assert mock_post.call_args.args[0] == MAIN_URL
