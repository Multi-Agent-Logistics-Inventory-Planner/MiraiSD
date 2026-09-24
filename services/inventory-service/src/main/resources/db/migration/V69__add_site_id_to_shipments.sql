-- Expand step (docs/specs/multi-site-data-and-api.md section 5, .specs/phase-7-shipments AC-1).
-- shipments/shipment_items/shipment_item_allocations/webhook_events predate multi-site and carry
-- no site dimension. Adds site_id nullable, with no FK and no default on every table, matching
-- V59's precedent: existing rows get a value from V70's backfill, not from a default that would
-- hide which rows are genuinely unresolved. New rows get site_id from application code (T-4's
-- InventoryOperations-routed writers and the shipment module's own create/receive paths) starting
-- with the release that ships alongside this migration.
--
-- webhook_events.site_id stays nullable even after the other three tables are constrained
-- (see V70's note): not every processed webhook event resolves to a known shipment (only
-- tracker.created/tracker.updated do; every other EasyPost event type is recorded for idempotency
-- and otherwise ignored, with no shipment context to derive a site from).
--
-- Constraining shipments/shipment_items/shipment_item_allocations (NOT NULL + FK + supporting
-- indexes) is deliberately deferred to a later, separately released migration once the code
-- rollout below has been deployed and verified against production data - see T-3/T-6.
ALTER TABLE shipments ADD COLUMN site_id UUID;
ALTER TABLE shipment_items ADD COLUMN site_id UUID;
ALTER TABLE shipment_item_allocations ADD COLUMN site_id UUID;
ALTER TABLE webhook_events ADD COLUMN site_id UUID;
