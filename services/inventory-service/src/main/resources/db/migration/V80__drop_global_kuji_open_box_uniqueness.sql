-- T-5 gate (.specs/phase-7-kuji-lootbox). Deferred from T-3 until open-box reads were verified
-- site-scoped end to end: KujiBoxService.getActiveBoxByProduct and every open-box lookup now
-- resolve through KujiBoxRepository.findByProductIdAndSiteId*(...) variants; the old unscoped
-- findByProductIdAndStatus method is unused in production code (grep-confirmed, no callers).
--
-- Replaces the global "one OPEN box per product across the whole organization" invariant with a
-- per-site one, so two sites can each have their own OPEN kuji box for the same product. Creates
-- the new index CONCURRENTLY first, then drops the old one, so there is no window with neither
-- constraint enforced.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uniq_kuji_boxes_open_per_site_product
    ON kuji_boxes (site_id, product_id) WHERE status = 'OPEN';

DROP INDEX CONCURRENTLY IF EXISTS uniq_kuji_boxes_open_per_product;
