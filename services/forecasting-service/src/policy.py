from __future__ import annotations

import math
from statistics import NormalDist
from typing import Optional, Union

import numpy as np
import pandas as pd


def z_for_service_level(alpha: float) -> float:
    """Return z-score for a given service level alpha (0<alpha<1).

    Uses the standard normal quantile function.
    """
    if not (0.0 < alpha < 1.0):
        raise ValueError("alpha must be in (0,1)")
    return float(NormalDist().inv_cdf(alpha))


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


# -----------------------------------------------------------------------------
# Vectorized Policy Functions
# -----------------------------------------------------------------------------


def compute_safety_stock_vectorized(
    mu_hat: pd.Series,
    sigma_d_hat: pd.Series,
    L: Union[pd.Series, int, float],
    alpha: float,
    sigma_L: Optional[Union[pd.Series, float]] = None,
) -> pd.Series:
    """Vectorized safety stock computation.

    Without sigma_L: SS = z(alpha) * sigma_d * sqrt(L)
    With sigma_L:    SS = z(alpha) * sqrt(L * sigma_d^2 + mu^2 * sigma_L^2)

    Args:
        mu_hat: Series of mean daily demand estimates.
        sigma_d_hat: Series of demand standard deviations.
        L: Lead time (scalar or Series).
        alpha: Service level (e.g., 0.95).
        sigma_L: Lead time standard deviation (scalar or Series). None = demand-only.

    Returns:
        Series of safety stock values.
    """
    z = z_for_service_level(alpha)
    sigma_d_safe = sigma_d_hat.clip(lower=0.0)
    L_safe = np.maximum(L, 0.0) if isinstance(L, pd.Series) else max(L, 0.0)

    if sigma_L is None:
        return z * sigma_d_safe * np.sqrt(L_safe)

    # Full formula: SS = z * sqrt(L * sigma_d^2 + mu^2 * sigma_L^2)
    sigma_L_safe = sigma_L.clip(lower=0.0) if isinstance(sigma_L, pd.Series) else max(float(sigma_L), 0.0)
    return z * np.sqrt(L_safe * sigma_d_safe**2 + mu_hat**2 * sigma_L_safe**2)


def reorder_point_vectorized(
    mu_hat: pd.Series,
    safety_stock: pd.Series,
    L: Union[pd.Series, int, float],
) -> pd.Series:
    """Vectorized reorder point computation.

    ROP = mu * L + safety_stock

    Args:
        mu_hat: Series of mean daily demand estimates.
        safety_stock: Series of safety stock values.
        L: Lead time (scalar or Series).

    Returns:
        Series of reorder point values.
    """
    L_arr = L if isinstance(L, pd.Series) else L
    L_safe = np.maximum(L_arr, 0.0) if isinstance(L_arr, pd.Series) else max(L_arr, 0.0)
    ss_safe = safety_stock.clip(lower=0.0)
    return mu_hat * L_safe + ss_safe


def days_to_stockout_vectorized(
    current_qty: pd.Series,
    mu_hat: pd.Series,
    epsilon: float = 0.1,
) -> pd.Series:
    """Vectorized days to stockout computation.

    Returns infinity when mu_hat < epsilon, otherwise qty / mu.

    Args:
        current_qty: Series of current inventory quantities.
        mu_hat: Series of mean daily demand estimates.
        epsilon: Minimum demand threshold - returns inf if mu < epsilon.

    Returns:
        Series of days to stockout values (inf when mu < epsilon).
    """
    eps = max(epsilon, 1e-12)
    qty_safe = current_qty.clip(lower=0.0)

    # Initialize result with infinity
    result = pd.Series(np.inf, index=mu_hat.index)

    # Only compute for rows where mu_hat >= epsilon
    mask = mu_hat >= eps
    result[mask] = qty_safe[mask] / mu_hat[mask]

    return result


def suggest_order_vectorized(
    current_qty: pd.Series,
    mu_hat: pd.Series,
    L: Union[pd.Series, int, float],
    safety_stock: pd.Series,
    target_days: int,
) -> pd.Series:
    """Vectorized order suggestion computation.

    Q = max(0, ceil(target_days * mu - current_qty))

    Args:
        current_qty: Series of current inventory quantities.
        mu_hat: Series of mean daily demand estimates.
        L: Lead time (scalar or Series) - included for API consistency.
        safety_stock: Series of safety stock values - included for API consistency.
        target_days: Target days of cover.

    Returns:
        Series of suggested order quantities (integers).
    """
    target_days_safe = max(target_days, 0)
    mu_safe = mu_hat.clip(lower=0.0)
    qty_safe = current_qty.clip(lower=0.0)

    target_cycle_stock = target_days_safe * mu_safe
    needed = target_cycle_stock - qty_safe

    # Apply ceiling and clip to 0, then convert to int
    return np.ceil(needed).clip(lower=0).astype(int)
