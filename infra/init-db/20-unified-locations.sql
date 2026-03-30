-- ============================================================================
-- Unified Locations Schema
-- Replaces 11 separate location tables + 9 inventory tables with a unified
-- two-level hierarchy that supports dynamic storage location creation.
-- ============================================================================

-- 1. Sites (for multi-site support)
-- Currently single site (Main), but designed for future expansion
CREATE TABLE IF NOT EXISTS sites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) UNIQUE NOT NULL,
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100) DEFAULT 'USA',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Storage Locations (categories shown as tabs in UI)
-- Replaces: box_bins, racks, cabinets, windows, single_claw_machines,
--           double_claw_machines, four_corner_machines, pusher_machines,
--           gachapons, keychain_machines tables
CREATE TABLE IF NOT EXISTS storage_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,              -- "Box Bins", "Gachapons"
    code VARCHAR(50) NOT NULL,               -- "BOX_BINS", "GACHAPONS"
    code_prefix VARCHAR(10),                 -- "B", "G" (suggested prefix for units)
    icon VARCHAR(50),                        -- Icon identifier for UI
    has_display BOOLEAN NOT NULL DEFAULT FALSE,    -- Tracks display history via machine_display
    is_display_only BOOLEAN NOT NULL DEFAULT FALSE,-- No inventory, only display tracking
    display_order INTEGER NOT NULL DEFAULT 0,      -- Tab ordering in UI
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(site_id, code),
    -- Constraint: display_only requires has_display
    CONSTRAINT chk_display_only_requires_display CHECK (NOT is_display_only OR has_display)
);

-- 3. Locations (individual units within storage locations)
-- These are the actual physical spots: B1, B2, R1, G1, etc.
CREATE TABLE IF NOT EXISTS locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    storage_location_id UUID NOT NULL REFERENCES storage_locations(id) ON DELETE CASCADE,
    location_code VARCHAR(50) NOT NULL,      -- "B1", "B2", "G1"
    metadata JSONB DEFAULT '{}',             -- Extensible properties
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(storage_location_id, location_code)
);

-- Indexes for tables that don't depend on products
CREATE INDEX IF NOT EXISTS idx_storage_locations_site ON storage_locations(site_id);
CREATE INDEX IF NOT EXISTS idx_storage_locations_display_order ON storage_locations(site_id, display_order);
CREATE INDEX IF NOT EXISTS idx_locations_storage ON locations(storage_location_id);

-- Trigger to update updated_at timestamp (doesn't depend on products)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sites_updated_at ON sites;
CREATE TRIGGER trg_sites_updated_at
BEFORE UPDATE ON sites
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_storage_locations_updated_at ON storage_locations;
CREATE TRIGGER trg_storage_locations_updated_at
BEFORE UPDATE ON storage_locations
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_locations_updated_at ON locations;
CREATE TRIGGER trg_locations_updated_at
BEFORE UPDATE ON locations
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- Location Inventory (depends on products table)
-- Only create if products table exists (Hibernate may create it later)
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'products'
    ) THEN
        -- 4. Location Inventory (unified inventory table)
        -- Replaces: box_bin_inventory, rack_inventory, cabinet_inventory,
        --           window_inventory, single_claw_machine_inventory,
        --           double_claw_machine_inventory, four_corner_machine_inventory,
        --           pusher_machine_inventory, not_assigned_inventory
        -- NOTE: location_id is NOT NULL - all inventory has a location
        -- NOT_ASSIGNED is a real storage_location category with its own location unit
        CREATE TABLE IF NOT EXISTS location_inventory (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            location_id UUID NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
            site_id UUID NOT NULL REFERENCES sites(id),
            product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
            quantity INTEGER NOT NULL DEFAULT 0,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMPTZ DEFAULT NOW(),
            CONSTRAINT chk_quantity_non_negative CHECK (quantity >= 0)
        );

        -- Unique constraint: one row per (location, product)
        CREATE UNIQUE INDEX IF NOT EXISTS idx_location_inventory_unique
            ON location_inventory(location_id, product_id);

        -- Performance indexes
        CREATE INDEX IF NOT EXISTS idx_location_inventory_location ON location_inventory(location_id);
        CREATE INDEX IF NOT EXISTS idx_location_inventory_product ON location_inventory(product_id);
        CREATE INDEX IF NOT EXISTS idx_location_inventory_site ON location_inventory(site_id);

        -- Updated_at trigger for location_inventory
        DROP TRIGGER IF EXISTS trg_location_inventory_updated_at ON location_inventory;
        CREATE TRIGGER trg_location_inventory_updated_at
        BEFORE UPDATE ON location_inventory
        FOR EACH ROW
        EXECUTE FUNCTION update_updated_at_column();

        RAISE NOTICE 'location_inventory table and indexes created';
    ELSE
        RAISE NOTICE 'products table does not exist yet; skipping location_inventory creation (Hibernate will create it)';
    END IF;
END $$;

-- ============================================================================
-- Triggers for location_inventory (only if the table exists)
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'location_inventory'
    ) THEN
        -- Trigger to auto-sync site_id from location's storage_location
        CREATE OR REPLACE FUNCTION sync_inventory_site_id()
        RETURNS TRIGGER AS $func$
        BEGIN
            SELECT sl.site_id INTO NEW.site_id
            FROM locations l
            JOIN storage_locations sl ON l.storage_location_id = sl.id
            WHERE l.id = NEW.location_id;

            IF NEW.site_id IS NULL THEN
                RAISE EXCEPTION 'Location % not found', NEW.location_id;
            END IF;
            RETURN NEW;
        END;
        $func$ LANGUAGE plpgsql;

        DROP TRIGGER IF EXISTS trg_sync_inventory_site_id ON location_inventory;
        CREATE TRIGGER trg_sync_inventory_site_id
        BEFORE INSERT OR UPDATE ON location_inventory
        FOR EACH ROW
        EXECUTE FUNCTION sync_inventory_site_id();

        -- Trigger to prevent inventory on display-only locations
        CREATE OR REPLACE FUNCTION check_display_only_inventory()
        RETURNS TRIGGER AS $func$
        DECLARE
            v_is_display_only BOOLEAN;
            v_storage_location_name VARCHAR(100);
        BEGIN
            SELECT sl.is_display_only, sl.name INTO v_is_display_only, v_storage_location_name
            FROM locations l
            JOIN storage_locations sl ON l.storage_location_id = sl.id
            WHERE l.id = NEW.location_id;

            IF v_is_display_only THEN
                RAISE EXCEPTION 'Cannot add inventory to display-only location type: %', v_storage_location_name;
            END IF;
            RETURN NEW;
        END;
        $func$ LANGUAGE plpgsql;

        DROP TRIGGER IF EXISTS trg_check_display_only_inventory ON location_inventory;
        CREATE TRIGGER trg_check_display_only_inventory
        BEFORE INSERT OR UPDATE ON location_inventory
        FOR EACH ROW
        EXECUTE FUNCTION check_display_only_inventory();

        RAISE NOTICE 'location_inventory triggers created';
    END IF;
END $$;

-- ============================================================================
-- Helper Functions (only if location_inventory exists)
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'location_inventory'
    ) THEN
        -- Get total inventory for a product across all locations
        CREATE OR REPLACE FUNCTION get_total_inventory(p_product_id UUID)
        RETURNS INTEGER AS $func$
            SELECT COALESCE(SUM(quantity), 0)::INTEGER
            FROM location_inventory
            WHERE product_id = p_product_id;
        $func$ LANGUAGE sql STABLE;

        -- Get total inventory for a product at a specific site
        CREATE OR REPLACE FUNCTION get_site_inventory(p_site_id UUID, p_product_id UUID)
        RETURNS INTEGER AS $func$
            SELECT COALESCE(SUM(quantity), 0)::INTEGER
            FROM location_inventory
            WHERE site_id = p_site_id AND product_id = p_product_id;
        $func$ LANGUAGE sql STABLE;

        RAISE NOTICE 'location_inventory helper functions created';
    END IF;
END $$;

-- Get the storage location type for a given location (doesn't depend on products)
CREATE OR REPLACE FUNCTION get_location_type(p_location_id UUID)
RETURNS VARCHAR(50) AS $$
    SELECT sl.code
    FROM locations l
    JOIN storage_locations sl ON l.storage_location_id = sl.id
    WHERE l.id = p_location_id;
$$ LANGUAGE sql STABLE;

-- Get the full location code (e.g., "BOX_BINS:B1") (doesn't depend on products)
CREATE OR REPLACE FUNCTION get_full_location_code(p_location_id UUID)
RETURNS VARCHAR(100) AS $$
    SELECT sl.code || ':' || l.location_code
    FROM locations l
    JOIN storage_locations sl ON l.storage_location_id = sl.id
    WHERE l.id = p_location_id;
$$ LANGUAGE sql STABLE;
