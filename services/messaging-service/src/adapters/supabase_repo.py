"""Supabase repository for querying product data."""

from __future__ import annotations

import logging
import uuid
from dataclasses import dataclass
from typing import TYPE_CHECKING
from urllib.parse import ParseResult, parse_qsl, quote, urlencode, urlparse, urlunparse

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

from .. import config

if TYPE_CHECKING:
    from sqlalchemy.engine import Connection

logger = logging.getLogger(__name__)


def _strip_jdbc_prefix(url: str) -> str:
    return url[len("jdbc:") :] if url.startswith("jdbc:") else url


def _ensure_sqla_url_with_creds(url: str) -> str:
    raw = _strip_jdbc_prefix(url)
    parsed = urlparse(raw)

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

    # Keep credentials
    if parsed.username or parsed.password:
        netloc = parsed.netloc
    else:
        user = config.SUPABASE_DB_USERNAME or "postgres"
        pw = config.SUPABASE_DB_PASSWORD or ""
        if not pw:
            raise ValueError(
                "SUPABASE_DB_PASSWORD not configured (required when SUPABASE_DB_URL has no embedded credentials)"
            )
        # Quote Pass
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


@dataclass
class Product:
    """Product data model."""

    id: str
    name: str
    sku: str | None
    quantity: int
    reorder_point: int
    current_quantity: int  # Current inventory quantity from inventories table


class SupabaseRepo:
    """Repository for querying product and inventory data from Supabase."""

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

    def get_product_with_inventory(self, product_id: str) -> Product | None:
        """Get product with current inventory quantity.

        Args:
            product_id: UUID of the product

        Returns:
            Product with current inventory quantity, or None if not found
        """
        query = """
            SELECT
                p.id,
                p.name,
                p.sku,
                COALESCE(p.reorder_point, 0) AS reorder_point,
                COALESCE(
                    (SELECT COALESCE(SUM(quantity), 0) FROM rack_inventory WHERE item_id = p.id) +
                    (SELECT COALESCE(SUM(quantity), 0) FROM box_bin_inventory WHERE item_id = p.id) +
                    (SELECT COALESCE(SUM(quantity), 0) FROM cabinet_inventory WHERE item_id = p.id) +
                    (SELECT COALESCE(SUM(quantity), 0) FROM keychain_machine_inventory WHERE item_id = p.id) +
                    (SELECT COALESCE(SUM(quantity), 0) FROM single_claw_machine_inventory WHERE item_id = p.id) +
                    (SELECT COALESCE(SUM(quantity), 0) FROM double_claw_machine_inventory WHERE item_id = p.id) +
                    (SELECT COALESCE(SUM(quantity), 0) FROM four_corner_machine_inventory WHERE item_id = p.id) +
                    (SELECT COALESCE(SUM(quantity), 0) FROM pusher_machine_inventory WHERE item_id = p.id) +
                    (SELECT COALESCE(SUM(quantity), 0) FROM not_assigned_inventory WHERE item_id = p.id)
                , 0) AS current_quantity
            FROM products p
            WHERE p.id = :product_id
        """

        try:
            with self._engine.connect() as conn:
                result = conn.execute(text(query), {"product_id": uuid.UUID(product_id)})
                row = result.fetchone()

                if row is None:
                    logger.warning("Product not found: %s", product_id)
                    return None

                return Product(
                    id=str(row.id),
                    name=row.name,
                    sku=row.sku,
                    quantity=int(row.current_quantity) if row.current_quantity else 0,
                    reorder_point=int(row.reorder_point) if row.reorder_point else 0,
                    current_quantity=int(row.current_quantity) if row.current_quantity else 0,
                )
        except Exception as e:
            logger.error("Failed to get product with inventory: %s", e)
            return None

    def create_notification(
        self,
        notification_type: str,
        severity: str,
        message: str,
        item_id: str,
        recipient_id: str | None = None,
        inventory_id: str | None = None,
        metadata: dict | None = None,
        source_event_id: str | None = None,
        dedupe_key: str | None = None,
    ) -> str | None:
        """Create a notification record in the database.

        Args:
            notification_type: Notification type (LOW_STOCK, OUT_OF_STOCK, etc.)
            severity: Severity level (INFO, WARNING, CRITICAL)
            message: Notification message
            item_id: Product/item UUID
            recipient_id: Optional recipient user UUID
            inventory_id: Optional inventory record UUID
            metadata: Optional JSON metadata
            source_event_id: Optional source Kafka event ID for idempotency
            dedupe_key: Optional deduplication key (e.g., "LOW_LOC:{item_id}:{location}")

        Returns:
            Created notification ID, or None if creation failed or duplicate
        """
        import json
        from datetime import datetime, timezone

        # Generate UUID client-side (matching Hibernate's GenerationType.UUID behavior)
        notification_id = uuid.uuid4()

        # Use INSERT with ON CONFLICT for idempotency when source_event_id is provided
        if source_event_id and dedupe_key:
            query = text("""
                INSERT INTO notifications (
                    id,
                    type,
                    severity,
                    message,
                    item_id,
                    recipient_id,
                    inventory_id,
                    via,
                    metadata,
                    created_at,
                    source_event_id,
                    dedupe_key
                ) VALUES (
                    :id,
                    :type,
                    :severity,
                    :message,
                    :item_id,
                    :recipient_id,
                    :inventory_id,
                    CAST(:via AS text[]),
                    CAST(:metadata AS jsonb),
                    :created_at,
                    :source_event_id,
                    :dedupe_key
                )
                ON CONFLICT (source_event_id, dedupe_key)
                WHERE source_event_id IS NOT NULL
                DO NOTHING
                RETURNING id
            """)
        else:
            query = text("""
                INSERT INTO notifications (
                    id,
                    type,
                    severity,
                    message,
                    item_id,
                    recipient_id,
                    inventory_id,
                    via,
                    metadata,
                    created_at,
                    source_event_id,
                    dedupe_key
                ) VALUES (
                    :id,
                    :type,
                    :severity,
                    :message,
                    :item_id,
                    :recipient_id,
                    :inventory_id,
                    CAST(:via AS text[]),
                    CAST(:metadata AS jsonb),
                    :created_at,
                    :source_event_id,
                    :dedupe_key
                )
                RETURNING id
            """)

        try:
            with self._engine.connect() as conn:
                # Prepare parameters
                via_array_str = "{" + ",".join(f'"{v}"' for v in ["slack", "app"]) + "}"
                metadata_json_str = json.dumps(metadata if metadata else {})

                params = {
                    "id": notification_id,
                    "type": notification_type,
                    "severity": severity,
                    "message": message,
                    "item_id": uuid.UUID(item_id),
                    "recipient_id": uuid.UUID(recipient_id) if recipient_id else None,
                    "inventory_id": uuid.UUID(inventory_id) if inventory_id else None,
                    "via": via_array_str,
                    "metadata": metadata_json_str,
                    "created_at": datetime.now(timezone.utc),
                    "source_event_id": uuid.UUID(source_event_id) if source_event_id else None,
                    "dedupe_key": dedupe_key,
                }

                result = conn.execute(query, params)
                row = result.fetchone()
                conn.commit()

                if row is None:
                    # ON CONFLICT DO NOTHING - duplicate detected
                    logger.debug(
                        "Duplicate notification skipped: event=%s, dedupe_key=%s",
                        source_event_id,
                        dedupe_key,
                    )
                    return None

                logger.info(
                    "Created notification: id=%s, type=%s, item=%s, dedupe_key=%s",
                    notification_id,
                    notification_type,
                    item_id,
                    dedupe_key,
                )
                return str(notification_id)
        except Exception as e:
            logger.error("Failed to create notification: %s", e)
            return None

    def claim_undelivered_notifications(self, limit: int = 10) -> list[dict]:
        """Atomically claim notifications pending Slack delivery.

        Uses FOR UPDATE SKIP LOCKED to prevent double-sends when
        multiple worker instances are running.

        Args:
            limit: Maximum number of notifications to claim

        Returns:
            List of notification dicts with all fields needed for delivery
        """
        query = text("""
            UPDATE notifications
            SET delivery_status = 'sending',
                delivery_claimed_at = NOW()
            WHERE id IN (
                SELECT id FROM notifications
                WHERE delivered_at IS NULL
                  AND (delivery_status IS NULL OR delivery_status = 'pending')
                  AND 'slack' = ANY(via)
                ORDER BY created_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            RETURNING id, type, severity, message, item_id, metadata
        """)

        try:
            with self._engine.connect() as conn:
                result = conn.execute(query, {"limit": limit})
                rows = result.fetchall()
                conn.commit()

                notifications = []
                for row in rows:
                    notifications.append({
                        "id": str(row.id),
                        "type": row.type,
                        "severity": row.severity,
                        "message": row.message,
                        "item_id": str(row.item_id) if row.item_id else None,
                        "metadata": row.metadata,
                    })

                if notifications:
                    logger.info("Claimed %d notifications for Slack delivery", len(notifications))

                return notifications
        except Exception as e:
            logger.error("Failed to claim notifications: %s", e)
            return []

    def mark_as_delivered(self, notification_id: str) -> bool:
        """Mark a notification as successfully delivered.

        Args:
            notification_id: UUID of the notification

        Returns:
            True if updated, False on error
        """
        query = text("""
            UPDATE notifications
            SET delivered_at = NOW(),
                delivery_status = 'delivered'
            WHERE id = :id
        """)

        try:
            with self._engine.connect() as conn:
                conn.execute(query, {"id": uuid.UUID(notification_id)})
                conn.commit()
                logger.debug("Marked notification %s as delivered", notification_id)
                return True
        except Exception as e:
            logger.error("Failed to mark notification as delivered: %s", e)
            return False

    def release_claim(self, notification_id: str) -> bool:
        """Release a claimed notification back to pending status.

        Called when delivery fails, allowing retry.

        Args:
            notification_id: UUID of the notification

        Returns:
            True if updated, False on error
        """
        query = text("""
            UPDATE notifications
            SET delivery_status = 'pending',
                delivery_attempts = COALESCE(delivery_attempts, 0) + 1
            WHERE id = :id
        """)

        try:
            with self._engine.connect() as conn:
                conn.execute(query, {"id": uuid.UUID(notification_id)})
                conn.commit()
                logger.debug("Released claim on notification %s", notification_id)
                return True
        except Exception as e:
            logger.error("Failed to release notification claim: %s", e)
            return False

