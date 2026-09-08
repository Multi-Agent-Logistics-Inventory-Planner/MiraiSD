-- Backfill step (docs/specs/multi-site-data-and-api.md section 5, .specs/phase-5c-site-products
-- AC-2): grants MAIN one site_products row per existing product, seeding only the two NOT NULL
-- columns from their current global values. Every nullable override column
-- (unit_cost/msrp/reorder_point/target_stock_level/lead_time_days) is left NULL, not copied -
-- see AC-2's rationale: a copied value would be indistinguishable from a deliberate override and
-- would silently stop inheriting forecasting-service's nightly products.reorder_point writes for
-- every product, for no reason a human chose.
--
-- SECOND (and any future site) gets zero rows here - "carried" is established only by an
-- explicit assortment write (SiteProductService), matching a real second-site-opening scenario.
--
-- Fails loudly rather than silently backfilling zero rows if the MAIN site row is somehow absent
-- at migration time, matching V53's precedent.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sites WHERE code = 'MAIN') THEN
        RAISE EXCEPTION 'site_products backfill requires a MAIN site row to already exist';
    END IF;
END $$;

INSERT INTO site_products (site_id, product_id, is_stocked, forecasting_enabled)
SELECT s.id, p.id, p.is_active, p.forecasting_enabled
FROM products p
CROSS JOIN sites s
WHERE s.code = 'MAIN'
ON CONFLICT (site_id, product_id) DO NOTHING;
