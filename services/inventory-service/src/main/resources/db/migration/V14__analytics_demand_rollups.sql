-- Analytics rollup tables migration
-- Creates pre-aggregated analytics tables for fast dashboard queries

-- Create daily sales rollup table for pre-aggregated per-product metrics
CREATE TABLE IF NOT EXISTS analytics_daily_rollup (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    rollup_date DATE NOT NULL,
    units_sold INTEGER NOT NULL DEFAULT 0,
    revenue DECIMAL(12,2) NOT NULL DEFAULT 0,
    restock_units INTEGER NOT NULL DEFAULT 0,
    damage_units INTEGER NOT NULL DEFAULT 0,
    movement_count INTEGER NOT NULL DEFAULT 0,
    demand_velocity_snapshot DECIMAL(10,4),
    forecast_confidence_snapshot DECIMAL(5,4),
    computed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_daily_rollup_item_date UNIQUE (item_id, rollup_date)
);

-- Create indexes for daily rollup queries
CREATE INDEX IF NOT EXISTS idx_daily_rollup_date ON analytics_daily_rollup(rollup_date);
CREATE INDEX IF NOT EXISTS idx_daily_rollup_item ON analytics_daily_rollup(item_id);

-- Create forecast daily snapshot table for historical demand analysis
CREATE TABLE IF NOT EXISTS analytics_forecast_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    snapshot_date DATE NOT NULL,
    mu_hat DECIMAL(10,4),
    sigma_d_hat DECIMAL(10,4),
    confidence DECIMAL(5,4),
    mape DECIMAL(5,4),
    days_to_stockout DECIMAL(10,2),
    current_stock INTEGER,
    dow_multipliers TEXT,
    computed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_forecast_snapshot_item_date UNIQUE (item_id, snapshot_date)
);

-- Create indexes for forecast snapshot queries
CREATE INDEX IF NOT EXISTS idx_forecast_snapshot_date ON analytics_forecast_snapshot(snapshot_date);
CREATE INDEX IF NOT EXISTS idx_forecast_snapshot_item ON analytics_forecast_snapshot(item_id);

-- Create category demand rollup table for pre-aggregated category metrics
CREATE TABLE IF NOT EXISTS analytics_category_demand_rollup (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    rollup_date DATE NOT NULL,
    total_demand_velocity DECIMAL(12,4) DEFAULT 0,
    avg_demand_velocity DECIMAL(10,4) DEFAULT 0,
    total_units_sold INTEGER DEFAULT 0,
    total_stock INTEGER DEFAULT 0,
    avg_stock_velocity DECIMAL(10,4) DEFAULT 0,
    items_at_risk INTEGER DEFAULT 0,
    items_critical INTEGER DEFAULT 0,
    items_healthy INTEGER DEFAULT 0,
    avg_confidence DECIMAL(5,4) DEFAULT 0,
    avg_volatility DECIMAL(10,4) DEFAULT 0,
    active_item_count INTEGER DEFAULT 0,
    computed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_category_demand_cat_date UNIQUE (category_id, rollup_date)
);

-- Create indexes for category demand rollup queries
CREATE INDEX IF NOT EXISTS idx_category_demand_date ON analytics_category_demand_rollup(rollup_date);
CREATE INDEX IF NOT EXISTS idx_category_demand_category ON analytics_category_demand_rollup(category_id);

-- Table comments
COMMENT ON TABLE analytics_daily_rollup IS 'Pre-aggregated daily sales data per product for fast analytics queries';
COMMENT ON TABLE analytics_forecast_snapshot IS 'Daily snapshot of forecast features for historical demand analysis';
COMMENT ON TABLE analytics_category_demand_rollup IS 'Pre-aggregated category-level demand metrics for fast insights';

-- Column comments for daily rollup
COMMENT ON COLUMN analytics_daily_rollup.demand_velocity_snapshot IS 'Demand velocity (mu_hat) at rollup time';
COMMENT ON COLUMN analytics_daily_rollup.forecast_confidence_snapshot IS 'Forecast confidence at rollup time';

-- Column comments for forecast snapshot
COMMENT ON COLUMN analytics_forecast_snapshot.mu_hat IS 'Expected daily demand from forecasting model';
COMMENT ON COLUMN analytics_forecast_snapshot.sigma_d_hat IS 'Demand standard deviation (volatility)';
COMMENT ON COLUMN analytics_forecast_snapshot.mape IS 'Mean Absolute Percentage Error of forecast';
COMMENT ON COLUMN analytics_forecast_snapshot.dow_multipliers IS 'JSON array of day-of-week demand multipliers';

-- Column comments for category rollup
COMMENT ON COLUMN analytics_category_demand_rollup.total_demand_velocity IS 'Sum of mu_hat for all items in category';
COMMENT ON COLUMN analytics_category_demand_rollup.avg_stock_velocity IS 'Average mu_hat/currentStock ratio';
