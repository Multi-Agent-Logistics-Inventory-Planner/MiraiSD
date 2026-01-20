"""Alert checker for reorder point monitoring."""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import datetime

from .. import config
from ..adapters.supabase_repo import Product, SupabaseRepo
from ..events import NormalizedEvent

logger = logging.getLogger(__name__)


@dataclass
class AlertResult:
    """Result of alert check."""

    should_alert: bool
    product: Product | None = None
    reason: str = ""


class AlertChecker:
    """Check if inventory level triggers reorder point alert."""

    def __init__(self, repo: SupabaseRepo):
        self._repo = repo
        self._last_alert_time: dict[str, datetime] = {}
        self._alert_debounce_seconds = config.ALERT_DEBOUNCE_SECONDS

    def check_reorder_point(self, event: NormalizedEvent) -> AlertResult:
        """Check if current inventory is below reorder point.

        Args:
            event: Inventory change event

        Returns:
            AlertResult indicating if alert should be sent
        """
        # Get product with current inventory
        product = self._repo.get_product_with_inventory(event.item_id)

        if product is None:
            return AlertResult(
                should_alert=False,
                reason=f"Product {event.item_id} not found",
            )

        # Check if quantity is below reorder point
        if product.current_quantity >= product.reorder_point:
            return AlertResult(
                should_alert=False,
                product=product,
                reason=f"Quantity {product.current_quantity} >= reorder point {product.reorder_point}",
            )

        # Check debounce
        now = datetime.now()
        last_alert = self._last_alert_time.get(product.id)

        if last_alert and (now - last_alert).total_seconds() < self._alert_debounce_seconds:
            return AlertResult(
                should_alert=False,
                product=product,
                reason=f"Alert already sent recently (debounce: {self._alert_debounce_seconds}s)",
            )

        # Update last alert time
        self._last_alert_time[product.id] = now

        logger.info(
            "Reorder point alert triggered: product=%s, qty=%d, reorder_point=%d",
            product.name,
            product.current_quantity,
            product.reorder_point,
        )

        return AlertResult(
            should_alert=True,
            product=product,
            reason=f"Quantity {product.current_quantity} < reorder point {product.reorder_point}",
        )

