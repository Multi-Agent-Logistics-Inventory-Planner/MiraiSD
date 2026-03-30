-- Inventory calculation functions
-- These depend on the unified location_inventory and locations tables

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'location_inventory'
    ) THEN
        -- Calculate total inventory for a product across all storage locations
        -- Uses the unified location_inventory table
        CREATE OR REPLACE FUNCTION calculate_total_inventory(p_product_id UUID)
        RETURNS INTEGER AS $func$
            SELECT COALESCE(SUM(quantity), 0)::INTEGER
            FROM location_inventory
            WHERE product_id = p_product_id;
        $func$ LANGUAGE sql STABLE;

        RAISE NOTICE 'calculate_total_inventory function created';
    ELSE
        RAISE NOTICE 'location_inventory table does not exist yet; skipping calculate_total_inventory (Hibernate will create tables)';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'locations'
    ) THEN
        -- Resolve location UUID to location code
        -- Uses the unified locations table (location UUIDs were preserved during migration)
        -- The p_location_type parameter is kept for backward compatibility but is now ignored
        CREATE OR REPLACE FUNCTION resolve_location_code(
            p_location_id UUID,
            p_location_type TEXT DEFAULT NULL
        )
        RETURNS TEXT AS $func$
            SELECT l.location_code
            FROM locations l
            WHERE l.id = p_location_id;
        $func$ LANGUAGE sql STABLE;

        RAISE NOTICE 'resolve_location_code function created';
    ELSE
        RAISE NOTICE 'locations table does not exist yet; skipping resolve_location_code';
    END IF;
END $$;
