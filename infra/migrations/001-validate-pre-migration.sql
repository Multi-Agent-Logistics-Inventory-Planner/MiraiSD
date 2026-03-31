-- ============================================================================
-- Pre-Migration Validation Script
-- Run this BEFORE creating new unified location tables
-- Expected: All queries should return 0 rows (no issues found)
-- ============================================================================

-- 1. Check for UUID collisions across location tables
-- If any IDs appear in multiple tables, migration will fail
SELECT 'UUID_COLLISION' as check_type, id, COUNT(*) as occurrences
FROM (
    SELECT id FROM box_bins
    UNION ALL SELECT id FROM racks
    UNION ALL SELECT id FROM cabinets
    UNION ALL SELECT id FROM windows
    UNION ALL SELECT id FROM single_claw_machines
    UNION ALL SELECT id FROM double_claw_machines
    UNION ALL SELECT id FROM four_corner_machines
    UNION ALL SELECT id FROM pusher_machines
    UNION ALL SELECT id FROM gachapons
    UNION ALL SELECT id FROM keychain_machines
) all_locations
GROUP BY id
HAVING COUNT(*) > 1;

-- 2. Check for negative quantities in inventory tables
-- New table has CHECK (quantity >= 0), so negatives would fail
SELECT 'NEGATIVE_QUANTITY' as check_type, 'box_bin_inventory' as table_name, COUNT(*) as count
FROM box_bin_inventory WHERE quantity < 0
UNION ALL
SELECT 'NEGATIVE_QUANTITY', 'rack_inventory', COUNT(*) FROM rack_inventory WHERE quantity < 0
UNION ALL
SELECT 'NEGATIVE_QUANTITY', 'cabinet_inventory', COUNT(*) FROM cabinet_inventory WHERE quantity < 0
UNION ALL
SELECT 'NEGATIVE_QUANTITY', 'window_inventory', COUNT(*) FROM window_inventory WHERE quantity < 0
UNION ALL
SELECT 'NEGATIVE_QUANTITY', 'single_claw_machine_inventory', COUNT(*) FROM single_claw_machine_inventory WHERE quantity < 0
UNION ALL
SELECT 'NEGATIVE_QUANTITY', 'double_claw_machine_inventory', COUNT(*) FROM double_claw_machine_inventory WHERE quantity < 0
UNION ALL
SELECT 'NEGATIVE_QUANTITY', 'four_corner_machine_inventory', COUNT(*) FROM four_corner_machine_inventory WHERE quantity < 0
UNION ALL
SELECT 'NEGATIVE_QUANTITY', 'pusher_machine_inventory', COUNT(*) FROM pusher_machine_inventory WHERE quantity < 0
UNION ALL
SELECT 'NEGATIVE_QUANTITY', 'not_assigned_inventory', COUNT(*) FROM not_assigned_inventory WHERE quantity < 0;

-- 3. Check for orphaned inventory references (inventory pointing to non-existent locations)
SELECT 'ORPHAN_INVENTORY' as check_type, 'box_bin_inventory' as table_name, bi.id as inventory_id
FROM box_bin_inventory bi
LEFT JOIN box_bins b ON bi.box_bin_id = b.id
WHERE b.id IS NULL
LIMIT 10;

SELECT 'ORPHAN_INVENTORY' as check_type, 'rack_inventory' as table_name, ri.id as inventory_id
FROM rack_inventory ri
LEFT JOIN racks r ON ri.rack_id = r.id
WHERE r.id IS NULL
LIMIT 10;

SELECT 'ORPHAN_INVENTORY' as check_type, 'cabinet_inventory' as table_name, ci.id as inventory_id
FROM cabinet_inventory ci
LEFT JOIN cabinets c ON ci.cabinet_id = c.id
WHERE c.id IS NULL
LIMIT 10;

SELECT 'ORPHAN_INVENTORY' as check_type, 'window_inventory' as table_name, wi.id as inventory_id
FROM window_inventory wi
LEFT JOIN windows w ON wi.window_id = w.id
WHERE w.id IS NULL
LIMIT 10;

-- 4. Check for orphaned stock_movements references
-- These reference location IDs that should exist in one of the location tables
SELECT 'ORPHAN_STOCK_MOVEMENT' as check_type, sm.id, sm.to_location_id, sm.location_type
FROM stock_movements sm
WHERE sm.to_location_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM box_bins WHERE id = sm.to_location_id)
  AND NOT EXISTS (SELECT 1 FROM racks WHERE id = sm.to_location_id)
  AND NOT EXISTS (SELECT 1 FROM cabinets WHERE id = sm.to_location_id)
  AND NOT EXISTS (SELECT 1 FROM windows WHERE id = sm.to_location_id)
  AND NOT EXISTS (SELECT 1 FROM single_claw_machines WHERE id = sm.to_location_id)
  AND NOT EXISTS (SELECT 1 FROM double_claw_machines WHERE id = sm.to_location_id)
  AND NOT EXISTS (SELECT 1 FROM four_corner_machines WHERE id = sm.to_location_id)
  AND NOT EXISTS (SELECT 1 FROM pusher_machines WHERE id = sm.to_location_id)
  AND NOT EXISTS (SELECT 1 FROM gachapons WHERE id = sm.to_location_id)
  AND NOT EXISTS (SELECT 1 FROM keychain_machines WHERE id = sm.to_location_id)
LIMIT 100;

SELECT 'ORPHAN_STOCK_MOVEMENT' as check_type, sm.id, sm.from_location_id, sm.location_type
FROM stock_movements sm
WHERE sm.from_location_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM box_bins WHERE id = sm.from_location_id)
  AND NOT EXISTS (SELECT 1 FROM racks WHERE id = sm.from_location_id)
  AND NOT EXISTS (SELECT 1 FROM cabinets WHERE id = sm.from_location_id)
  AND NOT EXISTS (SELECT 1 FROM windows WHERE id = sm.from_location_id)
  AND NOT EXISTS (SELECT 1 FROM single_claw_machines WHERE id = sm.from_location_id)
  AND NOT EXISTS (SELECT 1 FROM double_claw_machines WHERE id = sm.from_location_id)
  AND NOT EXISTS (SELECT 1 FROM four_corner_machines WHERE id = sm.from_location_id)
  AND NOT EXISTS (SELECT 1 FROM pusher_machines WHERE id = sm.from_location_id)
  AND NOT EXISTS (SELECT 1 FROM gachapons WHERE id = sm.from_location_id)
  AND NOT EXISTS (SELECT 1 FROM keychain_machines WHERE id = sm.from_location_id)
LIMIT 100;

-- 5. Check for orphaned machine_display references
SELECT 'ORPHAN_MACHINE_DISPLAY' as check_type, md.id, md.machine_id, md.location_type
FROM machine_display md
WHERE NOT EXISTS (SELECT 1 FROM box_bins WHERE id = md.machine_id)
  AND NOT EXISTS (SELECT 1 FROM racks WHERE id = md.machine_id)
  AND NOT EXISTS (SELECT 1 FROM cabinets WHERE id = md.machine_id)
  AND NOT EXISTS (SELECT 1 FROM windows WHERE id = md.machine_id)
  AND NOT EXISTS (SELECT 1 FROM single_claw_machines WHERE id = md.machine_id)
  AND NOT EXISTS (SELECT 1 FROM double_claw_machines WHERE id = md.machine_id)
  AND NOT EXISTS (SELECT 1 FROM four_corner_machines WHERE id = md.machine_id)
  AND NOT EXISTS (SELECT 1 FROM pusher_machines WHERE id = md.machine_id)
  AND NOT EXISTS (SELECT 1 FROM gachapons WHERE id = md.machine_id)
  AND NOT EXISTS (SELECT 1 FROM keychain_machines WHERE id = md.machine_id)
LIMIT 100;

-- 6. Summary counts (for reference, not validation)
SELECT 'LOCATION_COUNTS' as info_type, 'box_bins' as table_name, COUNT(*) as count FROM box_bins
UNION ALL SELECT 'LOCATION_COUNTS', 'racks', COUNT(*) FROM racks
UNION ALL SELECT 'LOCATION_COUNTS', 'cabinets', COUNT(*) FROM cabinets
UNION ALL SELECT 'LOCATION_COUNTS', 'windows', COUNT(*) FROM windows
UNION ALL SELECT 'LOCATION_COUNTS', 'single_claw_machines', COUNT(*) FROM single_claw_machines
UNION ALL SELECT 'LOCATION_COUNTS', 'double_claw_machines', COUNT(*) FROM double_claw_machines
UNION ALL SELECT 'LOCATION_COUNTS', 'four_corner_machines', COUNT(*) FROM four_corner_machines
UNION ALL SELECT 'LOCATION_COUNTS', 'pusher_machines', COUNT(*) FROM pusher_machines
UNION ALL SELECT 'LOCATION_COUNTS', 'gachapons', COUNT(*) FROM gachapons
UNION ALL SELECT 'LOCATION_COUNTS', 'keychain_machines', COUNT(*) FROM keychain_machines;

SELECT 'INVENTORY_COUNTS' as info_type, 'box_bin_inventory' as table_name, COUNT(*) as rows, SUM(quantity) as total_qty FROM box_bin_inventory
UNION ALL SELECT 'INVENTORY_COUNTS', 'rack_inventory', COUNT(*), SUM(quantity) FROM rack_inventory
UNION ALL SELECT 'INVENTORY_COUNTS', 'cabinet_inventory', COUNT(*), SUM(quantity) FROM cabinet_inventory
UNION ALL SELECT 'INVENTORY_COUNTS', 'window_inventory', COUNT(*), SUM(quantity) FROM window_inventory
UNION ALL SELECT 'INVENTORY_COUNTS', 'single_claw_machine_inventory', COUNT(*), SUM(quantity) FROM single_claw_machine_inventory
UNION ALL SELECT 'INVENTORY_COUNTS', 'double_claw_machine_inventory', COUNT(*), SUM(quantity) FROM double_claw_machine_inventory
UNION ALL SELECT 'INVENTORY_COUNTS', 'four_corner_machine_inventory', COUNT(*), SUM(quantity) FROM four_corner_machine_inventory
UNION ALL SELECT 'INVENTORY_COUNTS', 'pusher_machine_inventory', COUNT(*), SUM(quantity) FROM pusher_machine_inventory
UNION ALL SELECT 'INVENTORY_COUNTS', 'not_assigned_inventory', COUNT(*), SUM(quantity) FROM not_assigned_inventory;
