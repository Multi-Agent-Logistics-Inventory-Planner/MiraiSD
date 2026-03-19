-- V15: Add SHIPMENT_COMPLETED and SHIPMENT_DAMAGED to notifications type check constraint
-- These types are used by ShipmentService and EasyPostWebhookService but were missing from the constraint

DO $$
BEGIN
    -- Check if the constraint exists before trying to modify it
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'notifications'
        AND constraint_name = 'notifications_type_check'
    ) THEN
        -- Drop the old constraint
        ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
    END IF;

    -- Add updated constraint with all notification types
    ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
        CHECK (type IN (
            'LOW_STOCK',
            'OUT_OF_STOCK',
            'REORDER_SUGGESTION',
            'EXPIRY_WARNING',
            'SYSTEM_ALERT',
            'SHIPMENT_COMPLETED',
            'SHIPMENT_DAMAGED',
            'DISPLAY_STALE'
        ));
END $$;
