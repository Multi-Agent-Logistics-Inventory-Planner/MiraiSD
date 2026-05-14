-- Split the single `count` column on kuji_box_tiers into two stored counters:
--   active_count   = slips currently winnable / in the box
--   inactive_count = slips held back from the pool (kuji-internal, not on a slip)
--
-- Both are kuji-internal source-of-truth. Regular inventory movements never read
-- or write them. See plan: i-want-to-plan-enchanted-hopcroft.md.

ALTER TABLE kuji_box_tiers RENAME COLUMN count TO active_count;

ALTER TABLE kuji_box_tiers
    ADD COLUMN inactive_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE kuji_box_tiers
    ADD CONSTRAINT kuji_tier_active_nonneg   CHECK (active_count   >= 0);

ALTER TABLE kuji_box_tiers
    ADD CONSTRAINT kuji_tier_inactive_nonneg CHECK (inactive_count >= 0);
