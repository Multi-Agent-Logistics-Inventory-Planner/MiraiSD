# Execution log

## Current handoff

- Status: Complete.
- Next action: Continue Slice C.
- Surviving decisions: Use a static Testcontainers lifecycle, matching `BaseKafkaIntegrationTest`, so a cached Spring context cannot reference a stopped container. Do not start Kafka.
- Last verified command: `./mvnw -B clean test -Dtest='*IT'` — passed (526 tests).
- Open risks: `application-integration.properties` uses Hibernate `ddl-auto=create-drop`; this cannot demonstrate Flyway parity. Docker is required locally. Use Maven `clean` after source deletion/moves so ArchUnit does not inspect stale class files.

## 2026-09-18 — classification and baseline

- Classified as Full because it adds PostgreSQL Testcontainers authorization coverage.
- Read `docs/specs/authentication-and-authorization.md` and the Slice B work order.
- Confirmed no existing `controllers/security` test references `PostgreSQLContainer`.
- Confirmed the integration profile uses `spring.jpa.hibernate.ddl-auto=create-drop`.

## 2026-09-18 — PostgreSQL implementation

- Added `BasePostgresMockMvcIntegrationTest`: a static PostgreSQL Testcontainer and dynamic datasource properties, with no Kafka container.
- Added six Postgres-backed MockMvc scenarios: revoked membership, absent membership, foreign-site resource, wrong parent, forged request actor ID, and explicit system-admin bypass without a membership row.
- The first focused run failed only because the forged-actor fixture used a random storage-location code while legacy `LocationType.RACK` resolves canonical `RACKS`; corrected the test fixture, not production behavior.
- Focused rerun passed: 6 tests, 0 failures. The test logs expected non-fatal localhost Supabase broadcast connection-refused warnings after the stock mutation.
- Retained H2 regression classes passed: 45 tests, 0 failures (`LocationControllerSecurityIT`, `LocationInventoryControllerSecurityIT`, `InventoryAggregateControllerSecurityIT`, `StockMovementControllerSecurityIT`).
- Review follow-up: G3 was an inconsistency finding, so revoked and absent membership now each run across all four retained route families (`/api/locations`, nested location inventory, aggregate inventory, and stock-movement history), rather than being represented by one route each.
- Review follow-up expanded: all three legacy stock-movement reads had been unguarded and globally unscoped. They now resolve MAIN context and use the established Q-6c-5 predicate (MAIN `site_id` or null compatibility rows). The focused Postgres suite passes 17 tests, including revoked/absent membership on each read and one response test proving foreign-site exclusion plus null-site inclusion.
- `apps/web/src/lib/api/stock-movements.ts` actively consumes the legacy audit-log route. The response DTO and URL are unchanged; only the authorized result set is scoped.
- Removed the now-unreferenced unscoped `StockMovementService` history/audit overloads so future callers cannot silently bypass tenant scoping.
- The initial non-clean unit run reported `CatalogEntityAccessCallerSetTest` and frozen
  `ArchitectureTest` failures. These were stale `target/classes` artifacts after removing the
  unscoped overloads, not baseline drift: `./mvnw -B clean test` passed 481/481 and the two
  focused architecture tests passed 10/10. Do not update the caller inventory or alter the
  frozen-rule configuration; both correctly reject stale-class-induced changes.
- `./mvnw -B clean test -Dtest='*IT'` passed 526/526.
