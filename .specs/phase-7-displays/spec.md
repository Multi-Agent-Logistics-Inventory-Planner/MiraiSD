# Phase 7 — Displays

## Tier

Full — a site-owned operational module with API, migration and inventory-boundary changes.

## Problem and outcome

Machine display workflows use legacy persistence and location/product relationships without an explicit tenant boundary. This slice makes displays site-aware and modular while preserving display transfer and location-detail behavior.

## Durable context

- Plan: [Enterprise modernization §10](../../docs/plans/enterprise-modernization.md#10-phase-7--shipments-and-remaining-site-operations); [Stage G](../../docs/plans/spring-domain-modular-monolith-migration.md#9-stage-g-remaining-operational-modules-parent-phase-7)
- Specs: [Multi-site data and API](../../docs/specs/multi-site-data-and-api.md)
- Baseline: [Phase 7 inventory](../../docs/baseline/phase-7-inventory.md)

## Acceptance criteria

- AC-1: Production naming (`machine_display` versus `machine_displays`), ownership, row counts, and site source are verified before a migration is written.
- AC-2: Display rows have an expand/backfill/verify/constrain site migration with same-site location/product consistency where feasible.
- AC-3: All display reads, mutations, transfers and location-detail reads are authorized against the requested site before entity lookup; foreign-site IDs are inaccessible.
- AC-4: The `displays` module owns repositories and uses declared catalog/inventory contracts rather than foreign repositories; baseline edges are shrunk with review.
- AC-5: v1 site routes and web query/mutation/realtime consumers replace new legacy use; retained `/api/machine-displays` behavior is explicitly protected and deprecated.
- AC-6: PostgreSQL authorization/migration and module-boundary tests pass.

## Tasks

- T-1: Resolve actual schema name and all route/web/realtime consumers.
- T-2: Specify site source and implement expand/backfill/verification; hold constrain for a later release.
- T-3: Move vertical code into `displays` and introduce needed facade/read contracts.
- T-4: Add v1/web migration plus foreign-site and same-site consistency tests.
- T-5: Review the ArchUnit baseline and complete native validation.
