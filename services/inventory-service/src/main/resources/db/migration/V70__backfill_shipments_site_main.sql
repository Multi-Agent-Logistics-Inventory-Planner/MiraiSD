-- Backfill step (docs/specs/multi-site-data-and-api.md section 5, .specs/phase-7-shipments AC-1)
-- for V69's new site_id columns. Every location_type resolves through the unified `locations`
-- table regardless of its discriminator (see MachineDisplayService.resolveMachineSite's
-- locationRepository.findById(...).getStorageLocation().getSite() precedent).
--
-- One shipment owns exactly one site, and every item/allocation under it inherits that same
-- site -- matching the invariant the application layer enforces for every new row going forward
-- (ShipmentService always sets item.site = shipment.getSite() and allocation.site =
-- shipmentItem.getSite()). An earlier version of this migration derived each item/allocation's
-- site independently from its own location, which could split one shipment's rows across sites
-- for legacy data whose destination locations disagree -- that contradicts the one-shipment-one-
-- site rule the rest of this slice (AC-2's foreign-site rejection, the entities' own site field)
-- depends on, so it is not used here.
--
-- shipments resolves from any of its items' destination locations, since a shipment has no
-- location column of its own; every remaining unresolved row falls back to MAIN - correct for all
-- *existing* data only because every shipment, item and allocation today was created before the
-- second site (V54) had any locations of its own; it is not a rule new writers may rely on.
--
-- Fails loudly rather than silently backfilling zero rows if the MAIN site row is somehow absent
-- at migration time, matching V60's precedent.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sites WHERE code = 'MAIN') THEN
        RAISE EXCEPTION 'shipments site_id backfill requires a MAIN site row to already exist';
    END IF;
END $$;

-- shipments: derive from any item's destination location (first resolvable one), else MAIN.
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

-- shipment_items: always inherit the parent shipment's site (one shipment, one site).
UPDATE shipment_items si
SET site_id = s.site_id
FROM shipments s
WHERE s.id = si.shipment_id
  AND si.site_id IS NULL;

-- shipment_item_allocations: always inherit the parent shipment_item's site.
UPDATE shipment_item_allocations sia
SET site_id = si.site_id
FROM shipment_items si
WHERE si.id = sia.shipment_item_id
  AND sia.site_id IS NULL;

-- webhook_events: no reliable structured way to join payload -> shipment in a migration (the
-- tracker id lives inside JSONB and matching it to shipments.easypost_tracker_id is the
-- application's job, not a one-time SQL backfill's). Every existing row predates multi-site and
-- is set to MAIN; it is an idempotency/audit log, not user-facing tenant data, so this carries no
-- cross-tenant read/write risk. New rows get site_id from the resolved Shipment where the event
-- type resolves one (see T-2), and remain NULL where it does not (see V69's note) - the column
-- stays nullable indefinitely for this table.
UPDATE webhook_events
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;
