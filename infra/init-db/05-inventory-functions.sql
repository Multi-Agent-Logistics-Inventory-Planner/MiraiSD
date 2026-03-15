-- Inventory calculation functions

-- Calculate total inventory for a product across all storage locations
CREATE OR REPLACE FUNCTION calculate_total_inventory(p_product_id UUID)
RETURNS INTEGER AS $$
DECLARE
    total INTEGER := 0;
BEGIN
    SELECT COALESCE(SUM(qty), 0) INTO total
    FROM (
        SELECT quantity AS qty FROM not_assigned_inventory WHERE item_id = p_product_id
        UNION ALL
        SELECT quantity AS qty FROM rack_inventory WHERE item_id = p_product_id
        UNION ALL
        SELECT quantity AS qty FROM cabinet_inventory WHERE item_id = p_product_id
        UNION ALL
        SELECT quantity AS qty FROM box_bin_inventory WHERE item_id = p_product_id
        UNION ALL
        SELECT quantity AS qty FROM single_claw_machine_inventory WHERE item_id = p_product_id
        UNION ALL
        SELECT quantity AS qty FROM double_claw_machine_inventory WHERE item_id = p_product_id
        UNION ALL
        SELECT quantity AS qty FROM four_corner_machine_inventory WHERE item_id = p_product_id
        UNION ALL
        SELECT quantity AS qty FROM pusher_machine_inventory WHERE item_id = p_product_id
        UNION ALL
        SELECT quantity AS qty FROM window_inventory WHERE item_id = p_product_id
    ) AS all_inventory;

    RETURN total;
END;
$$ LANGUAGE plpgsql;

-- Resolve location UUID to location code
CREATE OR REPLACE FUNCTION resolve_location_code(
    p_location_id UUID,
    p_location_type TEXT
)
RETURNS TEXT AS $$
DECLARE
    result TEXT;
BEGIN
    IF p_location_id IS NULL THEN
        RETURN NULL;
    END IF;

    CASE p_location_type
        WHEN 'BOX_BIN' THEN
            SELECT box_bin_code INTO result FROM box_bins WHERE id = p_location_id;
        WHEN 'SINGLE_CLAW_MACHINE' THEN
            SELECT single_claw_machine_code INTO result FROM single_claw_machines WHERE id = p_location_id;
        WHEN 'DOUBLE_CLAW_MACHINE' THEN
            SELECT double_claw_machine_code INTO result FROM double_claw_machines WHERE id = p_location_id;
        WHEN 'KEYCHAIN_MACHINE' THEN
            SELECT keychain_machine_code INTO result FROM keychain_machines WHERE id = p_location_id;
        WHEN 'CABINET' THEN
            SELECT cabinet_code INTO result FROM cabinets WHERE id = p_location_id;
        WHEN 'RACK' THEN
            SELECT rack_code INTO result FROM racks WHERE id = p_location_id;
        WHEN 'FOUR_CORNER_MACHINE' THEN
            SELECT four_corner_machine_code INTO result FROM four_corner_machines WHERE id = p_location_id;
        WHEN 'PUSHER_MACHINE' THEN
            SELECT pusher_machine_code INTO result FROM pusher_machines WHERE id = p_location_id;
        WHEN 'WINDOW' THEN
            SELECT window_code INTO result FROM windows WHERE id = p_location_id;
        WHEN 'NOT_ASSIGNED' THEN
            RETURN 'NA';
        ELSE
            RETURN NULL;
    END CASE;

    RETURN result;
END;
$$ LANGUAGE plpgsql;
