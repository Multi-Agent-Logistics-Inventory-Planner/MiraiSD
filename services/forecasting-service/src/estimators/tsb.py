"""Teunter-Syntetos-Babai (TSB) intermittent demand estimator.

Reference: Teunter, R.H., Syntetos, A.A., Babai, M.Z. (2011).
Intermittent demand: Linking forecasting to inventory obsolescence.
European Journal of Operational Research, 214(3), 606-615.

The estimator decomposes daily demand into two exponentially smoothed series:
    p_t = alpha * s_t + (1 - alpha) * p_{t-1}   updated every day, s_t in {0, 1}
    z_t = beta  * d_t + (1 - beta)  * z_{t-1}   updated only on sale days
    forecast = p_t * z_t

Updating p every day (including zero-sale days) is the property Croston's method
lacks. When an SKU stops selling, p decays smoothly toward zero and the forecast
follows it down -- avoiding the "Croston zombie reorder" pattern for dying
blindbox/kuji series.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from .. import config


def tsb_estimate(
    group: pd.DataFrame,
    alpha: float | None = None,
    beta: float | None = None,
) -> tuple[float, float, float, float, dict[int, float]]:
    """Run the TSB recursion over a zero-filled daily consumption series.

    Args:
        group: DataFrame with at least ``date`` and ``consumption`` columns,
            assumed to already be zero-filled across the full window.
        alpha: smoothing constant for the sale probability ``p_t``.
            Defaults to ``config.TSB_ALPHA``.
        beta: smoothing constant for the sale size ``z_t``.
            Defaults to ``config.TSB_BETA``.

    Returns:
        ``(mu_hat, sigma_d_hat, p, z, dow_multipliers)`` where ``mu_hat``
        is ``p * z`` floored at ``config.MU_FLOOR``, ``sigma_d_hat`` is the
        residual std of the daily consumption series (used by the safety stock
        distribution), and ``dow_multipliers`` are computed from sale-day
        consumption only.
    """
    a = config.TSB_ALPHA if alpha is None else alpha
    b = config.TSB_BETA if beta is None else beta

    if group.empty:
        floor_dow = {d: 1.0 for d in range(7)}
        return config.MU_FLOOR, config.SIGMA_FLOOR, 0.0, config.MU_FLOOR, floor_dow

    g = group.sort_values("date").reset_index(drop=True)
    consumption = g["consumption"].to_numpy(dtype=float)
    s_arr = (consumption > 0).astype(float)

    # Seed p with the empirical sale frequency and z with the first observed
    # sale size. This converges to the true smoothed values within a few
    # window-lengths and avoids the "all-zero warmup" trap when alpha is small.
    p = float(s_arr.mean()) if len(s_arr) > 0 else 0.0
    sale_idx = np.flatnonzero(s_arr)
    z = float(consumption[sale_idx[0]]) if sale_idx.size > 0 else config.MU_FLOOR

    for t in range(len(consumption)):
        s_t = s_arr[t]
        p = a * s_t + (1.0 - a) * p
        if s_t == 1.0:
            z = b * consumption[t] + (1.0 - b) * z

    mu_hat = max(p * z, config.MU_FLOOR)

    # sigma_d_hat: empirical std of daily consumption. Used downstream by the
    # safety-stock distribution (Poisson/NegBin) to size the buffer for the
    # right tail. Computed over the full zero-filled window because that's the
    # process the safety stock has to protect against.
    sigma_d_hat = max(float(np.std(consumption, ddof=0)), config.SIGMA_FLOOR)

    # DOW multipliers: per-day-of-week sale-day consumption relative to the
    # overall sale-day mean. Stored as metadata for the "why this number"
    # drawer; the prediction itself is p * z, not DOW-adjusted.
    dow_multipliers: dict[int, float] = {d: 1.0 for d in range(7)}
    if sale_idx.size > 0:
        g["dow"] = pd.to_datetime(g["date"]).dt.dayofweek
        sale_g = g.loc[s_arr == 1.0]
        sale_overall = float(sale_g["consumption"].mean())
        for d, mean_d in sale_g.groupby("dow")["consumption"].mean().items():
            dow_multipliers[int(d)] = round(
                float(mean_d) / max(sale_overall, config.MU_FLOOR), 4
            )

    return float(mu_hat), float(sigma_d_hat), float(p), float(z), dow_multipliers
