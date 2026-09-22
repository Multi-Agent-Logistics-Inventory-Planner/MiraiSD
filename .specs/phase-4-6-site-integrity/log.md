# Execution log

## Current handoff

- Status: V67 safe seed/guard and separately gated V68 constraint implemented; both remain unapplied outside Testcontainers.
- Next action: Run the full clean IT gate after the split.
- Surviving decisions: Use a trigger because the predicate spans two tables. V67 is independently safe; V68 is the constraint because V61 is below already-present V62-V67 and would be out-of-order after Flyway baseline.
- Last verified command: `./mvnw -B clean test -Dtest='*IT'` — no failing or error integration reports after the V67/V68 split; focused `SiteIntegrityMigrationIT` passed 3 PostgreSQL tests.
- Open risks: Apply V67 with F1 only after the usual backup approval. Hold V68 out of automatic Flyway targeting until V60 verification, #328 deployment, and an immediate NULL re-check.

## Implementation

- V67 idempotently creates SECOND's `NOT_ASSIGNED` storage location and its sole `NA` location.
- V67 triggers serialize inserts and storage-location reparenting per NOT_ASSIGNED storage location with a transaction advisory lock and reject a second row.
- V68 adds the deferred `stock_movements.site_id` FK and NOT NULL constraint. Its header records why V61 is not usable and why it must remain human-gated.

## Test evidence

- `SiteIntegrityMigrationIT` executes V67/V68 verbatim against PostgreSQL, proves V67 leaves nullable movement writers compatible, checks V68's NULL/foreign rejection, races two inserts, and rejects reparenting an existing location into NOT_ASSIGNED.
