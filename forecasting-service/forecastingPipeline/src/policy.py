from __future__ import annotations

import math
from typing import Optional

from scipy.stats import norm


def z_for_service_level(alpha: float) -> float:
    """Return z-score for a given service level alpha (0<alpha<1).

    Uses the standard normal quantile function.
    """
    if not (0.0 < alpha < 1.0):
        raise ValueError("alpha must be in (0,1)")
    return float(norm.ppf(alpha))


def sigma_lead_time(
    mu_hat: float,
    sigma_d_hat: float,
    L: float,
    sigma_L: Optional[float] = None,
) -> float:
    """Estimate lead-time demand std-dev.

    - Demand-only variability (default): sqrt(L) * sigma_d_hat
    - Upgrade (if sigma_L is provided): sqrt(L*sigma_d_hat^2 + mu_hat^2*sigma_L^2)
    """
    mu = float(mu_hat)
    sigma_d = max(float(sigma_d_hat), 0.0)
    lead_time = max(float(L), 0.0)

    if not sigma_L:
        return math.sqrt(lead_time) * sigma_d

    sigma_lead = max(float(sigma_L), 0.0)
    return math.sqrt(lead_time * (sigma_d**2) + (mu**2) * (sigma_lead**2))


def compute_safety_stock(
    mu_hat: float,
    sigma_d_hat: float,
    L: float,
    alpha: float,
    sigma_L: Optional[float] = None,
) -> float:
    """Compute safety stock for service level alpha.

    SS = z(alpha) * sigma_lead_time(...)
    """
    z = z_for_service_level(alpha)
    sigma_lt = sigma_lead_time(mu_hat, sigma_d_hat, L, sigma_L)
    return z * sigma_lt


def reorder_point(mu_hat: float, safety_stock: float, L: float) -> float:
    """Compute reorder point: ROP = mu_hat * L + safety_stock."""
    return float(mu_hat) * float(max(L, 0.0)) + float(max(safety_stock, 0.0))


def days_to_stockout(current_qty: float, mu_hat: float, epsilon: float = 0.1) -> float:
    """Compute days until stockout; infinite if mu_hat < epsilon.

    Uses a guard to avoid division by very small mu_hat.
    """
    mu = float(mu_hat)
    qty = float(max(current_qty, 0.0))
    eps = float(max(epsilon, 1e-12))
    if mu < eps:
        return math.inf
    return qty / max(mu, eps)


def suggest_order(
    current_qty: float,
    mu_hat: float,
    L: float,
    safety_stock: float,
    target_days_of_cover: float,
) -> int:
    """Suggest a replenishment quantity to meet target days of cover.

    TargetCycleStock = target_days_of_cover * mu_hat
    Q = max(0, ceil(TargetCycleStock - current_qty))

    Note: safety_stock and L are included in the signature for policy completeness
    and possible future use (e.g., minimum order tied to ROP). They do not currently
    change Q for the basic FOQ target-days policy.
    """
    target_cycle_stock = float(max(target_days_of_cover, 0.0)) * float(max(mu_hat, 0.0))
    needed = target_cycle_stock - float(max(current_qty, 0.0))
    if needed <= 0.0:
        return 0
    return int(math.ceil(needed))


