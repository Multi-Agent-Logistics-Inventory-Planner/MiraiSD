"""Direct PostgreSQL query helpers for E2E test assertions."""

import os
from datetime import datetime
from typing import Any

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

# E2E PostgreSQL connection (port 5434 mapped from docker-compose.e2e.yml)
# Override via E2E_DB_URL env var if running against a different host/port.
E2E_DB_URL = os.environ.get(
    "E2E_DB_URL",
    "postgresql+psycopg2://postgres:postgres@localhost:5434/mirai_inventory",
)


def create_e2e_engine() -> Engine:
    """Create a SQLAlchemy engine for E2E test assertions."""
    return create_engine(E2E_DB_URL, pool_pre_ping=True)


def find_test_inventory(engine: Engine) -> dict[str, Any] | None:
    """Find a box_bin_inventory record with quantity > 5 for safe test adjustments.

    Returns dict with keys: inventory_id, quantity, product_id, product_name, sku
    """
    with engine.connect() as conn:
        row = conn.execute(
            text("""
                SELECT bbi.id as inventory_id, bbi.quantity,
                       p.id as product_id, p.name as product_name, p.sku
                FROM box_bin_inventories bbi
                JOIN products p ON bbi.item_id = p.id
                WHERE bbi.quantity > 5
                ORDER BY bbi.quantity DESC
                LIMIT 1
            """)
        ).fetchone()

    if row is None:
        return None
    return {
        "inventory_id": str(row.inventory_id),
        "quantity": row.quantity,
        "product_id": str(row.product_id),
        "product_name": row.product_name,
        "sku": row.sku,
    }


def count_audit_logs_after(engine: Engine, since: datetime) -> int:
    """Count audit_logs created after the given timestamp."""
    with engine.connect() as conn:
        result = conn.execute(
            text("SELECT count(*) FROM audit_logs WHERE created_at > :since"),
            {"since": since},
        ).scalar()
    return result or 0


def count_stock_movements_for_item(engine: Engine, product_id: str, since: datetime) -> int:
    """Count stock_movements for a product created after the given timestamp."""
    with engine.connect() as conn:
        result = conn.execute(
            text("""
                SELECT count(*) FROM stock_movements
                WHERE item_id = :product_id AND at > :since
            """),
            {"product_id": product_id, "since": since},
        ).scalar()
    return result or 0


def get_unpublished_outbox_count(engine: Engine) -> int:
    """Count event_outbox records that haven't been published yet."""
    with engine.connect() as conn:
        result = conn.execute(
            text("SELECT count(*) FROM event_outbox WHERE published_at IS NULL")
        ).scalar()
    return result or 0


def get_outbox_published_after(engine: Engine, since: datetime) -> list[dict]:
    """Get event_outbox records published after the given timestamp."""
    with engine.connect() as conn:
        rows = conn.execute(
            text("""
                SELECT id, topic, event_type, entity_type, payload, published_at
                FROM event_outbox
                WHERE published_at > :since
                ORDER BY published_at ASC
            """),
            {"since": since},
        ).fetchall()
    return [
        {
            "id": str(row.id),
            "topic": row.topic,
            "event_type": row.event_type,
            "entity_type": row.entity_type,
            "payload": row.payload,
            "published_at": row.published_at,
        }
        for row in rows
    ]


def get_forecast_for_item(engine: Engine, item_id: str, since: datetime) -> dict | None:
    """Get the latest forecast_prediction for an item computed after the given timestamp."""
    with engine.connect() as conn:
        row = conn.execute(
            text("""
                SELECT id, item_id, computed_at, horizon_days, avg_daily_delta,
                       days_to_stockout, suggested_reorder_qty, suggested_order_date,
                       confidence, features
                FROM forecast_predictions
                WHERE item_id = :item_id AND computed_at > :since
                ORDER BY computed_at DESC
                LIMIT 1
            """),
            {"item_id": item_id, "since": since},
        ).fetchone()

    if row is None:
        return None
    return {
        "id": str(row.id),
        "item_id": str(row.item_id),
        "computed_at": row.computed_at,
        "horizon_days": row.horizon_days,
        "avg_daily_delta": row.avg_daily_delta,
        "days_to_stockout": row.days_to_stockout,
        "suggested_reorder_qty": row.suggested_reorder_qty,
        "suggested_order_date": row.suggested_order_date,
        "confidence": float(row.confidence) if row.confidence else None,
        "features": row.features,
    }


def count_forecasts_after(engine: Engine, since: datetime) -> int:
    """Count forecast_predictions computed after the given timestamp."""
    with engine.connect() as conn:
        result = conn.execute(
            text("SELECT count(*) FROM forecast_predictions WHERE computed_at > :since"),
            {"since": since},
        ).scalar()
    return result or 0
