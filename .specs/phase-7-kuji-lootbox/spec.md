# Phase 7 — Kuji and lootbox

## Tier

Full — Kuji lifecycle, inventory mutations, public operational routes and tenant migrations require the Full lifecycle.

## Problem and outcome

Kuji and lootbox workflows remain legacy modules and include product/stock behavior that is unsafe to interpret per site. This slice gives each bounded context site-owned persistence and module ownership, routes inventory writes through `InventoryOperations`, and removes the deliberate non-MAIN Custom Kuji block only after its safety gates pass.

## Durable context

- Plan: [Enterprise modernization §10](../../docs/plans/enterprise-modernization.md#10-phase-7--shipments-and-remaining-site-operations); [Stage G](../../docs/plans/spring-domain-modular-monolith-migration.md#9-stage-g-remaining-operational-modules-parent-phase-7)
- Specs: [Multi-site data and API](../../docs/specs/multi-site-data-and-api.md)
- Baseline: [Phase 7 inventory](../../docs/baseline/phase-7-inventory.md)

## Acceptance criteria

- AC-1: `kuji_boxes`, `kuji_box_tiers`, `lootboxes`, `lootbox_tiers`, `lootbox_prizes`, `lootbox_plays`, and `coin_adjustments` have an explicit site-owner/source and expand/backfill/verify/constrain plan.
- AC-2: Kuji and lootbox commands and reads resolve authorized site context before lookup; all child/location/product relationships remain tenant consistent.
- AC-3: Draw, intake, allocation and related stock changes call `InventoryOperations`, commit audit/outbox atomically, and publish site-aware events/realtime notifications.
- AC-4: `kuji` and `lootbox` own their repositories and use facades/read projections rather than catalog, inventory or legacy repositories. Existing Kuji architecture edges shrink; any new lootbox edge is reviewed first.
- AC-5: v1 route and web migration covers `/api/kuji-boxes`, `/api/lootbox`, and Kuji's legacy inventory-by-product caller. The Custom Kuji non-MAIN unavailable state is removed only with demonstrated site authorization/data/realtime readiness.
- AC-6: PostgreSQL, lifecycle/concurrency, authorization, contract and architecture tests pass.

## Tasks

- T-1: Inventory lifecycle transitions, all stock writers, legacy web callers and the nine site-less Kuji inventory broadcast sites.
- T-2: Define site ownership and migration sequence, including child constraints and global coin-balance policy boundaries.
- T-3: Move Kuji and lootbox vertical slices; replace foreign repositories and direct stock persistence.
- T-4: Add v1/web migration and tenant/lifecycle/concurrency tests.
- T-5: Coordinate site-aware realtime events and lift the Custom Kuji gate only after its prerequisites are validated.
