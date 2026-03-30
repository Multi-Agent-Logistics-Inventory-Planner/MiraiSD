-- Inventory calculation functions

-- Calculate total inventory for a product across all storage locations
-- Uses the unified location_inventory table
CREATE OR REPLACE FUNCTION calculate_total_inventory(p_product_id UUID)
RETURNS INTEGER AS $$
    SELECT COALESCE(SUM(quantity), 0)::INTEGER
    FROM location_inventory
    WHERE product_id = p_product_id;
$$ LANGUAGE sql STABLE;

-- Resolve location UUID to location code
-- Uses the unified locations table (location UUIDs were preserved during migration)
-- The p_location_type parameter is kept for backward compatibility but is now ignored
CREATE OR REPLACE FUNCTION resolve_location_code(
    p_location_id UUID,
    p_location_type TEXT DEFAULT NULL
)
RETURNS TEXT AS $$
    SELECT l.location_code
    FROM locations l
    WHERE l.id = p_location_id;
$$ LANGUAGE sql STABLE;
