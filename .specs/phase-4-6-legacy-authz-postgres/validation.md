# Validation

## Required limitation

`application-integration.properties` sets `spring.jpa.hibernate.ddl-auto=create-drop`.
The PostgreSQL-backed HTTP tests therefore prove PostgreSQL runtime semantics, not
that the production schema was created or upgraded by Flyway. Slice F2 owns
Flyway baseline and migration parity.

## Commands

```bash
cd services/inventory-service
./mvnw -B clean test
./mvnw -B clean test -Dtest='*IT'
```

## Focused result

`LegacyRouteAuthorizationPostgresIT` passed on 2026-09-18: 17 tests, 0 failures.

The retained H2 legacy security classes also passed: 45 tests, 0 failures.

Clean full suites passed on 2026-09-18: `./mvnw -B clean test` 481/481 and
`./mvnw -B clean test -Dtest='*IT'` 526/526. A prior non-clean unit run was not
a test regression: Maven retained obsolete class files after source deletion, and
the frozen ArchUnit rules correctly rejected the resulting phantom changes.

## Compatibility assumption

`apps/web` still consumes `/api/stock-movements/audit-log`, so its URL and DTO were
kept stable. The `site_id = MAIN OR site_id IS NULL` filter changes only the result
set: a foreign-site row is now hidden while a pre-V60 null-site row remains visible.
Production is currently at V57 and has only MAIN stock data, so this does not hide
an existing second-site row; it preserves legacy rows until the V60 backfill/F1 path.
