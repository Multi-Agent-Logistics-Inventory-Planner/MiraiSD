-- Backfill step (docs/specs/multi-site-data-and-api.md section 5, .specs/phase-6-inventory AC-2)
-- for V59's new stock_movements.site_id column. Two deterministic passes:
--
-- Pass 1 (location-derived, authoritative): for any movement whose to_location_id or
-- from_location_id still resolves to a real locations row, take that location's site via
-- locations -> storage_locations.site_id. The choice is sign-aware, matching every write path
-- exactly: a negative quantity_change (a withdrawal/source leg - see InventoryOperations.
-- applyDelta and the two-row transfer writers in StockMovementService/KujiBoxService, which set
-- .site(sourceLocation...)/.site(destinationLocation...) independently per row) resolves from
-- from_location_id first; a non-negative quantity_change (a deposit/destination leg, or a
-- zero-delta display swap, which prefers its target machine's site - see
-- MachineDisplayService.resolveMachineSite) resolves from to_location_id first. Both halves of a
-- transfer carry both location ids, so COALESCE alone (picking to_location_id unconditionally)
-- would silently assign the destination's site to the withdrawal row too - wrong the moment two
-- sites both have real inventory, even though every existing row is MAIN today and cannot expose
-- it yet.
--
-- Pass 2 (MAIN fallback): for rows pass 1 could not resolve - either both location ids are null
-- (e.g. Kuji ledger rows, which record payout/removal events with no location_inventory change)
-- or the referenced location row no longer exists (from_location_id/to_location_id carry no FK,
-- so this is possible) - assign the MAIN site. This is correct for all *existing* data only
-- because the second site (V54) was seeded with no locations of its own and therefore has no
-- stock_movements rows yet; it is not a rule new writers may rely on, and application code must
-- set an explicit site for every new location-less movement instead of defaulting to MAIN.
--
-- Fails loudly rather than silently backfilling zero rows if the MAIN site row is somehow absent
-- at migration time, matching V53/V57's precedent.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sites WHERE code = 'MAIN') THEN
        RAISE EXCEPTION 'stock_movements site_id backfill requires a MAIN site row to already exist';
    END IF;
END $$;

UPDATE stock_movements sm
SET site_id = sl.site_id
FROM locations l
JOIN storage_locations sl ON sl.id = l.storage_location_id
WHERE l.id = CASE
                WHEN sm.quantity_change < 0 THEN COALESCE(sm.from_location_id, sm.to_location_id)
                ELSE COALESCE(sm.to_location_id, sm.from_location_id)
              END
  AND sm.site_id IS NULL;

UPDATE stock_movements
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;
