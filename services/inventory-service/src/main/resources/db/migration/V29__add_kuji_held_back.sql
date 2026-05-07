-- V29: Track held-back inventory on non-linked kuji tiers.
--
-- Non-linked kuji tiers (e.g. rare cards not stocked as products) need a way
-- to record physical units held in the box outside slips. Linked tiers keep
-- using LocationInventory; this column is only meaningful for non-linked.
--
-- Also introduces KUJI_SLIP_ADJUSTMENT as the reason code for stash, promote,
-- and add-slip events so they can be filtered out of the main activity feed
-- and surfaced in the per-box session log.

-- 1. held_back_count column on kuji_box_tiers
ALTER TABLE kuji_box_tiers
    ADD COLUMN IF NOT EXISTS held_back_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE kuji_box_tiers
    DROP CONSTRAINT IF EXISTS chk_kuji_held_back_nonneg;

ALTER TABLE kuji_box_tiers
    ADD CONSTRAINT chk_kuji_held_back_nonneg CHECK (held_back_count >= 0);

-- 2. Extend stock_movements.reason to include KUJI_SLIP_ADJUSTMENT
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
        'DISPLAY_SWAP',
        'KUJI_PRIZE_WON',
        'KUJI_DRAW_REVERSED',
        'KUJI_SLIP_ADJUSTMENT'
    ));

-- 3. Backfill existing add_slip rows so they stop leaking into the main feed.
UPDATE stock_movements
   SET reason = 'KUJI_SLIP_ADJUSTMENT'
 WHERE reason = 'ADJUSTMENT'
   AND metadata->>'action' = 'add_slip';
