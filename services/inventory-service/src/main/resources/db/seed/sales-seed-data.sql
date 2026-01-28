-- Sales Seed Data for Analytics Testing
-- This script generates sample sales data spread across the last 12 months
-- Run this against your Supabase/PostgreSQL database

-- First, let's see what products exist (run this to get product IDs)
-- SELECT id, sku, name, unit_cost FROM products LIMIT 10;

-- Generate sales data using existing products
-- This uses a CTE to create date series and random sales

DO $$
DECLARE
    product_record RECORD;
    sale_date TIMESTAMP WITH TIME ZONE;
    sale_qty INTEGER;
    day_offset INTEGER;
    sales_per_product INTEGER;
BEGIN
    -- Loop through each product
    FOR product_record IN
        SELECT id, unit_cost
        FROM products
        WHERE is_active = true
        LIMIT 20  -- Limit to first 20 products
    LOOP
        -- Generate 50-150 sales per product over the last 12 months
        sales_per_product := 50 + floor(random() * 100)::int;

        FOR i IN 1..sales_per_product LOOP
            -- Random day in the last 365 days
            day_offset := floor(random() * 365)::int;
            sale_date := NOW() - (day_offset || ' days')::interval
                       + (floor(random() * 12) || ' hours')::interval;

            -- Random quantity between 1 and 5
            sale_qty := 1 + floor(random() * 5)::int;

            -- Insert the sale (negative quantity for sales)
            INSERT INTO stock_movements (
                location_type,
                item_id,
                from_location_id,
                to_location_id,
                previous_quantity,
                current_quantity,
                quantity_change,
                reason,
                actor_id,
                at,
                metadata
            ) VALUES (
                'BOX_BIN',  -- Default location type
                product_record.id,
                NULL,
                NULL,
                sale_qty,  -- previous
                0,         -- current (simplified)
                -sale_qty, -- negative for sales
                'SALE',
                NULL,      -- No specific actor
                sale_date,
                '{"source": "seed_data"}'::jsonb
            );
        END LOOP;
    END LOOP;

    RAISE NOTICE 'Sales seed data generation complete!';
END $$;

-- Verify the data was inserted
SELECT
    DATE_TRUNC('month', at) as month,
    COUNT(*) as num_sales,
    SUM(ABS(quantity_change)) as total_units
FROM stock_movements
WHERE reason = 'SALE'
GROUP BY DATE_TRUNC('month', at)
ORDER BY month DESC;
