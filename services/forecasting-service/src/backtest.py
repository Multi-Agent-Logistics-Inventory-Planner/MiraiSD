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
        DataFrame[item_id, mape, forecast_mu, actual_mu, residual_bias,
        backtest_days]. residual_bias is the signed per-day error
        (forecast_mu - actual_mu); positive = over-prediction, negative
        = under-prediction. Used by the residual bias correction layer.
    """
    h = horizon_days if horizon_days is not None else config.BACKTEST_HORIZON_DAYS
    eps = epsilon if epsilon is not None else config.MAPE_EPSILON
    result_cols = ["item_id", "mape", "forecast_mu", "actual_mu", "residual_bias", "backtest_days"]

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
    merged["residual_bias"] = merged["forecast_mu"] - merged["actual_mu"]

    return merged[result_cols].reset_index(drop=True)


def apply_residual_bias_correction(
    mu_hat: pd.Series,
    residual_bias: pd.Series,
    backtest_days: pd.Series,
    cap_fraction: float | None = None,
    min_backtest_days: int | None = None,
    mu_floor: float | None = None,
) -> pd.DataFrame:
    """Subtract recent forecast bias from mu_hat, clipped and floored.

    For each item: ``corrected = max(mu_hat - clip(bias, +/- cap*mu_hat), mu_floor)``
    where ``bias = forecast_mu - actual_mu`` from a recent backtest window.
    Items with NaN bias or ``backtest_days < min_backtest_days`` pass through
    uncorrected (correction is 0).

    Args:
        mu_hat: Current period mu estimate per item.
        residual_bias: Signed per-day bias from compute_mape.
        backtest_days: Number of days the bias was measured over (per item).
        cap_fraction: Max correction as fraction of mu_hat (default config).
        min_backtest_days: Skip items with fewer than this many days (default config).
        mu_floor: Lower bound for corrected mu_hat (default config).

    Returns:
        DataFrame with columns [mu_hat_corrected, correction_applied,
        mu_hat_pre_correction], indexed like the inputs.
    """
    cap = cap_fraction if cap_fraction is not None else config.RESIDUAL_BIAS_CORRECTION_CAP
    min_days = (
        min_backtest_days if min_backtest_days is not None
        else config.RESIDUAL_BIAS_MIN_BACKTEST_DAYS
    )
    floor = mu_floor if mu_floor is not None else config.MU_FLOOR

    mu = mu_hat.astype(float)
    if mu.empty:
        return pd.DataFrame(
            {"mu_hat_corrected": mu, "correction_applied": mu, "mu_hat_pre_correction": mu}
        )

    bias = residual_bias.astype(float) if residual_bias is not None else pd.Series(
        np.nan, index=mu.index
    )
    days = backtest_days.astype(float) if backtest_days is not None else pd.Series(
        0.0, index=mu.index
    )

    eligible = bias.notna() & (days >= min_days)
    cap_per_item = cap * mu.abs()
    correction = bias.where(eligible, 0.0).clip(lower=-cap_per_item, upper=cap_per_item)
    corrected = (mu - correction).clip(lower=floor)

    return pd.DataFrame(
        {
            "mu_hat_corrected": corrected,
            "correction_applied": correction,
            "mu_hat_pre_correction": mu,
        }
    )


# ---------------------------------------------------------------------------
# Method comparison CLI -- Phase 1 ship gate
# ---------------------------------------------------------------------------


def run_method_comparison(
    methods: list[str],
    origin_days_ago_list: list[int],
    horizon_days: int = 14,
    lookback_days: int | None = None,
    metric: str = "lt_wape",
    group_by: str = "category",
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

    Returns a per-(method, group) DataFrame with WAPE, bias, scored count,
    and total units sold, where the group dimension is ``group_by``:
    ``"category"`` (default) or ``"segment"`` (demand-shape segments,
    classified per origin from the history window with empty priors). The
    segment view is the ship gate for segment policy routing: drop-segment
    WAPE must improve without a continuous-segment regression.
    """
    # Imported lazily so unit tests that hit compute_mape do not require DB.
    from . import features as feat
    from . import forecast as fc
    from . import segmentation
    from .adapters.supabase_repo import SupabaseRepo

    if group_by not in ("category", "segment"):
        raise ValueError(f"group_by must be 'category' or 'segment', got {group_by!r}")

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

    # Walk-forward bias correction: when enabled, for each (method, item) we
    # carry forward the per-day signed error from the most-recently-scored
    # origin and subtract it from mu_hat at the next (newer) origin -- the
    # same mechanic the deployed pipeline applies via mape_df, just exercised
    # one origin at a time. Origins are processed oldest-first so the bias
    # signal flows forward in time. Items lacking prior data at a given
    # origin pass through uncorrected (first origin always uncorrected).
    apply_bias = config.RESIDUAL_BIAS_CORRECTION_ENABLED
    prior_bias: dict[tuple[str, str], tuple[float, int]] = {}  # (method, item) -> (bias, days)

    rows: list[dict] = []
    ordered_origins = sorted(origin_days_ago_list, reverse=True) if apply_bias else origin_days_ago_list
    for origin_days_ago in ordered_origins:
        origin = datetime.now(timezone.utc) - timedelta(days=origin_days_ago)
        history_start = origin - timedelta(days=lookback)
        actual_end = origin + timedelta(days=horizon_days)

        history_mv = repo.get_stock_movements(history_start, origin)
        actual_mv = repo.get_stock_movements(origin, actual_end)

        if history_mv.empty:
            logging.warning("No history movements for origin %s", origin.date())
            continue

        history_daily = feat.build_daily_usage(history_mv)
        # Censored-demand path: merge is_stockout into history so the MLE in
        # _dow_weighted_estimate treats stockout days as right-censored
        # observations. Mirrors what pipeline.py:Step 4 does.
        if config.CENSORED_DEMAND_ENABLED:
            stockout_df = feat.detect_stockout_days(history_mv)
            if not stockout_df.empty:
                history_daily = history_daily.merge(
                    stockout_df, on=["item_id", "date"], how="left",
                )
                history_daily["is_stockout"] = history_daily["is_stockout"].fillna(False).astype(bool)
        actual_daily = (
            feat.build_daily_usage(actual_mv) if not actual_mv.empty
            else pd.DataFrame(columns=["item_id", "date", "consumption"])
        )

        # Segment dimension: classify each item's demand shape from the same
        # history window the estimators see (empty priors -- each origin is
        # scored on what would have been known at that time).
        segment_by_item: dict[str, str] = {}
        if group_by == "segment":
            seg_stockout = feat.detect_stockout_days(history_mv)
            seg_signals = segmentation.compute_segment_signals(
                history_daily, stockout_df=seg_stockout, today=origin.date(),
            )
            segment_by_item = segmentation.classify_segments(seg_signals)

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
                cap=config.EVENT_MULTIPLIER_CAP,
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

        # Collect this origin's per-(method, item) prediction errors so the
        # next (newer) origin can use them as the bias signal. Only populated
        # when apply_bias is on.
        per_origin_diffs: dict[tuple[str, str], list[float]] = {}

        for method in methods:
            estimates = fc.estimate_mu_sigma(history_daily, method=base_method(method))
            # Shrinkage: pull thin-history items toward category prior.
            # Mirrors pipeline.py:Step 5a.1.
            if config.SHRINKAGE_ENABLED and "n_observed_days" in estimates.columns:
                estimates = fc.apply_shrinkage(estimates, category_by_item)
            apply_events = method.endswith("_events")
            for _, est_row in estimates.iterrows():
                item_id = str(est_row["item_id"])
                mu_hat = float(est_row["mu_hat"])
                dow_mult = est_row.get("dow_multipliers") if "dow_multipliers" in est_row else None
                category = category_by_item.get(item_id, "(uncategorized)")

                # Apply walk-forward residual bias correction to mu_hat using
                # the prior-origin signal for this (method, item). Mirrors the
                # production helper exactly.
                if apply_bias:
                    prior = prior_bias.get((method, item_id))
                    if prior is not None:
                        bias_val, days_val = prior
                        corr_df = apply_residual_bias_correction(
                            mu_hat=pd.Series([mu_hat]),
                            residual_bias=pd.Series([bias_val]),
                            backtest_days=pd.Series([float(days_val)]),
                        )
                        mu_hat = float(corr_df["mu_hat_corrected"].iloc[0])

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
                        "segment": segment_by_item.get(item_id, "continuous"),
                        "date": date,
                        "predicted": predicted,
                        "actual": float(actual),
                    })
                    if apply_bias:
                        per_origin_diffs.setdefault((method, item_id), []).append(
                            predicted - float(actual)
                        )

        # After scoring this origin, freeze its signal for the next origin.
        if apply_bias:
            for key, diffs in per_origin_diffs.items():
                if diffs:
                    prior_bias[key] = (float(np.mean(diffs)), len(diffs))

    if not rows:
        return pd.DataFrame(columns=[
            "method", group_by, "scored", "wape", "bias", "units_sold"
        ])

    df = pd.DataFrame(rows)
    if metric == "daily_wape":
        return _aggregate_daily(df, dimension=group_by)
    if metric == "lt_wape":
        return _aggregate_lead_time(df, dimension=group_by)
    raise ValueError(f"Unknown metric: {metric!r}. Use 'lt_wape' or 'daily_wape'.")


def _aggregate_daily(df: pd.DataFrame, dimension: str = "category") -> pd.DataFrame:
    """Daily WAPE: every (item, day, origin) row contributes one |error|.

    Useful diagnostic but unfair to point estimates of intermittent demand --
    most days are zero or a burst, so a constant rate forecast always looks
    wrong on individual days even when the rate is correct.
    """
    grouped = df.groupby(["method", dimension], as_index=False)
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


def _aggregate_lead_time(df: pd.DataFrame, dimension: str = "category") -> pd.DataFrame:
    """Lead-time WAPE: each (item, origin) gets summed predicted vs summed actual.

    For a forecast that estimates a daily rate, the right test is whether
    ``mu_hat * window_length`` matches actual total demand over the window.
    That is the quantity that drives the reorder decision, and it lets a
    correct-on-average rate forecast pass even when daily actuals are lumpy.
    """
    per_item = df.groupby(
        ["method", "origin_days_ago", "item_id", dimension], as_index=False,
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

    agg = per_item.groupby(["method", dimension], as_index=False).agg(
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
    return agg[["method", dimension, "scored", "units_sold", "wape", "bias"]]


def evaluate_ship_gate(
    comparison_df: pd.DataFrame,
    new_method: str,
    baseline_method: str,
    category_regression_limit: float = 0.25,
    dimension: str = "category",
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
        zip(comparison_df[comparison_df["method"] == new_method][dimension],
            comparison_df[comparison_df["method"] == new_method]["wape"])
    )
    base_by_cat = dict(
        zip(comparison_df[comparison_df["method"] == baseline_method][dimension],
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
        "--by",
        default="category",
        choices=["category", "segment"],
        help=(
            "Grouping dimension for the report. 'segment' classifies items by "
            "demand shape (continuous/drop/dead/new) per origin -- use it to "
            "gate segment policy routing changes."
        ),
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
        group_by=args.by,
    )
    gate = evaluate_ship_gate(
        comparison_df=comparison_df,
        new_method=args.new_method,
        baseline_method=args.baseline,
        dimension=args.by,
    )
    print(f"Metric: {args.metric}\n")
    print(_format_report(comparison_df, gate))


if __name__ == "__main__":
    main()
