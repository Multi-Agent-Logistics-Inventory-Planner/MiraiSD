# Implementation log

## Current handoff

- Status: Specification kickoff complete; implementation has not started.
- Next action: T-1 — inventory projection ownership, native SQL, caches, materialized views, consumers and cross-service table writes.
- Decisions that must survive compaction: Stable view/event contracts own forecasting output; direct cross-service table writes and Java-module imports are removed; fixture isolation must make PostgreSQL-native behavior deterministic.
- Last verified: Current analytics baseline edges and projection inventory recorded in `docs/baseline/phase-7-inventory.md`.
- Open risks/questions: The future view/event ownership and materialized-view refresh policy require design review before implementation.

## Test plan

- AC-1: PostgreSQL migration/backfill/index/uniqueness tests.
- AC-2: PostgreSQL authorization/cache-key tests.
- AC-3–4: Contract and ArchUnit tests proving stable ownership.
- AC-5: OpenAPI/client/web and event-envelope tests.
