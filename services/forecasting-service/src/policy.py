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


def _dow_multiplier(dow_dict: object, dow: int) -> float:
    """Read a dow multiplier from a {0..6 -> float} dict tolerating str/int keys.

    Returns 1.0 when dow_dict is missing, malformed, or has no entry for ``dow``.
    """
    if not isinstance(dow_dict, dict) or not dow_dict:
        return 1.0
    val = dow_dict.get(dow)
    if val is None:
        val = dow_dict.get(str(dow))
    try:
        return float(val) if val is not None else 1.0
    except (TypeError, ValueError):
        return 1.0


def effective_lead_demand_vectorized(
    mu_hat: pd.Series,
    L: Union[pd.Series, int, float],
    dow_multipliers: Optional[pd.Series] = None,
    start_date: Optional[date] = None,
    event_multipliers: Optional[dict[str, float]] = None,
    event_days_since: Optional[dict[str, pd.Series]] = None,
    event_window_days: int = 7,
) -> pd.Series:
    """Expected demand over the lead-time window, DOW + event-adjusted.

    For each row this returns
    ``sum_{k=0..L-1} mu_hat * dow_mult[dow(start+k)] * Π event_mult_if_active``.
    Falls back to ``mu_hat * L`` when DOW info is missing and to the DOW-only
    sum when event info is missing.

    Event activation: an event with global multiplier ``m`` is "active" on
    lead-time day ``k`` (where ``k=0`` is ``start_date``) for SKUs whose last
    occurrence is ``X`` days before ``start_date`` and ``1 - k <= X <=
    event_window_days - k``. This matches the "recent in prior N days,
    excluding the event day itself" semantic used at training time.

    Args:
        mu_hat: per-SKU mean daily demand (constant, the window average).
        L: lead time (scalar or Series).
        dow_multipliers: optional Series of dicts ``{0..6 -> float}``; one entry
            per row, aligned to ``mu_hat.index``. Keys may be int or str.
        start_date: calendar date the lead-time window begins. The day at index 0
            is ``start_date`` itself. When None, the function degrades to the
            constant-rate sum ``mu_hat * L``.
        event_multipliers: global ``{event_col -> multiplier}`` learned from
            training. When None or empty, events are skipped.
        event_days_since: ``{event_col -> Series of days-since-last-event}``,
            aligned to ``mu_hat.index``. NaN means "no prior event observed";
            those SKUs do not get the multiplier.
        event_window_days: lookback length used to define "recent" (default 7,
            matching ``recent_*_7d`` flags).

    Returns:
        Series of expected lead-time demand, same index as ``mu_hat``.
    """
    if isinstance(L, pd.Series):
        L_arr = L.clip(lower=0.0).astype(float)
    else:
        L_arr = pd.Series([max(float(L), 0.0)] * len(mu_hat), index=mu_hat.index)

    if dow_multipliers is None or start_date is None:
        return mu_hat.astype(float) * L_arr

    out = np.zeros(len(mu_hat), dtype=float)
    mu_arr = mu_hat.astype(float).to_numpy()
    L_int = np.round(L_arr.to_numpy()).astype(int)
    dm_arr = dow_multipliers.reindex(mu_hat.index).to_numpy()

    # Pre-extract event arrays once per call. Each entry is (multiplier,
    # days_since_array). Multipliers within (1 - epsilon, 1 + epsilon) are
    # filtered out as no-ops to keep the inner loop fast.
    active_events: list[tuple[float, np.ndarray]] = []
    if event_multipliers and event_days_since:
        for col, mult in event_multipliers.items():
            if abs(float(mult) - 1.0) < 1e-9:
                continue
            recency = event_days_since.get(col)
            if recency is None:
                continue
            active_events.append(
                (float(mult), recency.reindex(mu_hat.index).to_numpy())
            )

    for i in range(len(mu_hat)):
        dm = dm_arr[i]
        L_i = int(L_int[i])
        if L_i <= 0:
            continue
        if not isinstance(dm, dict) or not dm:
            out[i] = mu_arr[i] * L_i
            continue
        total = 0.0
        for k in range(L_i):
            dow = (start_date + timedelta(days=k)).weekday()
            day_demand = mu_arr[i] * _dow_multiplier(dm, dow)
            for mult, recency_arr in active_events:
                X = recency_arr[i]
                # NaN means "never observed" -> flag inactive.
                if X != X:  # NaN check without numpy import in hot loop
                    continue
                # Active iff most recent event is within (k - event_window_days, k - 1]
                # relative to start_date. Equivalent: 1 - k <= X <= window - k.
                if (1 - k) <= X <= (event_window_days - k):
                    day_demand *= mult
            total += day_demand
        out[i] = total

    return pd.Series(out, index=mu_hat.index)


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
    dow_multipliers: Optional[pd.Series] = None,
    start_date: Optional[date] = None,
    event_multipliers: Optional[dict[str, float]] = None,
    event_days_since: Optional[dict[str, pd.Series]] = None,
    event_window_days: int = 7,
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
        return _distribution_safety_stock(
            mu_hat, sigma_d_hat, L, alpha, regime,
            dow_multipliers=dow_multipliers, start_date=start_date,
            event_multipliers=event_multipliers,
            event_days_since=event_days_since,
            event_window_days=event_window_days,
        )

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
    dow_multipliers: Optional[pd.Series] = None,
    start_date: Optional[date] = None,
    event_multipliers: Optional[dict[str, float]] = None,
    event_days_since: Optional[dict[str, pd.Series]] = None,
    event_window_days: int = 7,
) -> pd.Series:
    """Poisson (steady) / NegBin (bursty) safety stock, vectorized.

    Lead-time demand mean uses the DOW-adjusted sum when ``dow_multipliers`` +
    ``start_date`` are provided; otherwise it degrades to the flat ``mu * L``.
    Variance approximation stays ``sigma_d^2 * L`` (sigma_d already excludes
    DOW effect when produced by ``_dow_weighted_estimate``). NegBin parameters
    follow the scipy parameterization: ``p = mu_L / var_L``,
    ``n = mu_L * p / (1 - p)``. Per-row fallback to Poisson when
    ``var_L <= mu_L`` (no overdispersion to model).
    """
    idx = mu_hat.index
    mu = mu_hat.clip(lower=0.0).astype(float).to_numpy()
    sigma = sigma_d_hat.clip(lower=0.0).astype(float).to_numpy()
    if isinstance(L, pd.Series):
        L_arr = L.clip(lower=0.0).astype(float).to_numpy()
    else:
        L_arr = np.full_like(mu, max(float(L), 0.0))

    mu_L = effective_lead_demand_vectorized(
        mu_hat.clip(lower=0.0), L, dow_multipliers, start_date,
        event_multipliers=event_multipliers,
        event_days_since=event_days_since,
        event_window_days=event_window_days,
    ).astype(float).to_numpy()
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
    dow_multipliers: Optional[pd.Series] = None,
    start_date: Optional[date] = None,
    event_multipliers: Optional[dict[str, float]] = None,
    event_days_since: Optional[dict[str, pd.Series]] = None,
    event_window_days: int = 7,
) -> pd.Series:
    """Vectorized reorder point computation.

    ROP = expected_lead_demand + safety_stock

    Expected lead demand is the DOW-adjusted sum over the lead-time window
    when ``dow_multipliers`` + ``start_date`` are provided; otherwise it
    degrades to ``mu * L``.

    Args:
        mu_hat: Series of mean daily demand estimates.
        safety_stock: Series of safety stock values.
        L: Lead time (scalar or Series).
        dow_multipliers: Optional Series of per-SKU dicts {0..6 -> float}.
        start_date: Calendar date the lead-time window starts on.

    Returns:
        Series of reorder point values.
    """
    expected_lead = effective_lead_demand_vectorized(
        mu_hat.clip(lower=0.0), L, dow_multipliers, start_date,
        event_multipliers=event_multipliers,
        event_days_since=event_days_since,
        event_window_days=event_window_days,
    )
    ss_safe = safety_stock.clip(lower=0.0)
    return expected_lead + ss_safe


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


def suggest_order_v2_vectorized(
    current_qty: pd.Series,
    mu_hat: pd.Series,
    L: Union[pd.Series, int, float],
    safety_stock: pd.Series,
    target_days: int,
    on_order: Optional[pd.Series] = None,
) -> pd.Series:
    """Lead-time-aware order suggestion with on-order netting.

    Q = max(0, ceil(mu * (L + target_days) + safety_stock - on_hand - on_order))

    Unlike ``suggest_order_vectorized`` (v1), this covers the demand that
    arrives DURING the replenishment lead time plus the target cover window,
    keeps the safety buffer intact, and nets out units already inbound on
    PENDING shipments so the same stock is not ordered twice.

    Args:
        current_qty: Series of on-hand quantities.
        mu_hat: Series of mean daily demand estimates.
        L: Lead time in days (scalar or Series).
        safety_stock: Series of safety stock values.
        target_days: Target days of cover beyond the lead time.
        on_order: Optional Series of inbound units on PENDING shipments.

    Returns:
        Series of suggested order quantities (integers, floored at 0).
    """
    mu_safe = mu_hat.clip(lower=0.0).astype(float)
    qty_safe = current_qty.clip(lower=0.0).astype(float)
    ss_safe = safety_stock.clip(lower=0.0).astype(float)
    if isinstance(L, pd.Series):
        L_safe = L.clip(lower=0.0).astype(float)
    else:
        L_safe = max(float(L), 0.0)
    inbound = (
        on_order.fillna(0.0).clip(lower=0.0).astype(float)
        if on_order is not None
        else 0.0
    )

    needed = mu_safe * (L_safe + max(target_days, 0)) + ss_safe - qty_safe - inbound
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
