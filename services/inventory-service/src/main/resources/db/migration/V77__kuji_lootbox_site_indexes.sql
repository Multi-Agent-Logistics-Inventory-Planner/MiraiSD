-- Verify step (.specs/phase-7-kuji-lootbox T-2/T-3, matching V62/V71/V74's precedent exactly).
-- CONCURRENTLY + the paired .conf disabling the wrapping transaction, since these tables are in
-- daily production use and must not be locked.
--
-- The unique indexes below are not just read-path optimizations: V78/V79 reference them via
-- "ADD CONSTRAINT ... UNIQUE USING INDEX" (a fast, metadata-only operation) to become the target
-- of each child table's composite (parent_id, site_id) same-site foreign key, so the FK itself
-- can be added without a second full-table scan or lock. Creating them CONCURRENTLY here means
-- V78/V79 only do cheap constraint attachment, not concurrent index builds, during their gated
-- window.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kuji_boxes_site_status
  ON kuji_boxes(site_id, status);

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uniq_kuji_boxes_id_site
  ON kuji_boxes(id, site_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kuji_box_tiers_site_box
  ON kuji_box_tiers(site_id, box_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lootboxes_site_active_window
  ON lootboxes(site_id, active, starts_at, ends_at);

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uniq_lootboxes_id_site
  ON lootboxes(id, site_id);

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uniq_lootbox_tiers_id_site
  ON lootbox_tiers(id, site_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lootbox_tiers_site_lootbox
  ON lootbox_tiers(site_id, lootbox_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lootbox_prizes_site_tier
  ON lootbox_prizes(site_id, tier_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lootbox_plays_site_user_played
  ON lootbox_plays(site_id, user_id, played_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lootbox_plays_site_status_played
  ON lootbox_plays(site_id, status, played_at DESC);

-- Site-scoped idempotency: replaces the plain (user_id, idempotency_key) uniqueness once V79
-- drops it, so a replayed key at a different site never returns another site's play.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uniq_lootbox_plays_site_user_idem
  ON lootbox_plays(site_id, user_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_coin_adjustments_site_user_expires
  ON coin_adjustments(site_id, user_id, expires_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_coin_adjustments_site_created
  ON coin_adjustments(site_id, created_at DESC);

-- Site-scoped uniqueness for review_daily_counts, replacing uq_review_daily_counts_user_date
-- (V79 drops it) once a second site's reviews are ingested. NULL site rows cannot exist by the
-- time V79 runs (V76 backfills every row to MAIN), so no partial-index carve-out is needed here.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uniq_review_daily_counts_site_user_date
  ON review_daily_counts(site_id, user_id, date);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_review_daily_counts_site_user_expires
  ON review_daily_counts(site_id, user_id, expires_at);
