# Implementation log

## Current handoff

- Status: Specification kickoff complete; implementation has not started.
- Next action: T-1 — verify the production table name and inventory display routes, web consumers and location/product dependencies.
- Decisions that must survive compaction: Never assume `machine_display` versus `machine_displays`; site constraints follow verified production evidence; constrain is a separate release.
- Last verified: Phase 7 architecture-edge and source inventory recorded in `docs/baseline/phase-7-inventory.md`.
- Open risks/questions: The physical table name is explicitly unresolved.

## Test plan

- AC-1–2: PostgreSQL schema/migration checks.
- AC-3: Testcontainers authorization and foreign-site tests.
- AC-4: ArchUnit baseline review.
- AC-5: OpenAPI/client/web tests.
