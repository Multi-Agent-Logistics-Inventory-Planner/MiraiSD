-- Composite index on (item_id, at DESC) for stock_movements.
-- Supports: forecasting event-window reads, per-item history queries, and the
-- audit-log per-product views that get hit harder once batch-adjust ships.
CREATE INDEX IF NOT EXISTS idx_stock_movements_item_at_desc
    ON stock_movements (item_id, at DESC);
