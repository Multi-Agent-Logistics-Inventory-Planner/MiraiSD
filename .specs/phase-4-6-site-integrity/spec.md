# Site integrity and deferred stock-movement constraint

- Tier: Full
- Status: In progress
- Plan: `docs/plans/phase-4-6-closeout.md`, Slice C

## Acceptance criteria

1. Every site, including SECOND, has exactly one canonical NOT_ASSIGNED storage location and location.
2. A database guard rejects a second location beneath a site's NOT_ASSIGNED storage location, including concurrent insertion.
3. A new post-V66 migration makes `stock_movements.site_id` non-null and references `sites`; it is tested only and not applied to production.

## Decision

Use a PostgreSQL trigger: the invariant spans `locations` and `storage_locations`, so a unique index cannot express it.
