"""Supabase PostgreSQL repository for production use.

Notes on SUPABASE_DB_URL:
- The inventory-service uses a JDBC-style URL (e.g. `jdbc:postgresql://host:5432/db`)
  plus separate username/password env vars.
- SQLAlchemy expects a SQLAlchemy/psycopg2 URL (e.g. `postgresql+psycopg2://user:pass@host:5432/db`).

This adapter accepts either form and will stitch credentials in when needed so both
services can share the same docker-compose env vars.
"""

from __future__ import annotations

import json
import logging
import uuid
from datetime import datetime, timezone
from typing import TYPE_CHECKING

import pandas as pd
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine
from urllib.parse import ParseResult, parse_qsl, quote, urlencode, urlparse, urlunparse

from .. import config

if TYPE_CHECKING:
    from sqlalchemy.engine import Connection

logger = logging.getLogger(__name__)


def _strip_jdbc_prefix(url: str) -> str:
    return url[len("jdbc:") :] if url.startswith("jdbc:") else url


def _ensure_sqla_url_with_creds(url: str) -> str:
    """Ensure URL is a SQLAlchemy-compatible postgresql URL and includes credentials when available."""
    raw = _strip_jdbc_prefix(url)
    parsed = urlparse(raw)

    # If someone passed a bare host:port/db without scheme, urlparse won't help; fail loudly.
    if not parsed.scheme:
        raise ValueError(
            "SUPABASE_DB_URL must include a scheme (expected jdbc:postgresql://... or postgresql://...)"
        )

    scheme = parsed.scheme
    if scheme == "postgresql":
        scheme = "postgresql+psycopg2"
    elif scheme == "postgresql+psycopg2":
        pass
    else:
        raise ValueError(
            f"Unsupported SUPABASE_DB_URL scheme '{parsed.scheme}' (expected jdbc:postgresql://... or postgresql://...)"
        )

    # If creds already present, keep them.
    if parsed.username or parsed.password:
        netloc = parsed.netloc
    else:
        user = config.SUPABASE_DB_USERNAME or "postgres"
        pw = config.SUPABASE_DB_PASSWORD or ""
        if not pw:
            raise ValueError(
                "SUPABASE_DB_PASSWORD not configured (required when SUPABASE_DB_URL has no embedded credentials)"
            )
        # Quote password for URL safety
        user_enc = quote(user, safe="")
        pw_enc = quote(pw, safe="")
        host = parsed.hostname or ""
        port = f":{parsed.port}" if parsed.port else ""
        netloc = f"{user_enc}:{pw_enc}@{host}{port}"

    rebuilt = ParseResult(
        scheme=scheme,
        netloc=netloc,
        path=parsed.path,
        params=parsed.params,
        query=parsed.query,
        fragment=parsed.fragment,
    )
    return urlunparse(rebuilt)


def _sanitize_query_params_for_psycopg2(url: str) -> str:
    """Remove JDBC-only query params that break psycopg2/libpq DSNs."""
    parsed = urlparse(url)
    if not parsed.query:
        return url

    # JDBC driver options that are NOT valid for psycopg2/libpq
    drop_keys = {"prepareThreshold", "preferQueryMode"}
    kept = [(k, v) for (k, v) in parse_qsl(parsed.query, keep_blank_values=True) if k not in drop_keys]

    rebuilt = ParseResult(
        scheme=parsed.scheme,
        netloc=parsed.netloc,
        path=parsed.path,
        params=parsed.params,
        query=urlencode(kept, doseq=True),
        fragment=parsed.fragment,
    )
    return urlunparse(rebuilt)


def _build_connection_url() -> str:
    """Build SQLAlchemy PostgreSQL connection URL from config."""
    base_url = (config.SUPABASE_DB_URL or "").strip()
    if not base_url:
        raise ValueError("SUPABASE_DB_URL not configured")

    sqla_url = _ensure_sqla_url_with_creds(base_url)
    return _sanitize_query_params_for_psycopg2(sqla_url)


class SupabaseRepo:
    """Repository for Supabase PostgreSQL database operations."""

    def __init__(self, engine: Engine | None = None):
        if engine is not None:
            self._engine = engine
        else:
            self._engine = create_engine(
                _build_connection_url(),
                pool_pre_ping=True,
                pool_size=1,
                max_overflow=2,
            )

    def get_items(self, item_ids: list[str] | None = None) -> pd.DataFrame:
        """Load products (items) from database.

        Returns DataFrame with columns: item_id, name, category, lead_time_days, safety_stock_days
        """
        query = """
            SELECT
                id::text AS item_id,
                name,
                category,
                lead_time_days,
                COALESCE(reorder_point / NULLIF(target_stock_level / lead_time_days, 0), 7)::int AS safety_stock_days
            FROM products
            WHERE is_active = true
        """
        params: dict = {}

        if item_ids:
            query += " AND id = ANY(:item_ids)"
            params["item_ids"] = [uuid.UUID(iid) for iid in item_ids]

        with self._engine.connect() as conn:
            df = pd.read_sql(text(query), conn, params=params)

        if df.empty:
            return pd.DataFrame(
                columns=["item_id", "name", "category", "lead_time_days", "safety_stock_days"]
            )

        df["item_id"] = df["item_id"].astype(str)
        df["lead_time_days"] = df["lead_time_days"].fillna(7).astype(int)
        df["safety_stock_days"] = df["safety_stock_days"].fillna(7).astype(int)
        return df

    def get_current_inventory(self, item_ids: list[str] | None = None) -> pd.DataFrame:
        """Get current inventory quantities aggregated across all locations.

        Returns DataFrame with columns: item_id, as_of_ts, current_qty
        """
        # Union all inventory tables and sum quantities per item
        inventory_tables = [
            ("box_bin_inventory", "box_bin_id"),
            ("rack_inventory", "rack_id"),
            ("cabinet_inventory", "cabinet_id"),
            ("single_claw_machine_inventory", "single_claw_machine_id"),
            ("double_claw_machine_inventory", "double_claw_machine_id"),
            ("keychain_machine_inventory", "keychain_machine_id"),
        ]

        union_parts = []
        for table, _ in inventory_tables:
            union_parts.append(f"SELECT item_id, quantity FROM {table}")

        union_query = " UNION ALL ".join(union_parts)

        query = f"""
            SELECT
                item_id::text AS item_id,
                NOW() AS as_of_ts,
                COALESCE(SUM(quantity), 0)::int AS current_qty
            FROM ({union_query}) AS all_inventory
            GROUP BY item_id
        """

        params: dict = {}
        if item_ids:
            # Wrap in another select to filter
            query = f"""
                SELECT item_id, as_of_ts, current_qty
                FROM ({query}) AS inv
                WHERE item_id = ANY(:item_ids)
            """
            # Convert to UUID objects to match PostgreSQL UUID column type
            params["item_ids"] = [uuid.UUID(iid) for iid in item_ids]

        with self._engine.connect() as conn:
            df = pd.read_sql(text(query), conn, params=params)

        if df.empty:
            return pd.DataFrame(columns=["item_id", "as_of_ts", "current_qty"])

        df["item_id"] = df["item_id"].astype(str)
        df["as_of_ts"] = pd.to_datetime(df["as_of_ts"], utc=True)
        df["current_qty"] = df["current_qty"].astype(int)
        return df

    def get_stock_movements(
        self,
        start: str | datetime,
        end: str | datetime,
        item_ids: list[str] | None = None,
    ) -> pd.DataFrame:
        """Load stock movements within time window.

        Returns DataFrame with columns: event_id, item_id, quantity_change, reason, at
        """
        start_ts = pd.to_datetime(start, utc=True)
        end_ts = pd.to_datetime(end, utc=True)

        query = """
            SELECT
                id::text AS event_id,
                item_id::text AS item_id,
                quantity_change,
                LOWER(reason) AS reason,
                at
            FROM stock_movements
            WHERE at >= :start_ts AND at <= :end_ts
        """
        params: dict = {"start_ts": start_ts, "end_ts": end_ts}

        if item_ids:
            query += " AND item_id = ANY(:item_ids)"
            params["item_ids"] = [uuid.UUID(iid) for iid in item_ids]

        query += " ORDER BY at ASC"

        with self._engine.connect() as conn:
            df = pd.read_sql(text(query), conn, params=params)

        if df.empty:
            return pd.DataFrame(columns=["event_id", "item_id", "quantity_change", "reason", "at"])

        df["event_id"] = df["event_id"].astype(str)
        df["item_id"] = df["item_id"].astype(str)
        df["quantity_change"] = df["quantity_change"].astype(int)
        df["reason"] = df["reason"].astype(str)
        df["at"] = pd.to_datetime(df["at"], utc=True)
        return df

    def upsert_forecasts(self, forecasts_df: pd.DataFrame) -> int:
        """Upsert forecast predictions to database.

        Expects DataFrame with columns:
        item_id, computed_at, horizon_days, avg_daily_delta, days_to_stockout,
        suggested_reorder_qty, suggested_order_date, confidence, features

        Returns number of rows upserted.
        """
        if forecasts_df.empty:
            return 0

        required = {
            "item_id",
            "computed_at",
            "horizon_days",
            "avg_daily_delta",
            "days_to_stockout",
            "suggested_reorder_qty",
            "suggested_order_date",
            "confidence",
            "features",
        }
        missing = required - set(forecasts_df.columns)
        if missing:
            raise ValueError(f"Missing required columns: {sorted(missing)}")

        # Prepare rows for insertion
        rows = []
        for _, row in forecasts_df.iterrows():
            # Handle features - convert to JSON string if dict
            features = row["features"]
            if isinstance(features, dict):
                features_json = json.dumps(features)
            elif isinstance(features, str):
                features_json = features
            else:
                features_json = "{}"

            rows.append(
                {
                    "id": str(uuid.uuid4()),
                    "item_id": str(row["item_id"]),
                    "computed_at": pd.to_datetime(row["computed_at"], utc=True),
                    "horizon_days": int(row["horizon_days"]),
                    "avg_daily_delta": float(row["avg_daily_delta"]) if pd.notna(row["avg_daily_delta"]) else None,
                    "days_to_stockout": float(row["days_to_stockout"]) if pd.notna(row["days_to_stockout"]) else None,
                    "suggested_reorder_qty": int(row["suggested_reorder_qty"]) if pd.notna(row["suggested_reorder_qty"]) else None,
                    "suggested_order_date": row["suggested_order_date"] if pd.notna(row["suggested_order_date"]) else None,
                    "confidence": float(row["confidence"]) if pd.notna(row["confidence"]) else None,
                    "features": features_json,
                }
            )

        # Insert with ON CONFLICT DO UPDATE (upsert by item_id + computed_at)
        upsert_query = """
            INSERT INTO forecast_predictions (
                id, item_id, computed_at, horizon_days, avg_daily_delta,
                days_to_stockout, suggested_reorder_qty, suggested_order_date,
                confidence, features
            ) VALUES (
                :id, CAST(:item_id AS uuid), :computed_at, :horizon_days, :avg_daily_delta,
                :days_to_stockout, :suggested_reorder_qty, :suggested_order_date,
                :confidence, CAST(:features AS jsonb)
            )
            ON CONFLICT (item_id, computed_at) DO UPDATE SET
                horizon_days = EXCLUDED.horizon_days,
                avg_daily_delta = EXCLUDED.avg_daily_delta,
                days_to_stockout = EXCLUDED.days_to_stockout,
                suggested_reorder_qty = EXCLUDED.suggested_reorder_qty,
                suggested_order_date = EXCLUDED.suggested_order_date,
                confidence = EXCLUDED.confidence,
                features = EXCLUDED.features
        """

        with self._engine.begin() as conn:
            for row in rows:
                conn.execute(text(upsert_query), row)

        logger.info("Upserted %d forecast predictions", len(rows))
        return len(rows)
