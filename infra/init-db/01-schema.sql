-- Database Schema for Mirai Inventory System
-- This script creates all tables required for the forecasting service

-- Products table
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100),
    unit_cost DECIMAL(10,2) DEFAULT 0.00,
    is_active BOOLEAN DEFAULT true,
    lead_time_days INTEGER DEFAULT 7,
    reorder_point INTEGER DEFAULT 10,
    target_stock_level INTEGER DEFAULT 50,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Stock movements table (historical inventory changes)
CREATE TABLE IF NOT EXISTS stock_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_type VARCHAR(50),
    item_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    from_location_id UUID,
    to_location_id UUID,
    previous_quantity INTEGER DEFAULT 0,
    current_quantity INTEGER DEFAULT 0,
    quantity_change INTEGER NOT NULL,
    reason VARCHAR(50) NOT NULL,
    actor_id UUID,
    at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'::jsonb
);

-- Inventory tables (one per location type)
CREATE TABLE IF NOT EXISTS box_bin_inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    box_bin_id UUID,
    item_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS rack_inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rack_id UUID,
    item_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cabinet_inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cabinet_id UUID,
    item_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS single_claw_machine_inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    single_claw_machine_id UUID,
    item_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS double_claw_machine_inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    double_claw_machine_id UUID,
    item_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS keychain_machine_inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keychain_machine_id UUID,
    item_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Forecast predictions table (written by forecasting service)
CREATE TABLE IF NOT EXISTS forecast_predictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    computed_at TIMESTAMPTZ NOT NULL,
    horizon_days INTEGER,
    avg_daily_delta DOUBLE PRECISION,
    days_to_stockout DOUBLE PRECISION,
    suggested_reorder_qty INTEGER,
    suggested_order_date VARCHAR(20),
    confidence DOUBLE PRECISION,
    features JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(item_id, computed_at)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_stock_movements_at ON stock_movements(at);
CREATE INDEX IF NOT EXISTS idx_stock_movements_item_id ON stock_movements(item_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_reason ON stock_movements(reason);
CREATE INDEX IF NOT EXISTS idx_forecast_predictions_item_id ON forecast_predictions(item_id);
CREATE INDEX IF NOT EXISTS idx_forecast_predictions_computed_at ON forecast_predictions(computed_at);
CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku);
CREATE INDEX IF NOT EXISTS idx_products_is_active ON products(is_active);

-- Grant permissions (for dev environment)
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;
