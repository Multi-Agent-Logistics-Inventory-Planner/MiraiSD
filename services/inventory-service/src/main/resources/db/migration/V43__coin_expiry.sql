-- Coin expiry: 90 days after the earning timestamp.
--
-- Generated columns mean we never write expires_at directly — Postgres computes it on
-- INSERT/UPDATE from the row's earn timestamp. No trigger, no app-side discipline.
-- Pre-launch rows have created_at well in the past, so their expires_at is already past
-- and they're naturally filtered out by the new balance formula (which is the launch-day
-- "hard wipe" we agreed on). Admin grants fresh starting balances via coin_adjustments.

ALTER TABLE coin_adjustments
    ADD COLUMN expires_at TIMESTAMPTZ
    GENERATED ALWAYS AS (created_at + INTERVAL '90 days') STORED;

ALTER TABLE review_daily_counts
    ADD COLUMN expires_at DATE
    GENERATED ALWAYS AS ((date + INTERVAL '90 days')::date) STORED;

CREATE INDEX ix_coin_adjustments_user_expires
    ON coin_adjustments(user_id, expires_at);
CREATE INDEX ix_review_daily_counts_user_expires
    ON review_daily_counts(user_id, expires_at);
