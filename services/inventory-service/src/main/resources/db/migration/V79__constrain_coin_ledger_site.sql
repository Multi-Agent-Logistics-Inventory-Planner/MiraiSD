-- Human-gated post-deploy constraint (.specs/phase-7-kuji-lootbox T-2/T-3), run after V78 because
-- lootbox_plays' composite FK references lootboxes(id, site_id). Do not include V79 in an
-- automatic Flyway target until: V78 is deployed and verified; the kuji/lootbox module's
-- site-aware coin-ledger writers are deployed; the messaging-service redeploy (site-aware
-- review_daily_counts writer, .specs/phase-7-kuji-lootbox log.md T-2) has shipped; and an
-- immediate production check confirms none of these columns have NULLs.
--
-- The 2026-09-27 pre-flight check found zero NULL lootbox_plays.lootbox_id rows, so that FK can
-- become NOT NULL alongside site_id.

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

-- Site-scoped idempotency replaces the plain (user_id, idempotency_key) uniqueness: a replayed
-- key at a different site must not be able to return another site's play.
DROP INDEX ux_lootbox_plays_user_idem;

ALTER TABLE coin_adjustments
    ADD CONSTRAINT fk_coin_adjustments_site FOREIGN KEY (site_id) REFERENCES sites(id);
ALTER TABLE coin_adjustments
    ALTER COLUMN site_id SET NOT NULL;

ALTER TABLE review_daily_counts
    ADD CONSTRAINT fk_review_daily_counts_site FOREIGN KEY (site_id) REFERENCES sites(id);
ALTER TABLE review_daily_counts
    ALTER COLUMN site_id SET NOT NULL;

-- Replace the legacy (user_id, date) uniqueness the messaging-service upsert used to depend on
-- (confirmed constraint name via the 2026-09-27 pre-flight check) with the site-scoped one V77
-- already built concurrently.
ALTER TABLE review_daily_counts DROP CONSTRAINT uq_review_daily_counts_user_date;
