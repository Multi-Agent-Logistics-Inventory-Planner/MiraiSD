# Dev database setup

## Tier
Full — local schema reconciliation and tenant backfills.

## Context
- docs/specs/multi-site-data-and-api.md, sections 4–5
- docs/specs/authentication-and-authorization.md, membership lifecycle
- .specs/phase-6-inventory/spec.md and log.md (manual migration reality and deferred V61)

## Outcome
A manually invoked local-only companion to existing dev data seeders reconciles the
Hibernate-created development database with Phase 6 schema requirements. This is not a
historical migration runner or a replacement for the future canonical Flyway bootstrap.
No production Compose, service code, auth behavior, or migration files change.

## Acceptance criteria
- AC-1: Only a local Docker daemon and the running Compose postgres-dev service are accepted;
  connection credentials/URLs from .env or PG environment cannot redirect SQL.
- AC-2: Require Hibernate baseline tables; ensure Phase 6 columns, TEXT types, defaults,
  idempotency table and indexes; preserve deferred nullable site columns (no V61).
- AC-3: Seed MAIN and backfill missing MAIN memberships/site products using existing SQL,
  preserving existing overrides, inactive memberships, roles, and inventory quantities.
  Refuse MAIN-fallback backfills if non-MAIN operational inventory already exists.
- AC-4: Reinstall existing location inventory triggers skipped on first Postgres startup;
  verify schema/backfills and support repeat runs without duplicates.
- AC-5: Document startup/seed/rerun order and verify local execution plus isolation guards.

## Plan
1. Inspect existing seed mechanisms and durable migration constraints.
2. Add guarded runner and scoped reconciliation SQL, reusing existing SQL where safe.
3. Test isolation guards and live PostgreSQL behavior, including rerun and preservation.
4. Independent Standards and Spec reviews, record validation, commit scoped changes.
