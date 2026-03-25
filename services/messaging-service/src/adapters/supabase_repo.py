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
                pool_size=10,
                max_overflow=20,
                pool_timeout=30,  # Fail after 30s instead of hanging indefinitely
            )

    def get_product_with_inventory(self, product_id: str) -> Product | None:
        """Get product with current inventory quantity.

        Args:
            product_id: UUID of the product

        Returns:
            Product with current inventory quantity, or None if not found
        """
        # Use the database function for efficient inventory calculation
        query = """
            SELECT
                p.id,
                p.name,
                p.sku,
                COALESCE(p.reorder_point, 0) AS reorder_point,
                calculate_total_inventory(p.id) AS current_quantity
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

        # Use INSERT with ON CONFLICT for idempotency based on dedupe_key + date
        # This prevents duplicate notifications for the same alert on the same day,
        # while allowing re-alerting on different days
        if dedupe_key:
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
                ON CONFLICT (dedupe_key)
                WHERE dedupe_key IS NOT NULL
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

    # -------------------------------------------------------------------------
    # Review tracking methods (using users table)
    # -------------------------------------------------------------------------

    def get_review_employees(self) -> list[dict]:
        """Get all users tracked for reviews with their name variants.

        Returns:
            List of dicts with id, canonical_name, name_variants, is_active.
        """
        query = text("""
            SELECT id, full_name, canonical_name, name_variants, is_review_tracked
            FROM users
            WHERE is_review_tracked = true
            ORDER BY full_name
        """)

        try:
            with self._engine.connect() as conn:
                result = conn.execute(query)
                rows = result.fetchall()

                employees = []
                for row in rows:
                    employees.append({
                        "id": str(row.id),
                        "canonical_name": row.canonical_name or row.full_name.split()[0],
                        "name_variants": list(row.name_variants) if row.name_variants else [],
                        "is_active": row.is_review_tracked,
                    })
                return employees
        except Exception as e:
            logger.error("Failed to get review employees: %s", e)
            return []

    def get_employee_id_by_name(self, canonical_name: str) -> str | None:
        """Get user ID by canonical name.

        Args:
            canonical_name: User's canonical name for reviews.

        Returns:
            User UUID or None if not found.
        """
        query = text("""
            SELECT id FROM users
            WHERE canonical_name = :name AND is_review_tracked = true
        """)

        try:
            with self._engine.connect() as conn:
                result = conn.execute(query, {"name": canonical_name})
                row = result.fetchone()
                return str(row.id) if row else None
        except Exception as e:
            logger.error("Failed to get employee by name: %s", e)
            return None

    def create_review(
        self,
        employee_name: str,
        external_id: str,
        review_date,  # date
        review_text: str,
        rating: int,
        reviewer_name: str,
    ) -> str | None:
        """Create an individual review record.

        Uses ON CONFLICT to handle duplicates (same external_id).

        Args:
            employee_name: User's full name.
            external_id: External review ID (from Apify).
            review_date: Date of the review.
            review_text: Review text content.
            rating: Star rating (1-5).
            reviewer_name: Name of the reviewer.

        Returns:
            Review UUID if created, None if duplicate.
        """
        # First get the user ID
        user_id = self.get_employee_id_by_name(employee_name)
        if not user_id:
            logger.warning("User not found: %s", employee_name)
            return None

        review_id = uuid.uuid4()
        query = text("""
            INSERT INTO reviews (
                id, external_id, user_id, review_date,
                review_text, rating, reviewer_name
            ) VALUES (
                :id, :external_id, :user_id, :review_date,
                :review_text, :rating, :reviewer_name
            )
            ON CONFLICT (external_id, review_date) DO NOTHING
            RETURNING id
        """)

        try:
            with self._engine.connect() as conn:
                result = conn.execute(query, {
                    "id": review_id,
                    "external_id": external_id,
                    "user_id": uuid.UUID(user_id),
                    "review_date": review_date,
                    "review_text": review_text,
                    "rating": rating,
                    "reviewer_name": reviewer_name,
                })
                row = result.fetchone()
                conn.commit()

                if row is None:
                    logger.debug("Duplicate review skipped: %s on %s", external_id, review_date)
                    return None

                return str(review_id)
        except Exception as e:
            logger.error("Failed to create review: %s", e)
            return None

    def increment_daily_count(self, employee_name: str, review_date) -> bool:
        """Increment daily review count for a user.

        Uses UPSERT to create or increment the count.

        Args:
            employee_name: User's full name.
            review_date: Date of the review.

        Returns:
            True if successful, False on error.
        """
        user_id = self.get_employee_id_by_name(employee_name)
        if not user_id:
            logger.warning("User not found for count update: %s", employee_name)
            return False

        query = text("""
            INSERT INTO review_daily_counts (id, user_id, date, review_count)
            VALUES (gen_random_uuid(), :user_id, :date, 1)
            ON CONFLICT (user_id, date)
            DO UPDATE SET review_count = review_daily_counts.review_count + 1
        """)

        try:
            with self._engine.connect() as conn:
                conn.execute(query, {
                    "user_id": uuid.UUID(user_id),
                    "date": review_date,
                })
                conn.commit()
                return True
        except Exception as e:
            logger.error("Failed to increment daily count: %s", e)
            return False

    def get_daily_counts(self, target_date) -> list[tuple[str, int]]:
        """Get review counts for a specific date.

        Args:
            target_date: Date to query.

        Returns:
            List of (user_name, count) tuples, ordered by count descending.
        """
        query = text("""
            SELECT COALESCE(u.canonical_name, u.full_name) AS name, c.review_count
            FROM review_daily_counts c
            JOIN users u ON c.user_id = u.id
            WHERE c.date = :date
            ORDER BY c.review_count DESC
        """)

        try:
            with self._engine.connect() as conn:
                result = conn.execute(query, {"date": target_date})
                return [(row.name, row.review_count) for row in result.fetchall()]
        except Exception as e:
            logger.error("Failed to get daily counts: %s", e)
            return []

    def get_monthly_review_totals(self, year: int, month: int) -> list[tuple[str, int]]:
        """Get total review counts for a month.

        Args:
            year: Year (e.g., 2026).
            month: Month (1-12).

        Returns:
            List of (user_name, total_count) tuples, ordered by count descending.
        """
        from datetime import date as date_type
        import calendar

        first_day = date_type(year, month, 1)
        last_day = date_type(year, month, calendar.monthrange(year, month)[1])

        query = text("""
            SELECT COALESCE(u.canonical_name, u.full_name) AS name, SUM(c.review_count) AS total_count
            FROM review_daily_counts c
            JOIN users u ON c.user_id = u.id
            WHERE c.date >= :first_day AND c.date <= :last_day
            GROUP BY COALESCE(u.canonical_name, u.full_name)
            ORDER BY total_count DESC
        """)

        try:
            with self._engine.connect() as conn:
                result = conn.execute(query, {
                    "first_day": first_day,
                    "last_day": last_day,
                })
                return [(row.name, int(row.total_count)) for row in result.fetchall()]
        except Exception as e:
            logger.error("Failed to get monthly review totals: %s", e)
            return []

    # -------------------------------------------------------------------------
    # Display staleness methods
    # -------------------------------------------------------------------------

    def get_stale_displays(self, threshold_days: int = 45) -> list[dict]:
        """Get active displays that have been displayed longer than threshold.

        Args:
            threshold_days: Number of days after which a display is considered stale.

        Returns:
            List of dicts with display and product info.
        """
        query = text("""
            SELECT
                md.id,
                md.machine_id,
                md.product_id,
                md.location_type,
                p.name as product_name,
                p.sku as product_sku,
                md.started_at,
                EXTRACT(DAY FROM NOW() - md.started_at)::INTEGER as days_active
            FROM machine_displays md
            JOIN products p ON md.product_id = p.id
            WHERE md.ended_at IS NULL
              AND md.started_at < NOW() - MAKE_INTERVAL(days => :threshold_days)
            ORDER BY md.started_at ASC
        """)

        try:
            with self._engine.connect() as conn:
                result = conn.execute(query, {"threshold_days": threshold_days})
                displays = []
                for row in result.fetchall():
                    displays.append({
                        "id": str(row.id),
                        "machine_id": str(row.machine_id),
                        "product_id": str(row.product_id),
                        "location_type": row.location_type,
                        "product_name": row.product_name,
                        "product_sku": row.product_sku,
                        "started_at": row.started_at.isoformat() if row.started_at else None,
                        "days_active": row.days_active,
                    })
                logger.info("Found %d stale displays (>%d days)", len(displays), threshold_days)
                return displays
        except Exception as e:
            logger.error("Failed to get stale displays: %s", e)
            return []

    def create_stale_display_notification(
        self,
        product_id: str,
        product_name: str,
        machine_id: str,
        location_type: str,
        days_active: int,
    ) -> str | None:
        """Create a stale display notification with deduplication.

        Uses dedupe_key to ensure only one notification per product/machine combo
        per day.

        Args:
            product_id: UUID of the product.
            product_name: Name of the product.
            machine_id: UUID of the machine.
            location_type: Type of location (e.g., SINGLE_CLAW, DOUBLE_CLAW).
            days_active: Number of days the product has been displayed.

        Returns:
            Notification UUID if created, None if duplicate or error.
        """
        from datetime import date

        # Dedupe key includes date to allow daily re-notification
        dedupe_key = f"DISPLAY_STALE:{product_id}:{machine_id}:{date.today().isoformat()}"

        metadata = {
            "machine_id": machine_id,
            "location_type": location_type,
            "product_name": product_name,
            "days_active": days_active,
            "category": "display",
        }

        message = f"{product_name} has been on display for {days_active} days"

        return self.create_notification(
            notification_type="DISPLAY_STALE",
            severity="WARNING",
            message=message,
            item_id=product_id,
            metadata=metadata,
            dedupe_key=dedupe_key,
        )

