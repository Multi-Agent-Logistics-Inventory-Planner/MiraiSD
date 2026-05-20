ALTER TABLE shipments DROP CONSTRAINT IF EXISTS shipments_shipment_number_key;

CREATE INDEX IF NOT EXISTS idx_shipments_tracking_id
    ON shipments(tracking_id) WHERE tracking_id IS NOT NULL;
