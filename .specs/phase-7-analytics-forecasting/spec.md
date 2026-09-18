# Phase 7 — Analytics and forecasting projections

## Tier

Full — forecasting, site-scoped projections, cross-service contracts, migrations and public APIs require the Full lifecycle.

## Problem and outcome

Analytics and forecasting projections are organization-oriented legacy code with direct cross-module/table coupling. This slice makes dimensions and output site-aware, establishes stable view/event ownership for forecasting output, and removes cross-service writes into another service's tables.

## Durable context

- Plan: [Enterprise modernization §10](../../docs/plans/enterprise-modernization.md#10-phase-7--shipments-and-remaining-site-operations); [Stage G](../../docs/plans/spring-domain-modular-monolith-migration.md#9-stage-g-remaining-operational-modules-parent-phase-7)
- Specs: [Multi-site data and API](../../docs/specs/multi-site-data-and-api.md), [Events and replica readiness](../../docs/specs/events-and-replica-readiness.md)
- Baseline: [Phase 7 inventory](../../docs/baseline/phase-7-inventory.md)

## Acceptance criteria

- AC-1: `forecast_predictions`, all three analytics rollups, and `mv_lead_time_stats` have recorded site dimensions, source/backfill strategy, uniqueness/index changes and expand/backfill/verify/constrain releases.
- AC-2: Analytics and forecast APIs resolve authorized site before querying a projection; foreign-site IDs and cache keys cannot cross tenant boundaries.
- AC-3: Stable owned views or event-derived projections define forecasting output ownership. Java module imports and cross-service writes to another service's tables are removed.
- AC-4: `analytics` owns its repositories/projections and uses declared catalog/inventory contracts; the four approved `analytics` baseline edges are shrunk with review.
- AC-5: v1 analytics/forecasting routes and web consumers migrate without new legacy callers; event consumers retain `site_id` and site-scoped deduplication.
- AC-6: PostgreSQL native-SQL/projection, migration, authorization, event-contract and architecture tests pass deterministically.

## Tasks

- T-1: Inventory projection producers, consumers, native SQL, materialized views, cache keys and all cross-service table writes.
- T-2: Decide and document each stable view/event contract and migration ownership before code movement.
- T-3: Implement site dimensions through expand/backfill/verify; stage constraints separately.
- T-4: Move vertical modules, eliminate forbidden table writes/imports, and migrate v1/web/event consumers.
- T-5: Add deterministic PostgreSQL fixture isolation and full contract/architecture validation.
