-- Run outside a transaction (paired .conf): CONCURRENTLY avoids locking a live table.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_shipments_site_status
  ON shipments(site_id, status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_shipment_items_site
  ON shipment_items(site_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_shipment_item_allocations_site
  ON shipment_item_allocations(site_id);
