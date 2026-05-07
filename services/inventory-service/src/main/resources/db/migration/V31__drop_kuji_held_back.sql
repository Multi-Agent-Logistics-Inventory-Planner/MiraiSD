-- V31: Drop held_back_count from kuji_box_tiers.
--
-- Pivot: custom kuji prizes are now always-linked products (auto-created at
-- open-box time when needed), so the parallel held-back ledger collapses into
-- real LocationInventory at the box location. The KUJI_SLIP_ADJUSTMENT enum
-- value and add_slip backfill from V29 stay — addSlip still uses that reason.

ALTER TABLE kuji_box_tiers
    DROP CONSTRAINT IF EXISTS chk_kuji_held_back_nonneg;

ALTER TABLE kuji_box_tiers
    DROP COLUMN IF EXISTS held_back_count;
