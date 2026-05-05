-- V25: Allow machine display notification types in the notifications.type CHECK constraint.
-- New types: DISPLAY_SET, DISPLAY_REMOVED, DISPLAY_SWAP, DISPLAY_RENEWED

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'notifications'
        AND constraint_name = 'notifications_type_check'
    ) THEN
        ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
    END IF;

    ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
        CHECK (type IN (
            'LOW_STOCK',
            'OUT_OF_STOCK',
            'REORDER_SUGGESTION',
            'EXPIRY_WARNING',
            'SYSTEM_ALERT',
            'SHIPMENT_COMPLETED',
            'SHIPMENT_DAMAGED',
            'SHIPMENT_DELIVERY_FAILED',
            'PACKAGE_ARRIVED',
            'DISPLAY_STALE',
            'DISPLAY_SET',
            'DISPLAY_REMOVED',
            'DISPLAY_SWAP',
            'DISPLAY_RENEWED'
        ));
END $$;
