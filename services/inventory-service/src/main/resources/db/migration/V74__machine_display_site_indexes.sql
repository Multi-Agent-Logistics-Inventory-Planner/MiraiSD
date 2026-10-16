-- Run outside a transaction (paired .conf): CONCURRENTLY avoids locking a live table.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_machine_display_site_active
  ON machine_display(site_id, location_type, machine_id, ended_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_machine_display_site_product
  ON machine_display(site_id, product_id);
