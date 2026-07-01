-- shipments.received_by and shipments.created_by reference the acting user,
-- not the record owner -- set to NULL on delete so shipment history is preserved.

ALTER TABLE shipments
    DROP CONSTRAINT IF EXISTS shipments_received_by_fkey,
    ADD CONSTRAINT shipments_received_by_fkey
        FOREIGN KEY (received_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE shipments
    DROP CONSTRAINT IF EXISTS shipments_created_by_fkey,
    ADD CONSTRAINT shipments_created_by_fkey
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;
