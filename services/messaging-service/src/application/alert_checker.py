"""Alert checker for inventory threshold crossing detection."""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from enum import Enum

from .. import config
from ..adapters.supabase_repo import SupabaseRepo
from ..events import NormalizedEvent

logger = logging.getLogger(__name__)


class AlertType(str, Enum):
    """Types of stock alerts."""

    LOCATION_LOW_STOCK = "location_low_stock"
    LOCATION_OUT_OF_STOCK = "location_out_of_stock"
    TOTAL_LOW_STOCK = "total_low_stock"
    TOTAL_OUT_OF_STOCK = "total_out_of_stock"


@dataclass
class AlertResult:
    """Result of an alert check."""

    should_alert: bool
    alert_type: AlertType | None = None
    reason: str = ""
    # Additional context for notification creation
    item_id: str | None = None
    location_code: str | None = None
    previous_qty: int | None = None
    current_qty: int | None = None
    threshold: int | None = None


@dataclass
class AlertCheckResults:
    """Collection of all alert results from an event."""

    alerts: list[AlertResult] = field(default_factory=list)

    @property
    def has_alerts(self) -> bool:
        return any(a.should_alert for a in self.alerts)

    def get_triggered_alerts(self) -> list[AlertResult]:
        return [a for a in self.alerts if a.should_alert]


class AlertChecker:
    """Check if inventory changes trigger threshold crossing alerts.

    Uses crossing logic: alerts only fire when quantity CROSSES a threshold,
    not when it remains below. This prevents repeated alerts for the same condition.
    """

    def __init__(self, repo: SupabaseRepo):
        self._repo = repo

    def check_all(self, event: NormalizedEvent) -> AlertCheckResults:
        """Check all alert conditions for an event.

        Returns all triggered alerts. Note that one event can trigger
        multiple alerts (e.g., location OUT_OF_STOCK + total LOW_STOCK).

        OUT_OF_STOCK always takes precedence over LOW_STOCK for the same scope.
        """
        results = AlertCheckResults()

        # Check location-level alerts (if location info present)
        location_alert = self._check_location_stock(event)
        if location_alert.should_alert:
            results.alerts.append(location_alert)

        # Check total-level alerts (if total qty info present)
        total_alert = self._check_total_stock(event)
        if total_alert.should_alert:
            results.alerts.append(total_alert)

        return results

    def _check_location_stock(self, event: NormalizedEvent) -> AlertResult:
        """Check if location-level stock crosses threshold.

        Alerts:
        - LOCATION_OUT_OF_STOCK: when qty crosses to 0
        - LOCATION_LOW_STOCK: when qty crosses below threshold (but > 0)

        Uses crossing logic: only alerts on the transition, not while staying low.
        """
        # Need location and quantity info
        location_code = event.to_location_code
        if location_code is None:
            return AlertResult(should_alert=False, reason="No destination location")

        prev_qty = event.previous_location_qty
        curr_qty = event.current_location_qty

        if prev_qty is None or curr_qty is None:
            return AlertResult(
                should_alert=False,
                reason="Missing location quantity info in event",
            )

        threshold = config.LOCATION_LOW_STOCK_THRESHOLD

        # OUT_OF_STOCK: crossed to zero (check first - takes precedence)
        if prev_qty > 0 and curr_qty == 0:
            logger.info(
                "Location OUT_OF_STOCK triggered: item=%s, location=%s, prev=%d, curr=%d",
                event.item_id,
                location_code,
                prev_qty,
                curr_qty,
            )
            return AlertResult(
                should_alert=True,
                alert_type=AlertType.LOCATION_OUT_OF_STOCK,
                reason=f"Crossed to 0 at {location_code}",
                item_id=event.item_id,
                location_code=location_code,
                previous_qty=prev_qty,
                current_qty=curr_qty,
                threshold=0,
            )

        # LOW_STOCK: crossed below threshold (but not to zero)
        if prev_qty >= threshold and curr_qty < threshold and curr_qty > 0:
            logger.info(
                "Location LOW_STOCK triggered: item=%s, location=%s, prev=%d, curr=%d, threshold=%d",
                event.item_id,
                location_code,
                prev_qty,
                curr_qty,
                threshold,
            )
            return AlertResult(
                should_alert=True,
                alert_type=AlertType.LOCATION_LOW_STOCK,
                reason=f"Crossed below {threshold} at {location_code}",
                item_id=event.item_id,
                location_code=location_code,
                previous_qty=prev_qty,
                current_qty=curr_qty,
                threshold=threshold,
            )

        return AlertResult(
            should_alert=False,
            reason=f"No location threshold crossing (prev={prev_qty}, curr={curr_qty}, threshold={threshold})",
        )

    def _check_total_stock(self, event: NormalizedEvent) -> AlertResult:
        """Check if total inventory crosses threshold.

        Alerts:
        - TOTAL_OUT_OF_STOCK: when total qty crosses to 0
        - TOTAL_LOW_STOCK: when total qty crosses below reorder_point (but > 0)

        Uses crossing logic with data from the event (no DB query needed).
        """
        prev_total = event.previous_total_qty
        curr_total = event.current_total_qty
        reorder_point = event.reorder_point

        if prev_total is None or curr_total is None:
            return AlertResult(
                should_alert=False,
                reason="Missing total quantity info in event",
            )

        if reorder_point is None:
            return AlertResult(
                should_alert=False,
                reason="Missing reorder_point in event",
            )

        # OUT_OF_STOCK (total): crossed to zero (check first - takes precedence)
        if prev_total > 0 and curr_total == 0:
            logger.info(
                "Total OUT_OF_STOCK triggered: item=%s, prev=%d, curr=%d",
                event.item_id,
                prev_total,
                curr_total,
            )
            return AlertResult(
                should_alert=True,
                alert_type=AlertType.TOTAL_OUT_OF_STOCK,
                reason=f"Total inventory crossed to 0",
                item_id=event.item_id,
                location_code=None,
                previous_qty=prev_total,
                current_qty=curr_total,
                threshold=0,
            )

        # LOW_STOCK (total): crossed below reorder_point (but not to zero)
        if prev_total >= reorder_point and curr_total < reorder_point and curr_total > 0:
            logger.info(
                "Total LOW_STOCK triggered: item=%s, prev=%d, curr=%d, reorder_point=%d",
                event.item_id,
                prev_total,
                curr_total,
                reorder_point,
            )
            return AlertResult(
                should_alert=True,
                alert_type=AlertType.TOTAL_LOW_STOCK,
                reason=f"Total crossed below reorder point {reorder_point}",
                item_id=event.item_id,
                location_code=None,
                previous_qty=prev_total,
                current_qty=curr_total,
                threshold=reorder_point,
            )

        return AlertResult(
            should_alert=False,
            reason=f"No total threshold crossing (prev={prev_total}, curr={curr_total}, reorder_point={reorder_point})",
        )

    # Backward compatibility alias
    def check_reorder_point(self, event: NormalizedEvent) -> AlertResult:
        """Deprecated: Use check_all() instead."""
        return self._check_total_stock(event)
