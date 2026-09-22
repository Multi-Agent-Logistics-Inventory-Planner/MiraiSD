# Implementation log

## Current handoff

- Status: Specification kickoff complete; implementation has not started.
- Next action: T-1 — inventory writers/readers, recipient semantics, dashboard callers and notification/audit broadcasts.
- Decisions that must survive compaction: Site authorization precedes lookup; intentional system-admin reporting is explicit; realtime tenancy changes coordinate with the separate event/realtime rollout.
- Last verified: Current legacy ownership inventory is recorded in `docs/baseline/phase-7-inventory.md`.
- Open risks/questions: Some records may lack a direct site parent; site source must be proven before backfill.

## Test plan

- AC-1: PostgreSQL migration/uniqueness/backfill checks.
- AC-2: MockMvc/Testcontainers foreign-site and system-admin policy tests.
- AC-3: Event/realtime payload tests.
- AC-4–5: ArchUnit, OpenAPI/client and web tests.
