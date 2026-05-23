-- Lootbox ("Pito Coin") feature — full launch schema.
--
-- Multi-crate from day one: a Lootbox parent groups its own tiers + prizes, has its own
-- cost, and an optional active window (NULL = unbounded). Multiple crates can be open
-- simultaneously; each is rolled independently (per-crate sum-to-100, enforced in the
-- service layer).
--
-- Balance is derived per user from the ledger:
--   total_earned  = SUM(review_daily_counts.coins_awarded) + SUM(coin_adjustments.delta)
--   total_spent   = SUM(lootbox_plays.cost)
--   total_expired = sum of earned rows whose expires_at < now()
--   balance       = MAX(0, total_earned - MAX(total_spent, total_expired))
-- Invariant: balance >= 0 (enforced in service layer on play + adjustment).
--
-- Hard-delete posture: lootbox_plays snapshots prize/tier/crate display fields at
-- spin-time, so play history survives a prize deletion. Deleting a prize NULLs
-- plays.prize_id; deleting a tier cascades to its prizes.

-- Parent crate. NULL window bounds mean "open indefinitely on that side".
-- site_id is a forward-compatibility hedge (no FK, no query usage, hidden from admin UI);
-- when real per-site scoping arrives it gets wired up without another migration.
CREATE TABLE lootboxes (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT         NOT NULL,
    description TEXT,
    image_url   TEXT,
    cost        INTEGER      NOT NULL DEFAULT 1 CHECK (cost >= 0),
    starts_at   TIMESTAMPTZ,
    ends_at     TIMESTAMPTZ,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    site_id     UUID,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX ix_lootboxes_active_window ON lootboxes(active, starts_at, ends_at);

CREATE TABLE lootbox_tiers (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    lootbox_id      UUID         NOT NULL REFERENCES lootboxes(id),
    name            TEXT         NOT NULL,
    probability_pct NUMERIC(5,2) NOT NULL CHECK (probability_pct >= 0 AND probability_pct <= 100),
    display_color   TEXT,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (lootbox_id, name)
);
CREATE INDEX ix_lootbox_tiers_lootbox ON lootbox_tiers(lootbox_id);
-- Sum-to-100 across active tiers per crate is enforced in the service layer
-- (not a CHECK, since CHECK cannot span rows).

-- quantity: NULL = unlimited; >= 0 once set. Decremented atomically by LootboxService
-- on every win via a conditional UPDATE that also flips active=false on the last copy.
CREATE TABLE lootbox_prizes (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tier_id     UUID        NOT NULL REFERENCES lootbox_tiers(id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    description TEXT,
    image_url   TEXT,
    quantity    INTEGER     CHECK (quantity IS NULL OR quantity >= 0),
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_lootbox_prizes_tier_active ON lootbox_prizes(tier_id) WHERE active;

-- Each row is one spin AND the prize won from it (1:1, since every play wins).
-- lootbox_id is nullable for forward/backward compatibility with any historical play
-- that may pre-date the parent table (none today, but cheap insurance).
-- prize_id is nullable + ON DELETE SET NULL so admins can purge prizes without
-- destroying play history (snapshot columns preserve the display).
CREATE TABLE lootbox_plays (
    id                         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                    UUID        NOT NULL REFERENCES users(id),
    lootbox_id                 UUID        REFERENCES lootboxes(id),
    lootbox_name_snapshot      TEXT,
    cost                       INTEGER     NOT NULL DEFAULT 1 CHECK (cost >= 0),
    prize_id                   UUID        REFERENCES lootbox_prizes(id) ON DELETE SET NULL,
    prize_name_snapshot        TEXT        NOT NULL,
    prize_description_snapshot TEXT,
    prize_image_url_snapshot   TEXT,
    prize_tier_name_snapshot   TEXT        NOT NULL,
    status                     TEXT        NOT NULL CHECK (status IN ('WON','REDEEMED')) DEFAULT 'WON',
    redeemed_at                TIMESTAMPTZ,
    redeemed_by_user_id        UUID        REFERENCES users(id),
    idempotency_key            TEXT,
    played_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- DB-level safety net for replays; nullable keys are not deduped (Postgres treats NULLs as distinct).
CREATE UNIQUE INDEX ux_lootbox_plays_user_idem ON lootbox_plays(user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_lootbox_plays_user_played   ON lootbox_plays(user_id, played_at DESC);
CREATE INDEX ix_lootbox_plays_user_status   ON lootbox_plays(user_id, status);
CREATE INDEX ix_lootbox_plays_status_played ON lootbox_plays(status, played_at DESC);
CREATE INDEX ix_lootbox_plays_lootbox       ON lootbox_plays(lootbox_id) WHERE lootbox_id IS NOT NULL;

-- Coin expiry: 90 days after the earning timestamp.
-- Defaulted at INSERT (created_at is never updated, so a column default is equivalent
-- to a generated column here). GENERATED ALWAYS can't be used because timestamptz +
-- day-interval is not immutable (depends on session TZ across DST).
CREATE TABLE coin_adjustments (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES users(id),
    delta              INTEGER     NOT NULL,
    reason             TEXT        NOT NULL CHECK (length(reason) > 0),
    granted_by_user_id UUID        NOT NULL REFERENCES users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '90 days')
);
CREATE INDEX ix_coin_adjustments_user         ON coin_adjustments(user_id, created_at DESC);
CREATE INDEX ix_coin_adjustments_user_expires ON coin_adjustments(user_id, expires_at);

-- Admin-editable coin economy config.
-- Singleton row (id = 1) holds the current review-to-coin rate. Java reads on demand
-- (rarely); the Python messaging-service reads it once at the start of each 6 AM
-- review-fetch batch so a mid-batch rate change cannot split a single batch across
-- two rates.
CREATE TABLE coin_economy_config (
    id                 INT         PRIMARY KEY CHECK (id = 1),
    review_coin_rate   INTEGER     NOT NULL DEFAULT 1 CHECK (review_coin_rate >= 0),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by_user_id UUID        REFERENCES users(id)
);
INSERT INTO coin_economy_config (id, review_coin_rate) VALUES (1, 1);

-- review_daily_counts gains coins_awarded so the balance formula sums what was
-- actually granted (immutable per row), not review_count * current_rate. Rate
-- changes apply to FUTURE batches only; past balances stay frozen.
-- expires_at mirrors the coin_adjustments 90-day rule.
ALTER TABLE review_daily_counts
    ADD COLUMN coins_awarded INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN expires_at    DATE
        GENERATED ALWAYS AS ((date + INTERVAL '90 days')::date) STORED;

-- Backfill: every pre-existing review counted for 1 coin under the launch rate.
UPDATE review_daily_counts SET coins_awarded = review_count;

CREATE INDEX ix_review_daily_counts_user_expires ON review_daily_counts(user_id, expires_at);
