# Phase 6 — Inventory and stock movements

## Tier and status

Full — module ownership, tenant migrations, authorization, public contracts, events and web behavior.
Planning record; implementation has not started. Checkpoint scopes below are agreed; concrete
caller/table/endpoint inventories and rollout decisions must be completed before dependent changes.

## Problem and outcome

Inventory remains largely in technical-layer packages and legacy site-blind workflows. Phase 6
establishes an inventory module and trusted site boundary, migrates inventory API/web usage, restores
site-scoped quantities on Products, and reduces repeated whole-catalog totals reads. Existing legacy
consumers remain compatible until their documented migration; Phase 7 workflows are not pulled in
wholesale merely because they call inventory.

## Durable context

- Plan: [Enterprise modernization §9](../../docs/plans/enterprise-modernization.md#9-phase-6--inventory-and-stock-movements).
- Workstream: [Stage E](../../docs/plans/spring-domain-modular-monolith-migration.md#7-stage-e-inventory-module-parent-phase-6).
- Specs: [Module boundaries](../../docs/specs/spring-domain-modular-monolith.md),
  [Multi-site data/API](../../docs/specs/multi-site-data-and-api.md),
  [Client applications](../../docs/specs/client-applications.md),
  [Events](../../docs/specs/events-and-replica-readiness.md).
- ADR: [Domain-modular monolith](../../docs/adr/0001-spring-domain-modular-monolith.md).
- Inputs: [5b catalog facades](../phase-5b-catalog-facade/spec.md),
  [5c site products](../phase-5c-site-products/spec.md),
  [5d catalog/web adoption](../phase-5d-catalog-v1-and-web/spec.md).

## Delivery decisions

- One working branch, one draft PR, five logical commits/checkpoints (6a–6e). Additional fix
  commits are allowed; the count is not a reason to combine unrelated behavior or skip review.
- This one record owns execution; the plans own the phase sequence. Validate and review each
  checkpoint before proceeding, then run the complete phase exit gate before merge.
- Ordered commits do not provide separate deployments. Before 6b implementation, document the
  actual migration mechanism, compatibility with currently deployed writers, deterministic backfill,
  verification, rollback compatibility, and when enforcement is safe. If enforcement requires an
  earlier deployed release, split that prerequisite into a separate PR/record. No production apply
  or deployment is authorized by this record.
- Before changing global stock state, inventory all `products.quantity`, `products.is_active` and
  `ProductStockStateWriter` consumers. Reconcile Phase 5b's removal intent with durable activity
  semantics and Kuji/forecasting compatibility. Do not replace global activity with site assortment
  or drop columns by assumption. Preserve documented adapters until replacement consumers exist.
- Kuji/lootbox site migration, forecasting projection migration and audited inter-site transfers
  remain in their later phases. Necessary inventory facade/event integration may update those
  callers without migrating their entire domain.

## Acceptance criteria

- AC-1: Inventory owns its entities, persistence and workflows. External production callers use
  documented application facades/read contracts, not inventory repositories. ArchUnit verifies
  the boundary, including callers still in legacy packages. Facades preserve caller transactions.
- AC-2: A table/caller worksheet records ownership sources, schema/index/constraint changes,
  readers/writers, backfill and rollback compatibility. Expand/backfill/verify/constrain is proven
  against the actual rollout order, including old writers; no null, orphan, conflicting-site or
  uniqueness violations remain when constraints are enforced.
- AC-3: Every site-owned command/query requires trusted site context. Site-qualified lookup and
  composite constraints reject foreign-site identifiers. Role/membership checks, concurrent writes
  and idempotent retries are tested. Existing MAIN compatibility remains explicit and tested.
- AC-4: Inventory mutation, movement, audit and outbox persist atomically. Versioned inventory
  events carry the durable envelope (site, actor where applicable, correlation, causation and
  idempotency context), stable event identity and site/product partitioning. Affected producers and
  consumers pass compatibility, claim, duplicate, reorder and crash/retry tests using the existing
  outbox/idempotency foundation.
- AC-5: Inventory/movement v1 routes expose DTOs through the application boundary. Slim totals
  query projections fetch product ID, quantity and last-update time without duplicated catalog
  metadata. Zero-stock products and quantity semantics remain correct. Bounded/batched reads
  support known affected IDs. Regenerate OpenAPI and TypeScript with endpoint changes; prove
  legacy compatibility and no unintended contract break.
- AC-6: Inventory web workflows use v1 and site-qualified queries. Products quantities/status are
  restored only from scoped totals. Rendered tests cover detail state, role-dependent controls,
  stock workflows, site switching, unresolved/error states and rejection of late old-site results.
  Preserve the non-MAIN Kuji unavailable gate until its own domain migration.
- AC-7: Local mutation and realtime refreshes share a coalesced, bounded strategy. Known IDs cause
  neither full-catalog refreshes nor one request per product. Initial load, reconnect/missed events
  and unknown-ID batches retain full selected-site recovery. Duplicate/reordered notifications
  converge to authoritative state without cross-site cache contamination.
- AC-8: Controlled before/after workloads record database rows, projected bytes (label estimates),
  API bytes and request/query counts for equal catalog size, changes and active browsers. Show slim
  projections and targeted reads reduce returned data; result size follows affected IDs. Historical
  cumulative counters alone are not proof. All phase exit gates pass locally and in the PR gate;
  remaining adapters/debt and a cost-impact statement are recorded.

## Tasks and checkpoint gates

| Checkpoint | Work and gate |
| --- | --- |
| 6a — Inventory module boundary | First inventory callers, associations, tables, endpoints and event consumers; record baseline and concrete tasks. Move module, add narrow facades, migrate callers and enforce ownership. Preserve behavior and transaction semantics; pass native/architecture tests (AC-1). |
| 6b — Site ownership foundation | Finalize rollout worksheet before editing schema. Expand, update writers and supply backfill/verification. Prove compatibility and ownership before permitting enforcement (AC-2). |
| 6c — Scoped inventory backend | Trusted context, scoped repositories/constraints, atomic writes, compatible event context, v1 and slim/batched totals. Resolve stock-state compatibility before changing it. Pass tenant, concurrency, event, migration and contract checks; capture query measurements (AC-2–5, AC-8). |
| 6d — Web adoption | Migrate inventory reads/mutations and restore scoped Products inventory. Prove rendered behavior and site-switch isolation (AC-6). |
| 6e — Targeted refresh and exit proof | Coalesce targeted refresh, prove recovery and measured savings, remove only obsolete compatible paths, and run the complete phase gate (AC-7–8 and regression of AC-1–6). |

Each checkpoint requires independent Standards and Spec review, recorded in review.md, and actual
commands/results in validation.md. Refine tests and concrete work from the inventory before each
checkpoint; do not treat this planning outline as completed implementation proof.
