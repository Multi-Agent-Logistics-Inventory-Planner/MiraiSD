# Implementation log

## Current handoff

- Status: F1 runbook and verification queries prepared; user approved the fresh backup after V67/V68 split, but this environment has no Supabase backup creation capability.
- Next action: Create a Supabase dashboard/API backup externally, record its snapshot ID here, then re-run drift queries before each approved migration action.
- Decisions that must survive compaction: Apply only V58, V59, V60, V62, V63, V64, V65, V66 in order. V58/V62/V66 are non-transactional concurrent-index migrations. Do not merge PR #328, push, or apply V67 until their stated approvals/prerequisites are met.
- Last verified: `./mvnw -B clean test -Dtest='*IT'` — no failing/error integration reports after the V67/V68 split; no production connection was made.
- Open risks/questions: Backup cannot be created from the available connector/CLI surface. F2 Flyway canonicalization is blocked on F1. Image rollback cannot roll back schema.
