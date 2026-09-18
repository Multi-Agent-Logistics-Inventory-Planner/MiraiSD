-- Safe independently of V59/V60: SECOND was intentionally seeded empty by V54.
-- Give it the single canonical NOT_ASSIGNED storage location and NA location unit
-- without creating an assortment, membership, or other site data.
INSERT INTO storage_locations
    (id, site_id, name, code, code_prefix, icon, has_display, is_display_only, display_order)
SELECT gen_random_uuid(), s.id, 'Not Assigned', 'NOT_ASSIGNED', 'NA', 'CircleHelp', false, false, 99
FROM sites s
WHERE s.code = 'SECOND'
  AND NOT EXISTS (
      SELECT 1 FROM storage_locations sl
      WHERE sl.site_id = s.id AND sl.code = 'NOT_ASSIGNED'
  );

INSERT INTO locations (id, storage_location_id, location_code)
SELECT gen_random_uuid(), sl.id, 'NA'
FROM storage_locations sl
JOIN sites s ON s.id = sl.site_id
WHERE s.code = 'SECOND'
  AND sl.code = 'NOT_ASSIGNED'
  AND NOT EXISTS (
      SELECT 1 FROM locations l WHERE l.storage_location_id = sl.id
  );

-- locations has no site_id. A unique index cannot enforce this cross-table
-- invariant. The transaction advisory lock serializes competing INSERTs and
-- storage-location reparenting updates before checking the canonical row.
CREATE OR REPLACE FUNCTION enforce_one_not_assigned_location()
RETURNS TRIGGER AS $$
DECLARE
    storage_code TEXT;
BEGIN
    SELECT code INTO storage_code FROM storage_locations WHERE id = NEW.storage_location_id;
    IF storage_code = 'NOT_ASSIGNED' THEN
        PERFORM pg_advisory_xact_lock(hashtextextended(NEW.storage_location_id::text, 0));
        IF EXISTS (SELECT 1 FROM locations WHERE storage_location_id = NEW.storage_location_id) THEN
            RAISE EXCEPTION 'only one canonical NOT_ASSIGNED location is allowed per site';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_one_not_assigned_location_insert ON locations;
DROP TRIGGER IF EXISTS trg_one_not_assigned_location_update ON locations;
CREATE TRIGGER trg_one_not_assigned_location_insert
BEFORE INSERT ON locations
FOR EACH ROW EXECUTE FUNCTION enforce_one_not_assigned_location();
CREATE TRIGGER trg_one_not_assigned_location_update
BEFORE UPDATE OF storage_location_id ON locations
FOR EACH ROW
WHEN (OLD.storage_location_id IS DISTINCT FROM NEW.storage_location_id)
EXECUTE FUNCTION enforce_one_not_assigned_location();
