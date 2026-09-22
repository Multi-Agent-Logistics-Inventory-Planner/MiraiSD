-- Human-gated post-deploy constraint. V61 was the historical placeholder named
-- by V59, but V62-V67 now exist; an out-of-order V61 would not run after F2's
-- Flyway baseline. Do not include V68 in an automatic Flyway target until the
-- V59/V60 backfill is verified, PR #328's site-aware writers are deployed, and
-- an immediate production check confirms stock_movements.site_id has no NULLs.
ALTER TABLE stock_movements
    ADD CONSTRAINT fk_stock_movements_site FOREIGN KEY (site_id) REFERENCES sites(id);
ALTER TABLE stock_movements
    ALTER COLUMN site_id SET NOT NULL;
