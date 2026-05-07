-- V28: Make kuji audit logs queryable by the box's parent product id.
--
-- Kuji draws on linked tiers create stock_movements whose item is the linked
-- prize product, not the kuji box's own product. The activity feed in the
-- kuji box panel filters by the box product's id and so misses every linked
-- draw. We denormalize the box product id onto the audit log so the spec can
-- match it directly without scanning JSONB metadata.

ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS kuji_box_product_id UUID;

CREATE INDEX IF NOT EXISTS idx_audit_logs_kuji_box_product_id
    ON audit_logs(kuji_box_product_id) WHERE kuji_box_product_id IS NOT NULL;

-- Backfill: every existing kuji-related audit log carries kuji_box_id in the
-- metadata of one of its child stock_movements. Resolve that to the box's
-- product id via the kuji_boxes table.
UPDATE audit_logs al
SET kuji_box_product_id = kb.product_id
FROM stock_movements sm
JOIN kuji_boxes kb ON kb.id = (sm.metadata->>'kuji_box_id')::uuid
WHERE sm.audit_log_id = al.id
  AND sm.metadata ? 'kuji_box_id'
  AND al.kuji_box_product_id IS NULL;

COMMENT ON COLUMN audit_logs.kuji_box_product_id IS
    'When this audit log relates to a kuji box, the box parent product id. '
    'Lets activity-feed queries find linked-tier draws whose stock_movement.item '
    'is the prize product rather than the box product.';
