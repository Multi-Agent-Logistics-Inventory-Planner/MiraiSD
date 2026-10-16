-- Nullable on purpose: V76 backfills existing rows, and a default would hide unresolved ones.
-- lootboxes already has site_id and is not touched here.
ALTER TABLE kuji_boxes ADD COLUMN site_id UUID;
ALTER TABLE kuji_box_tiers ADD COLUMN site_id UUID;
ALTER TABLE lootbox_tiers ADD COLUMN site_id UUID;
ALTER TABLE lootbox_prizes ADD COLUMN site_id UUID;
ALTER TABLE lootbox_plays ADD COLUMN site_id UUID;
ALTER TABLE coin_adjustments ADD COLUMN site_id UUID;
ALTER TABLE review_daily_counts ADD COLUMN site_id UUID;
