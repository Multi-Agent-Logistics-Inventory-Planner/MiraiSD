-- Phase 6b (.specs/phase-6-inventory, AC-2): location_inventory is already fully site-owned
-- (site_id NOT NULL, trigger-derived from the location's site - see infra/init-db/20-unified-
-- locations.sql) but has no index leading with site_id, which 6c's site-scoped slim totals
-- projection (sumQuantityByProductIdAndSiteId) needs to stay cheap as the second site's data
-- grows. CONCURRENTLY because location_inventory is in daily production use and must not be
-- locked; matches V19's precedent exactly, including the paired .conf disabling the wrapping
-- transaction Flyway would otherwise use (CREATE INDEX CONCURRENTLY cannot run inside one).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_location_inventory_site_product
  ON location_inventory(site_id, product_id);
