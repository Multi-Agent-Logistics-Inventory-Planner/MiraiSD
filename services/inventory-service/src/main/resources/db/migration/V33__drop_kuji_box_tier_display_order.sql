-- V33: Drop display_order from kuji_box_tiers.
-- Tiers are now sorted by price DESC NULLS LAST, label ASC. The manual
-- display_order field (introduced in V26) is no longer surfaced or written.

ALTER TABLE kuji_box_tiers DROP COLUMN IF EXISTS display_order;
