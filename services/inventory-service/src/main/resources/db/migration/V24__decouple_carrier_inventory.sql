-- V24: Decouple carrier delivery from inventory receipt.
-- Splits ShipmentStatus into two independent fields:
--   * status (inventory): PENDING / RECEIVED / CANCELLED
--   * carrier_status (logistics): PRE_TRANSIT / IN_TRANSIT / DELIVERED / FAILED, nullable
--
-- Old enum values being removed: IN_TRANSIT, DELIVERED, DELIVERY_FAILED.
-- The webhook used to write DELIVERED into status even when no items were received,
-- leaving shipments in an unactionable state. Now the webhook writes carrier_status
-- only; status is changed only by user action (receive items, manual override).

-- 1. Add new columns
ALTER TABLE shipments
    ADD COLUMN IF NOT EXISTS carrier_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS carrier_delivered_at TIMESTAMP WITH TIME ZONE;

-- 2. Drop the existing status CHECK constraint so we can rewrite values
ALTER TABLE shipments DROP CONSTRAINT IF EXISTS shipments_status_check;

-- 3. Backfill existing rows
-- IN_TRANSIT -> carrier_status=IN_TRANSIT, status=PENDING
UPDATE shipments
SET carrier_status = 'IN_TRANSIT',
    status = 'PENDING'
WHERE status = 'IN_TRANSIT';

-- DELIVERY_FAILED -> carrier_status=FAILED, status=PENDING
UPDATE shipments
SET carrier_status = 'FAILED',
    status = 'PENDING'
WHERE status = 'DELIVERY_FAILED';

-- DELIVERED with no items received (the stuck-shipment bug) ->
--   status=PENDING, carrier_status=DELIVERED, carrier_delivered_at=updated_at
UPDATE shipments s
SET carrier_status = 'DELIVERED',
    carrier_delivered_at = s.updated_at,
    status = 'PENDING'
WHERE s.status = 'DELIVERED'
  AND NOT EXISTS (
      SELECT 1 FROM shipment_items si
      WHERE si.shipment_id = s.id
        AND (
            COALESCE(si.received_quantity, 0)
          + COALESCE(si.damaged_quantity, 0)
          + COALESCE(si.display_quantity, 0)
          + COALESCE(si.shop_quantity, 0)
        ) > 0
  );

-- DELIVERED with items satisfying fully-received math ->
--   status=RECEIVED, carrier_status=DELIVERED, carrier_delivered_at=updated_at
UPDATE shipments s
SET carrier_status = 'DELIVERED',
    carrier_delivered_at = s.updated_at,
    status = 'RECEIVED'
WHERE s.status = 'DELIVERED'
  AND NOT EXISTS (
      SELECT 1 FROM shipment_items si
      WHERE si.shipment_id = s.id
        AND (
            COALESCE(si.received_quantity, 0)
          + COALESCE(si.damaged_quantity, 0)
          + COALESCE(si.display_quantity, 0)
          + COALESCE(si.shop_quantity, 0)
        ) < COALESCE(si.ordered_quantity, 0)
  );

-- DELIVERED with partial receipts (data drift) ->
--   status=PENDING, carrier_status=DELIVERED. Frontend will derive PARTIAL display.
UPDATE shipments
SET carrier_status = 'DELIVERED',
    carrier_delivered_at = updated_at,
    status = 'PENDING'
WHERE status = 'DELIVERED';

-- 4. Add new CHECK constraints
ALTER TABLE shipments ADD CONSTRAINT shipments_status_check
    CHECK (status IN ('PENDING', 'RECEIVED', 'CANCELLED'));

ALTER TABLE shipments ADD CONSTRAINT shipments_carrier_status_check
    CHECK (carrier_status IS NULL OR carrier_status IN (
        'PRE_TRANSIT', 'IN_TRANSIT', 'DELIVERED', 'FAILED'
    ));

-- 5. Add SHIPMENT_STATUS_OVERRIDDEN to stock_movements_reason_check
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
        'SHIPMENT_STATUS_OVERRIDDEN',
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

-- 6. Add PACKAGE_ARRIVED to notifications_type_check
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
            'DISPLAY_STALE'
        ));
END $$;

-- 7. Index for filtering shipments by carrier-delivered awaiting-receipt state
CREATE INDEX IF NOT EXISTS idx_shipments_carrier_status ON shipments(carrier_status);
