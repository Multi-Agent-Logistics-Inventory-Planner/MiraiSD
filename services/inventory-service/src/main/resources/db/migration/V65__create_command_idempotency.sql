-- Durable command idempotency (.specs/phase-6-inventory AC-3/AC-4, Q-6c-3, T-6c-10). Required on
-- the v1 mutation routes (T-6c-12) via a client-supplied Idempotency-Key header; legacy
-- /api/inventory/*, /api/stock-movements/* are explicitly unaffected (T-6c-13 leaves them alone).
--
-- Uniqueness is on the *trusted* site/user from AuthorizedSiteContext, never a client-supplied
-- value, so a replay can only be recognized as the same caller retrying the same command at the
-- same site -- a different site or a different user with the same key is a distinct command, not
-- a replay.
--
-- No FK to sites/users: this table is a short-lived operational log (see the retention policy
-- below), not a durable business record, matching event_outbox's own no-FK precedent for the same
-- reason (V16, V63).
--
-- Retention policy (T-6c-10's task text requires this be explicit, not implicit): rows are
-- retained for 7 days from creation, then eligible for deletion by
-- IdempotencyService's scheduled cleanup. 7 days comfortably covers any realistic client retry
-- window (network partition, a queued background retry, a human retrying a failed page load the
-- next business day) while keeping the table from growing unbounded; it is not tied to any
-- specific compliance/audit retention requirement, since this table's purpose is de-duplication,
-- not the audit trail (audit_log/stock_movements already carry the durable record of what
-- happened).
CREATE TABLE command_idempotency (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    site_id UUID NOT NULL,
    user_id UUID NOT NULL,
    idempotency_key TEXT NOT NULL,
    command_type TEXT NOT NULL,
    request_fingerprint TEXT NOT NULL,
    result_status INTEGER NOT NULL,
    result_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_command_idempotency_site_user_key
    ON command_idempotency (site_id, user_id, idempotency_key);

-- Supports the scheduled retention cleanup's range scan.
CREATE INDEX idx_command_idempotency_created_at ON command_idempotency (created_at);
