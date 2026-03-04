-- Migration: Add audit_logs table and link to stock_movements
-- This enables grouping multiple stock movements into a single audit action

-- 1. Create the audit_logs table
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID,
    actor_name VARCHAR(255),
    reason VARCHAR(50) NOT NULL,
    primary_from_location_id UUID,
    primary_to_location_id UUID,
    primary_from_location_code VARCHAR(50),
    primary_to_location_code VARCHAR(50),
    item_count INTEGER NOT NULL DEFAULT 1,
    total_quantity_moved INTEGER NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Add audit_log_id column to stock_movements (nullable for backward compatibility)
ALTER TABLE stock_movements
ADD COLUMN IF NOT EXISTS audit_log_id UUID REFERENCES audit_logs(id);

-- 3. Create indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_id ON audit_logs(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_reason ON audit_logs(reason);
CREATE INDEX IF NOT EXISTS idx_stock_movements_audit_log_id ON stock_movements(audit_log_id);

-- 4. Backfill: Create audit_log entries for existing stock_movements
-- Groups movements by actor_id, reason, and timestamp (within 1 second window)
-- This preserves historical data while migrating to the new structure

DO $$
DECLARE
    batch_record RECORD;
    new_audit_log_id UUID;
    movement_count INTEGER;
    total_qty INTEGER;
    first_from_location UUID;
    first_to_location UUID;
    first_from_code VARCHAR(50);
    first_to_code VARCHAR(50);
BEGIN
    -- Find distinct batches based on actor, reason, and approximate time
    FOR batch_record IN
        SELECT DISTINCT
            actor_id,
            reason,
            DATE_TRUNC('second', at) as batch_time
        FROM stock_movements
        WHERE audit_log_id IS NULL
        ORDER BY batch_time DESC
    LOOP
        -- Get aggregated data for this batch
        SELECT
            COUNT(*),
            COALESCE(SUM(ABS(quantity_change)), 0),
            MIN(from_location_id),
            MIN(to_location_id)
        INTO movement_count, total_qty, first_from_location, first_to_location
        FROM stock_movements
        WHERE audit_log_id IS NULL
          AND actor_id IS NOT DISTINCT FROM batch_record.actor_id
          AND reason = batch_record.reason
          AND DATE_TRUNC('second', at) = batch_record.batch_time;

        -- Create the audit log entry
        INSERT INTO audit_logs (
            actor_id,
            reason,
            primary_from_location_id,
            primary_to_location_id,
            item_count,
            total_quantity_moved,
            created_at
        ) VALUES (
            batch_record.actor_id,
            batch_record.reason,
            first_from_location,
            first_to_location,
            movement_count,
            total_qty,
            batch_record.batch_time
        ) RETURNING id INTO new_audit_log_id;

        -- Update stock movements to reference this audit log
        UPDATE stock_movements
        SET audit_log_id = new_audit_log_id
        WHERE audit_log_id IS NULL
          AND actor_id IS NOT DISTINCT FROM batch_record.actor_id
          AND reason = batch_record.reason
          AND DATE_TRUNC('second', at) = batch_record.batch_time;
    END LOOP;
END $$;

-- 5. Update audit_logs with actor names (batch update)
UPDATE audit_logs al
SET actor_name = CONCAT(u.first_name, ' ', u.last_name)
FROM users u
WHERE al.actor_id = u.id AND al.actor_name IS NULL;

-- 6. Optional: Add comment for documentation
COMMENT ON TABLE audit_logs IS 'Groups related stock movements into single audit actions for UI display';
COMMENT ON COLUMN audit_logs.item_count IS 'Number of distinct items affected in this action';
COMMENT ON COLUMN audit_logs.total_quantity_moved IS 'Sum of absolute quantity changes across all items';
COMMENT ON COLUMN stock_movements.audit_log_id IS 'Reference to the parent audit log entry';
