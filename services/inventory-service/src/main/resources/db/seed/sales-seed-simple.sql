-- Simple Sales Seed Data
-- Use this if you want more control or the PL/pgSQL script doesn't work
-- Replace {PRODUCT_ID} with actual product UUIDs from your database

-- First, find your product IDs:
-- SELECT id, sku, name, unit_cost FROM products LIMIT 5;

-- Example: Insert sales for a specific product across different dates
-- Copy and modify this block for each product you want to seed

-- January sales (recent month - more activity)
INSERT INTO stock_movements (location_type, item_id, quantity_change, reason, at, metadata)
SELECT
    'BOX_BIN',
    p.id,
    -(1 + floor(random() * 4)::int),
    'SALE',
    NOW() - ((floor(random() * 30))::int || ' days')::interval + ((floor(random() * 12))::int || ' hours')::interval,
    '{"source": "seed"}'::jsonb
FROM products p
CROSS JOIN generate_series(1, 30) -- 30 sales per product this month
WHERE p.is_active = true
LIMIT 200;

-- Last 2-3 months
INSERT INTO stock_movements (location_type, item_id, quantity_change, reason, at, metadata)
SELECT
    'BOX_BIN',
    p.id,
    -(1 + floor(random() * 3)::int),
    'SALE',
    NOW() - ((30 + floor(random() * 60))::int || ' days')::interval,
    '{"source": "seed"}'::jsonb
FROM products p
CROSS JOIN generate_series(1, 25)
WHERE p.is_active = true
LIMIT 300;

-- 4-6 months ago
INSERT INTO stock_movements (location_type, item_id, quantity_change, reason, at, metadata)
SELECT
    'BOX_BIN',
    p.id,
    -(1 + floor(random() * 3)::int),
    'SALE',
    NOW() - ((90 + floor(random() * 90))::int || ' days')::interval,
    '{"source": "seed"}'::jsonb
FROM products p
CROSS JOIN generate_series(1, 20)
WHERE p.is_active = true
LIMIT 250;

-- 7-12 months ago
INSERT INTO stock_movements (location_type, item_id, quantity_change, reason, at, metadata)
SELECT
    'BOX_BIN',
    p.id,
    -(1 + floor(random() * 2)::int),
    'SALE',
    NOW() - ((180 + floor(random() * 180))::int || ' days')::interval,
    '{"source": "seed"}'::jsonb
FROM products p
CROSS JOIN generate_series(1, 15)
WHERE p.is_active = true
LIMIT 200;

-- Verify results
SELECT
    TO_CHAR(DATE_TRUNC('month', at), 'YYYY-MM') as month,
    COUNT(*) as num_sales,
    SUM(ABS(quantity_change)) as total_units
FROM stock_movements
WHERE reason = 'SALE'
GROUP BY DATE_TRUNC('month', at)
ORDER BY month DESC;
