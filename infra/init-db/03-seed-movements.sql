-- Seed Stock Movements for Testing
-- Generates 30 days of historical sales and shipment data

DO $$
DECLARE
    product_record RECORD;
    sale_date TIMESTAMPTZ;
    sale_qty INTEGER;
    day_offset INTEGER;
    hour_offset INTEGER;
    daily_sales INTEGER;
    is_weekend BOOLEAN;
    base_demand INTEGER;
    i INTEGER;
BEGIN
    -- Loop through each product with its expected demand pattern
    FOR product_record IN
        SELECT
            id,
            sku,
            CASE
                WHEN sku = 'PLUSH-001' THEN 15      -- High: ~15/day
                WHEN sku = 'KEYCHAIN-001' THEN 20  -- High: ~20/day
                WHEN sku = 'SNACK-001' THEN 25     -- High: ~25/day
                WHEN sku = 'PLUSH-002' THEN 5      -- Medium: ~5/day
                WHEN sku = 'CANDY-001' THEN 8      -- Medium: ~8/day
                WHEN sku = 'FIGURE-001' THEN 3     -- Medium: ~3/day
                WHEN sku = 'KEYCHAIN-002' THEN 1   -- Low: ~1/day
                WHEN sku = 'STICKER-001' THEN 2    -- Low: ~2/day
                ELSE 5
            END AS base_demand
        FROM products
        WHERE is_active = true
    LOOP
        base_demand := product_record.base_demand;

        -- Generate 30 days of data
        FOR day_offset IN 0..29 LOOP
            sale_date := (NOW() - (day_offset || ' days')::interval)::date;

            -- Check if weekend (0=Sunday, 6=Saturday in PostgreSQL)
            is_weekend := EXTRACT(DOW FROM sale_date) IN (0, 6);

            -- Reduce sales on weekends by ~30%
            IF is_weekend THEN
                daily_sales := GREATEST(1, (base_demand * 0.7)::integer);
            ELSE
                daily_sales := base_demand;
            END IF;

            -- Add some randomness (+/- 30%)
            daily_sales := GREATEST(1, daily_sales + floor(random() * (base_demand * 0.6) - base_demand * 0.3)::integer);

            -- Generate individual sale transactions spread throughout the day
            FOR i IN 1..daily_sales LOOP
                -- Random hour between 10am and 10pm (business hours)
                hour_offset := 10 + floor(random() * 12)::integer;

                -- Random quantity per transaction (1-3 for most, 1-5 for snacks)
                IF product_record.sku IN ('SNACK-001', 'CANDY-001') THEN
                    sale_qty := 1 + floor(random() * 5)::integer;
                ELSE
                    sale_qty := 1 + floor(random() * 3)::integer;
                END IF;

                -- Insert the sale (negative quantity)
                INSERT INTO stock_movements (
                    location_type,
                    item_id,
                    quantity_change,
                    reason,
                    at,
                    metadata
                ) VALUES (
                    'BOX_BIN',
                    product_record.id,
                    -sale_qty,
                    'SALE',
                    sale_date + (hour_offset || ' hours')::interval + (floor(random() * 60) || ' minutes')::interval,
                    '{"source": "seed_data"}'::jsonb
                );
            END LOOP;
        END LOOP;

        -- Add shipment events (restocking) - one every 7-14 days
        FOR day_offset IN 1..4 LOOP
            -- Shipment arrives every ~7 days
            sale_date := (NOW() - ((day_offset * 7) || ' days')::interval)::date + '9 hours'::interval;

            -- Shipment quantity = ~14 days of demand
            sale_qty := base_demand * 14;

            INSERT INTO stock_movements (
                location_type,
                item_id,
                quantity_change,
                reason,
                at,
                metadata
            ) VALUES (
                'BOX_BIN',
                product_record.id,
                sale_qty,
                'RESTOCK',
                sale_date,
                ('{"source": "seed_data", "shipment_id": "' || gen_random_uuid() || '"}')::jsonb
            );
        END LOOP;
    END LOOP;

    RAISE NOTICE 'Stock movements seed data generation complete!';
END $$;

-- Verify the data was inserted
SELECT
    p.sku,
    COUNT(*) FILTER (WHERE sm.reason = 'SALE') as num_sales,
    COUNT(*) FILTER (WHERE sm.reason = 'RESTOCK') as num_restocks,
    SUM(ABS(sm.quantity_change)) FILTER (WHERE sm.reason = 'SALE') as total_units_sold
FROM stock_movements sm
JOIN products p ON sm.item_id = p.id
GROUP BY p.sku
ORDER BY p.sku;
