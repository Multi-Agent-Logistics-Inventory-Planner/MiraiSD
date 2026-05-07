-- V30: Fix mv_lead_time_stats status filter
-- V20 filtered shipments by status = 'DELIVERED', but ShipmentStatus is
-- {PENDING, RECEIVED, CANCELLED} (V24 confirmed the split into status vs
-- carrier_status). The materialized view never populated, and the V20
-- preferred_supplier_id backfill matched zero rows.
-- This migration recreates the view with the correct status and replays the
-- backfill for products that are still missing a preferred supplier.

-- 1. Drop and recreate the view with the correct status filter.
-- View definitions cannot be ALTERed when changing column lists; DROP + CREATE
-- is required. We detect the actual relkind because some environments have
-- mv_lead_time_stats as a plain view (relkind='v') instead of the materialized
-- view (relkind='m') that V20 declared. Indexes on the view drop automatically.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'mv_lead_time_stats' AND relkind = 'm') THEN
        EXECUTE 'DROP MATERIALIZED VIEW mv_lead_time_stats';
    ELSIF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'mv_lead_time_stats' AND relkind = 'v') THEN
        EXECUTE 'DROP VIEW mv_lead_time_stats';
    END IF;
END $$;

CREATE MATERIALIZED VIEW mv_lead_time_stats AS
WITH delivered AS (
    SELECT
        si.item_id,
        s.supplier_id,
        (s.actual_delivery_date - s.order_date) AS lead_time_days
    FROM shipments s
    JOIN shipment_items si ON si.shipment_id = s.id
    WHERE s.status = 'RECEIVED'
      AND s.actual_delivery_date IS NOT NULL
      AND s.order_date IS NOT NULL
      AND (s.actual_delivery_date - s.order_date) > 0
)
-- Level 1: Item + Supplier specific
SELECT
    item_id,
    supplier_id,
    COUNT(*) AS n,
    AVG(lead_time_days)::NUMERIC(10,2) AS avg_lt,
    COALESCE(STDDEV_SAMP(lead_time_days), 2.0)::NUMERIC(10,2) AS sigma_l,
    1 AS hierarchy_level
FROM delivered
WHERE supplier_id IS NOT NULL
GROUP BY item_id, supplier_id
HAVING COUNT(*) >= 3

UNION ALL

-- Level 2: Supplier only (all items from this supplier)
SELECT
    NULL::UUID AS item_id,
    supplier_id,
    COUNT(*) AS n,
    AVG(lead_time_days)::NUMERIC(10,2) AS avg_lt,
    COALESCE(STDDEV_SAMP(lead_time_days), 2.0)::NUMERIC(10,2) AS sigma_l,
    2 AS hierarchy_level
FROM delivered
WHERE supplier_id IS NOT NULL
GROUP BY supplier_id
HAVING COUNT(*) >= 3

UNION ALL

-- Level 3: Item only (across all suppliers, for multi-supplier products)
SELECT
    item_id,
    NULL::UUID AS supplier_id,
    COUNT(*) AS n,
    AVG(lead_time_days)::NUMERIC(10,2) AS avg_lt,
    COALESCE(STDDEV_SAMP(lead_time_days), 2.0)::NUMERIC(10,2) AS sigma_l,
    3 AS hierarchy_level
FROM delivered
GROUP BY item_id
HAVING COUNT(*) >= 3;

-- 2. Recreate indexes (CONCURRENTLY refresh requires the unique index)
CREATE UNIQUE INDEX idx_mv_lead_time_stats_pk ON mv_lead_time_stats (
    COALESCE(item_id, '00000000-0000-0000-0000-000000000000'::UUID),
    COALESCE(supplier_id, '00000000-0000-0000-0000-000000000000'::UUID),
    hierarchy_level
);

CREATE INDEX idx_mv_lead_time_stats_item ON mv_lead_time_stats (item_id) WHERE item_id IS NOT NULL;
CREATE INDEX idx_mv_lead_time_stats_supplier ON mv_lead_time_stats (supplier_id) WHERE supplier_id IS NOT NULL;

-- 3. Replay the V20 preferred_supplier_id backfill, but only for products
-- that still have no preferred supplier — don't clobber manual selections.
WITH latest_shipments AS (
    SELECT DISTINCT ON (si.item_id)
        si.item_id,
        s.supplier_id
    FROM shipment_items si
    JOIN shipments s ON s.id = si.shipment_id
    WHERE s.status = 'RECEIVED'
      AND s.supplier_id IS NOT NULL
      AND s.actual_delivery_date IS NOT NULL
    ORDER BY si.item_id, s.actual_delivery_date DESC
)
UPDATE products p
SET preferred_supplier_id = ls.supplier_id,
    preferred_supplier_auto = true
FROM latest_shipments ls
WHERE p.id = ls.item_id
  AND p.preferred_supplier_id IS NULL;

-- 4. Initial refresh of the recreated view
REFRESH MATERIALIZED VIEW mv_lead_time_stats;
