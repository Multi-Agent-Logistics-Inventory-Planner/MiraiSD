-- Human-gated: apply after V78, once the messaging-service redeploy is live, and only when no
-- NULL site_id remains. Dropping the (user_id, date) uniqueness breaks the old review upsert.

ALTER TABLE lootbox_plays
    ADD CONSTRAINT fk_lootbox_plays_site FOREIGN KEY (site_id) REFERENCES sites(id);
ALTER TABLE lootbox_plays
    ALTER COLUMN site_id SET NOT NULL;
ALTER TABLE lootbox_plays
    ALTER COLUMN lootbox_id SET NOT NULL;

ALTER TABLE lootbox_plays DROP CONSTRAINT lootbox_plays_lootbox_id_fkey;
ALTER TABLE lootbox_plays
    ADD CONSTRAINT fk_lootbox_plays_lootbox_site
        FOREIGN KEY (lootbox_id, site_id) REFERENCES lootboxes(id, site_id);

-- Replaced by uniq_lootbox_plays_site_user_idem from V77.
DROP INDEX ux_lootbox_plays_user_idem;

ALTER TABLE coin_adjustments
    ADD CONSTRAINT fk_coin_adjustments_site FOREIGN KEY (site_id) REFERENCES sites(id);
ALTER TABLE coin_adjustments
    ALTER COLUMN site_id SET NOT NULL;

ALTER TABLE review_daily_counts
    ADD CONSTRAINT fk_review_daily_counts_site FOREIGN KEY (site_id) REFERENCES sites(id);
ALTER TABLE review_daily_counts
    ALTER COLUMN site_id SET NOT NULL;

-- Replaced by uniq_review_daily_counts_site_user_date from V77.
ALTER TABLE review_daily_counts DROP CONSTRAINT uq_review_daily_counts_user_date;
