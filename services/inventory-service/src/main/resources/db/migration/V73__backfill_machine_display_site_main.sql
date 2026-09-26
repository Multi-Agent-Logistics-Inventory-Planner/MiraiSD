-- Backfill step (docs/specs/multi-site-data-and-api.md section 5, .specs/phase-7-displays AC-1)
-- for V72's new site_id column.
--
-- Every machine_display row resolves through the same location_id -> locations ->
-- storage_locations chain the application already uses (MachineDisplayService.resolveMachineSite).
-- T-1 verified this resolves cleanly for all 879 current production rows, with zero rows joining
-- to a missing site_products entry for their resolved (site, product) pair - unlike shipments,
-- there is no legacy cross-site ambiguity to fall back to MAIN for here, but the MAIN fallback is
-- kept for defensive completeness (an orphaned location_id would otherwise leave site_id NULL).
--
-- Fails loudly rather than silently backfilling zero rows if the MAIN site row is somehow absent
-- at migration time, matching V60/V70's precedent.
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
