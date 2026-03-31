-- ============================================================================
-- Post-Migration Validation Script
-- Run this AFTER 003-migrate-data.sql to verify data integrity
-- Expected: All validation queries should return 0 rows (no discrepancies)
-- ============================================================================

-- ============================================================================
-- 1. Compare Total Quantities Per Product (Critical Check)
-- This is the most important validation - quantities must match exactly
-- ============================================================================
WITH old_totals AS (
    SELECT item_id, SUM(quantity) as old_qty FROM (
        SELECT item_id, quantity FROM box_bin_inventory
        UNION ALL SELECT item_id, quantity FROM rack_inventory
        UNION ALL SELECT item_id, quantity FROM cabinet_inventory
        UNION ALL SELECT item_id, quantity FROM window_inventory
        UNION ALL SELECT item_id, quantity FROM single_claw_machine_inventory
        UNION ALL SELECT item_id, quantity FROM double_claw_machine_inventory
        UNION ALL SELECT item_id, quantity FROM four_corner_machine_inventory
        UNION ALL SELECT item_id, quantity FROM pusher_machine_inventory
        UNION ALL SELECT item_id, quantity FROM not_assigned_inventory
    ) combined
    GROUP BY item_id
),
new_totals AS (
    SELECT product_id, SUM(quantity) as new_qty
    FROM location_inventory
    GROUP BY product_id
)
SELECT
    'QUANTITY_MISMATCH' as check_type,
    COALESCE(o.item_id, n.product_id) as product_id,
    o.old_qty,
    n.new_qty,
    COALESCE(n.new_qty, 0) - COALESCE(o.old_qty, 0) as difference
FROM old_totals o
FULL OUTER JOIN new_totals n ON o.item_id = n.product_id
WHERE o.old_qty != n.new_qty
   OR o.old_qty IS NULL
   OR n.new_qty IS NULL;
-- Expected: 0 rows

-- ============================================================================
-- 2. Compare Location Counts
-- All locations from old tables should exist in new locations table
-- ============================================================================
SELECT 'LOCATION_COUNT_MISMATCH' as check_type,
       'Expected' as source,
       (
           (SELECT COUNT(*) FROM box_bins) +
           (SELECT COUNT(*) FROM racks) +
           (SELECT COUNT(*) FROM cabinets) +
           (SELECT COUNT(*) FROM windows) +
           (SELECT COUNT(*) FROM single_claw_machines) +
           (SELECT COUNT(*) FROM double_claw_machines) +
           (SELECT COUNT(*) FROM four_corner_machines) +
           (SELECT COUNT(*) FROM pusher_machines) +
           (SELECT COUNT(*) FROM gachapons) +
           (SELECT COUNT(*) FROM keychain_machines)
       ) as expected_count,
       (SELECT COUNT(*) FROM locations) as actual_count
WHERE (
    (SELECT COUNT(*) FROM box_bins) +
    (SELECT COUNT(*) FROM racks) +
    (SELECT COUNT(*) FROM cabinets) +
    (SELECT COUNT(*) FROM windows) +
    (SELECT COUNT(*) FROM single_claw_machines) +
    (SELECT COUNT(*) FROM double_claw_machines) +
    (SELECT COUNT(*) FROM four_corner_machines) +
    (SELECT COUNT(*) FROM pusher_machines) +
    (SELECT COUNT(*) FROM gachapons) +
    (SELECT COUNT(*) FROM keychain_machines)
) != (SELECT COUNT(*) FROM locations);
-- Expected: 0 rows

-- ============================================================================
-- 3. Compare Inventory Row Counts
-- All inventory rows should be migrated
-- ============================================================================
SELECT 'INVENTORY_ROW_COUNT_MISMATCH' as check_type,
       'Expected' as source,
       (
           (SELECT COUNT(*) FROM box_bin_inventory) +
           (SELECT COUNT(*) FROM rack_inventory) +
           (SELECT COUNT(*) FROM cabinet_inventory) +
           (SELECT COUNT(*) FROM window_inventory) +
           (SELECT COUNT(*) FROM single_claw_machine_inventory) +
           (SELECT COUNT(*) FROM double_claw_machine_inventory) +
           (SELECT COUNT(*) FROM four_corner_machine_inventory) +
           (SELECT COUNT(*) FROM pusher_machine_inventory) +
           (SELECT COUNT(*) FROM not_assigned_inventory)
       ) as expected_count,
       (SELECT COUNT(*) FROM location_inventory) as actual_count
WHERE (
    (SELECT COUNT(*) FROM box_bin_inventory) +
    (SELECT COUNT(*) FROM rack_inventory) +
    (SELECT COUNT(*) FROM cabinet_inventory) +
    (SELECT COUNT(*) FROM window_inventory) +
    (SELECT COUNT(*) FROM single_claw_machine_inventory) +
    (SELECT COUNT(*) FROM double_claw_machine_inventory) +
    (SELECT COUNT(*) FROM four_corner_machine_inventory) +
    (SELECT COUNT(*) FROM pusher_machine_inventory) +
    (SELECT COUNT(*) FROM not_assigned_inventory)
) != (SELECT COUNT(*) FROM location_inventory);
-- Expected: 0 rows

-- ============================================================================
-- 4. Verify All Old Location UUIDs Exist in New Table
-- This ensures FK compatibility for stock_movements and machine_display
-- ============================================================================
SELECT 'MISSING_LOCATION_UUID' as check_type, 'box_bins' as source_table, id
FROM box_bins WHERE id NOT IN (SELECT id FROM locations)
UNION ALL
SELECT 'MISSING_LOCATION_UUID', 'racks', id
FROM racks WHERE id NOT IN (SELECT id FROM locations)
UNION ALL
SELECT 'MISSING_LOCATION_UUID', 'cabinets', id
FROM cabinets WHERE id NOT IN (SELECT id FROM locations)
UNION ALL
SELECT 'MISSING_LOCATION_UUID', 'windows', id
FROM windows WHERE id NOT IN (SELECT id FROM locations)
UNION ALL
SELECT 'MISSING_LOCATION_UUID', 'single_claw_machines', id
FROM single_claw_machines WHERE id NOT IN (SELECT id FROM locations)
UNION ALL
SELECT 'MISSING_LOCATION_UUID', 'double_claw_machines', id
FROM double_claw_machines WHERE id NOT IN (SELECT id FROM locations)
UNION ALL
SELECT 'MISSING_LOCATION_UUID', 'four_corner_machines', id
FROM four_corner_machines WHERE id NOT IN (SELECT id FROM locations)
UNION ALL
SELECT 'MISSING_LOCATION_UUID', 'pusher_machines', id
FROM pusher_machines WHERE id NOT IN (SELECT id FROM locations)
UNION ALL
SELECT 'MISSING_LOCATION_UUID', 'gachapons', id
FROM gachapons WHERE id NOT IN (SELECT id FROM locations)
UNION ALL
SELECT 'MISSING_LOCATION_UUID', 'keychain_machines', id
FROM keychain_machines WHERE id NOT IN (SELECT id FROM locations);
-- Expected: 0 rows

-- ============================================================================
-- 5. Verify Storage Location Categories Created Correctly
-- ============================================================================
SELECT 'MISSING_STORAGE_LOCATION' as check_type, expected_code
FROM (
    VALUES ('BOX_BINS'), ('RACKS'), ('CABINETS'), ('WINDOWS'),
           ('SINGLE_CLAW'), ('DOUBLE_CLAW'), ('FOUR_CORNER'), ('PUSHER'),
           ('GACHAPON'), ('KEYCHAIN')
) AS expected(expected_code)
WHERE expected_code NOT IN (SELECT code FROM storage_locations);
-- Expected: 0 rows

-- ============================================================================
-- 6. Verify Site Created
-- ============================================================================
SELECT 'MISSING_SITE' as check_type, 'MAIN' as expected_code
WHERE NOT EXISTS (SELECT 1 FROM sites WHERE code = 'MAIN');
-- Expected: 0 rows

-- ============================================================================
-- 7. Summary Statistics (For Reference)
-- ============================================================================
SELECT 'SUMMARY' as type, 'sites' as entity, COUNT(*) as count FROM sites
UNION ALL
SELECT 'SUMMARY', 'storage_locations', COUNT(*) FROM storage_locations
UNION ALL
SELECT 'SUMMARY', 'locations', COUNT(*) FROM locations
UNION ALL
SELECT 'SUMMARY', 'location_inventory', COUNT(*) FROM location_inventory
UNION ALL
SELECT 'SUMMARY', 'total_quantity', SUM(quantity) FROM location_inventory;

-- Per storage location breakdown
SELECT 'BREAKDOWN' as type, sl.name, COUNT(l.id) as location_count
FROM storage_locations sl
LEFT JOIN locations l ON l.storage_location_id = sl.id
GROUP BY sl.name, sl.display_order
ORDER BY sl.display_order;

-- Per storage location inventory
SELECT 'INVENTORY_BREAKDOWN' as type, sl.name,
       COUNT(DISTINCT li.id) as inventory_rows,
       SUM(li.quantity) as total_quantity
FROM storage_locations sl
LEFT JOIN locations l ON l.storage_location_id = sl.id
LEFT JOIN location_inventory li ON li.location_id = l.id
GROUP BY sl.name, sl.display_order
ORDER BY sl.display_order;

-- Unassigned inventory
SELECT 'UNASSIGNED_INVENTORY' as type,
       COUNT(*) as rows,
       SUM(quantity) as total_quantity
FROM location_inventory
WHERE location_id IS NULL;
