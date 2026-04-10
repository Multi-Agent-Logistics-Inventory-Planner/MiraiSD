-- Product Assistant: composite indexes to keep per-product drill-downs cheap.
-- CONCURRENTLY is required because these tables are in daily production use
-- and must not be locked. Flyway runs this migration without a wrapping
-- transaction because CREATE INDEX CONCURRENTLY cannot run inside one.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_stock_movements_item_id_at
  ON stock_movements(item_id, at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_shipment_items_item_id
  ON shipment_items(item_id);
