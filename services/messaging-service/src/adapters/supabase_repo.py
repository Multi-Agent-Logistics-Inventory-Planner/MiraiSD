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
    """Ensure URL is a SQLAlchemy-compatible postgresql URL and includes credentials when available."""
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
                pool_size=5,
                max_overflow=10,
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
                COALESCE(SUM(
                    CASE 
                        WHEN ri.quantity IS NOT NULL THEN ri.quantity
                        WHEN bi.quantity IS NOT NULL THEN bi.quantity
                        WHEN ci.quantity IS NOT NULL THEN ci.quantity
                        WHEN kmi.quantity IS NOT NULL THEN kmi.quantity
                        WHEN scmi.quantity IS NOT NULL THEN scmi.quantity
                        ELSE 0
                    END
                ), 0) AS current_quantity
            FROM products p
            LEFT JOIN rack_inventories ri ON ri.item_id = p.id
            LEFT JOIN box_bin_inventories bi ON bi.item_id = p.id
            LEFT JOIN cabinet_inventories ci ON ci.item_id = p.id
            LEFT JOIN keychain_machine_inventories kmi ON kmi.item_id = p.id
            LEFT JOIN single_claw_machine_inventories scmi ON scmi.item_id = p.id
            WHERE p.id = :product_id
            GROUP BY p.id, p.name, p.sku, p.reorder_point
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

