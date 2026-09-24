-- Verify step (.specs/phase-7-shipments T-3, matching V62's precedent exactly): supports the
-- site-scoped list/lookup reads T-4/T-5 add (ShipmentRepository site-scoped finders, the v1
-- /api/v1/sites/{siteId}/shipments routes). CONCURRENTLY + the paired .conf disabling the
-- wrapping transaction, since shipments is in daily production use and must not be locked.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_shipments_site_status
  ON shipments(site_id, status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_shipment_items_site
  ON shipment_items(site_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_shipment_item_allocations_site
  ON shipment_item_allocations(site_id);
