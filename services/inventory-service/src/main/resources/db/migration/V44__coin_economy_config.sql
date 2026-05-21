-- Admin-editable coin economy config.
--
-- Singleton row (id = 1) holds the current review-to-coin rate. Java reads
-- on demand (rarely); the Python messaging-service reads it once at the start
-- of each 6 AM review-fetch batch so a mid-batch rate change cannot split a
-- single batch across two rates.
--
-- review_daily_counts gains coins_awarded so the balance formula sums what
-- was actually granted (immutable per row), not review_count * current_rate.
-- This means rate changes apply to FUTURE batches only; past balances stay frozen.

CREATE TABLE coin_economy_config (
    id                 INT         PRIMARY KEY CHECK (id = 1),
    review_coin_rate   INTEGER     NOT NULL DEFAULT 1 CHECK (review_coin_rate >= 0),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by_user_id UUID        REFERENCES users(id)
);

INSERT INTO coin_economy_config (id, review_coin_rate) VALUES (1, 1);

ALTER TABLE review_daily_counts
    ADD COLUMN coins_awarded INTEGER NOT NULL DEFAULT 0;

-- Backfill: every existing review counted for 1 coin.
UPDATE review_daily_counts SET coins_awarded = review_count;
