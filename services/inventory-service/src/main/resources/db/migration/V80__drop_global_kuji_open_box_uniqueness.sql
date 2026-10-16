-- One OPEN box per product per site instead of globally. The new index is built first so
-- uniqueness is never unenforced. Apply only once open-box lookups are site-scoped.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uniq_kuji_boxes_open_per_site_product
    ON kuji_boxes (site_id, product_id) WHERE status = 'OPEN';

DROP INDEX CONCURRENTLY IF EXISTS uniq_kuji_boxes_open_per_product;
