-- Expand step (.specs/phase-6-inventory AC-4, T-6c-7): event_outbox predates Flyway (created by
-- Hibernate before Flyway adoption, same as stock_movements before V59 -- see that migration's
-- header) and today carries no site, event-version, correlation, causation or idempotency columns.
-- EventOutboxService currently smuggles a correlation id into the JSON payload and re-reads it at
-- publish time (F-6c-6); this migration adds the durable columns the envelope needs without
-- touching that behavior yet -- T-6c-8 is the task that populates these columns at creation time
-- and publishes them at the envelope level, and T-6c-10 is the separate durable idempotency
-- command table (this column only carries a command's key *into* the event once T-6c-8/T-6c-10
-- exist; it does not itself enforce request-level dedup).
--
-- All five columns are nullable with no FK, matching 6b's expand-only precedent (V59): old
-- deployed images that do not know about these columns keep inserting rows exactly as before,
-- since none of them are NOT NULL. No constrain step in this record (P-3).
--
-- Column types:
--   site_id          UUID    -- no FK yet, mirrors stock_movements.site_id (V59)
--   event_version    INTEGER -- DEFAULT 1 so any row inserted after this migration (including by
--                               an old writer that does not set it explicitly) starts versioned;
--                               still nullable, and existing rows get it explicitly in V64
--   correlation_id   TEXT    -- matches the existing JSON payload value's actual type: it is a
--                               caller-supplied or generated string (CorrelationIdFilter accepts
--                               any incoming X-Correlation-Id header verbatim), not guaranteed to
--                               be UUID-parseable, so TEXT rather than UUID
--   causation_id     TEXT    -- same shape as correlation_id (nothing populates it until T-6c-8)
--   idempotency_key  TEXT    -- client-supplied Idempotency-Key header value (Q-6c-3), not a
--                               generated UUID
ALTER TABLE event_outbox ADD COLUMN site_id UUID;
ALTER TABLE event_outbox ADD COLUMN event_version INTEGER DEFAULT 1;
ALTER TABLE event_outbox ADD COLUMN correlation_id TEXT;
ALTER TABLE event_outbox ADD COLUMN causation_id TEXT;
ALTER TABLE event_outbox ADD COLUMN idempotency_key TEXT;
