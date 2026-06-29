-- Fix FK constraints that block user deletion.
--
-- lootbox_plays.user_id and coin_adjustments.user_id belong to the deleted
-- user — cascade delete them.
--
-- coin_adjustments.granted_by_user_id and lootbox_plays.redeemed_by_user_id
-- reference the *acting* admin, not the owner of the record — set to NULL so
-- other users' history is preserved.

ALTER TABLE lootbox_plays
    DROP CONSTRAINT IF EXISTS lootbox_plays_user_id_fkey,
    ADD CONSTRAINT lootbox_plays_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE lootbox_plays
    DROP CONSTRAINT IF EXISTS lootbox_plays_redeemed_by_user_id_fkey,
    ADD CONSTRAINT lootbox_plays_redeemed_by_user_id_fkey
        FOREIGN KEY (redeemed_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE coin_adjustments
    DROP CONSTRAINT IF EXISTS coin_adjustments_user_id_fkey,
    ADD CONSTRAINT coin_adjustments_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE coin_adjustments
    ALTER COLUMN granted_by_user_id DROP NOT NULL,
    DROP CONSTRAINT IF EXISTS coin_adjustments_granted_by_user_id_fkey,
    ADD CONSTRAINT coin_adjustments_granted_by_user_id_fkey
        FOREIGN KEY (granted_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE coin_economy_config
    DROP CONSTRAINT IF EXISTS coin_economy_config_updated_by_user_id_fkey,
    ADD CONSTRAINT coin_economy_config_updated_by_user_id_fkey
        FOREIGN KEY (updated_by_user_id) REFERENCES users(id) ON DELETE SET NULL;
