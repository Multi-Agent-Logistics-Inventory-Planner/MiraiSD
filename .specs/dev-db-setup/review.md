# Review

Independent Standards and Spec passes reviewed the runner, SQL and tests in parallel.

- [Standards + Spec] Non-MAIN guard originally checked only locations/assortment. Site-bound
  movement or outbox histories can survive their deletion. **Acted on:** reject non-MAIN
  site IDs in both operational tables before any MAIN fallback. Real PostgreSQL rollback-only
  fixtures prove both refusal paths.
- [Spec] Startup/seed/rerun documentation and integration proof outstanding at initial review.
  **Acted on:** scripts/README-dev-db.md and validation.md now document and prove these paths.
- Standards confirmed pinned local Docker/socket/container, cleared PG environment,
  transaction rollback, and preservation by canonical ON CONFLICT backfills.

Both independent reviewers rechecked the fixes and confirmed no outstanding findings.

## Residual limitations
This targeted Phase 6 bridge assumes Hibernate's current baseline, does not adopt Flyway or
reconcile every older constraint. Backend restart can restore mapped VARCHAR types; rerun
setup after backend startup, documented and observed during validation. No production schema
or application authorization code changed.
