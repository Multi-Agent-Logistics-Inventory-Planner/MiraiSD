-- V21: Add DELIVERY_FAILED shipment status, new stock movement reasons for shipment auditing,
-- and SHIPMENT_DELIVERY_FAILED notification type

-- 1. Add DELIVERY_FAILED to shipments.status
-- This distinguishes carrier failures from intentional cancellations
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'shipments'
        AND constraint_name = 'shipments_status_check'
    ) THEN
        ALTER TABLE shipments DROP CONSTRAINT shipments_status_check;
    END IF;

    ALTER TABLE shipments ADD CONSTRAINT shipments_status_check
        CHECK (status IN (
            'PENDING',
            'IN_TRANSIT',
            'DELIVERED',
            'CANCELLED',
            'DELIVERY_FAILED'
        ));
END $$;

-- 2. Add new stock movement reasons for shipment auditing
-- SHIPMENT_PARTIAL_RECEIPT: When a shipment is partially received
-- SHIPMENT_EDITED: When a shipment is edited
-- SHIPMENT_DELETED: When a shipment is deleted
ALTER TABLE stock_movements
    DROP CONSTRAINT IF EXISTS stock_movements_reason_check;

ALTER TABLE stock_movements
    ADD CONSTRAINT stock_movements_reason_check CHECK (reason IN (
        'INITIAL_STOCK',
        'RESTOCK',
        'SHIPMENT_RECEIPT',
        'SHIPMENT_RECEIPT_REVERSED',
        'SHIPMENT_PARTIAL_RECEIPT',
        'SHIPMENT_EDITED',
        'SHIPMENT_DELETED',
        'SALE',
        'DAMAGE',
        'ADJUSTMENT',
        'RETURN',
        'TRANSFER',
        'REMOVED',
        'DISPLAY_SET',
        'DISPLAY_REMOVED',
        'DISPLAY_SWAP'
    ));

-- 3. Add SHIPMENT_DELIVERY_FAILED notification type
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
            'DISPLAY_STALE'
        ));
END $$;
