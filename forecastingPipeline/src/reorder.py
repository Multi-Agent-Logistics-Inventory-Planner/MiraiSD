# reorder.py
"""Reorder policy computation and recommendations."""

import pandas as pd


def compute_reorder_policy(
    onhand: pd.DataFrame,
    daily_consumption: pd.DataFrame,
    target_days: int = 21,
    reorder_threshold: int = 7,
    min_order_qty: int | None = None,
) -> pd.DataFrame:
    """
    Compute reorder recommendations based on current inventory and consumption patterns.

    Args:
        onhand: DataFrame with columns [item_id, quantity]
        daily_consumption: DataFrame with columns [item_id, date, consumption, avg_14]
        target_days: Target days of inventory to maintain
        reorder_threshold: Days before stockout to trigger reorder
        min_order_qty: Minimum order quantity (optional)

    Returns:
        DataFrame with reorder recommendations
    """
    # Get latest average daily consumption per item
    latest = daily_consumption.groupby("item_id", as_index=False).agg(avg_daily=("avg_14", "last"))

    # Merge with on-hand inventory
    df = latest.merge(onhand, on="item_id", how="left").fillna({"quantity": 0})

    # Calculate days to stockout
    df["days_to_stockout"] = (
        (df["quantity"] / df["avg_daily"])
        .replace([float("inf"), float("-inf")], [9999, 0])
        .fillna(9999)
    )

    # Calculate target quantity and suggested reorder quantity
    df["target_qty"] = (df["avg_daily"] * target_days).round().astype("Int64")
    df["suggested_reorder_qty"] = (df["target_qty"] - df["quantity"]).clip(lower=0).astype("Int64")

    # Apply minimum order quantity if specified
    if min_order_qty is not None:
        df.loc[df["suggested_reorder_qty"] > 0, "suggested_reorder_qty"] = df.loc[
            df["suggested_reorder_qty"] > 0, "suggested_reorder_qty"
        ].clip(lower=min_order_qty)

    # Flag items that need reordering
    df["needs_reorder"] = (df["days_to_stockout"] <= reorder_threshold) & (
        df["suggested_reorder_qty"] > 0
    )

    return df
