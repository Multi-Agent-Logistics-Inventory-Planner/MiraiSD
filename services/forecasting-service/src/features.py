# features.py
import pandas as pd
import numpy as np


def detect_stockout_days(movements_df: pd.DataFrame) -> pd.DataFrame:
    """Detect days where inventory was zero (stockout) from movement history.

    Uses previous_quantity and current_quantity from stock_movements to
    determine end-of-day inventory level per item. Forward-fills across
    the full date range so days with no movements inherit prior inventory.

    Args:
        movements_df: DataFrame with columns including item_id, at,
            previous_quantity, current_quantity.

    Returns:
        DataFrame with columns [item_id, date, is_stockout] where
        is_stockout is True when the item had zero inventory that day.
    """
    if movements_df.empty:
        return pd.DataFrame(columns=["item_id", "date", "is_stockout"])

    has_inv_cols = (
        "current_quantity" in movements_df.columns
        and "previous_quantity" in movements_df.columns
    )
    if not has_inv_cols:
        return pd.DataFrame(columns=["item_id", "date", "is_stockout"])

    df = movements_df.copy()
    df["at"] = pd.to_datetime(df["at"], utc=True)
    df["date"] = df["at"].dt.floor("D").dt.tz_localize(None)

    # Take the last movement per (item_id, date) to get end-of-day inventory
    eod = (
        df.dropna(subset=["current_quantity"])
        .sort_values("at")
        .groupby(["item_id", "date"], as_index=False)
        .last()[["item_id", "date", "current_quantity"]]
    )

    if eod.empty:
        return pd.DataFrame(columns=["item_id", "date", "is_stockout"])

    # Build full date grid per item and forward-fill inventory
    all_items = eod["item_id"].unique()
    all_dates = pd.date_range(eod["date"].min(), eod["date"].max(), freq="D")
    full_idx = pd.MultiIndex.from_product(
        [all_items, all_dates], names=["item_id", "date"]
    )
    eod_full = eod.set_index(["item_id", "date"]).reindex(full_idx)

    # Forward-fill within each item, then back-fill the very first days
    eod_full["current_quantity"] = (
        eod_full.groupby(level="item_id")["current_quantity"]
        .ffill()
        .groupby(level="item_id")
        .bfill()
    )

    eod_full["is_stockout"] = eod_full["current_quantity"] == 0
    return (
        eod_full[["is_stockout"]]
        .reset_index()
        .sort_values(["item_id", "date"])
        .reset_index(drop=True)
    )


def build_daily_usage(events_df: pd.DataFrame) -> pd.DataFrame:
    """Build per-SKU daily consumption from inventory change events.

    Rules:
    - Consumption counts only sales events (reason in {"sale","sales"}) with negative quantity_change.
    - Daily consumption per SKU = max(-sum(quantity_change where sale), 0).
    - Generate a continuous daily series per SKU across the global window [min(date), max(date)].
    - Fill missing dates with 0 consumption.
    """
    if events_df.empty:
        return pd.DataFrame(columns=["date", "item_id", "consumption"])  # empty with headers

    df = events_df.copy()
    df["at"] = pd.to_datetime(df["at"], utc=True)
    df["date"] = df["at"].dt.floor("D").dt.tz_localize(None)

    # Identify sales events
    reason = df["reason"].astype("string").str.lower().str.strip()
    is_sale = reason.isin({"sale", "sales"}) & (df["quantity_change"] < 0)
    sales = df.loc[is_sale, ["item_id", "date", "quantity_change"]].copy()
    if sales.empty:
        # still need a complete grid of dates per item with zeros
        all_items = df["item_id"].astype(str).unique()
        all_dates = pd.date_range(df["date"].min(), df["date"].max(), freq="D")
        out = pd.MultiIndex.from_product(
            [all_items, all_dates], names=["item_id", "date"]
        ).to_frame(index=False)
        out["consumption"] = 0.0
        return out.sort_values(["item_id", "date"]).reset_index(drop=True)

    # Aggregate consumption: turn negatives into positive demand and sum
    sales["consumption"] = (-sales["quantity_change"]).astype(float)
    daily = sales.groupby(["item_id", "date"], as_index=False)["consumption"].sum()

    # Complete grid per SKU across global window
    all_items = df["item_id"].astype(str).unique()
    all_dates = pd.date_range(df["date"].min(), df["date"].max(), freq="D")
    full_idx = pd.MultiIndex.from_product([all_items, all_dates], names=["item_id", "date"])
    daily = daily.set_index(["item_id", "date"]).reindex(full_idx)
    daily["consumption"] = daily["consumption"].fillna(0.0)
    daily = daily.reset_index().sort_values(["item_id", "date"]).reset_index(drop=True)
    return daily


def build_stats(daily_df: pd.DataFrame) -> pd.DataFrame:
    """Add rolling and calendar features to per-SKU daily usage.

    Adds:
    - ma7, ma14: rolling means (min_periods=1)
    - std14: rolling std with ddof=0 (min_periods=1) to avoid NaNs
    - dow: day of week (0=Mon)
    - is_weekend: True if dow in {5,6}
    """
    if daily_df.empty:
        return pd.DataFrame(
            columns=["date", "item_id", "consumption", "ma7", "ma14", "std14", "dow", "is_weekend"]
        )  # noqa: E501

    df = daily_df.copy()
    df["date"] = pd.to_datetime(df["date"]).dt.floor("D")
    df = df.sort_values(["item_id", "date"]).reset_index(drop=True)

    grouped = df.groupby("item_id", sort=False)["consumption"]
    values = grouped.transform(lambda x: x.astype(float))
    df["ma7"] = grouped.transform(
        lambda x: x.astype(float).rolling(window=7, min_periods=1).mean()
    )
    df["ma14"] = grouped.transform(
        lambda x: x.astype(float).rolling(window=14, min_periods=1).mean()
    )
    # ddof=0 so that a single value window has std=0 (not NaN)
    df["std14"] = grouped.transform(
        lambda x: x.astype(float).rolling(window=14, min_periods=1).std(ddof=0)
    )

    df["dow"] = df["date"].dt.dayofweek
    df["is_weekend"] = df["dow"].isin([5, 6])
    cols = ["date", "item_id", "consumption", "ma7", "ma14", "std14", "dow", "is_weekend"]
    if "is_stockout" in df.columns:
        cols.append("is_stockout")
    return df[cols]


def build_daily_consumption(events_df: pd.DataFrame) -> pd.DataFrame:
    # events_df has columns from event payload; ensure datetime
    events_df["at"] = pd.to_datetime(events_df["at"], utc=True)
    events_df["date"] = events_df["at"].dt.date
    # negative is consumption; clip positive to 0 for demand
    events_df["consumption"] = (-events_df["quantity_change"]).clip(lower=0)
    daily = events_df.groupby(["item_id", "date"], as_index=False).agg(
        consumption=("consumption", "sum")
    )
    # rolling 14-day mean demand per item
    daily = daily.sort_values(["item_id", "date"])
    daily["avg_14"] = daily.groupby("item_id")["consumption"].transform(
        lambda s: s.rolling(14, min_periods=3).mean()
    )
    return daily
