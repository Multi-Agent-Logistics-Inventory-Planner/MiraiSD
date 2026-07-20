# segmentation.py
"""Demand-shape segmentation: continuous / drop / dead / new.

The daily-mu forecasting path assumes continuous demand. A large share of
this store's revenue comes from "drop" products (booster boxes, releases)
that sell 100+ units in 1-3 days and then sit out of stock -- zero-filled
OOS days crush their mu_hat and the policy layer then reports them as
healthy for months. Dead tail items have the opposite problem: a stale
nonzero mu keeps them in the at-risk list. This module classifies each SKU
from its sales shape so the pipeline can route policy per segment.
"""

from __future__ import annotations

import math
from datetime import date
from typing import Optional

import numpy as np
import pandas as pd

from . import config

SEGMENT_CONTINUOUS = "continuous"
SEGMENT_DROP = "drop"
SEGMENT_DEAD = "dead"
SEGMENT_NEW = "new"

VALID_SEGMENTS = {SEGMENT_CONTINUOUS, SEGMENT_DROP, SEGMENT_DEAD, SEGMENT_NEW}

_SIGNAL_COLUMNS = [
    "item_id",
    "window_days",
    "sale_days",
    "total_units",
    "top3_share",
    "stockout_frac",
    "in_stock_days",
    "days_since_last_sale",
    "rate_while_available",
    "last_drop_size",
    "last_drop_days",
    "drop_sizes",
]


def _drop_clusters(sale_rows: pd.DataFrame, gap_days: int) -> list[tuple[float, int]]:
    """Group sale days into clusters tolerating up to ``gap_days`` zero days.

    Returns a list of ``(units, span_days)`` per cluster in chronological order.
    """
    if sale_rows.empty:
        return []
    dates = sale_rows["date"].tolist()
    units = sale_rows["consumption"].tolist()
    clusters: list[tuple[float, int]] = []
    start = prev = dates[0]
    total = units[0]
    for d, u in zip(dates[1:], units[1:]):
        if (d - prev).days > gap_days + 1:
            clusters.append((float(total), (prev - start).days + 1))
            start = d
            total = 0.0
        total += u
        prev = d
    clusters.append((float(total), (prev - start).days + 1))
    return clusters


def _item_signals(
    group: pd.DataFrame,
    stockout_map: dict,
    today: date,
    first_activity: Optional[dict[str, date]],
    item_id: str,
) -> dict:
    """Compute the signal row for one item's zero-filled daily grid."""
    window_days = int(len(group))
    if first_activity and item_id in first_activity:
        activity_days = (today - first_activity[item_id]).days + 1
        window_days = min(window_days, max(activity_days, 0))

    sales = group[group["consumption"] > 0]
    sale_days = int(len(sales))
    total_units = float(group["consumption"].sum())
    top3_share = (
        float(group["consumption"].nlargest(3).sum() / total_units)
        if total_units > 0
        else 0.0
    )

    stockout_days = int(stockout_map.get(item_id, 0))
    grid_days = int(len(group))
    stockout_frac = stockout_days / grid_days if grid_days else 0.0
    in_stock_days = max(grid_days - stockout_days, 0)

    if sale_days:
        days_since_last_sale = float((today - sales["date"].max().date()).days)
    else:
        days_since_last_sale = float("nan")

    clusters = _drop_clusters(sales, config.SEGMENT_DROP_CLUSTER_GAP_DAYS)
    drop_sizes = [c[0] for c in clusters]

    return {
        "item_id": item_id,
        "window_days": window_days,
        "sale_days": sale_days,
        "total_units": total_units,
        "top3_share": top3_share,
        "stockout_frac": stockout_frac,
        "in_stock_days": in_stock_days,
        "days_since_last_sale": days_since_last_sale,
        "rate_while_available": total_units / max(in_stock_days, 1),
        "last_drop_size": clusters[-1][0] if clusters else float("nan"),
        "last_drop_days": clusters[-1][1] if clusters else float("nan"),
        "drop_sizes": drop_sizes,
    }


def compute_segment_signals(
    daily_df: pd.DataFrame,
    stockout_df: Optional[pd.DataFrame] = None,
    today: Optional[date] = None,
    first_activity: Optional[dict[str, date]] = None,
) -> pd.DataFrame:
    """Per-item demand-shape signals from the zero-filled daily usage grid.

    Args:
        daily_df: output of ``features.build_daily_usage`` (item_id, date,
            consumption; one row per item-day over the shared window).
        stockout_df: optional output of ``features.detect_stockout_days``.
        today: reference date for recency signals (defaults to grid max).
        first_activity: optional ``{item_id: first movement date}`` used to
            bound ``window_days`` for recently created items (the shared grid
            starts at the global min date, which overstates their history).

    Returns:
        DataFrame with one row per item (see ``_SIGNAL_COLUMNS``).
    """
    if daily_df.empty:
        return pd.DataFrame(columns=_SIGNAL_COLUMNS)

    df = daily_df.copy()
    df["item_id"] = df["item_id"].astype(str)
    df["date"] = pd.to_datetime(df["date"]).dt.floor("D")
    if today is None:
        today = df["date"].max().date()

    stockout_map: dict[str, int] = {}
    if stockout_df is not None and not stockout_df.empty:
        so = stockout_df.copy()
        so["item_id"] = so["item_id"].astype(str)
        stockout_map = (
            so.groupby("item_id")["is_stockout"].sum().astype(int).to_dict()
        )

    rows = [
        _item_signals(group.sort_values("date"), stockout_map, today, first_activity, item_id)
        for item_id, group in df.groupby("item_id", sort=False)
    ]
    return pd.DataFrame(rows, columns=_SIGNAL_COLUMNS)


def classify_segments(
    signals_df: pd.DataFrame,
    prior_segments: Optional[dict[str, str]] = None,
) -> dict[str, str]:
    """First-match segment rules with drop-entry/exit hysteresis.

    1. new: short observed history and nothing sold yet.
    2. dead: nothing sold, or a 1-2 sale trickle quiet for >28 days.
    3. drop: enough volume, sharply concentrated (top3_share), and either
       stockout-prone or sold on very few days.
    4. continuous: everything else.
    """
    priors = prior_segments or {}
    out: dict[str, str] = {}
    for row in signals_df.itertuples(index=False):
        item_id = str(row.item_id)
        if row.sale_days == 0:
            if row.window_days < config.SEGMENT_NEW_MAX_HISTORY_DAYS:
                out[item_id] = SEGMENT_NEW
            else:
                out[item_id] = SEGMENT_DEAD
            continue
        # Trickle-dead needs a volume guard: a 150-unit burst that sold out
        # weeks ago also has 1-2 sale-days and a long quiet gap, but it is a
        # restock candidate (drop), not a dead item.
        if (
            row.sale_days <= config.SEGMENT_DEAD_MAX_SALE_DAYS
            and row.total_units < config.SEGMENT_DROP_MIN_UNITS
            and float(row.days_since_last_sale) > config.SEGMENT_DEAD_DAYS_SINCE_SALE
        ):
            out[item_id] = SEGMENT_DEAD
            continue
        share_threshold = (
            config.SEGMENT_TOP3_SHARE_EXIT
            if priors.get(item_id) == SEGMENT_DROP
            else config.SEGMENT_TOP3_SHARE_ENTER
        )
        if (
            row.total_units >= config.SEGMENT_DROP_MIN_UNITS
            and row.top3_share >= share_threshold
            and (
                row.stockout_frac >= config.SEGMENT_DROP_MIN_STOCKOUT_FRAC
                or row.sale_days <= config.SEGMENT_DROP_MAX_SALE_DAYS
            )
        ):
            out[item_id] = SEGMENT_DROP
            continue
        out[item_id] = SEGMENT_CONTINUOUS
    return out


def classify_quiet_item(created_at, today: date) -> str:
    """Segment for an item with zero movements in the whole lookback window.

    Such items never appear in the daily grid, so ``classify_segments`` cannot
    apply its new-vs-dead rule to them. Mirror it here from the product's
    creation date: a recently created product awaiting its first order is
    ``new`` (kept visible), anything older with no activity is ``dead``.
    Missing/NaT ``created_at`` degrades to ``dead``.
    """
    if created_at is None or pd.isna(created_at):
        return SEGMENT_DEAD
    created = pd.Timestamp(created_at)
    if created.tzinfo is not None:
        created = created.tz_convert("UTC").tz_localize(None)
    age_days = (pd.Timestamp(today) - created.normalize()).days
    if age_days < config.SEGMENT_NEW_MAX_HISTORY_DAYS:
        return SEGMENT_NEW
    return SEGMENT_DEAD


def build_drop_order_qty(
    signals_df: pd.DataFrame,
    avg_last_n: Optional[int] = None,
) -> dict[str, float]:
    """Suggested order size per drop item: mean of the last N drop sizes.

    Items with no observed drops are absent from the result.
    """
    n = avg_last_n if avg_last_n is not None else config.SEGMENT_DROP_AVG_LAST_N
    out: dict[str, float] = {}
    for row in signals_df.itertuples(index=False):
        sizes = list(row.drop_sizes) if isinstance(row.drop_sizes, list) else []
        if sizes:
            out[str(row.item_id)] = float(np.mean(sizes[-n:]))
    return out


def apply_drop_mu_override(
    mu_hat: pd.Series,
    item_ids: pd.Series,
    segment_map: dict[str, str],
    rate_map: dict[str, float],
) -> tuple[pd.Series, pd.Series]:
    """Replace mu for drop items with their in-stock demand rate.

    Returns ``(mu, applied_mask)`` -- applied only where the item is a drop
    AND a rate is available (missing rate = no-op, never worse than before).
    """
    ids = item_ids.astype(str)
    is_drop = ids.map(lambda i: segment_map.get(i) == SEGMENT_DROP)
    rates = ids.map(rate_map)
    applied = is_drop & rates.notna()
    new_mu = mu_hat.astype(float).where(~applied, rates.astype(float))
    return new_mu, applied


def apply_dead_policy_mask(
    days_to_stockout: pd.Series,
    suggested_qty: pd.Series,
    item_ids: pd.Series,
    segment_map: dict[str, str],
) -> tuple[pd.Series, pd.Series]:
    """Suppress urgency outputs for dead items.

    days_to_stockout becomes inf ("no urgency" -> persisted as NULL) and the
    suggested quantity zero. Must be explicit: EPSILON_MU == MU_FLOOR, so
    floor-pinned dead items would otherwise show qty/0.1 = misleadingly
    small finite numbers.
    """
    ids = item_ids.astype(str)
    is_dead = ids.map(lambda i: segment_map.get(i) == SEGMENT_DEAD)
    days = days_to_stockout.where(~is_dead, np.inf)
    qty = suggested_qty.where(~is_dead, 0).astype(int)
    return days, qty


def apply_drop_qty_override(
    suggested_qty: pd.Series,
    current_qty: pd.Series,
    item_ids: pd.Series,
    segment_map: dict[str, str],
    drop_qty_map: dict[str, float],
    on_order: Optional[pd.Series] = None,
) -> pd.Series:
    """Order-per-drop quantity for drop items, netting stock and inbound units."""
    ids = item_ids.astype(str)
    targets = ids.map(drop_qty_map)
    applicable = ids.map(lambda i: segment_map.get(i) == SEGMENT_DROP) & targets.notna()
    inbound = (
        on_order.fillna(0).astype(float)
        if on_order is not None
        else pd.Series(0.0, index=suggested_qty.index)
    )
    drop_qty = (
        np.ceil(targets.fillna(0).astype(float))
        - current_qty.astype(float)
        - inbound
    ).clip(lower=0)
    return suggested_qty.where(~applicable, drop_qty).astype(int)
