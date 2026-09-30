-- Backfill step (docs/specs/multi-site-data-and-api.md section 5, .specs/phase-7-kuji-lootbox
-- AC-1/T-2) for V75's new site_id columns, plus lootboxes' pre-existing but never-backfilled
-- column. Every update is idempotent (WHERE site_id IS NULL) so this migration can be re-run
-- manually if application code inserts a NULL-site row between this release and V78/V79's
-- constrain, matching V68's human-gated precedent.
--
-- Fails loudly rather than silently backfilling zero rows if the MAIN site row is somehow absent
-- at migration time, matching V53/V57/V60's precedent.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sites WHERE code = 'MAIN') THEN
        RAISE EXCEPTION 'kuji/lootbox site_id backfill requires a MAIN site row to already exist';
    END IF;
END $$;

-- kuji_boxes: authoritative source is the box's location -> storage_locations.site_id
-- (KujiBox.location, NOT NULL). Any box whose location cannot be resolved (should not exist,
-- location_id is NOT NULL and FK-enforced) falls back to MAIN below along with everything else.
UPDATE kuji_boxes kb
SET site_id = sl.site_id
FROM locations l
JOIN storage_locations sl ON sl.id = l.storage_location_id
WHERE l.id = kb.location_id
  AND kb.site_id IS NULL;

UPDATE kuji_boxes
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;

-- kuji_box_tiers: inherit the parent box's now-resolved site_id.
UPDATE kuji_box_tiers kbt
SET site_id = kb.site_id
FROM kuji_boxes kb
WHERE kb.id = kbt.box_id
  AND kbt.site_id IS NULL;

-- lootboxes: this column already existed (nullable, unenforced, client-writable through the
-- admin API - see .specs/phase-7-kuji-lootbox log.md T-2). The 2026-09-27 pre-flight check found
-- zero dangling or valid non-MAIN values across production, so treating every NULL or dangling
-- value as MAIN is safe. This is correct only because the org was single-site until this
-- migration; application code (T-3) must stop trusting the request body for this field and never
-- fall back to MAIN for a new crate.
UPDATE lootboxes
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL
   OR site_id NOT IN (SELECT id FROM sites);

-- lootbox_tiers / lootbox_prizes: inherit through the catalog chain.
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

-- lootbox_plays: inherit through the crate when it still resolves (lootbox_id is nullable -
-- see LootboxPlay.lootbox); a play whose crate no longer resolves falls back to MAIN below.
UPDATE lootbox_plays plp
SET site_id = lb.site_id
FROM lootboxes lb
WHERE lb.id = plp.lootbox_id
  AND plp.site_id IS NULL;

UPDATE lootbox_plays
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;

-- coin_adjustments / review_daily_counts: no site-derivable parent (user_site_memberships is
-- many-to-many, so there is no single "user's site" to backfill onto). Per the user-confirmed
-- coin-balance policy (site-of-action, per-site; see log.md), every historical row is attributed
-- to MAIN because the org was single-site until this migration. New rows capture the actual site
-- of action going forward and must never default to MAIN.
UPDATE coin_adjustments
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;

UPDATE review_daily_counts
SET site_id = (SELECT id FROM sites WHERE code = 'MAIN')
WHERE site_id IS NULL;
