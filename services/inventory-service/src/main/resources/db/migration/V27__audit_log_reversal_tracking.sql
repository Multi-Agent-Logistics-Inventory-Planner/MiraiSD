-- V27: Track reversal directly on audit_logs.
--
-- Adds reversed_at and reversed_by_log_id columns so the UI can show whether a
-- given audit log (e.g. a kuji draw) has been undone, without scanning child
-- stock_movements metadata. Backfills from existing KUJI_DRAW_REVERSED rows.

ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS reversed_at TIMESTAMPTZ;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS reversed_by_log_id UUID REFERENCES audit_logs(id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_reversed_at
    ON audit_logs(reversed_at) WHERE reversed_at IS NOT NULL;

-- Backfill: for every KUJI_DRAW_REVERSED audit log, mark the original draw it reversed.
-- The reversal log's child stock_movements carry metadata.reverses_audit_log_id pointing
-- back at the original draw's audit_log id.
UPDATE audit_logs orig
SET reversed_at        = rev.created_at,
    reversed_by_log_id = rev.id
FROM audit_logs rev
JOIN stock_movements sm ON sm.audit_log_id = rev.id
WHERE rev.reason = 'KUJI_DRAW_REVERSED'
  AND sm.metadata ? 'reverses_audit_log_id'
  AND orig.id = (sm.metadata->>'reverses_audit_log_id')::uuid
  AND orig.reversed_at IS NULL;

COMMENT ON COLUMN audit_logs.reversed_at IS 'When this audit log was reversed/undone (NULL if not reversed)';
COMMENT ON COLUMN audit_logs.reversed_by_log_id IS 'audit_logs.id of the reversal log that undid this entry';
