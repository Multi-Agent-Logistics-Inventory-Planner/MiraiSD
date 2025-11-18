# models.py
"""Forecasting models and prediction logic."""

import pandas as pd


def forecast_demand(daily_consumption: pd.DataFrame, horizon_days: int = 21) -> pd.DataFrame:
    """
    Generate demand forecasts using available models.

    Args:
        daily_consumption: DataFrame with columns [item_id, date, consumption, avg_14]
        horizon_days: Number of days to forecast ahead

    Returns:
        DataFrame with forecast predictions
    """
    latest = daily_consumption.groupby("item_id", as_index=False).agg(avg_daily=("avg_14", "last"))
    latest["forecast_demand"] = latest["avg_daily"] * horizon_days
    return latest
