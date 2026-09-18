-- Backfill step (.specs/phase-6-inventory AC-4, T-6c-7) for V63's new event_outbox columns.
-- Scoped to unpublished rows only (published_at IS NULL): a published event has already gone out
-- over Kafka without this context, so backfilling it here would not change what a consumer
-- already received, and every current producer (EventOutboxService) writes entity_type =
-- 'stock_movement' with the referenced movement's id stashed at payload->>'stock_movement_id'
-- (see EventOutboxService.createStockMovementEvent).
--
-- Pass 1 (movement-derived, authoritative): for a row whose stock_movement_id still resolves to a
-- real stock_movements row, take that movement's site_id (populated for every existing row by
-- V60's backfill) and set event_version to the initial version (1) and correlation_id from the
-- value already smuggled into the JSON payload (F-6c-6).
--
-- Pass 2 (guard case, .specs/phase-6-inventory T-6c-7's explicit test requirement): a row whose
-- movement no longer exists -- stock_movement_id does not parse as an integer, or no
-- stock_movements row has that id -- cannot get a site this way. Rather than leaving it fully
-- unbackfilled, still set event_version/correlation_id (site_id stays NULL, the same
-- "unknown-site" posture Q-6c-5 accepts for stock_movements rows during the compatibility
-- window) so a dangling reference degrades gracefully instead of blocking the rest of the
-- backfill or throwing, unlike V60's MAIN-required hard failure, which guarded a case with no
-- safe partial answer -- this one does.
--
-- Each pass is guarded and written defensively (COALESCE, never overwriting a non-NULL value) so
-- rerunning either statement changes nothing -- deliberately not guarded on event_version alone:
-- V63's event_version DEFAULT 1 means a row inserted by a writer that omits the column entirely
-- (the true "predates this migration" case, see EventOutboxEnvelopeMigrationIT's
-- oldWriterInsertOmittingNewColumnsStillSucceedsAndDefaultsEventVersion, and
-- EventOutboxEnvelopeBackfillIT's oldWriterDefaultedEventVersionStillGetsSiteAndCorrelationBackfilled)
-- already reads back event_version = 1 before this script ever runs, which would make an
-- event_version-only guard wrongly skip that row's still-unresolved site_id/correlation_id.
-- site_id has no such DEFAULT, so it is the reliable "not yet touched" signal for pass 1, and
-- pass 2's guard checks event_version/correlation_id individually rather than assuming one
-- implies the other.
UPDATE event_outbox eo
SET site_id = COALESCE(eo.site_id, sm.site_id),
    event_version = COALESCE(eo.event_version, 1),
    correlation_id = COALESCE(eo.correlation_id, eo.payload ->> 'correlation_id')
FROM stock_movements sm
WHERE eo.published_at IS NULL
  AND eo.entity_type = 'stock_movement'
  AND eo.site_id IS NULL
  AND eo.payload ->> 'stock_movement_id' ~ '^[0-9]+$'
  AND sm.id = (eo.payload ->> 'stock_movement_id')::bigint;

UPDATE event_outbox
SET event_version = COALESCE(event_version, 1),
    correlation_id = COALESCE(correlation_id, payload ->> 'correlation_id')
WHERE published_at IS NULL
  AND site_id IS NULL
  AND (event_version IS NULL OR correlation_id IS NULL);
