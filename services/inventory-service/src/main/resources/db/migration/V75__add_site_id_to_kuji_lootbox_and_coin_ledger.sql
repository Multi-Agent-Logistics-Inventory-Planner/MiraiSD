-- Expand step (docs/specs/multi-site-data-and-api.md section 5, .specs/phase-7-kuji-lootbox
-- AC-1/T-2). kuji_boxes/kuji_box_tiers/lootbox_tiers/lootbox_prizes/lootbox_plays/
-- coin_adjustments/review_daily_counts predate multi-site and carry no site dimension.
-- lootboxes already has a nullable, unenforced site_id column (see Lootbox.java's
-- "forward-compat hedge" comment) and is not touched here.
--
-- Adds site_id nullable, with no FK and no default on every other table, matching V59/V69/V72's
-- precedent: existing rows get a value from V76's backfill, not from a default that would hide
-- which rows are genuinely unresolved. New rows get site_id from application code (the kuji and
-- lootbox modules' own writers, and the messaging-service review writer for review_daily_counts)
-- starting with the release that ships alongside this migration.
--
-- Constraining these columns (NOT NULL + FK + composite same-site FKs + supporting indexes) is
-- deliberately deferred to later, separately released migrations once the code rollout has been
-- deployed and verified against production data - see V78/V79.
ALTER TABLE kuji_boxes ADD COLUMN site_id UUID;
ALTER TABLE kuji_box_tiers ADD COLUMN site_id UUID;
ALTER TABLE lootbox_tiers ADD COLUMN site_id UUID;
ALTER TABLE lootbox_prizes ADD COLUMN site_id UUID;
ALTER TABLE lootbox_plays ADD COLUMN site_id UUID;
ALTER TABLE coin_adjustments ADD COLUMN site_id UUID;
ALTER TABLE review_daily_counts ADD COLUMN site_id UUID;
