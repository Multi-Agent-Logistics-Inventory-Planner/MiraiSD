-- Run outside a transaction (paired .conf): CONCURRENTLY avoids locking live tables.
-- The (id, site_id) unique indexes are the targets of V78/V79's composite same-site foreign keys.
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

-- Replaces the (user_id, idempotency_key) uniqueness that V79 drops.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uniq_lootbox_plays_site_user_idem
  ON lootbox_plays(site_id, user_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_coin_adjustments_site_user_expires
  ON coin_adjustments(site_id, user_id, expires_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_coin_adjustments_site_created
  ON coin_adjustments(site_id, created_at DESC);

-- Replaces uq_review_daily_counts_user_date, which V79 drops.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uniq_review_daily_counts_site_user_date
  ON review_daily_counts(site_id, user_id, date);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_review_daily_counts_site_user_expires
  ON review_daily_counts(site_id, user_id, expires_at);
