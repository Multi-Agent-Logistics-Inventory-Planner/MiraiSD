-- Server-persisted prediction dismissals. Org-wide scope: one row per item
-- (PK on item_id), so when any user dismisses a prediction it disappears for
-- everyone. Auto-expire after 30 days is enforced at read time via a
-- WHERE dismissed_at > NOW() - INTERVAL '30 days' filter -- no cleanup cron.
--
-- computed_at captures the forecast computedAt at the moment of dismissal so
-- the client can auto-undismiss when a fresh forecast lands. NULL means "the
-- user did not target a specific forecast computation."
CREATE TABLE prediction_dismissals (
    item_id       uuid PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    dismissed_at  timestamptz NOT NULL DEFAULT NOW(),
    dismissed_by  uuid        NOT NULL REFERENCES users(id),
    computed_at   timestamptz,
    reason        text
);

CREATE INDEX idx_prediction_dismissals_dismissed_at
    ON prediction_dismissals (dismissed_at DESC);
