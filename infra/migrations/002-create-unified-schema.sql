-- ============================================================================
-- Create Unified Schema
-- This migration creates the new unified location tables
-- Run AFTER 001-validate-pre-migration.sql passes
--
-- NOTE: The full schema DDL is in infra/init-db/20-unified-locations.sql
-- This file executes that schema for existing databases.
-- ============================================================================

-- For PostgreSQL, you can use \i to include the schema file:
-- \i ../init-db/20-unified-locations.sql

-- Or copy the contents of 20-unified-locations.sql here for standalone execution.
-- The schema uses IF NOT EXISTS, so it's safe to run multiple times.

-- ============================================================================
-- Quick verification that tables were created
-- ============================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'sites') THEN
        RAISE EXCEPTION 'Table sites was not created. Run infra/init-db/20-unified-locations.sql first.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'storage_locations') THEN
        RAISE EXCEPTION 'Table storage_locations was not created. Run infra/init-db/20-unified-locations.sql first.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'locations') THEN
        RAISE EXCEPTION 'Table locations was not created. Run infra/init-db/20-unified-locations.sql first.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'location_inventory') THEN
        RAISE EXCEPTION 'Table location_inventory was not created. Run infra/init-db/20-unified-locations.sql first.';
    END IF;

    RAISE NOTICE 'All unified location tables exist. Ready for data migration.';
END $$;
