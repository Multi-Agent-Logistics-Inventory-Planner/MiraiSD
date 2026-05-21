-- Lootbox ("Pito Coin") feature
--
-- Multi-crate from day one: a Lootbox parent groups its own tiers + prizes, has its own
-- cost, and an optional active window (NULL = unbounded). Multiple crates can be open
-- simultaneously; each is rolled independently (per-crate sum-to-100, enforced in the
-- service layer).
--
-- Balance is derived per user from the ledger:
--   total_earned  = SUM(review_daily_counts.review_count) + SUM(coin_adjustments.delta)
--   total_spent   = SUM(lootbox_plays.cost)
--   total_expired = (only filled in once V43 coin-expiry ships; until then = 0)
--   balance       = MAX(0, total_earned - MAX(total_spent, total_expired))
-- Invariant: balance >= 0 (enforced in service layer on play + adjustment).

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

CREATE TABLE lootbox_prizes (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tier_id     UUID        NOT NULL REFERENCES lootbox_tiers(id),
    name        TEXT        NOT NULL,
    description TEXT,
    image_url   TEXT,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_lootbox_prizes_tier_active ON lootbox_prizes(tier_id) WHERE active;

-- Each row is one spin AND the prize won from it (1:1, since every play wins).
-- lootbox_id is nullable for forward/backward compatibility with any historical play
-- that may pre-date the parent table (none today, but cheap insurance).
CREATE TABLE lootbox_plays (
    id                         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                    UUID        NOT NULL REFERENCES users(id),
    lootbox_id                 UUID        REFERENCES lootboxes(id),
    lootbox_name_snapshot      TEXT,
    cost                       INTEGER     NOT NULL DEFAULT 1 CHECK (cost >= 0),
    prize_id                   UUID        NOT NULL REFERENCES lootbox_prizes(id),
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

CREATE TABLE coin_adjustments (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES users(id),
    delta              INTEGER     NOT NULL,
    reason             TEXT        NOT NULL CHECK (length(reason) > 0),
    granted_by_user_id UUID        NOT NULL REFERENCES users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_coin_adjustments_user ON coin_adjustments(user_id, created_at DESC);

-- Seed: one default crate ("Mirai Mystery Crate") with the original four tiers + prizes.
-- Probabilities sum to 100 within this crate.
DO $$
DECLARE
    crate_id     UUID;
    common_id    UUID;
    rare_id      UUID;
    epic_id      UUID;
    legendary_id UUID;
BEGIN
    INSERT INTO lootboxes (name, description, cost, active, sort_order)
        VALUES ('Mirai Mystery Crate', 'The original mystery crate.', 1, TRUE, 0)
        RETURNING id INTO crate_id;

    INSERT INTO lootbox_tiers (lootbox_id, name, probability_pct, display_color, sort_order)
        VALUES (crate_id, 'COMMON', 70.00, '#9CA3AF', 1) RETURNING id INTO common_id;
    INSERT INTO lootbox_tiers (lootbox_id, name, probability_pct, display_color, sort_order)
        VALUES (crate_id, 'RARE', 20.00, '#3B82F6', 2) RETURNING id INTO rare_id;
    INSERT INTO lootbox_tiers (lootbox_id, name, probability_pct, display_color, sort_order)
        VALUES (crate_id, 'EPIC', 8.00, '#A855F7', 3) RETURNING id INTO epic_id;
    INSERT INTO lootbox_tiers (lootbox_id, name, probability_pct, display_color, sort_order)
        VALUES (crate_id, 'LEGENDARY', 2.00, '#F59E0B', 4) RETURNING id INTO legendary_id;

    INSERT INTO lootbox_prizes (tier_id, name, description) VALUES
        (common_id,    'Pito Sticker',   'Classic sticker'),
        (rare_id,      'Coffee Voucher', '$5 coffee shop card'),
        (epic_id,      'Lunch on Pito',  'Free lunch up to $25'),
        (legendary_id, 'Day Off',        'A free PTO day');
END $$;
