-- Audit Logs: Groups related stock movements into single audit actions
-- This enables the UI to show one row per user action instead of one row per item

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

-- 2. Create indexes for audit_logs
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_id ON audit_logs(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_reason ON audit_logs(reason);

-- 3. Add audit_log_id column to stock_movements if the table exists
-- (stock_movements is created by Hibernate, so it may not exist on fresh init)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'stock_movements') THEN
        -- Add the column if it doesn't exist
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_name = 'stock_movements' AND column_name = 'audit_log_id') THEN
            ALTER TABLE stock_movements ADD COLUMN audit_log_id UUID REFERENCES audit_logs(id);
            CREATE INDEX IF NOT EXISTS idx_stock_movements_audit_log_id ON stock_movements(audit_log_id);
            RAISE NOTICE 'Added audit_log_id column to stock_movements';
        END IF;
    ELSE
        RAISE NOTICE 'stock_movements table does not exist yet; Hibernate will create it with audit_log_id column';
    END IF;
END $$;

-- Add comments for documentation
COMMENT ON TABLE audit_logs IS 'Groups related stock movements into single audit actions for UI display';
COMMENT ON COLUMN audit_logs.item_count IS 'Number of distinct items affected in this action';
COMMENT ON COLUMN audit_logs.total_quantity_moved IS 'Sum of absolute quantity changes across all items';
