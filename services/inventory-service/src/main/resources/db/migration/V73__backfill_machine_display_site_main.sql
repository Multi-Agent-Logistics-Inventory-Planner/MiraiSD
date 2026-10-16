-- Site comes from location -> storage location. MAIN is only a fallback for orphaned locations.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sites WHERE code = 'MAIN') THEN
        RAISE EXCEPTION 'machine_display site_id backfill requires a MAIN site row to already exist';
    END IF;
END $$;

UPDATE machine_display md
SET site_id = sl.site_id
FROM locations l
JOIN storage_locations sl ON sl.id = l.storage_location_id
WHERE l.id = md.location_id
  AND md.site_id IS NULL;

UPDATE machine_display
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;
