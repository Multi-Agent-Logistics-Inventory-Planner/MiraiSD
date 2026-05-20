-- Lootbox ("Pito Coin") feature
--
-- Balance is derived per user:
--   balance = SUM(review_daily_counts.review_count WHERE date >= LOOTBOX_LAUNCH_DATE)
--           + SUM(coin_adjustments.delta)
--           - SUM(lootbox_plays.cost)
-- Invariant: balance >= 0 (enforced in service layer on play + adjustment).

CREATE TABLE lootbox_tiers (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT         NOT NULL UNIQUE,
    probability_pct NUMERIC(5,2) NOT NULL CHECK (probability_pct >= 0 AND probability_pct <= 100),
    display_color   TEXT,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
-- Sum-to-100 across active tiers is enforced in the service layer (not a CHECK,
-- since a CHECK cannot span rows).

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
-- Forward-compat for "Open N": add a nullable batch_id column later; no reshape needed.
CREATE TABLE lootbox_plays (
    id                         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                    UUID        NOT NULL REFERENCES users(id),
    cost                       INTEGER     NOT NULL DEFAULT 1,
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

CREATE TABLE coin_adjustments (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES users(id),
    delta              INTEGER     NOT NULL,
    reason             TEXT        NOT NULL CHECK (length(reason) > 0),
    granted_by_user_id UUID        NOT NULL REFERENCES users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_coin_adjustments_user ON coin_adjustments(user_id, created_at DESC);

-- Seed initial tiers + one starter prize per tier (probabilities sum to 100).
DO $$
DECLARE
    common_id    UUID;
    rare_id      UUID;
    epic_id      UUID;
    legendary_id UUID;
BEGIN
    INSERT INTO lootbox_tiers (name, probability_pct, display_color, sort_order)
        VALUES ('COMMON', 70.00, '#9CA3AF', 1) RETURNING id INTO common_id;
    INSERT INTO lootbox_tiers (name, probability_pct, display_color, sort_order)
        VALUES ('RARE', 20.00, '#3B82F6', 2) RETURNING id INTO rare_id;
    INSERT INTO lootbox_tiers (name, probability_pct, display_color, sort_order)
        VALUES ('EPIC', 8.00, '#A855F7', 3) RETURNING id INTO epic_id;
    INSERT INTO lootbox_tiers (name, probability_pct, display_color, sort_order)
        VALUES ('LEGENDARY', 2.00, '#F59E0B', 4) RETURNING id INTO legendary_id;

    INSERT INTO lootbox_prizes (tier_id, name, description) VALUES
        (common_id,    'Pito Sticker',   'Classic sticker'),
        (rare_id,      'Coffee Voucher', '$5 coffee shop card'),
        (epic_id,      'Lunch on Pito',  'Free lunch up to $25'),
        (legendary_id, 'Day Off',        'A free PTO day');
END $$;
