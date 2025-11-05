# forecast_job.py
import json
from datetime import UTC, datetime, timedelta

import pandas as pd
from features import build_daily_consumption, compute_policy
from repo import Repo


def load_event_payloads(ndjson_path="events/inventory-changes.ndjson") -> pd.DataFrame:
    rows = []
    with open(ndjson_path, encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            e = json.loads(line)
            p = e["payload"]
            rows.append(
                {
                    "item_id": p["item_id"],
                    "from_box_id": p.get("from_box_id"),
                    "to_box_id": p.get("to_box_id"),
                    "quantity_change": p["quantity_change"],
                    "reason": p.get("reason"),
                    "actor_id": p.get("actor_id"),
                    "at": p["at"],
                }
            )
    return (
        pd.DataFrame(rows)
        if rows
        else pd.DataFrame(
            columns=[
                "item_id",
                "from_box_id",
                "to_box_id",
                "quantity_change",
                "reason",
                "actor_id",
                "at",
            ]
        )
    )


def snapshot_onhand(inv_df: pd.DataFrame) -> pd.DataFrame:
    return inv_df.groupby("item_id", as_index=False).agg(quantity=("quantity", "sum"))


def run_batch():
    repo = Repo()
    inv = repo.load_inventories()
    onhand = snapshot_onhand(inv)

    ev = load_event_payloads()
    daily = (
        build_daily_consumption(ev)
        if not ev.empty
        else pd.DataFrame(columns=["item_id", "date", "consumption", "avg_14"])
    )

    policy = compute_policy(onhand, daily)
    now = datetime.now(UTC).isoformat()
    out = policy.assign(
        id=[f"fp-{i:06d}" for i in range(len(policy))],
        computed_at=now,
        horizon_days=21,
        avg_daily_delta=policy["avg_daily"].fillna(0.0),
        suggested_order_date=(datetime.now(UTC) + timedelta(days=1)).isoformat(),
        confidence=0.6,
        features="{}",
    )[
        [
            "id",
            "item_id",
            "computed_at",
            "horizon_days",
            "avg_daily_delta",
            "days_to_stockout",
            "suggested_reorder_qty",
            "suggested_order_date",
            "confidence",
            "features",
        ]
    ]

    repo.write_forecasts(out)


if __name__ == "__main__":
    run_batch()
