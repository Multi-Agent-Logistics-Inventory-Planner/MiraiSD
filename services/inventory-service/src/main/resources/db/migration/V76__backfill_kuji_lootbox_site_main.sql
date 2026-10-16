-- Idempotent: safe to re-run before V78/V79 if old code inserted NULL-site rows in between.
-- MAIN is the fallback only because the org was single-site until now; new writers must not default to it.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sites WHERE code = 'MAIN') THEN
        RAISE EXCEPTION 'kuji/lootbox site_id backfill requires a MAIN site row to already exist';
    END IF;
END $$;

UPDATE kuji_boxes kb
SET site_id = sl.site_id
FROM locations l
JOIN storage_locations sl ON sl.id = l.storage_location_id
WHERE l.id = kb.location_id
  AND kb.site_id IS NULL;

UPDATE kuji_boxes
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;

UPDATE kuji_box_tiers kbt
SET site_id = kb.site_id
FROM kuji_boxes kb
WHERE kb.id = kbt.box_id
  AND kbt.site_id IS NULL;

-- This column was client-writable, so dangling values are reset too.
UPDATE lootboxes
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL
   OR site_id NOT IN (SELECT id FROM sites);

UPDATE lootbox_tiers lt
SET site_id = lb.site_id
FROM lootboxes lb
WHERE lb.id = lt.lootbox_id
  AND lt.site_id IS NULL;

UPDATE lootbox_prizes lp
SET site_id = lt.site_id
FROM lootbox_tiers lt
WHERE lt.id = lp.tier_id
  AND lp.site_id IS NULL;

-- lootbox_id is nullable, so unresolved plays fall back to MAIN below.
UPDATE lootbox_plays plp
SET site_id = lb.site_id
FROM lootboxes lb
WHERE lb.id = plp.lootbox_id
  AND plp.site_id IS NULL;

UPDATE lootbox_plays
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;

-- Users belong to many sites, so there is no parent to inherit from.
UPDATE coin_adjustments
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;

UPDATE review_daily_counts
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;
