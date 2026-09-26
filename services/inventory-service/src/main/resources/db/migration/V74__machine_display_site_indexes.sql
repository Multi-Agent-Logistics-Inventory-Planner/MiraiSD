-- Verify step (.specs/phase-7-displays T-2, matching V62/V71's precedent exactly): supports the
-- site-scoped list/lookup reads T-3/T-4 add (MachineDisplayRepository site-scoped finders, the
-- v1 /api/v1/sites/{siteId}/machine-displays routes). CONCURRENTLY + the paired .conf disabling
-- the wrapping transaction, since machine_display is in daily production use and must not be
-- locked.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_machine_display_site_active
  ON machine_display(site_id, location_type, machine_id, ended_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_machine_display_site_product
  ON machine_display(site_id, product_id);
