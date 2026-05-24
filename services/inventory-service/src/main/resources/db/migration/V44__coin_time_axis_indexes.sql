-- Time-axis indexes for the admin Coins tab: cross-user aggregates and the
-- recent-activity feed both need ORDER BY <time> DESC LIMIT N over the whole
-- table. The V42 indexes are all (user_id, ...) so a global time scan today
-- requires a full table sort.
--
-- CONCURRENTLY because these tables are in daily production use; the .conf
-- sibling disables Flyway's wrapping transaction (CREATE INDEX CONCURRENTLY
-- cannot run inside one).

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_coin_adjustments_created_at
  ON coin_adjustments(created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_review_daily_counts_date
  ON review_daily_counts(date DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_lootbox_plays_played_at
  ON lootbox_plays(played_at DESC);
