# features.py
import pandas as pd
import numpy as np


def detect_stockout_days(movements_df: pd.DataFrame) -> pd.DataFrame:
    """Detect days the SKU had no inventory available to sell at any point.

    A day is a stockout when the maximum inventory observed during the day
    is zero -- i.e., the SKU started empty AND no restock arrived. End-of-day
    sellouts (started with stock, sold it all by close) are NOT stockouts:
    they had real demand and must remain in the training set.

    Uses two signals from ``stock_movements``:

    * Start-of-day inventory, computed as the previous day's end-of-day
      forward-filled across no-movement days, with the recorded
      ``previous_quantity`` from the first movement of the day overriding it
      when present.
    * Per-day max ``current_quantity`` across any movement during the day,
      to catch mid-day restocks that brought the SKU out of stockout.

    Args:
        movements_df: DataFrame with columns ``item_id``, ``at``,
            ``previous_quantity``, ``current_quantity``.

    Returns:
        DataFrame with columns ``[item_id, date, is_stockout]`` where
        ``is_stockout`` is True only on days the SKU was empty the entire day.
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

    # End-of-day inventory: last movement per (item, date). Used to derive the
    # following day's start-of-day on no-movement days.
    eod = (
        df.dropna(subset=["current_quantity"])
        .sort_values("at")
        .groupby(["item_id", "date"], as_index=False)
        .last()[["item_id", "date", "current_quantity"]]
        .rename(columns={"current_quantity": "eod_qty"})
    )

    # First-movement previous_quantity per (item, date): the SKU's actual
    # start-of-day inventory when at least one movement occurred that day.
    first_prev = (
        df.dropna(subset=["previous_quantity"])
        .sort_values("at")
        .groupby(["item_id", "date"], as_index=False)
        .first()[["item_id", "date", "previous_quantity"]]
        .rename(columns={"previous_quantity": "first_prev_qty"})
    )

    # Per-day peak inventory observed across any movement that day. A mid-day
    # restock that brings inventory above zero rules out a stockout for that day.
    peak = (
        df.dropna(subset=["current_quantity"])
        .groupby(["item_id", "date"], as_index=False)["current_quantity"]
        .max()
        .rename(columns={"current_quantity": "peak_qty"})
    )

    if eod.empty and first_prev.empty:
        return pd.DataFrame(columns=["item_id", "date", "is_stockout"])

    # Full (item, date) grid across the observed window.
    all_items = pd.concat([eod["item_id"], first_prev["item_id"]]).unique()
    date_min = min(
        d for d in [eod["date"].min(), first_prev["date"].min()] if pd.notna(d)
    )
    date_max = max(
        d for d in [eod["date"].max(), first_prev["date"].max()] if pd.notna(d)
    )
    all_dates = pd.date_range(date_min, date_max, freq="D")
    full_idx = pd.MultiIndex.from_product(
        [all_items, all_dates], names=["item_id", "date"]
    )

    grid = (
        eod.set_index(["item_id", "date"]).reindex(full_idx)
        .join(first_prev.set_index(["item_id", "date"]), how="left")
        .join(peak.set_index(["item_id", "date"]), how="left")
    )

    # Forward-fill end-of-day across no-movement days (inventory carries over).
    grid["eod_qty"] = grid.groupby(level="item_id")["eod_qty"].ffill()

    # Start-of-day: prefer the recorded start (first movement's previous_quantity);
    # fall back to the previous day's end-of-day for no-movement days.
    prev_eod = grid.groupby(level="item_id")["eod_qty"].shift(1)
    grid["start_qty"] = grid["first_prev_qty"].fillna(prev_eod)
    # Back-fill the very first days of the window when no prior inventory
    # exists, using the next observed start_qty as a best-effort baseline.
    grid["start_qty"] = grid.groupby(level="item_id")["start_qty"].bfill()

    # Max inventory observed during the day. Days with no movements have NaN
    # peak; those inherit start_qty (nothing changed all day).
    grid["max_during_day"] = grid["peak_qty"].fillna(grid["start_qty"])
    grid["max_inventory"] = grid[["start_qty", "max_during_day"]].max(axis=1)

    grid["is_stockout"] = (grid["max_inventory"].fillna(0) == 0)

    return (
        grid[["is_stockout"]]
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


def compute_per_sku_cv(daily_df: pd.DataFrame) -> dict[str, float]:
    """Per-SKU coefficient of variation, computed on sale-days only.

    Used to route safety stock between Poisson (CV <= threshold) and Negative
    Binomial (CV > threshold). The zero-filled grid would understate the
    burstiness of intermittent demand because it deflates both the mean and
    the std with zeros -- the metric we care about is "how variable are the
    days something actually sold."

    Args:
        daily_df: Output of ``build_daily_usage``: long-form DataFrame with
            columns ``item_id`` and ``consumption``, one row per (item, day).

    Returns:
        Dict ``{item_id: cv}``. Items with no sale-days are absent (caller
        should default to a regime).
    """
    if daily_df.empty:
        return {}

    sale_days = daily_df[daily_df["consumption"] > 0]
    if sale_days.empty:
        return {}

    grouped = sale_days.groupby("item_id")["consumption"]
    means = grouped.mean()
    stds = grouped.std(ddof=0)

    result: dict[str, float] = {}
    for item_id, mean_v in means.items():
        if mean_v <= 0:
            continue
        std_v = stds.get(item_id, 0.0)
        result[str(item_id)] = float(std_v / mean_v)
    return result


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
