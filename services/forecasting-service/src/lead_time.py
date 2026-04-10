"""Compute dynamic lead time statistics from shipment history."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

import numpy as np
import pandas as pd

from . import config


class LeadTimeSource(str, Enum):
    """Source of lead time calculation in the 4-level hierarchy."""
    PREFERRED_SUPPLIER_ITEM = "preferred_supplier_item"  # Level 1
    PREFERRED_SUPPLIER_AVG = "preferred_supplier_avg"    # Level 2
    ITEM_HISTORY = "item_history"                        # Level 3
    GLOBAL_FALLBACK = "global_fallback"                  # Level 4
    PRODUCT_DEFAULT = "product_default"                  # Legacy: from products table


@dataclass
class LeadTimeResult:
    """Result of lead time computation for a single item."""
    item_id: str
    avg_lead_time: float
    sigma_L: float
    shipment_count: int
    source: LeadTimeSource
    preferred_supplier_id: str | None = None


def compute_hierarchical_lead_time(
    mv_stats_df: pd.DataFrame,
    products_df: pd.DataFrame,
    min_shipments: int | None = None,
) -> pd.DataFrame:
    """Compute lead times using 4-level supplier-based hierarchy.

    Hierarchy:
    1. Preferred Supplier + Item: 3+ shipments of this item from preferred supplier
    2. Preferred Supplier Average: Preferred supplier has 3+ total shipments
    3. Item History: 3+ shipments of this item from any supplier
    4. Global Fallback: 11 days, sigma_L = 2.0

    Args:
        mv_stats_df: DataFrame from mv_lead_time_stats with columns:
            item_id, supplier_id, n, avg_lt, sigma_L, hierarchy_level
        products_df: DataFrame with columns:
            item_id, preferred_supplier_id (nullable), lead_time_days (static default)
        min_shipments: Minimum shipment count to use a level. Defaults to 3.

    Returns:
        DataFrame[item_id, avg_lead_time, sigma_L, shipment_count, source, preferred_supplier_id]
    """
    min_n = min_shipments if min_shipments is not None else 3
    global_lt = config.LEAD_TIME_GLOBAL_FALLBACK_DAYS
    sigma_default = config.LEAD_TIME_STD_DEFAULT_DAYS

    result_cols = ["item_id", "avg_lead_time", "sigma_L", "shipment_count", "source", "preferred_supplier_id"]

    # Normalize products
    prods = products_df[["item_id"]].copy()
    prods["item_id"] = prods["item_id"].astype(str)
    if "preferred_supplier_id" in products_df.columns:
        prods["preferred_supplier_id"] = products_df["preferred_supplier_id"].astype(str).replace("None", pd.NA)
    else:
        prods["preferred_supplier_id"] = pd.NA

    # Handle empty MV data - all items get global fallback
    if mv_stats_df.empty:
        return pd.DataFrame({
            "item_id": prods["item_id"],
            "avg_lead_time": global_lt,
            "sigma_L": sigma_default,
            "shipment_count": 0,
            "source": LeadTimeSource.GLOBAL_FALLBACK.value,
            "preferred_supplier_id": prods["preferred_supplier_id"],
        })[result_cols]

    # Normalize MV stats
    mv = mv_stats_df.copy()
    mv["item_id"] = mv["item_id"].astype(str)
    mv["supplier_id"] = mv["supplier_id"].astype(str).replace("None", pd.NA)
    mv["n"] = mv["n"].fillna(0).astype(int)
    mv["avg_lt"] = pd.to_numeric(mv["avg_lt"], errors="coerce")
    mv["sigma_L"] = pd.to_numeric(mv["sigma_L"], errors="coerce").fillna(sigma_default)

    # Pre-build lookup indices for O(1) access instead of O(N) DataFrame filtering
    # This converts O(products * mv_rows) to O(products + mv_rows)
    mv_by_item_supplier: dict[tuple[str, str], dict] = {}  # Level 1: (item_id, supplier_id) -> row
    mv_by_supplier: dict[str, list[dict]] = {}             # Level 2: supplier_id -> [rows with n >= min_n]
    mv_by_item: dict[str, list[dict]] = {}                 # Level 3: item_id -> [rows with n >= min_n]

    for _, row in mv.iterrows():
        row_dict = {
            "avg_lt": row["avg_lt"],
            "sigma_L": row["sigma_L"],
            "n": row["n"],
        }
        item_id = row["item_id"]
        supplier_id = row["supplier_id"] if pd.notna(row["supplier_id"]) else None

        # For Level 1, store all (item, supplier) pairs regardless of n
        # The lookup function will check n >= min_n
        if supplier_id:
            mv_by_item_supplier[(item_id, supplier_id)] = row_dict

        # For Level 2 and 3, only include rows that meet min_n threshold
        if row["n"] >= min_n:
            if supplier_id:
                mv_by_supplier.setdefault(supplier_id, []).append(row_dict)
            mv_by_item.setdefault(item_id, []).append(row_dict)

    # Build result row-by-row for each product using indexed lookups
    results = []
    for _, prod in prods.iterrows():
        item_id = prod["item_id"]
        pref_supplier = prod["preferred_supplier_id"] if pd.notna(prod["preferred_supplier_id"]) else None

        result = _resolve_lead_time_indexed(
            item_id=item_id,
            preferred_supplier_id=pref_supplier,
            mv_by_item_supplier=mv_by_item_supplier,
            mv_by_supplier=mv_by_supplier,
            mv_by_item=mv_by_item,
            min_n=min_n,
            global_lt=global_lt,
            sigma_default=sigma_default,
        )
        results.append(result)

    return pd.DataFrame([
        {
            "item_id": r.item_id,
            "avg_lead_time": r.avg_lead_time,
            "sigma_L": r.sigma_L,
            "shipment_count": r.shipment_count,
            "source": r.source.value,
            "preferred_supplier_id": r.preferred_supplier_id,
        }
        for r in results
    ])[result_cols]


def _resolve_lead_time_indexed(
    item_id: str,
    preferred_supplier_id: str | None,
    mv_by_item_supplier: dict[tuple[str, str], dict],
    mv_by_supplier: dict[str, list[dict]],
    mv_by_item: dict[str, list[dict]],
    min_n: int,
    global_lt: float,
    sigma_default: float,
) -> LeadTimeResult:
    """Resolve lead time using pre-indexed lookups (O(1) instead of O(N) per call)."""

    # Level 1: Preferred Supplier + Item
    if preferred_supplier_id:
        level1_row = mv_by_item_supplier.get((item_id, preferred_supplier_id))
        if level1_row and level1_row["n"] >= min_n:
            return LeadTimeResult(
                item_id=item_id,
                avg_lead_time=float(level1_row["avg_lt"]),
                sigma_L=float(level1_row["sigma_L"]),
                shipment_count=int(level1_row["n"]),
                source=LeadTimeSource.PREFERRED_SUPPLIER_ITEM,
                preferred_supplier_id=preferred_supplier_id,
            )

        # Level 2: Preferred Supplier Average (all items from this supplier)
        level2_rows = mv_by_supplier.get(preferred_supplier_id, [])
        if level2_rows:
            total_n = sum(r["n"] for r in level2_rows)
            weighted_lt = sum(r["avg_lt"] * r["n"] for r in level2_rows) / total_n
            weighted_sigma = np.sqrt(
                sum(r["sigma_L"] ** 2 * r["n"] for r in level2_rows) / total_n
            )
            return LeadTimeResult(
                item_id=item_id,
                avg_lead_time=float(weighted_lt),
                sigma_L=float(weighted_sigma) if weighted_sigma > 0 else sigma_default,
                shipment_count=int(total_n),
                source=LeadTimeSource.PREFERRED_SUPPLIER_AVG,
                preferred_supplier_id=preferred_supplier_id,
            )

    # Level 3: Item History (any supplier)
    level3_rows = mv_by_item.get(item_id, [])
    if level3_rows:
        total_n = sum(r["n"] for r in level3_rows)
        weighted_lt = sum(r["avg_lt"] * r["n"] for r in level3_rows) / total_n
        weighted_sigma = np.sqrt(
            sum(r["sigma_L"] ** 2 * r["n"] for r in level3_rows) / total_n
        )
        return LeadTimeResult(
            item_id=item_id,
            avg_lead_time=float(weighted_lt),
            sigma_L=float(weighted_sigma) if weighted_sigma > 0 else sigma_default,
            shipment_count=int(total_n),
            source=LeadTimeSource.ITEM_HISTORY,
            preferred_supplier_id=preferred_supplier_id,
        )

    # Level 4: Global Fallback
    return LeadTimeResult(
        item_id=item_id,
        avg_lead_time=global_lt,
        sigma_L=sigma_default,
        shipment_count=0,
        source=LeadTimeSource.GLOBAL_FALLBACK,
        preferred_supplier_id=preferred_supplier_id,
    )


def _resolve_lead_time(
    item_id: str,
    preferred_supplier_id: str | None,
    mv: pd.DataFrame,
    min_n: int,
    global_lt: float,
    sigma_default: float,
) -> LeadTimeResult:
    """Resolve lead time for a single item through the 4-level hierarchy.

    DEPRECATED: Use _resolve_lead_time_indexed for better performance.
    Kept for backwards compatibility with compute_lead_time_stats.
    """

    # Level 1: Preferred Supplier + Item
    if preferred_supplier_id:
        level1 = mv[
            (mv["item_id"] == item_id) &
            (mv["supplier_id"] == preferred_supplier_id) &
            (mv["n"] >= min_n)
        ]
        if not level1.empty:
            row = level1.iloc[0]
            return LeadTimeResult(
                item_id=item_id,
                avg_lead_time=float(row["avg_lt"]),
                sigma_L=float(row["sigma_L"]),
                shipment_count=int(row["n"]),
                source=LeadTimeSource.PREFERRED_SUPPLIER_ITEM,
                preferred_supplier_id=preferred_supplier_id,
            )

        # Level 2: Preferred Supplier Average (all items from this supplier)
        level2 = mv[
            (mv["supplier_id"] == preferred_supplier_id) &
            (mv["n"] >= min_n)
        ]
        if not level2.empty:
            # Weighted average across all items from this supplier
            total_n = level2["n"].sum()
            weighted_lt = (level2["avg_lt"] * level2["n"]).sum() / total_n
            # Pooled sigma
            weighted_sigma = np.sqrt((level2["sigma_L"] ** 2 * level2["n"]).sum() / total_n)
            return LeadTimeResult(
                item_id=item_id,
                avg_lead_time=float(weighted_lt),
                sigma_L=float(weighted_sigma) if weighted_sigma > 0 else sigma_default,
                shipment_count=int(total_n),
                source=LeadTimeSource.PREFERRED_SUPPLIER_AVG,
                preferred_supplier_id=preferred_supplier_id,
            )

    # Level 3: Item History (any supplier)
    level3 = mv[
        (mv["item_id"] == item_id) &
        (mv["n"] >= min_n)
    ]
    if not level3.empty:
        # Weighted average across all suppliers for this item
        total_n = level3["n"].sum()
        weighted_lt = (level3["avg_lt"] * level3["n"]).sum() / total_n
        weighted_sigma = np.sqrt((level3["sigma_L"] ** 2 * level3["n"]).sum() / total_n)
        return LeadTimeResult(
            item_id=item_id,
            avg_lead_time=float(weighted_lt),
            sigma_L=float(weighted_sigma) if weighted_sigma > 0 else sigma_default,
            shipment_count=int(total_n),
            source=LeadTimeSource.ITEM_HISTORY,
            preferred_supplier_id=preferred_supplier_id,
        )

    # Level 4: Global Fallback
    return LeadTimeResult(
        item_id=item_id,
        avg_lead_time=global_lt,
        sigma_L=sigma_default,
        shipment_count=0,
        source=LeadTimeSource.GLOBAL_FALLBACK,
        preferred_supplier_id=preferred_supplier_id,
    )


def compute_lead_time_stats(
    shipment_df: pd.DataFrame,
    fallback_df: pd.DataFrame,
    min_shipments: int | None = None,
) -> pd.DataFrame:
    """Aggregate shipment lead times per product with fallback to product defaults.

    DEPRECATED: Use compute_hierarchical_lead_time for supplier-based hierarchy.

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
