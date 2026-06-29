-- stock_movements.actor_id is audit data — drop the FK so users can be deleted
-- without losing movement history. The UUID stays on the row.
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS stock_movements_actor_id_fkey;

-- audit_logs.actor_id: actor_name is already denormalized on the row, so SET NULL
-- preserves the readable history while allowing user deletion.
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_actor_id_fkey;
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS fk_audit_logs_actor;

-- shipments.created_by: set to NULL on user deletion so the shipment record is kept.
ALTER TABLE shipments DROP CONSTRAINT IF EXISTS shipments_created_by_fkey;
ALTER TABLE shipments DROP CONSTRAINT IF EXISTS fk_shipments_created_by;
ALTER TABLE shipments ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE shipments ADD CONSTRAINT shipments_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;
