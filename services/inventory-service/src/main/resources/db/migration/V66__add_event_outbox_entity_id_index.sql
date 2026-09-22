-- Review finding (.specs/phase-6-inventory T-6c-8, code-review pass 2026-09-13): T-6c-8's
-- existsByEntityId(entityId) dedupe guard (EventOutboxRepository.java) runs on every stock
-- movement's outbox-event creation, but entity_id carries no index of its own -- V16 indexes a
-- JSONB payload expression (payload->>'stock_movement_id'), not this plain column. Every new-
-- event lookup therefore sequentially scans retained outbox history as the table grows.
--
-- CONCURRENTLY + the paired .conf disabling the wrapping transaction, same as V62: event_outbox
-- is written on every stock movement in production use and must not be locked.
--
-- Not unique: entity_id is deterministic per stock movement (T-6c-8), so in practice at most one
-- row exists per value once the existsByEntityId guard is in place, but V16's JSONB unique index
-- is what actually enforces that at the database level for stock_movement rows; this index exists
-- purely to make existsByEntityId's lookup fast, not to add a second uniqueness guarantee for
-- other entity_type values that may not carry the same deterministic-id guarantee.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_event_outbox_entity_id
  ON event_outbox(entity_id);
