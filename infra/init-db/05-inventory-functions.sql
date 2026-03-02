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
        SELECT quantity AS qty FROM keychain_machine_inventory WHERE item_id = p_product_id
        UNION ALL
        SELECT quantity AS qty FROM pusher_machine_inventory WHERE item_id = p_product_id
    ) AS all_inventory;

    RETURN total;
END;
$$ LANGUAGE plpgsql;
