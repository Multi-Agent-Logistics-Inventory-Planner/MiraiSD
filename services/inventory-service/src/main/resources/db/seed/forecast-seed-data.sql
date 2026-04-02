-- Forecast Predictions Seed Data
-- This script generates sample forecast data for all active products
-- covering all risk bands for dashboard and analytics visualization
--
-- Risk Bands:
--   Critical:    <= 3 days to stockout
--   Warning:     4-7 days to stockout
--   Healthy:     8-30 days to stockout
--   Safe:        31-60 days to stockout
--   Overstocked: > 60 days to stockout
--
-- Run this against your Supabase/PostgreSQL database after products exist

-- First, clear any existing forecast predictions (optional - uncomment if needed)
-- DELETE FROM forecast_predictions WHERE TRUE;

DO $$
DECLARE
    product_record RECORD;
    product_count INTEGER := 0;
    total_products INTEGER;
    risk_band TEXT;
    days_stockout DECIMAL;
    daily_delta DECIMAL;
    reorder_qty INTEGER;
    order_date DATE;
    confidence_val DECIMAL;
    horizon INTEGER := 30;  -- Standard 30-day forecast horizon
BEGIN
    -- Count total products for distribution
    SELECT COUNT(*) INTO total_products FROM products WHERE is_active = true;

    IF total_products = 0 THEN
        RAISE NOTICE 'No active products found. Please seed products first.';
        RETURN;
    END IF;

    RAISE NOTICE 'Generating forecasts for % active products...', total_products;

    -- Loop through each active product
    FOR product_record IN
        SELECT
            p.id,
            p.sku,
            p.name,
            p.unit_cost,
            p.reorder_point,
            p.target_stock_level,
            p.lead_time_days,
            COALESCE(inv.total_qty, 0) as current_stock
        FROM products p
        LEFT JOIN (
            -- Aggregate inventory across all locations (unified schema)
            SELECT product_id, SUM(quantity) as total_qty
            FROM location_inventory
            GROUP BY product_id
        ) inv ON inv.product_id = p.id
        WHERE p.is_active = true
        ORDER BY p.created_at
    LOOP
        product_count := product_count + 1;

        -- Distribute products across risk bands based on position
        -- This ensures visual representation of all categories
        CASE
            WHEN product_count % 10 = 1 THEN risk_band := 'critical_urgent';  -- 0-1 days (pulse)
            WHEN product_count % 10 = 2 THEN risk_band := 'critical';         -- 2-3 days
            WHEN product_count % 10 IN (3, 4) THEN risk_band := 'warning';    -- 4-7 days
            WHEN product_count % 10 IN (5, 6, 7) THEN risk_band := 'healthy'; -- 8-30 days
            WHEN product_count % 10 IN (8, 9) THEN risk_band := 'safe';       -- 31-60 days
            ELSE risk_band := 'overstocked';                                   -- > 60 days
        END CASE;

        -- Set days_to_stockout based on risk band with some variance
        CASE risk_band
            WHEN 'critical_urgent' THEN
                days_stockout := 0.5 + (random() * 1);  -- 0.5-1.5 days
            WHEN 'critical' THEN
                days_stockout := 2 + (random() * 1);    -- 2-3 days
            WHEN 'warning' THEN
                days_stockout := 4 + (random() * 3);    -- 4-7 days
            WHEN 'healthy' THEN
                days_stockout := 8 + (random() * 22);   -- 8-30 days
            WHEN 'safe' THEN
                days_stockout := 31 + (random() * 29);  -- 31-60 days
            ELSE
                days_stockout := 61 + (random() * 120); -- 61-180 days
        END CASE;

        -- Calculate daily delta (negative = consumption/sales)
        -- Higher consumption for critical items, lower for overstocked
        IF product_record.current_stock > 0 AND days_stockout > 0 THEN
            daily_delta := -1 * (product_record.current_stock / days_stockout);
        ELSE
            daily_delta := -1 * (2 + random() * 8);  -- Default -2 to -10 per day
        END IF;

        -- Calculate suggested reorder quantity
        -- Based on lead time and consumption rate
        reorder_qty := GREATEST(
            10,
            CEIL(ABS(daily_delta) * COALESCE(product_record.lead_time_days, 14) * 1.5)
        )::INTEGER;

        -- Adjust reorder qty for overstocked items (shouldn't reorder)
        IF risk_band = 'overstocked' THEN
            reorder_qty := 0;
        END IF;

        -- Calculate suggested order date based on lead time
        IF days_stockout <= COALESCE(product_record.lead_time_days, 14) THEN
            order_date := CURRENT_DATE;  -- Order immediately
        ELSE
            order_date := CURRENT_DATE + (days_stockout - COALESCE(product_record.lead_time_days, 14))::INTEGER;
        END IF;

        -- Set confidence based on data quality (simulated)
        -- Critical items have higher confidence (more sales data)
        CASE risk_band
            WHEN 'critical_urgent' THEN confidence_val := 0.85 + (random() * 0.10);
            WHEN 'critical' THEN confidence_val := 0.80 + (random() * 0.15);
            WHEN 'warning' THEN confidence_val := 0.75 + (random() * 0.15);
            WHEN 'healthy' THEN confidence_val := 0.70 + (random() * 0.15);
            WHEN 'safe' THEN confidence_val := 0.65 + (random() * 0.15);
            ELSE confidence_val := 0.50 + (random() * 0.20);
        END CASE;

        -- Insert or update forecast prediction
        INSERT INTO forecast_predictions (
            id,
            item_id,
            horizon_days,
            avg_daily_delta,
            days_to_stockout,
            suggested_reorder_qty,
            suggested_order_date,
            confidence,
            features,
            computed_at
        ) VALUES (
            gen_random_uuid(),
            product_record.id,
            horizon,
            ROUND(daily_delta::NUMERIC, 2),
            ROUND(days_stockout::NUMERIC, 1),
            reorder_qty,
            order_date,
            ROUND(confidence_val::NUMERIC, 2),
            jsonb_build_object(
                'ma7', ROUND((ABS(daily_delta) * (0.9 + random() * 0.2))::NUMERIC, 2),
                'ma14', ROUND((ABS(daily_delta) * (0.85 + random() * 0.3))::NUMERIC, 2),
                'std14', ROUND((ABS(daily_delta) * 0.3 * random())::NUMERIC, 2),
                'dow', EXTRACT(DOW FROM CURRENT_DATE),
                'is_weekend', EXTRACT(DOW FROM CURRENT_DATE) IN (0, 6),
                'risk_band', risk_band,
                'seed_generated', true
            ),
            NOW()
        )
        ON CONFLICT (item_id, computed_at)
        DO UPDATE SET
            horizon_days = EXCLUDED.horizon_days,
            avg_daily_delta = EXCLUDED.avg_daily_delta,
            days_to_stockout = EXCLUDED.days_to_stockout,
            suggested_reorder_qty = EXCLUDED.suggested_reorder_qty,
            suggested_order_date = EXCLUDED.suggested_order_date,
            confidence = EXCLUDED.confidence,
            features = EXCLUDED.features;

    END LOOP;

    RAISE NOTICE 'Forecast seed data generation complete! Generated % forecasts.', product_count;
END $$;

-- Verification queries

-- 1. Show distribution by risk band
SELECT
    CASE
        WHEN days_to_stockout <= 3 THEN 'Critical (<= 3d)'
        WHEN days_to_stockout <= 7 THEN 'Warning (4-7d)'
        WHEN days_to_stockout <= 30 THEN 'Healthy (8-30d)'
        WHEN days_to_stockout <= 60 THEN 'Safe (31-60d)'
        ELSE 'Overstocked (> 60d)'
    END as risk_band,
    COUNT(*) as item_count,
    ROUND(AVG(days_to_stockout)::NUMERIC, 1) as avg_days,
    ROUND(AVG(confidence)::NUMERIC, 2) as avg_confidence
FROM forecast_predictions
WHERE computed_at = (
    SELECT MAX(computed_at) FROM forecast_predictions
)
GROUP BY
    CASE
        WHEN days_to_stockout <= 3 THEN 'Critical (<= 3d)'
        WHEN days_to_stockout <= 7 THEN 'Warning (4-7d)'
        WHEN days_to_stockout <= 30 THEN 'Healthy (8-30d)'
        WHEN days_to_stockout <= 60 THEN 'Safe (31-60d)'
        ELSE 'Overstocked (> 60d)'
    END
ORDER BY
    CASE
        WHEN days_to_stockout <= 3 THEN 1
        WHEN days_to_stockout <= 7 THEN 2
        WHEN days_to_stockout <= 30 THEN 3
        WHEN days_to_stockout <= 60 THEN 4
        ELSE 5
    END;

-- 2. Show items that need action (< 7 days)
SELECT
    p.name,
    p.sku,
    fp.days_to_stockout,
    fp.suggested_reorder_qty,
    ROUND((fp.suggested_reorder_qty * COALESCE(p.unit_cost, 0))::NUMERIC, 2) as reorder_cost,
    fp.suggested_order_date,
    fp.confidence
FROM forecast_predictions fp
JOIN products p ON p.id = fp.item_id
WHERE fp.days_to_stockout <= 7
AND fp.computed_at = (SELECT MAX(computed_at) FROM forecast_predictions)
ORDER BY fp.days_to_stockout ASC;

-- 3. Summary statistics
SELECT
    COUNT(*) as total_forecasts,
    COUNT(*) FILTER (WHERE days_to_stockout <= 7) as at_risk_items,
    ROUND(AVG(confidence)::NUMERIC, 2) as avg_confidence,
    ROUND(AVG(ABS(avg_daily_delta))::NUMERIC, 2) as avg_daily_consumption
FROM forecast_predictions
WHERE computed_at = (SELECT MAX(computed_at) FROM forecast_predictions);
