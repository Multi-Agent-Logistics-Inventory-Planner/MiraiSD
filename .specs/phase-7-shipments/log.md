# Implementation log

## Current handoff

- Status: Specification kickoff complete; implementation has not started.
- Next action: T-1 — inventory shipment tables, webhook references, routes, web callers and direct inventory/repository dependencies.
- Decisions that must survive compaction: Webhook site identity is derived from a server-controlled mapping; receipt/undo must use `InventoryOperations`; constrain migration is separately released.
- Last verified: `rg` inventory of current module files, broadcast calls, migration references and architecture edges — recorded in `docs/baseline/phase-7-inventory.md`.
- Open risks/questions: Production schema/table ownership and provider identifier mapping require read-only evidence before migration design.

## Assumptions and decisions

- The Phase 7 plan's completion order is authoritative; this record does not authorize production migration or deploy.

## Test plan

- AC-1: PostgreSQL migration IT with backfill/orphan/tenant-consistency checks.
- AC-2–3: MockMvc/Testcontainers authorization and atomic receipt/undo tests.
- AC-4: ArchUnit baseline review.
- AC-5: OpenAPI/client and web tests.
