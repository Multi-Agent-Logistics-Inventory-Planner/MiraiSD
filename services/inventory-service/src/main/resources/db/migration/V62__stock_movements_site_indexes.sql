-- Phase 6c (.specs/phase-6-inventory, T-6c-3, P-2): split out of what the 6b worksheet originally
-- planned as V61 -- only the two site-scoped indexes below are rollback-safe at any point
-- (matches V58's precedent exactly). V61's SET NOT NULL + FK remains a separate, gated PR/record,
-- shipped once the writer release (6b) has been deployed and verified against production data.
-- Without these, 6c's scoped movement/audit-log reads (StockMovementRepository.
-- findByItem_IdAndSite_IdOrderByAtDesc, StockMovementSpecifications.withSiteFilter) seq-scan the
-- stock_movements ledger. CONCURRENTLY + the paired .conf disabling the wrapping transaction,
-- because stock_movements is in daily production use and must not be locked.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_stock_movements_site_at
  ON stock_movements(site_id, at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_stock_movements_site_item_at
  ON stock_movements(site_id, item_id, at DESC);
