-- Allow SHIPMENT_RECEIPT (and any other enum values) in stock_movements.reason.
-- Hibernate 6 creates a check constraint from the enum at table creation time;
-- when new enum values are added in Java, the DB constraint must be updated.

ALTER TABLE stock_movements
    DROP CONSTRAINT IF EXISTS stock_movements_reason_check;

ALTER TABLE stock_movements
    ADD CONSTRAINT stock_movements_reason_check CHECK (reason IN (
        'INITIAL_STOCK',
        'RESTOCK',
        'SHIPMENT_RECEIPT',
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
