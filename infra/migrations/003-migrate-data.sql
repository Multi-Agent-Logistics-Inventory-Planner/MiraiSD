-- ============================================================================
-- Data Migration Script
-- Migrates data from old location/inventory tables to unified schema
-- Run this AFTER 002-create-unified-schema.sql
-- ============================================================================

BEGIN;

-- ============================================================================
-- Step 1: Create Main Site
-- ============================================================================
INSERT INTO sites (id, name, code, country)
VALUES (gen_random_uuid(), 'Main Store', 'MAIN', 'USA')
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- Step 2: Create Storage Locations (Categories)
-- These replace the 11 separate location type tables
-- ============================================================================
INSERT INTO storage_locations (site_id, name, code, code_prefix, has_display, is_display_only, display_order)
VALUES
    -- Storage only (no display tracking)
    ((SELECT id FROM sites WHERE code = 'MAIN'), 'Box Bins', 'BOX_BINS', 'B', false, false, 1),
    ((SELECT id FROM sites WHERE code = 'MAIN'), 'Racks', 'RACKS', 'R', false, false, 2),
    ((SELECT id FROM sites WHERE code = 'MAIN'), 'Cabinets', 'CABINETS', 'C', false, false, 3),
    ((SELECT id FROM sites WHERE code = 'MAIN'), 'Windows', 'WINDOWS', 'W', false, false, 4),

    -- Storage + Display (quantities AND display tracking)
    ((SELECT id FROM sites WHERE code = 'MAIN'), 'Single Claw', 'SINGLE_CLAW', 'S', true, false, 5),
    ((SELECT id FROM sites WHERE code = 'MAIN'), 'Double Claw', 'DOUBLE_CLAW', 'D', true, false, 6),
    ((SELECT id FROM sites WHERE code = 'MAIN'), 'Four Corner', 'FOUR_CORNER', 'M', true, false, 7),
    ((SELECT id FROM sites WHERE code = 'MAIN'), 'Pusher', 'PUSHER', 'P', true, false, 8),

    -- Display only (no inventory, only display tracking)
    ((SELECT id FROM sites WHERE code = 'MAIN'), 'Gachapon', 'GACHAPON', 'G', true, true, 9),
    ((SELECT id FROM sites WHERE code = 'MAIN'), 'Keychain', 'KEYCHAIN', 'K', true, true, 10),

    -- Not Assigned (explicit location for unassigned inventory)
    ((SELECT id FROM sites WHERE code = 'MAIN'), 'Not Assigned', 'NOT_ASSIGNED', 'NA', false, false, 99)
ON CONFLICT (site_id, code) DO NOTHING;

-- ============================================================================
-- Step 3: Migrate Location Units (Preserve UUIDs)
-- Preserving UUIDs ensures stock_movements and machine_display FKs still work
-- ============================================================================

-- Box Bins
INSERT INTO locations (id, storage_location_id, location_code, created_at, updated_at)
SELECT
    id,
    (SELECT id FROM storage_locations WHERE code = 'BOX_BINS'),
    box_bin_code,
    created_at,
    COALESCE(updated_at, created_at)
FROM box_bins
ON CONFLICT (storage_location_id, location_code) DO NOTHING;

-- Racks
INSERT INTO locations (id, storage_location_id, location_code, created_at, updated_at)
SELECT
    id,
    (SELECT id FROM storage_locations WHERE code = 'RACKS'),
    rack_code,
    created_at,
    COALESCE(updated_at, created_at)
FROM racks
ON CONFLICT (storage_location_id, location_code) DO NOTHING;

-- Cabinets
INSERT INTO locations (id, storage_location_id, location_code, created_at, updated_at)
SELECT
    id,
    (SELECT id FROM storage_locations WHERE code = 'CABINETS'),
    cabinet_code,
    created_at,
    COALESCE(updated_at, created_at)
FROM cabinets
ON CONFLICT (storage_location_id, location_code) DO NOTHING;

-- Windows
INSERT INTO locations (id, storage_location_id, location_code, created_at, updated_at)
SELECT
    id,
    (SELECT id FROM storage_locations WHERE code = 'WINDOWS'),
    window_code,
    created_at,
    COALESCE(updated_at, created_at)
FROM windows
ON CONFLICT (storage_location_id, location_code) DO NOTHING;

-- Single Claw Machines
INSERT INTO locations (id, storage_location_id, location_code, created_at, updated_at)
SELECT
    id,
    (SELECT id FROM storage_locations WHERE code = 'SINGLE_CLAW'),
    single_claw_machine_code,
    created_at,
    COALESCE(updated_at, created_at)
FROM single_claw_machines
ON CONFLICT (storage_location_id, location_code) DO NOTHING;

-- Double Claw Machines
INSERT INTO locations (id, storage_location_id, location_code, created_at, updated_at)
SELECT
    id,
    (SELECT id FROM storage_locations WHERE code = 'DOUBLE_CLAW'),
    double_claw_machine_code,
    created_at,
    COALESCE(updated_at, created_at)
FROM double_claw_machines
ON CONFLICT (storage_location_id, location_code) DO NOTHING;

-- Four Corner Machines
INSERT INTO locations (id, storage_location_id, location_code, created_at, updated_at)
SELECT
    id,
    (SELECT id FROM storage_locations WHERE code = 'FOUR_CORNER'),
    four_corner_machine_code,
    created_at,
    COALESCE(updated_at, created_at)
FROM four_corner_machines
ON CONFLICT (storage_location_id, location_code) DO NOTHING;

-- Pusher Machines
INSERT INTO locations (id, storage_location_id, location_code, created_at, updated_at)
SELECT
    id,
    (SELECT id FROM storage_locations WHERE code = 'PUSHER'),
    pusher_machine_code,
    created_at,
    COALESCE(updated_at, created_at)
FROM pusher_machines
ON CONFLICT (storage_location_id, location_code) DO NOTHING;

-- Gachapons (display-only, but still need location entries)
INSERT INTO locations (id, storage_location_id, location_code, created_at, updated_at)
SELECT
    id,
    (SELECT id FROM storage_locations WHERE code = 'GACHAPON'),
    gachapon_code,
    created_at,
    COALESCE(updated_at, created_at)
FROM gachapons
ON CONFLICT (storage_location_id, location_code) DO NOTHING;

-- Keychain Machines (display-only, but still need location entries)
INSERT INTO locations (id, storage_location_id, location_code, created_at, updated_at)
SELECT
    id,
    (SELECT id FROM storage_locations WHERE code = 'KEYCHAIN'),
    keychain_machine_code,
    created_at,
    COALESCE(updated_at, created_at)
FROM keychain_machines
ON CONFLICT (storage_location_id, location_code) DO NOTHING;

-- Not Assigned location unit (one per site for unassigned inventory)
INSERT INTO locations (id, storage_location_id, location_code, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    (SELECT id FROM storage_locations WHERE code = 'NOT_ASSIGNED'),
    'NA',
    NOW(),
    NOW()
)
ON CONFLICT (storage_location_id, location_code) DO NOTHING;

-- ============================================================================
-- Step 4: Migrate Inventory Data (9 tables → 1)
-- Note: site_id is set by trigger based on location_id
-- All inventory has a location_id (NOT_ASSIGNED is an explicit location)
-- ============================================================================

-- Box Bin Inventory
INSERT INTO location_inventory (location_id, site_id, product_id, quantity, created_at, updated_at)
SELECT
    box_bin_id,
    (SELECT id FROM sites WHERE code = 'MAIN'),
    item_id,
    quantity,
    created_at,
    COALESCE(updated_at, created_at)
FROM box_bin_inventory
ON CONFLICT DO NOTHING;

-- Rack Inventory
INSERT INTO location_inventory (location_id, site_id, product_id, quantity, created_at, updated_at)
SELECT
    rack_id,
    (SELECT id FROM sites WHERE code = 'MAIN'),
    item_id,
    quantity,
    created_at,
    COALESCE(updated_at, created_at)
FROM rack_inventory
ON CONFLICT DO NOTHING;

-- Cabinet Inventory
INSERT INTO location_inventory (location_id, site_id, product_id, quantity, created_at, updated_at)
SELECT
    cabinet_id,
    (SELECT id FROM sites WHERE code = 'MAIN'),
    item_id,
    quantity,
    created_at,
    COALESCE(updated_at, created_at)
FROM cabinet_inventory
ON CONFLICT DO NOTHING;

-- Window Inventory
INSERT INTO location_inventory (location_id, site_id, product_id, quantity, created_at, updated_at)
SELECT
    window_id,
    (SELECT id FROM sites WHERE code = 'MAIN'),
    item_id,
    quantity,
    created_at,
    COALESCE(updated_at, created_at)
FROM window_inventory
ON CONFLICT DO NOTHING;

-- Single Claw Machine Inventory
INSERT INTO location_inventory (location_id, site_id, product_id, quantity, created_at, updated_at)
SELECT
    single_claw_machine_id,
    (SELECT id FROM sites WHERE code = 'MAIN'),
    item_id,
    quantity,
    created_at,
    COALESCE(updated_at, created_at)
FROM single_claw_machine_inventory
ON CONFLICT DO NOTHING;

-- Double Claw Machine Inventory
INSERT INTO location_inventory (location_id, site_id, product_id, quantity, created_at, updated_at)
SELECT
    double_claw_machine_id,
    (SELECT id FROM sites WHERE code = 'MAIN'),
    item_id,
    quantity,
    created_at,
    COALESCE(updated_at, created_at)
FROM double_claw_machine_inventory
ON CONFLICT DO NOTHING;

-- Four Corner Machine Inventory
INSERT INTO location_inventory (location_id, site_id, product_id, quantity, created_at, updated_at)
SELECT
    four_corner_machine_id,
    (SELECT id FROM sites WHERE code = 'MAIN'),
    item_id,
    quantity,
    created_at,
    COALESCE(updated_at, created_at)
FROM four_corner_machine_inventory
ON CONFLICT DO NOTHING;

-- Pusher Machine Inventory
INSERT INTO location_inventory (location_id, site_id, product_id, quantity, created_at, updated_at)
SELECT
    pusher_machine_id,
    (SELECT id FROM sites WHERE code = 'MAIN'),
    item_id,
    quantity,
    created_at,
    COALESCE(updated_at, created_at)
FROM pusher_machine_inventory
ON CONFLICT DO NOTHING;

-- Not Assigned Inventory (now uses explicit NOT_ASSIGNED location)
INSERT INTO location_inventory (location_id, site_id, product_id, quantity, created_at, updated_at)
SELECT
    (SELECT id FROM locations WHERE location_code = 'NA' AND storage_location_id = (SELECT id FROM storage_locations WHERE code = 'NOT_ASSIGNED')),
    (SELECT id FROM sites WHERE code = 'MAIN'),
    item_id,
    quantity,
    created_at,
    COALESCE(updated_at, created_at)
FROM not_assigned_inventory
ON CONFLICT DO NOTHING;

-- ============================================================================
-- Step 5: Verify Migration Counts
-- These should be printed/logged for validation
-- ============================================================================

-- Location counts
SELECT 'MIGRATION_RESULT' as type, 'locations_migrated' as metric, COUNT(*) as value FROM locations;

-- Inventory counts
SELECT 'MIGRATION_RESULT' as type, 'inventory_rows_migrated' as metric, COUNT(*) as value FROM location_inventory;

-- Total quantity migrated
SELECT 'MIGRATION_RESULT' as type, 'total_quantity_migrated' as metric, SUM(quantity) as value FROM location_inventory;

COMMIT;
