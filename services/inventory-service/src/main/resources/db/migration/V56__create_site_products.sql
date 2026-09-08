-- Expand step (docs/specs/multi-site-data-and-api.md section 5, .specs/phase-5c-site-products
-- AC-1): introduces per-site assortment and setting overrides for products. All override
-- columns are nullable - effective value is COALESCE(site_products.X, products.X), computed in
-- application code (catalog.application.EffectiveProductSettings), not here.
--
-- site_id references sites, which is not Flyway-managed (see V52's comment) - every deployed
-- environment already has it from infra/init-db/*.sql.
--
-- site_id is RESTRICT (a site cannot be deleted while it still has assortment rows - sites are
-- not expected to be deleted in normal operation); product_id is CASCADE (deleting a product
-- removes its per-site assortment, matching how the product itself no longer exists anywhere).
CREATE TABLE site_products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    is_stocked BOOLEAN NOT NULL DEFAULT FALSE,
    forecasting_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    unit_cost NUMERIC(10, 2),
    msrp NUMERIC(10, 2),
    reorder_point INTEGER,
    target_stock_level INTEGER,
    lead_time_days INTEGER,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (site_id, product_id)
);

CREATE INDEX idx_site_products_site ON site_products(site_id);
CREATE INDEX idx_site_products_site_stocked ON site_products(site_id, is_stocked);
CREATE INDEX idx_site_products_product ON site_products(product_id);
