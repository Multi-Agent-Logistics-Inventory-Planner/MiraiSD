-- Items and allocations inherit the shipment's site so one shipment never spans sites.
-- The MAIN fallback is valid only because all existing rows predate the second site.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sites WHERE code = 'MAIN') THEN
        RAISE EXCEPTION 'shipments site_id backfill requires a MAIN site row to already exist';
    END IF;
END $$;

-- Shipments have no location column, so use the first item's destination.
UPDATE shipments s
SET site_id = resolved.site_id
FROM (
    SELECT DISTINCT ON (si.shipment_id) si.shipment_id, sl.site_id
    FROM shipment_items si
    JOIN locations l ON l.id = si.destination_location_id
    JOIN storage_locations sl ON sl.id = l.storage_location_id
    ORDER BY si.shipment_id, si.created_at
) resolved
WHERE s.id = resolved.shipment_id
  AND s.site_id IS NULL;

UPDATE shipments
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;

UPDATE shipment_items si
SET site_id = s.site_id
FROM shipments s
WHERE s.id = si.shipment_id
  AND si.site_id IS NULL;

UPDATE shipment_item_allocations sia
SET site_id = si.site_id
FROM shipment_items si
WHERE si.id = sia.shipment_item_id
  AND sia.site_id IS NULL;

-- The tracker id is inside JSONB, so payloads are not joined to shipments here.
UPDATE webhook_events
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;
