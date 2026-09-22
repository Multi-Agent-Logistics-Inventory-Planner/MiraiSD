# Site integrity and deferred stock-movement constraint

- Tier: Full
- Status: In progress
- Plan: `docs/plans/phase-4-6-closeout.md`, Slice C

## Acceptance criteria

1. Every site, including SECOND, has exactly one canonical NOT_ASSIGNED storage location and location.
2. A database guard rejects a second location beneath a site's NOT_ASSIGNED storage location, including concurrent insertion.
3. V67 safely seeds/guards canonical NOT_ASSIGNED rows; separately releasable V68 makes `stock_movements.site_id` non-null and references `sites` only after writer deployment and a fresh NULL check.

## Decision

Use a PostgreSQL trigger: the invariant spans `locations` and `storage_locations`, so a unique index cannot express it.
