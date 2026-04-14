-- Add cost and profit columns to daily rollups for revenue/cost/profit tracking
-- Revenue = msrp x units_sold (retail price)
-- Cost = unit_cost x units_sold (cost of goods)
-- Profit = revenue - cost (gross margin)

ALTER TABLE analytics_daily_rollup
ADD COLUMN cost DECIMAL(12, 2) NOT NULL DEFAULT 0,
ADD COLUMN profit DECIMAL(12, 2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN analytics_daily_rollup.cost IS 'Total cost = unit_cost x units_sold';
COMMENT ON COLUMN analytics_daily_rollup.profit IS 'Gross profit = revenue - cost = (msrp - unit_cost) x units_sold';
