from __future__ import annotations

import math
from datetime import date, datetime, timedelta
from statistics import NormalDist
from typing import Optional, Union

import numpy as np
import pandas as pd
from scipy.stats import nbinom, poisson


REGIME_STEADY = "steady"
REGIME_BURSTY = "bursty"


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
    regime: Optional[pd.Series] = None,
) -> pd.Series:
    """Vectorized safety stock computation.

    Three modes:

    * ``regime is None`` (legacy) and ``sigma_L is None`` -- Normal-quantile
      buffer: ``SS = z(alpha) * sigma_d * sqrt(L)``. Used by the dow_weighted
      estimator's existing call sites.
    * ``regime is None`` and ``sigma_L`` is provided -- Normal-quantile buffer
      with lead-time variance term:
      ``SS = z(alpha) * sqrt(L * sigma_d^2 + mu^2 * sigma_L^2)``.
    * ``regime`` is provided -- distribution-routed buffer. Each SKU's buffer
      is sized against the lead-time demand distribution that matches its
      demand shape:

      - ``steady`` (CV <= threshold): ``SS = poisson.ppf(alpha, mu*L) - mu*L``
      - ``bursty`` (CV > threshold):  Negative Binomial fit to (mean=mu*L,
        var=sigma^2*L), then ``SS = nbinom.ppf(alpha, n, p) - mu*L``

      Falls back to Poisson per-row when overdispersion does not hold
      (variance <= mean), keeping behavior conservative.

    Args:
        mu_hat: Series of mean daily demand estimates.
        sigma_d_hat: Series of demand standard deviations.
        L: Lead time (scalar or Series).
        alpha: Service level (e.g., 0.95).
        sigma_L: Lead time standard deviation (scalar or Series). None =
            demand-only. Ignored when ``regime`` is provided.
        regime: Per-SKU demand regime label ("steady" or "bursty"). When
            provided, switches the buffer from Normal to Poisson / NegBin.

    Returns:
        Series of safety stock values (floored at 0).
    """
    if regime is not None:
        return _distribution_safety_stock(mu_hat, sigma_d_hat, L, alpha, regime)

    z = z_for_service_level(alpha)
    sigma_d_safe = sigma_d_hat.clip(lower=0.0)
    L_safe = np.maximum(L, 0.0) if isinstance(L, pd.Series) else max(L, 0.0)

    if sigma_L is None:
        return z * sigma_d_safe * np.sqrt(L_safe)

    # Full formula: SS = z * sqrt(L * sigma_d^2 + mu^2 * sigma_L^2)
    sigma_L_safe = sigma_L.clip(lower=0.0) if isinstance(sigma_L, pd.Series) else max(float(sigma_L), 0.0)
    return z * np.sqrt(L_safe * sigma_d_safe**2 + mu_hat**2 * sigma_L_safe**2)


def _distribution_safety_stock(
    mu_hat: pd.Series,
    sigma_d_hat: pd.Series,
    L: Union[pd.Series, int, float],
    alpha: float,
    regime: pd.Series,
) -> pd.Series:
    """Poisson (steady) / NegBin (bursty) safety stock, vectorized.

    Lead-time demand mean = mu * L, variance approximation = sigma_d^2 * L
    (treating L days as iid). NegBin parameters are derived from those moments
    (mean=mu_L, var=var_L) via the standard scipy parameterization:
    ``p = mu_L / var_L`` and ``n = mu_L * p / (1 - p)``. Per-row fallback to
    Poisson when ``var_L <= mu_L`` (no overdispersion to model).
    """
    idx = mu_hat.index
    mu = mu_hat.clip(lower=0.0).astype(float).to_numpy()
    sigma = sigma_d_hat.clip(lower=0.0).astype(float).to_numpy()
    if isinstance(L, pd.Series):
        L_arr = L.clip(lower=0.0).astype(float).to_numpy()
    else:
        L_arr = np.full_like(mu, max(float(L), 0.0))

    mu_L = mu * L_arr
    var_L = (sigma**2) * L_arr

    # Poisson baseline (works for every SKU as a safe fallback).
    safe_mu_L = np.maximum(mu_L, 1e-9)
    poisson_ss = poisson.ppf(alpha, safe_mu_L) - mu_L

    # NegBin where overdispersion holds. Where it doesn't (var_L <= mu_L)
    # NegBin reduces to Poisson, so we mask those rows out.
    overdispersed = var_L > mu_L + 1e-9
    nbinom_ss = np.zeros_like(mu_L)
    if overdispersed.any():
        mu_L_od = mu_L[overdispersed]
        var_L_od = var_L[overdispersed]
        p_param = np.clip(mu_L_od / np.maximum(var_L_od, 1e-9), 1e-9, 1.0 - 1e-9)
        n_param = mu_L_od * p_param / (1.0 - p_param)
        nbinom_ss[overdispersed] = nbinom.ppf(alpha, n_param, p_param) - mu_L_od

    regime_arr = regime.reindex(idx).fillna(REGIME_BURSTY).astype(str).to_numpy()
    use_nbinom = (regime_arr == REGIME_BURSTY) & overdispersed
    ss = np.where(use_nbinom, nbinom_ss, poisson_ss)

    # ppf returns NaN for degenerate inputs (e.g., mu_L = 0). Treat as 0.
    ss = np.where(np.isnan(ss), 0.0, ss)
    ss = np.maximum(ss, 0.0)
    return pd.Series(ss, index=idx)


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


def compute_order_date(
    days_to_stockout: float,
    lead_time: float,
    now: datetime,
) -> date | None:
    """Compute suggested order date based on days to stockout and lead time.

    Returns:
        None if days_to_stockout is infinite (no urgency).
        today if days_to_stockout <= lead_time (order immediately).
        A future date otherwise: now + int(days_to_stockout - lead_time) days.
    """
    if days_to_stockout >= float("inf"):
        return None
    if days_to_stockout <= lead_time:
        return now.date()
    return (now + timedelta(days=int(days_to_stockout - lead_time))).date()
