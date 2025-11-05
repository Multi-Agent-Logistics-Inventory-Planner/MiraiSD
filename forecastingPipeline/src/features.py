# features.py
import pandas as pd


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


def compute_policy(
    onhand_df: pd.DataFrame, daily_df: pd.DataFrame, target_days=21, reorder_threshold=7, now=None
) -> pd.DataFrame:
    latest = daily_df.groupby("item_id", as_index=False).agg(avg_daily=("avg_14", "last"))
    df = latest.merge(onhand_df, on="item_id", how="left").fillna({"quantity": 0})
    df["days_to_stockout"] = (df["quantity"] / df["avg_daily"]).replace([float("inf")], 9999)
    df["target_qty"] = (df["avg_daily"] * target_days).round().astype("Int64")
    df["suggested_reorder_qty"] = (df["target_qty"] - df["quantity"]).clip(lower=0).astype("Int64")
    return df
