-- Allow admins to permanently delete tiers + prizes.
--
-- lootbox_plays already snapshots prize/tier/crate display fields at spin-time
-- (prize_name_snapshot, prize_tier_name_snapshot, etc. — see V42), so play history
-- survives a prize deletion without the live FK. We relax the FKs so:
--   - deleting a prize  -> plays.prize_id becomes NULL (history preserved via snapshots)
--   - deleting a tier   -> its prizes are CASCADE-deleted (an orphan prize can't be
--                          rolled or shown), which in turn nulls plays.prize_id
--
-- Soft-delete remains available via the `active` flag on tiers/prizes; this only
-- enables the "purge" path the admin UI was missing.
--
-- The DROP CONSTRAINT loops query pg_constraint by relationship (not by name) so
-- this migration is idempotent across any environment that may have accumulated
-- duplicate FKs from Hibernate's ddl-auto=update before the entities were locked
-- down with @ForeignKey(ConstraintMode.NO_CONSTRAINT).

DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'lootbox_prizes'::regclass
          AND contype = 'f'
          AND confrelid = 'lootbox_tiers'::regclass
    LOOP
        EXECUTE format('ALTER TABLE lootbox_prizes DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE lootbox_prizes
    ADD CONSTRAINT lootbox_prizes_tier_id_fkey
    FOREIGN KEY (tier_id) REFERENCES lootbox_tiers(id) ON DELETE CASCADE;

DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'lootbox_plays'::regclass
          AND contype = 'f'
          AND confrelid = 'lootbox_prizes'::regclass
    LOOP
        EXECUTE format('ALTER TABLE lootbox_plays DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE lootbox_plays ALTER COLUMN prize_id DROP NOT NULL;

ALTER TABLE lootbox_plays
    ADD CONSTRAINT lootbox_plays_prize_id_fkey
    FOREIGN KEY (prize_id) REFERENCES lootbox_prizes(id) ON DELETE SET NULL;
