"""Compute MAPE from historical forecasts vs actual daily usage.

Also exposes a CLI entry point for the Phase 1 ship gate (TSB vs
dow_weighted, WAPE per category) — see ``main()`` at the bottom.
"""

from __future__ import annotations

import argparse
import logging
from datetime import datetime, timedelta, timezone

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


# ---------------------------------------------------------------------------
# Method comparison CLI -- Phase 1 ship gate
# ---------------------------------------------------------------------------


def run_method_comparison(
    methods: list[str],
    origin_days_ago_list: list[int],
    horizon_days: int = 14,
    lookback_days: int | None = None,
    metric: str = "lt_wape",
) -> pd.DataFrame:
    """Run a walk-forward backtest comparing forecast methods on live data.

    For each origin date ``o = today - origin_days_ago``:
      - Build features from the ``lookback_days`` of stock movements before ``o``.
      - Run each method, producing ``mu_hat`` per item.
      - Compare ``mu_hat`` to actual daily consumption in ``[o, o + horizon_days]``.

    Metric choices:

    * ``"lt_wape"`` (default) -- score lead-time demand: for each (origin, item)
      compare ``mu_hat * days_observed`` to ``sum(actual over the window)``.
      This is the metric that matches the reorder-quantity decision and is the
      right gate for intermittent demand. The "scored" column is the number of
      (origin, item) pairs evaluated.
    * ``"daily_wape"`` -- score per-day: compare ``mu_hat`` to each day's
      actual. Brutal on point estimates of bursty demand (most days are 0 or a
      burst) but useful as a diagnostic. The "scored" column is item-days.

    Returns a per-(method, category) DataFrame with WAPE, bias, scored count,
    and total units sold. Use these numbers as the Phase 1 ship gate.
    """
    # Imported lazily so unit tests that hit compute_mape do not require DB.
    from . import features as feat
    from . import forecast as fc
    from .adapters.supabase_repo import SupabaseRepo

    lookback = lookback_days if lookback_days is not None else max(
        config.ROLLING_WINDOW * 2, config.CV_WINDOW_DAYS
    )

    repo = SupabaseRepo()
    items_df = repo.get_items()
    if items_df.empty:
        raise RuntimeError("No active items found in database")
    category_by_item = dict(zip(items_df["item_id"].astype(str), items_df["category_name"]))

    # Phase 4: methods ending in "_events" learn global event multipliers
    # (recent SHIPMENT_RECEIPT / DISPLAY_SET in the prior 7 days) from the
    # history window and apply them at scoring time. The underlying estimator
    # is the prefix ("dow_weighted" for "dow_weighted_events").
    event_window_days = 7
    ship_col = f"recent_shipment_{event_window_days}d"
    disp_col = f"recent_display_{event_window_days}d"

    def base_method(m: str) -> str:
        return m[:-len("_events")] if m.endswith("_events") else m

    rows: list[dict] = []
    for origin_days_ago in origin_days_ago_list:
        origin = datetime.now(timezone.utc) - timedelta(days=origin_days_ago)
        history_start = origin - timedelta(days=lookback)
        actual_end = origin + timedelta(days=horizon_days)

        history_mv = repo.get_stock_movements(history_start, origin)
        actual_mv = repo.get_stock_movements(origin, actual_end)

        if history_mv.empty:
            logging.warning("No history movements for origin %s", origin.date())
            continue

        history_daily = feat.build_daily_usage(history_mv)
        actual_daily = (
            feat.build_daily_usage(actual_mv) if not actual_mv.empty
            else pd.DataFrame(columns=["item_id", "date", "consumption"])
        )

        # Index actual consumption by (item_id, date) for fast lookup.
        if actual_daily.empty:
            continue
        actual_daily["item_id"] = actual_daily["item_id"].astype(str)
        actual_daily = actual_daily.set_index(["item_id", "date"])

        # Combined movements for event-feature lookup at scoring time.
        # Multipliers are learned from history only; the at-scoring feature
        # state uses any event up to (scored_day - 1), which can span both
        # windows -- that mirrors how the deployed pipeline would see events
        # arriving by the time it predicts each day.
        combined_mv = pd.concat([history_mv, actual_mv], ignore_index=True) \
            if not actual_mv.empty else history_mv

        # Learn global event multipliers from history training window.
        history_events = feat.build_event_features(history_mv, window_days=event_window_days)
        if not history_events.empty and not history_daily.empty:
            history_daily_keyed = history_daily.copy()
            history_daily_keyed["item_id"] = history_daily_keyed["item_id"].astype(str)
            history_daily_keyed["date"] = pd.to_datetime(history_daily_keyed["date"]).dt.floor("D")
            history_events["date"] = pd.to_datetime(history_events["date"]).dt.floor("D")
            train = history_daily_keyed.merge(
                history_events, on=["item_id", "date"], how="left",
            )
            train[ship_col] = train[ship_col].fillna(0).astype(int)
            train[disp_col] = train[disp_col].fillna(0).astype(int)
            global_event_mults = feat.compute_global_event_multipliers(
                train, event_cols=[ship_col, disp_col],
            )
        else:
            global_event_mults = {ship_col: 1.0, disp_col: 1.0}

        # Per (item, day) event-state lookup across the full window.
        all_events = feat.build_event_features(combined_mv, window_days=event_window_days)
        if not all_events.empty:
            all_events["date"] = pd.to_datetime(all_events["date"]).dt.floor("D")
            all_events["item_id"] = all_events["item_id"].astype(str)
            events_index = all_events.set_index(["item_id", "date"])
        else:
            events_index = None

        for method in methods:
            estimates = fc.estimate_mu_sigma(history_daily, method=base_method(method))
            apply_events = method.endswith("_events")
            for _, est_row in estimates.iterrows():
                item_id = str(est_row["item_id"])
                mu_hat = float(est_row["mu_hat"])
                dow_mult = est_row.get("dow_multipliers") if "dow_multipliers" in est_row else None
                category = category_by_item.get(item_id, "(uncategorized)")
                try:
                    item_actuals = actual_daily.loc[item_id]
                except KeyError:
                    continue
                if isinstance(item_actuals, pd.Series):
                    item_actuals = item_actuals.to_frame().T
                for date, actual in zip(item_actuals.index, item_actuals["consumption"]):
                    if isinstance(dow_mult, dict) and dow_mult:
                        dow = pd.Timestamp(date).dayofweek
                        mult = dow_mult.get(dow, dow_mult.get(str(dow), 1.0))
                        predicted = mu_hat * float(mult)
                    else:
                        predicted = mu_hat
                    if apply_events and events_index is not None:
                        key = (item_id, pd.Timestamp(date).floor("D"))
                        if key in events_index.index:
                            row_ev = events_index.loc[key]
                            if int(row_ev[ship_col]) == 1:
                                predicted *= global_event_mults.get(ship_col, 1.0)
                            if int(row_ev[disp_col]) == 1:
                                predicted *= global_event_mults.get(disp_col, 1.0)
                    rows.append({
                        "method": method,
                        "origin_days_ago": origin_days_ago,
                        "item_id": item_id,
                        "category": category,
                        "date": date,
                        "predicted": predicted,
                        "actual": float(actual),
                    })

    if not rows:
        return pd.DataFrame(columns=[
            "method", "category", "scored", "wape", "bias", "units_sold"
        ])

    df = pd.DataFrame(rows)
    if metric == "daily_wape":
        return _aggregate_daily(df)
    if metric == "lt_wape":
        return _aggregate_lead_time(df)
    raise ValueError(f"Unknown metric: {metric!r}. Use 'lt_wape' or 'daily_wape'.")


def _aggregate_daily(df: pd.DataFrame) -> pd.DataFrame:
    """Daily WAPE: every (item, day, origin) row contributes one |error|.

    Useful diagnostic but unfair to point estimates of intermittent demand --
    most days are zero or a burst, so a constant rate forecast always looks
    wrong on individual days even when the rate is correct.
    """
    grouped = df.groupby(["method", "category"], as_index=False)
    return grouped.apply(_daily_category_metrics, include_groups=False).reset_index(drop=True)


def _daily_category_metrics(g: pd.DataFrame) -> pd.Series:
    actual_sum = float(g["actual"].sum())
    abs_err = float(np.abs(g["predicted"] - g["actual"]).sum())
    signed_err = float((g["predicted"] - g["actual"]).mean())
    wape = abs_err / actual_sum if actual_sum > 0 else float("nan")
    return pd.Series({
        "scored": int(len(g)),
        "units_sold": int(actual_sum),
        "wape": wape,
        "bias": signed_err,
    })


def _aggregate_lead_time(df: pd.DataFrame) -> pd.DataFrame:
    """Lead-time WAPE: each (item, origin) gets summed predicted vs summed actual.

    For a forecast that estimates a daily rate, the right test is whether
    ``mu_hat * window_length`` matches actual total demand over the window.
    That is the quantity that drives the reorder decision, and it lets a
    correct-on-average rate forecast pass even when daily actuals are lumpy.
    """
    per_item = df.groupby(
        ["method", "origin_days_ago", "item_id", "category"], as_index=False,
    ).agg(
        actual_sum=("actual", "sum"),
        days_count=("actual", "count"),
        # Sum per-day predictions; for the flat-mu methods every row carries
        # the same value, while DOW-adjusted methods now contribute the
        # right per-day amount.
        predicted_sum=("predicted", "sum"),
    )
    per_item["abs_err"] = (per_item["predicted_sum"] - per_item["actual_sum"]).abs()
    per_item["signed_err"] = per_item["predicted_sum"] - per_item["actual_sum"]

    agg = per_item.groupby(["method", "category"], as_index=False).agg(
        scored=("actual_sum", "count"),
        units_sold=("actual_sum", "sum"),
        abs_err_total=("abs_err", "sum"),
        signed_err_total=("signed_err", "sum"),
    )
    agg["wape"] = np.where(
        agg["units_sold"] > 0,
        agg["abs_err_total"] / agg["units_sold"].clip(lower=1e-9),
        np.nan,
    )
    agg["bias"] = np.where(
        agg["scored"] > 0,
        agg["signed_err_total"] / agg["scored"].clip(lower=1),
        np.nan,
    )
    return agg[["method", "category", "scored", "units_sold", "wape", "bias"]]


def evaluate_ship_gate(
    comparison_df: pd.DataFrame,
    new_method: str,
    baseline_method: str,
    category_regression_limit: float = 0.25,
) -> dict:
    """Apply the Phase 1 ship gate to a method-comparison DataFrame.

    Gate (all must pass for SHIP):
      1. Overall WAPE on ``new_method`` <= overall WAPE on ``baseline_method``.
      2. No category's WAPE on ``new_method`` exceeds the baseline's category
         WAPE by more than ``category_regression_limit`` (relative).

    Returns a dict with keys ``decision`` ("SHIP" | "HOLD"), ``overall``,
    ``regressed_categories``.
    """
    overall: dict[str, dict[str, float]] = {}
    for method in (new_method, baseline_method):
        m = comparison_df[comparison_df["method"] == method]
        units = m["units_sold"].sum()
        # Reconstruct overall WAPE from per-category aggregates.
        abs_err = (m["wape"] * m["units_sold"]).sum()
        overall[method] = {
            "wape": float(abs_err / units) if units > 0 else float("nan"),
            "units_sold": int(units),
        }

    new_overall = overall[new_method]["wape"]
    base_overall = overall[baseline_method]["wape"]

    regressed = []
    new_by_cat = dict(
        zip(comparison_df[comparison_df["method"] == new_method]["category"],
            comparison_df[comparison_df["method"] == new_method]["wape"])
    )
    base_by_cat = dict(
        zip(comparison_df[comparison_df["method"] == baseline_method]["category"],
            comparison_df[comparison_df["method"] == baseline_method]["wape"])
    )
    for cat, new_wape in new_by_cat.items():
        base_wape = base_by_cat.get(cat)
        if base_wape is None or pd.isna(base_wape) or pd.isna(new_wape):
            continue
        # Skip near-zero baselines where relative change is meaningless.
        if base_wape < 1e-6:
            continue
        relative = (new_wape - base_wape) / base_wape
        if relative > category_regression_limit:
            regressed.append({"category": cat, "baseline": base_wape, "new": new_wape, "delta": relative})

    overall_ok = not (pd.isna(new_overall) or pd.isna(base_overall)) and new_overall <= base_overall
    decision = "SHIP" if (overall_ok and not regressed) else "HOLD"
    return {
        "decision": decision,
        "overall": overall,
        "regressed_categories": regressed,
    }


def _format_report(comparison_df: pd.DataFrame, gate: dict) -> str:
    lines = ["Per-category comparison:", comparison_df.to_string(index=False), ""]
    lines.append("Overall:")
    for method, vals in gate["overall"].items():
        lines.append(f"  {method:>12}: WAPE = {vals['wape']:.3%}, units_sold = {vals['units_sold']}")
    lines.append("")
    lines.append(f"Decision: {gate['decision']}")
    if gate["regressed_categories"]:
        lines.append("Regressed categories (> 25% worse than baseline):")
        for r in gate["regressed_categories"]:
            lines.append(
                f"  {r['category']}: baseline {r['baseline']:.3%} -> new {r['new']:.3%} "
                f"({r['delta']:+.1%})"
            )
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Phase 1 ship gate: compare TSB vs dow_weighted on live data."
    )
    parser.add_argument(
        "--methods",
        default="tsb,dow_weighted",
        help="Comma-separated estimator methods to compare (default: tsb,dow_weighted).",
    )
    parser.add_argument(
        "--origins",
        default="7,14,21,28",
        help="Comma-separated backtest origins in days-ago (default: 7,14,21,28).",
    )
    parser.add_argument("--horizon", type=int, default=14, help="Score horizon in days (default: 14).")
    parser.add_argument(
        "--lookback",
        type=int,
        default=None,
        help="Movement history window per origin (default: max(2x rolling, CV_WINDOW_DAYS)).",
    )
    parser.add_argument(
        "--new-method", default="tsb", help="Method under test for the ship gate (default: tsb)."
    )
    parser.add_argument(
        "--baseline", default="dow_weighted", help="Baseline method for the ship gate."
    )
    parser.add_argument(
        "--metric",
        default="lt_wape",
        choices=["lt_wape", "daily_wape"],
        help=(
            "Scoring metric. lt_wape (default) compares summed predicted-vs-actual "
            "over each backtest window -- matches reorder decision granularity. "
            "daily_wape compares per day -- diagnostic only on intermittent demand."
        ),
    )
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

    methods = [m.strip() for m in args.methods.split(",") if m.strip()]
    origins = [int(x) for x in args.origins.split(",") if x.strip()]
    comparison_df = run_method_comparison(
        methods=methods,
        origin_days_ago_list=origins,
        horizon_days=args.horizon,
        lookback_days=args.lookback,
        metric=args.metric,
    )
    gate = evaluate_ship_gate(
        comparison_df=comparison_df,
        new_method=args.new_method,
        baseline_method=args.baseline,
    )
    print(f"Metric: {args.metric}\n")
    print(_format_report(comparison_df, gate))


if __name__ == "__main__":
    main()
