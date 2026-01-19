from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

import pandas as pd

from . import config, events, features, forecast, policy, repo


def _now_utc_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def snapshot_onhand(inv_df: pd.DataFrame) -> pd.DataFrame:
    """Return latest on-hand quantity per item_id using as_of_ts."""
    if inv_df.empty:
        return pd.DataFrame({"item_id": [], "current_qty": []})
    inv_df = inv_df.sort_values(["item_id", "as_of_ts"])  # ensure time order
    last = inv_df.groupby("item_id", as_index=False).tail(1)
    return last[["item_id", "current_qty"]]


def _build_features(events_df: pd.DataFrame) -> pd.DataFrame:
    if events_df.empty:
        return pd.DataFrame(
            columns=["date", "item_id", "consumption", "ma7", "ma14", "std14", "dow", "is_weekend"]
        )
    daily = features.build_daily_usage(events_df)
    feats = features.build_stats(daily)
    return feats


def run_batch(
    from_ts: Optional[str] = None,
    to_ts: Optional[str] = None,
    method: str = "ma14",
    target_days: Optional[int] = None,
) -> Path:
    """Run the forecasting pipeline and write database-aligned forecasts CSV.

    Output columns/order:
      id, item_id, computed_at, horizon_days, avg_daily_delta, days_to_stockout,
      suggested_reorder_qty, suggested_order_date, confidence, features
    """
    computed_at = _now_utc_iso()
    target_days_of_cover = int(target_days or config.TARGET_DAYS)

    # 1) Load items & inventories
    items_df = repo.load_items()
    inv_df = repo.load_inventories()
    onhand_df = snapshot_onhand(inv_df)

    # Ensure optional columns exist with defaults
    if "service_level" not in items_df.columns:
        items_df["service_level"] = config.SERVICE_LEVEL_DEFAULT
    else:
        items_df["service_level"] = (
            pd.to_numeric(items_df["service_level"], errors="coerce")
            .fillna(config.SERVICE_LEVEL_DEFAULT)
        )

    if "lead_time_std_days" not in items_df.columns:
        items_df["lead_time_std_days"] = config.LEAD_TIME_STD_DEFAULT_DAYS
    else:
        items_df["lead_time_std_days"] = (
            pd.to_numeric(items_df["lead_time_std_days"], errors="coerce")
            .fillna(config.LEAD_TIME_STD_DEFAULT_DAYS)
        )

    # 2) Load events window
    if from_ts or to_ts:
        ev_df = events.load_events_window(
            from_ts or "1970-01-01T00:00:00Z", to_ts or _now_utc_iso()
        )
    else:
        ev_df = pd.DataFrame(columns=["event_id", "item_id", "quantity_change", "reason", "at"])

    # 3) Features
    feats = _build_features(ev_df)

    # 4) Forecast (per item_id)
    if feats.empty:
        fcst_df = pd.DataFrame(columns=["item_id", "mu_hat", "sigma_d_hat", "method"])  # floors later
    else:
        fcst_df = forecast.estimate_mu_sigma(feats, method=method)

    # 5) Join items + onhand + forecast; fill floors when missing
    merged = (
        items_df[["item_id", "lead_time_days", "lead_time_std_days", "service_level"]]
        .merge(onhand_df, on="item_id", how="left")
        .merge(fcst_df, on="item_id", how="left")
    )
    merged["current_qty"] = pd.to_numeric(merged["current_qty"], errors="coerce").fillna(0).astype(float)
    merged["mu_hat"] = pd.to_numeric(merged["mu_hat"], errors="coerce").fillna(config.MU_FLOOR)
    merged["sigma_d_hat"] = (
        pd.to_numeric(merged["sigma_d_hat"], errors="coerce").fillna(config.SIGMA_FLOOR)
    )
    merged["method"] = merged.get("method", method)

    # 6) Policy per item
    out_rows: list[dict] = []
    for _, r in merged.iterrows():
        item_id = str(r["item_id"])
        mu_hat = float(r["mu_hat"])
        sigma_d_hat = float(r["sigma_d_hat"])
        L = float(r["lead_time_days"]) if pd.notna(r["lead_time_days"]) else 0.0
        sigma_L = float(r["lead_time_std_days"]) if pd.notna(r["lead_time_std_days"]) else 0.0
        alpha = (
            float(r["service_level"]) if pd.notna(r["service_level"]) else config.SERVICE_LEVEL_DEFAULT
        )
        current_qty = float(r["current_qty"]) if pd.notna(r["current_qty"]) else 0.0

        z = policy.z_for_service_level(alpha)
        sigma_lt = policy.sigma_lead_time(mu_hat, sigma_d_hat, L, sigma_L)
        ss = z * sigma_lt
        rop = policy.reorder_point(mu_hat, ss, L)
        dso = policy.days_to_stockout(current_qty, mu_hat, epsilon=config.EPSILON_MU)
        suggested_qty = policy.suggest_order(current_qty, mu_hat, L, ss, target_days_of_cover)

        order_date = ""
        try:
            if current_qty <= rop:
                order_date = pd.to_datetime(computed_at, utc=True).date().isoformat()
        except Exception:
            order_date = ""

        out_rows.append(
            {
                "item_id": item_id,
                "computed_at": computed_at,
                "horizon_days": int(target_days_of_cover),
                "avg_daily_delta": float(mu_hat),
                "days_to_stockout": float(dso) if dso != float("inf") else 1e12,
                "suggested_reorder_qty": int(suggested_qty),
                "suggested_order_date": order_date,
                "confidence": float(alpha),
                "features": json.dumps(
                    {
                        "sigma_d_hat": sigma_d_hat,
                        "lead_time_days": L,
                        "lead_time_std_days": sigma_L,
                        "z_score": z,
                        "safety_stock": ss,
                        "rop": rop,
                        "current_qty": current_qty,
                        "method": r.get("method", method),
                    },
                    ensure_ascii=False,
                ),
            }
        )

    out_df = pd.DataFrame(out_rows)
    if not out_df.empty:
        out_df = out_df.sort_values("item_id").reset_index(drop=True)

    out_path = repo.write_forecasts(out_df)
    return out_path


def main() -> None:
    parser = argparse.ArgumentParser(description="Run forecasting job")
    parser.add_argument("--from", dest="from_ts", type=str, default=None, help="Start ISO timestamp (inclusive)")
    parser.add_argument("--to", dest="to_ts", type=str, default=None, help="End ISO timestamp (inclusive)")
    parser.add_argument(
        "--method",
        dest="method",
        type=str,
        default="ma14",
        choices=["ma7", "ma14", "exp_smooth"],
        help="Forecast method",
    )
    parser.add_argument("--target-days", dest="target_days", type=int, default=None, help="Target days of cover")
    args = parser.parse_args()

    out_path = run_batch(from_ts=args.from_ts, to_ts=args.to_ts, method=args.method, target_days=args.target_days)
    print(str(out_path))


if __name__ == "__main__":
    main()

