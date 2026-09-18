# Phase 7 — Shipments

## Tier

Full — site ownership, webhook handling, inventory mutation, public API and asynchronous delivery boundaries are involved.

## Problem and outcome

Shipments, allocations, receipt/undo, tracking and EasyPost webhook handling retain legacy persistence and MAIN-oriented assumptions. This slice establishes a site-scoped `shipments` module whose receipt workflow changes stock only through `InventoryOperations` and whose webhook site resolution is server controlled.

## Durable context

- Plan: [Enterprise modernization §10](../../docs/plans/enterprise-modernization.md#10-phase-7--shipments-and-remaining-site-operations); [modular-monolith migration Stage F](../../docs/plans/spring-domain-modular-monolith-migration.md#8-stage-f-shipments-module-parent-phase-7)
- Specs: [Multi-site data and API](../../docs/specs/multi-site-data-and-api.md), especially §§2, 5–7
- Baseline: [Phase 7 inventory](../../docs/baseline/phase-7-inventory.md)

## Acceptance criteria

- AC-1: `shipments`, `shipment_items`, `shipment_item_allocations`, and `webhook_events` have recorded owners and an expand/backfill/verify/constrain site plan; constraints are a separately released final step.
- AC-2: Every shipment, allocation, receive, undo, tracking lookup and webhook mutation resolves an authorized or server-derived site before entity lookup; foreign-site IDs cannot read or write data.
- AC-3: Receipt and undo use `InventoryOperations`, retain transactionally coupled audit/outbox behavior, and emit site-aware realtime/event context.
- AC-4: `shipments` owns its repositories and exposes narrow facades/read contracts instead of foreign repository access; the reviewed `shipments -> catalog` and `shipments -> repositories` edges are removed or explicitly reduced.
- AC-5: `/api/v1/sites/{siteId}/shipments` and tracking/webhook contracts are migrated with web consumers; legacy routes are protected and deprecated until their documented removal gate.
- AC-6: PostgreSQL migration, authorization, concurrency/idempotency, contract and module-boundary tests prove the behavior.

## Tasks

- T-1: Inventory current tables, routes, web callers, webhook provider identifiers, stock writes and realtime/event producers.
- T-2: Specify and test site resolution, including webhook mapping that never trusts arbitrary provider payload site metadata.
- T-3: Implement schema expand/backfill/verification and a separately staged constrain migration.
- T-4: Move the vertical slice into `shipments`; replace direct stock persistence with `InventoryOperations` and foreign repository access with facades.
- T-5: Deliver v1/web migration and legacy compatibility/deprecation evidence.
- T-6: Remove reviewed architecture edges and complete native validation.
