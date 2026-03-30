-- ============================================================================
-- Phase B: Update Dependencies
-- Updates machine_display and stock_movements to use unified locations
-- Run this AFTER 003-migrate-data.sql and app code updates
-- ============================================================================

BEGIN;

-- ============================================================================
-- Step 1: Add location_id FK to machine_display
-- Keep location_type and machine_id for backward compatibility during transition
-- ============================================================================

-- Add the new column (nullable initially for backfill)
ALTER TABLE machine_display ADD COLUMN IF NOT EXISTS location_id UUID;

-- Backfill: Since UUIDs were preserved, machine_id = location_id
-- Only backfill rows where location_id is NULL to be idempotent
UPDATE machine_display
SET location_id = machine_id
WHERE location_id IS NULL;

-- Add FK constraint
ALTER TABLE machine_display
DROP CONSTRAINT IF EXISTS fk_machine_display_location;

ALTER TABLE machine_display
ADD CONSTRAINT fk_machine_display_location
FOREIGN KEY (location_id) REFERENCES locations(id);

-- Make location_id NOT NULL after backfill
ALTER TABLE machine_display ALTER COLUMN location_id SET NOT NULL;

-- Create index for new FK
CREATE INDEX IF NOT EXISTS idx_machine_display_location ON machine_display(location_id);

RAISE NOTICE 'machine_display.location_id column added and backfilled';

-- ============================================================================
-- Step 2: Add FK constraints to stock_movements
-- The to_location_id and from_location_id columns already exist
-- Just need to add FK constraints (UUIDs were preserved during migration)
-- ============================================================================

-- Add FK constraint for to_location_id (nullable - for additions without source)
ALTER TABLE stock_movements
DROP CONSTRAINT IF EXISTS fk_stock_movements_to_location;

ALTER TABLE stock_movements
ADD CONSTRAINT fk_stock_movements_to_location
FOREIGN KEY (to_location_id) REFERENCES locations(id);

-- Add FK constraint for from_location_id (nullable - for new stock)
ALTER TABLE stock_movements
DROP CONSTRAINT IF EXISTS fk_stock_movements_from_location;

ALTER TABLE stock_movements
ADD CONSTRAINT fk_stock_movements_from_location
FOREIGN KEY (from_location_id) REFERENCES locations(id);

-- Create indexes for the FKs
CREATE INDEX IF NOT EXISTS idx_stock_movements_to_location ON stock_movements(to_location_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_from_location ON stock_movements(from_location_id);

RAISE NOTICE 'stock_movements FK constraints added';

-- ============================================================================
-- Verification
-- ============================================================================

SELECT 'DEPENDENCY_UPDATE' as type, 'machine_display_with_location_id' as metric,
       COUNT(*) as value
FROM machine_display
WHERE location_id IS NOT NULL;

SELECT 'DEPENDENCY_UPDATE' as type, 'stock_movements_valid_to_location' as metric,
       COUNT(*) as value
FROM stock_movements sm
WHERE sm.to_location_id IS NULL OR EXISTS (SELECT 1 FROM locations l WHERE l.id = sm.to_location_id);

SELECT 'DEPENDENCY_UPDATE' as type, 'stock_movements_valid_from_location' as metric,
       COUNT(*) as value
FROM stock_movements sm
WHERE sm.from_location_id IS NULL OR EXISTS (SELECT 1 FROM locations l WHERE l.id = sm.from_location_id);

COMMIT;
