-- Idempotent: safe to re-run before the constrain release if old writers inserted NULL-site rows.
-- MAIN is the fallback only because the org was single-site until now; new writers must not default to it.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sites WHERE code = 'MAIN') THEN
        RAISE EXCEPTION 'notifications/reviews/audit_logs site_id backfill requires a MAIN site row to already exist';
    END IF;
END $$;

-- A log grouping movements of several sites cannot be given one owner without leaking the others.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM stock_movements
        WHERE audit_log_id IS NOT NULL AND site_id IS NOT NULL
        GROUP BY audit_log_id
        HAVING COUNT(DISTINCT site_id) > 1
    ) THEN
        RAISE EXCEPTION 'audit_logs backfill refused: some audit logs have movements that span more than one site; split them first';
    END IF;
END $$;

-- Audit rows inherit the site of the stock movements they group.
UPDATE audit_logs al
SET site_id = src.site_id
FROM (
    SELECT audit_log_id, MIN(site_id::text)::uuid AS site_id
    FROM stock_movements
    WHERE audit_log_id IS NOT NULL AND site_id IS NOT NULL
    GROUP BY audit_log_id
    HAVING COUNT(DISTINCT site_id) = 1
) src
WHERE src.audit_log_id = al.id
  AND al.site_id IS NULL;

UPDATE audit_logs al
SET site_id = s.site_id
FROM shipments s
WHERE s.id = al.shipment_id
  AND al.site_id IS NULL;

UPDATE audit_logs al
SET site_id = sl.site_id
FROM locations l
JOIN storage_locations sl ON sl.id = l.storage_location_id
WHERE l.id = COALESCE(al.primary_to_location_id, al.primary_from_location_id)
  AND al.site_id IS NULL;

UPDATE audit_logs
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;

-- Alerts are raised for a stocked location; fall back to MAIN when the inventory row is gone.
UPDATE notifications n
SET site_id = sl.site_id
FROM location_inventory li
JOIN locations l ON l.id = li.location_id
JOIN storage_locations sl ON sl.id = l.storage_location_id
WHERE li.id = n.inventory_id
  AND n.site_id IS NULL;

-- Stale-display alerts have no inventory row; the machine is a location named in the metadata.
UPDATE notifications n
SET site_id = sl.site_id
FROM locations l
JOIN storage_locations sl ON sl.id = l.storage_location_id
WHERE l.id::text = n.metadata->>'machine_id'
  AND n.site_id IS NULL;

-- Tracking and receipt alerts name their shipment in the metadata.
UPDATE notifications n
SET site_id = sh.site_id
FROM shipments sh
WHERE sh.id::text = n.metadata->>'shipment_id'
  AND n.site_id IS NULL;

UPDATE notifications
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;

-- Reviews come from one Google place attributed to REVIEW_SITE_CODE, and users belong to many sites.
UPDATE reviews
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;
