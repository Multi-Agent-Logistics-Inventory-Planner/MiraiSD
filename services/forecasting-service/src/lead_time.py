"""Compute dynamic lead time statistics from shipment history."""

from __future__ import annotations

import numpy as np
import pandas as pd

from . import config


def compute_lead_time_stats(
    shipment_df: pd.DataFrame,
    fallback_df: pd.DataFrame,
    min_shipments: int | None = None,
) -> pd.DataFrame:
    """Aggregate shipment lead times per product with fallback to product defaults.

    Args:
        shipment_df: DataFrame[item_id, lead_time_days] from delivered shipments.
        fallback_df: DataFrame[item_id, lead_time_days] from products table.
        min_shipments: Minimum shipment count for using computed std.
            Defaults to config.LEAD_TIME_MIN_SHIPMENTS.

    Returns:
        DataFrame[item_id, avg_lead_time, sigma_L, shipment_count, source]
    """
    min_n = min_shipments if min_shipments is not None else config.LEAD_TIME_MIN_SHIPMENTS
    result_cols = ["item_id", "avg_lead_time", "sigma_L", "shipment_count", "source"]

    # Normalize fallback
    fb = fallback_df[["item_id", "lead_time_days"]].copy()
    fb["item_id"] = fb["item_id"].astype(str)

    # Handle empty shipment data -- all items fall back to product defaults
    if shipment_df.empty:
        return pd.DataFrame({
            "item_id": fb["item_id"],
            "avg_lead_time": fb["lead_time_days"].fillna(14).astype(float),
            "sigma_L": config.LEAD_TIME_STD_DEFAULT_DAYS,
            "shipment_count": 0,
            "source": "product_default",
        })[result_cols]

    # Filter out non-positive lead times
    sdf = shipment_df.copy()
    sdf["item_id"] = sdf["item_id"].astype(str)
    sdf["lead_time_days"] = pd.to_numeric(sdf["lead_time_days"], errors="coerce")
    sdf = sdf[sdf["lead_time_days"] > 0].copy()

    if sdf.empty:
        return pd.DataFrame({
            "item_id": fb["item_id"],
            "avg_lead_time": fb["lead_time_days"].fillna(14).astype(float),
            "sigma_L": config.LEAD_TIME_STD_DEFAULT_DAYS,
            "shipment_count": 0,
            "source": "product_default",
        })[result_cols]

    # Aggregate per item
    agg = sdf.groupby("item_id", as_index=False).agg(
        avg_lead_time=("lead_time_days", "mean"),
        sigma_L=("lead_time_days", lambda x: float(x.std(ddof=1)) if len(x) > 1 else config.LEAD_TIME_STD_DEFAULT_DAYS),
        shipment_count=("lead_time_days", "count"),
    )

    # For items with count < min_shipments but > 0, use default sigma_L
    mask_low_count = (agg["shipment_count"] > 0) & (agg["shipment_count"] < min_n)
    agg.loc[mask_low_count, "sigma_L"] = config.LEAD_TIME_STD_DEFAULT_DAYS

    # Handle NaN sigma (can happen with ddof=1 on single value)
    agg["sigma_L"] = agg["sigma_L"].fillna(config.LEAD_TIME_STD_DEFAULT_DAYS)

    agg["source"] = "shipment_history"

    # Merge with fallback for items not in shipment data
    all_items = fb[["item_id"]].copy()
    merged = all_items.merge(agg, on="item_id", how="left")

    # Fill missing items with product defaults
    missing_mask = merged["source"].isna()
    merged.loc[missing_mask, "avg_lead_time"] = fb.set_index("item_id").reindex(
        merged.loc[missing_mask, "item_id"]
    )["lead_time_days"].fillna(14).values
    merged.loc[missing_mask, "sigma_L"] = config.LEAD_TIME_STD_DEFAULT_DAYS
    merged.loc[missing_mask, "shipment_count"] = 0
    merged.loc[missing_mask, "source"] = "product_default"

    merged["avg_lead_time"] = merged["avg_lead_time"].astype(float)
    merged["shipment_count"] = merged["shipment_count"].astype(int)

    return merged[result_cols].reset_index(drop=True)
