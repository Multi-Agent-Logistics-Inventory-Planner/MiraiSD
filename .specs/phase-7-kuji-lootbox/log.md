# Implementation log

## Current handoff

- Status: Specification kickoff complete; implementation has not started.
- Next action: T-1 — inventory lifecycle transitions, stock writes, legacy callers and site-less broadcast call sites.
- Decisions that must survive compaction: `products.is_active` remains global stock-derived state; site assortment is `site_products.is_stocked`; no non-MAIN Custom Kuji enablement until tenancy/realtime readiness is proved.
- Last verified: Kuji and broadcast source inventory recorded in `docs/baseline/phase-7-inventory.md`.
- Open risks/questions: Coin balance policy and child-table constraint design need product and schema evidence.

## Test plan

- AC-1: PostgreSQL migration/backfill/constraint ITs.
- AC-2–3: Lifecycle, concurrency, site authorization and atomic inventory/outbox tests.
- AC-4: ArchUnit baseline review.
- AC-5: Contract and rendered web tests.
