# Phase 7 operational-module inventory

- Status: Kickoff baseline
- Captured: 2026-09-17
- Durable context: [Enterprise modernization](../plans/enterprise-modernization.md#10-phase-7--shipments-and-remaining-site-operations), [Spring domain-modular-monolith migration](../plans/spring-domain-modular-monolith-migration.md#8-stage-f-shipments-module-parent-phase-7), and [Multi-site data and API](../specs/multi-site-data-and-api.md)

This inventory is the planning baseline for Phase 7, not evidence that any listed module is site-safe. Each implementation slice must refresh its table/route inventory from the deployed-schema evidence before writing migrations.

## Completion order and ownership

| Slice | Current legacy surface | Tables / projections requiring site decision | Required module outcome | Approved baseline edges to shrink |
| --- | --- | --- | --- | --- |
| Shipments | `/api/shipments`, `/api/tracking`, EasyPost webhook | `shipments`, `shipment_items`, `shipment_item_allocations`, `webhook_events` | `shipments` owns persistence; receipt uses `InventoryOperations`; protected webhook resolution derives site server-side | `shipments -> catalog`, `shipments -> repositories` |
| Displays | `/api/machine-displays`, display transfer/location reads | `machine_display` | `displays` owns persistence and reads inventory through a declared contract | `displays -> catalog`, `displays -> repositories` |
| Kuji / lootbox | `/api/kuji-boxes`, `/api/lootbox`, `/api/inventory/by-product/{productId}` | `kuji_boxes`, `kuji_box_tiers`, `lootboxes`, `lootbox_tiers`, `lootbox_prizes`, `lootbox_plays`, `coin_adjustments` | separate `kuji` and `lootbox` ownership; stock writes only through `InventoryOperations` | `kuji -> catalog`, `kuji -> models`, `kuji -> repositories`; no standalone lootbox edge exists yet, so additions require review |
| Notifications / reviews / audit | `/api/notifications`, `/api/reviews`, `/api/audit-logs`, stock audit dashboard | `notifications`, `reviews`, `review_daily_counts`, `audit_logs` | recipient, review and audit reads/writes are site-scoped; audit remains an explicit cross-module contract | no named edges yet; legacy `services`/`repositories` dependencies must be inventoried and removed without adding unreviewed edges |
| Analytics / forecasting | `/api/analytics`, `/api/forecasts`, `/api/activity-feed` | `forecast_predictions`, `analytics_daily_rollup`, `analytics_category_demand_rollup`, `mv_lead_time_stats`; delete dead dev-only `MonthlyPerformanceRollup`/repository/seed path | stable views/events own cross-service reads; no cross-service table writes | `analytics -> catalog`, `analytics -> inventory`, `analytics -> models`, `analytics -> repositories` |

## Shared gates inherited by every slice

1. Follow expand → backfill → verify → code rollout → constrain. Constrain is a separately releasable migration and uses the next free migration version; no historical-looking gap is reused.
2. Resolve site from the trusted authorized-site context before entity lookup. Foreign-site IDs use the authorization/not-found behavior in the durable multi-site specification.
3. The module owns its repositories and vertical API/application/domain/infrastructure code. It uses module facades, declared read projections, or events rather than foreign repositories.
4. Inventory changes go only through `InventoryOperations`, with atomic inventory, audit and outbox behavior retained.
5. New clients use v1 site routes. Retained legacy routes must be explicitly classified, protected, deprecated and assigned a removal gate.
6. `products.is_active` is the durable data/API §2 global stock-derived state; it is not a catalog-retirement or site-assortment flag. `site_products.is_stocked` is the per-site assortment flag. No Phase 7 slice may change either semantic implicitly.
7. Realtime and event producers retain `site_id`; the second-site Realtime/event gate remains a prerequisite before a non-MAIN user is enabled.

## Known cross-slice dependencies

- Phase 5d's Custom Kuji non-MAIN unavailable state is removed by its owning Kuji/lootbox slice only after site authorization, data and realtime gates are proven.
- Shipments and Kuji contain inventory-mutating calls that currently emit site-less realtime events. Their migration coordinates with `.specs/second-site-realtime-and-events/` when that record is opened.
- Analytics/forecasting owns a stable view/event boundary for forecasting output and must not retain direct writes into another service's tables.
- Every slice must review `module-dependency-edges-baseline.txt`; deleting an obsolete edge is a same-review responsibility, while adding an edge needs an explicit reviewed line.

## Web consumers to inventory per slice

The API map is authoritative for route families. Before implementation, enumerate `apps/web` query keys, mutation invalidations, realtime handlers, and any retained `/api/...` callers for the owning route family. The Phase 7 migration does not authorize incidental conversion of workflows owned by a different slice.

## Completed deferred inventory

| Route family | Web consumers to re-check before migration |
| --- | --- |
| Shipments/webhooks | shipment queries and receipt mutations; webhook has no browser caller |
| Displays | machine-display queries/mutations, display-transfer dialog and location-detail reads |
| Kuji/lootbox | Kuji box and lootbox queries/mutations plus inventory-by-product compatibility reads |
| Notifications/reviews/audit | notification feed, review management and audit dashboard queries/realtime invalidations |
| Analytics/forecasting | analytics and forecast dashboards plus activity-feed consumer |

Controllers omitted from the initial baseline: `ActivityFeedController` (`/api/activity-feed`, analytics), `EasyPostWebhookController` (`/api/webhooks`, shipments), and `LootboxAdminController` (`/api/lootbox/admin`, kuji-lootbox).

| Producer | Current inventory-event state |
| --- | --- |
| `KujiBoxService` | Nine `broadcastInventoryUpdated()` calls are site-less (359, 487, 656, 804, 994, 1211, 1257, 1446, 1663). |
| `ShipmentService` | Site-less inventory broadcasts at 711 and 860. |
| `EasyPostWebhookService` | Emits via shipment processing at 180; site propagation requires the shipment-derived context. |
| `AuditLogService` | Audit broadcasts at 128 and 174 lack site context. |
| `NotificationService` | Notification broadcast at 113 lacks site context. |
| Displays, lootbox, reviews, analytics, transfers | Emit no inventory event at all; this is distinct from emitting a site-less event. |
