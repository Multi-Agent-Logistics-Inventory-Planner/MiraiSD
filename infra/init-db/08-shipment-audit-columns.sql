-- Shipment Audit Columns: structured columns for shipment lifecycle events on audit_logs
-- Inventory rows leave these null; shipment events populate the relevant subset.

ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS shipment_id        UUID,
    ADD COLUMN IF NOT EXISTS shipment_number    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS field_changes      JSONB,
    ADD COLUMN IF NOT EXISTS previous_status    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS new_status         VARCHAR(50),
    ADD COLUMN IF NOT EXISTS override_reason    TEXT;

CREATE INDEX IF NOT EXISTS idx_audit_logs_shipment_id ON audit_logs(shipment_id);

COMMENT ON COLUMN audit_logs.shipment_id IS 'Parent shipment for shipment-related audits (no FK; audit log is denormalized history)';
COMMENT ON COLUMN audit_logs.shipment_number IS 'Denormalized shipment number for display when the shipment is gone';
COMMENT ON COLUMN audit_logs.field_changes IS 'JSON array of {field, from, to} for SHIPMENT_EDITED, or {field: deleted_items, to: [...]} for SHIPMENT_DELETED';
COMMENT ON COLUMN audit_logs.previous_status IS 'Status before override (SHIPMENT_STATUS_OVERRIDDEN only)';
COMMENT ON COLUMN audit_logs.new_status IS 'Status after override (SHIPMENT_STATUS_OVERRIDDEN only)';
COMMENT ON COLUMN audit_logs.override_reason IS 'User-supplied reason for SHIPMENT_STATUS_OVERRIDDEN';
