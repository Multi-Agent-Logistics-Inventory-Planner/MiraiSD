"""Compute MAPE from historical forecasts vs actual daily usage."""

from __future__ import annotations

import numpy as np
import pandas as pd

from . import config


def compute_mape(
    historical_forecasts_df: pd.DataFrame,
    actual_daily_usage_df: pd.DataFrame,
    horizon_days: int | None = None,
    epsilon: float | None = None,
) -> pd.DataFrame:
    """Compute Mean Absolute Percentage Error per item.

    Args:
        historical_forecasts_df: DataFrame[item_id, computed_at, mu_hat]
            Past forecast predictions closest to the backtest target date.
        actual_daily_usage_df: DataFrame[item_id, date, consumption]
            Actual daily consumption over the backtest horizon window.
        horizon_days: Number of days in the backtest window.
            Defaults to config.BACKTEST_HORIZON_DAYS.
        epsilon: Floor for actual_mu to avoid division by zero.
            Defaults to config.MAPE_EPSILON.

    Returns:
        DataFrame[item_id, mape, forecast_mu, actual_mu, backtest_days]
    """
    h = horizon_days if horizon_days is not None else config.BACKTEST_HORIZON_DAYS
    eps = epsilon if epsilon is not None else config.MAPE_EPSILON
    result_cols = ["item_id", "mape", "forecast_mu", "actual_mu", "backtest_days"]

    if historical_forecasts_df.empty:
        return pd.DataFrame(columns=result_cols)

    if actual_daily_usage_df.empty:
        return pd.DataFrame(columns=result_cols)

    # Compute actual mean daily consumption per item over the horizon
    usage = actual_daily_usage_df.copy()
    usage["item_id"] = usage["item_id"].astype(str)

    actual_agg = usage.groupby("item_id", as_index=False).agg(
        actual_mu=("consumption", "mean"),
        backtest_days=("date", "nunique"),
    )

    # Merge with historical forecasts
    fc = historical_forecasts_df[["item_id", "mu_hat"]].copy()
    fc["item_id"] = fc["item_id"].astype(str)
    fc = fc.rename(columns={"mu_hat": "forecast_mu"})

    merged = fc.merge(actual_agg, on="item_id", how="inner")

    if merged.empty:
        return pd.DataFrame(columns=result_cols)

    # MAPE = |actual_mu - forecast_mu| / max(actual_mu, epsilon)
    merged["mape"] = (
        np.abs(merged["actual_mu"] - merged["forecast_mu"])
        / np.maximum(merged["actual_mu"], eps)
    )

    return merged[result_cols].reset_index(drop=True)
