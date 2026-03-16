-- Notifications Seed Data
-- This script generates sample notifications for dashboard visualization
-- covering all severity levels and notification types
--
-- Run this against your Supabase/PostgreSQL database after products exist

-- First, clear any existing seed notifications (optional - uncomment if needed)
-- DELETE FROM notifications WHERE metadata->>'seed_generated' = 'true';

DO $$
DECLARE
    product_record RECORD;
    notification_count INTEGER := 0;
    severity_val TEXT;
    notification_type TEXT;
    message_text TEXT;
    resolved_at_val TIMESTAMPTZ;
BEGIN
    RAISE NOTICE 'Generating notification seed data...';

    -- Loop through products to create various notifications
    FOR product_record IN
        SELECT
            p.id,
            p.sku,
            p.name,
            ROW_NUMBER() OVER (ORDER BY p.created_at) as row_num
        FROM products p
        WHERE p.is_active = true
        LIMIT 30  -- Create notifications for first 30 products
    LOOP
        -- Vary notification types based on row number
        CASE (product_record.row_num % 6)
            WHEN 0 THEN
                notification_type := 'LOW_STOCK';
                severity_val := 'WARNING';
                message_text := format('Low stock alert: %s (%s) is running low', product_record.name, product_record.sku);
            WHEN 1 THEN
                notification_type := 'OUT_OF_STOCK';
                severity_val := 'CRITICAL';
                message_text := format('Out of stock: %s (%s) has no inventory remaining', product_record.name, product_record.sku);
            WHEN 2 THEN
                notification_type := 'REORDER_SUGGESTION';
                severity_val := 'INFO';
                message_text := format('Reorder suggested: Consider ordering %s (%s) soon', product_record.name, product_record.sku);
            WHEN 3 THEN
                notification_type := 'LOW_STOCK';
                severity_val := 'CRITICAL';
                message_text := format('Critical stock level: %s (%s) needs immediate attention', product_record.name, product_record.sku);
            WHEN 4 THEN
                notification_type := 'SYSTEM_ALERT';
                severity_val := 'INFO';
                message_text := format('Stock level update: %s (%s) inventory has been adjusted', product_record.name, product_record.sku);
            ELSE
                notification_type := 'DISPLAY_STALE';
                severity_val := 'WARNING';
                message_text := format('Stale display: %s (%s) has been on display for over 45 days', product_record.name, product_record.sku);
        END CASE;

        -- Some notifications are resolved, others are active
        IF product_record.row_num % 3 = 0 THEN
            resolved_at_val := NOW() - (random() * interval '7 days');
        ELSE
            resolved_at_val := NULL;
        END IF;

        -- Insert notification
        INSERT INTO notifications (
            id,
            type,
            severity,
            message,
            item_id,
            via,
            metadata,
            created_at,
            delivered_at,
            resolved_at
        ) VALUES (
            gen_random_uuid(),
            notification_type,
            severity_val,
            message_text,
            product_record.id,
            ARRAY['DASHBOARD'],
            jsonb_build_object(
                'seed_generated', true,
                'product_sku', product_record.sku,
                'product_name', product_record.name
            ),
            NOW() - (random() * interval '14 days'),
            NOW() - (random() * interval '14 days'),
            resolved_at_val
        );

        notification_count := notification_count + 1;
    END LOOP;

    -- Add some system-level notifications (not tied to products)
    INSERT INTO notifications (id, type, severity, message, via, metadata, created_at, delivered_at)
    VALUES
        (gen_random_uuid(), 'SYSTEM_ALERT', 'INFO', 'Weekly inventory report generated successfully', ARRAY['DASHBOARD'], '{"seed_generated": true}'::jsonb, NOW() - interval '1 day', NOW() - interval '1 day'),
        (gen_random_uuid(), 'SYSTEM_ALERT', 'INFO', 'Forecasting service completed daily predictions', ARRAY['DASHBOARD'], '{"seed_generated": true}'::jsonb, NOW() - interval '2 hours', NOW() - interval '2 hours'),
        (gen_random_uuid(), 'SYSTEM_ALERT', 'WARNING', 'High volume of stock movements detected', ARRAY['DASHBOARD', 'SLACK'], '{"seed_generated": true}'::jsonb, NOW() - interval '3 days', NOW() - interval '3 days');

    notification_count := notification_count + 3;

    RAISE NOTICE 'Notification seed data generation complete! Generated % notifications.', notification_count;
END $$;

-- Verification queries

-- 1. Show notification counts by type and severity
SELECT
    type,
    severity,
    COUNT(*) as count,
    COUNT(*) FILTER (WHERE resolved_at IS NULL) as active,
    COUNT(*) FILTER (WHERE resolved_at IS NOT NULL) as resolved
FROM notifications
GROUP BY type, severity
ORDER BY
    CASE severity WHEN 'CRITICAL' THEN 1 WHEN 'WARNING' THEN 2 ELSE 3 END,
    type;

-- 2. Show active vs resolved summary
SELECT
    COUNT(*) FILTER (WHERE resolved_at IS NULL) as active_notifications,
    COUNT(*) FILTER (WHERE resolved_at IS NOT NULL) as resolved_notifications,
    COUNT(*) as total_notifications
FROM notifications;

-- 3. Recent notifications
SELECT
    type,
    severity,
    message,
    CASE WHEN resolved_at IS NOT NULL THEN 'Resolved' ELSE 'Active' END as status,
    created_at
FROM notifications
ORDER BY created_at DESC
LIMIT 10;
