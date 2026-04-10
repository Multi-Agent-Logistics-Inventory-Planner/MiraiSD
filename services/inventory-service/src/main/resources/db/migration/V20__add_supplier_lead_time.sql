-- V20: Supplier-based lead time calculation
-- Adds suppliers table, links shipments to suppliers, adds preferred supplier to products,
-- and creates materialized view for lead time statistics

-- 1. Canonicalization function for supplier names
-- Normalizes: NFKC -> lowercase -> trim -> collapse whitespace
CREATE OR REPLACE FUNCTION canonicalize_supplier_name(input_name TEXT)
RETURNS TEXT AS $$
BEGIN
    RETURN LOWER(REGEXP_REPLACE(TRIM(NORMALIZE(input_name, NFKC)), '\s+', ' ', 'g'));
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- 2. Suppliers table
CREATE TABLE IF NOT EXISTS suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name VARCHAR(255) NOT NULL,
    canonical_name VARCHAR(255) NOT NULL,
    contact_email VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_suppliers_canonical_name ON suppliers (canonical_name);
CREATE INDEX IF NOT EXISTS idx_suppliers_display_name ON suppliers (display_name);
CREATE INDEX IF NOT EXISTS idx_suppliers_is_active ON suppliers (is_active);

-- 3. Trigger to auto-compute canonical_name
CREATE OR REPLACE FUNCTION suppliers_canonicalize_trigger()
RETURNS TRIGGER AS $$
BEGIN
    NEW.canonical_name := canonicalize_supplier_name(NEW.display_name);
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_suppliers_canonicalize ON suppliers;
CREATE TRIGGER trg_suppliers_canonicalize
    BEFORE INSERT OR UPDATE OF display_name ON suppliers
    FOR EACH ROW EXECUTE FUNCTION suppliers_canonicalize_trigger();

-- 4. Add supplier_id FK to shipments
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS supplier_id UUID REFERENCES suppliers(id);
CREATE INDEX IF NOT EXISTS idx_shipments_supplier_id ON shipments (supplier_id);

-- 5. Add preferred supplier fields to products
ALTER TABLE products ADD COLUMN IF NOT EXISTS preferred_supplier_id UUID REFERENCES suppliers(id);
ALTER TABLE products ADD COLUMN IF NOT EXISTS preferred_supplier_auto BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS idx_products_preferred_supplier_id ON products (preferred_supplier_id);

-- 6. Backfill: Create suppliers from existing supplier_name values in shipments
INSERT INTO suppliers (display_name, canonical_name)
SELECT DISTINCT
    supplier_name,
    canonicalize_supplier_name(supplier_name)
FROM shipments
WHERE supplier_name IS NOT NULL
  AND supplier_name != ''
  AND TRIM(supplier_name) != ''
ON CONFLICT (canonical_name) DO NOTHING;

-- 7. Backfill: Link existing shipments to suppliers
UPDATE shipments s
SET supplier_id = sup.id
FROM suppliers sup
WHERE canonicalize_supplier_name(s.supplier_name) = sup.canonical_name
  AND s.supplier_id IS NULL
  AND s.supplier_name IS NOT NULL;

-- 8. Backfill: Set preferred_supplier_id based on most recent shipment per product
-- Find the most recent delivered shipment for each product and use that supplier
WITH latest_shipments AS (
    SELECT DISTINCT ON (si.item_id)
        si.item_id,
        s.supplier_id
    FROM shipment_items si
    JOIN shipments s ON s.id = si.shipment_id
    WHERE s.status = 'DELIVERED'
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

-- 9. Create materialized view for lead time statistics
-- Hierarchy levels:
-- 1: Item + Supplier specific
-- 2: Supplier only (across all items)
-- 3: Item only (across all suppliers) - for multi-supplier products
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_lead_time_stats AS
WITH delivered AS (
    SELECT
        si.item_id,
        s.supplier_id,
        (s.actual_delivery_date - s.order_date) AS lead_time_days
    FROM shipments s
    JOIN shipment_items si ON si.shipment_id = s.id
    WHERE s.status = 'DELIVERED'
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

-- Unique index for concurrent refresh
CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_lead_time_stats_pk ON mv_lead_time_stats (
    COALESCE(item_id, '00000000-0000-0000-0000-000000000000'::UUID),
    COALESCE(supplier_id, '00000000-0000-0000-0000-000000000000'::UUID),
    hierarchy_level
);

-- Index for efficient lookups by item
CREATE INDEX IF NOT EXISTS idx_mv_lead_time_stats_item ON mv_lead_time_stats (item_id) WHERE item_id IS NOT NULL;

-- Index for efficient lookups by supplier
CREATE INDEX IF NOT EXISTS idx_mv_lead_time_stats_supplier ON mv_lead_time_stats (supplier_id) WHERE supplier_id IS NOT NULL;

-- 10. Try to schedule pg_cron hourly refresh (if extension available)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        -- Remove existing job if any
        PERFORM cron.unschedule('refresh_lead_time_stats');
        -- Schedule hourly refresh at minute 0
        PERFORM cron.schedule(
            'refresh_lead_time_stats',
            '0 * * * *',
            'REFRESH MATERIALIZED VIEW CONCURRENTLY mv_lead_time_stats'
        );
        RAISE NOTICE 'Scheduled pg_cron job for mv_lead_time_stats refresh';
    ELSE
        RAISE NOTICE 'pg_cron not available - manual refresh required: REFRESH MATERIALIZED VIEW CONCURRENTLY mv_lead_time_stats';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Could not schedule pg_cron job: %', SQLERRM;
END $$;

-- 11. Initial refresh of materialized view
REFRESH MATERIALIZED VIEW mv_lead_time_stats;
