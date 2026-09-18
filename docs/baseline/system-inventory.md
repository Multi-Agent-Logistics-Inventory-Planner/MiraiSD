# Phase 0 System Inventory

- Status: Baseline
- Captured: 2026-08-12

## 1. Deployable and hosted components

| Component | Deployment form | Data access | Proposed owner |
| --- | --- | --- | --- |
| `apps/web` | Next.js standalone web application | Spring HTTP API, Supabase auth/realtime, S3-compatible object storage through server routes | Client team |
| `inventory-service` | Spring Boot JVM/container | Direct Supabase PostgreSQL through 37 JPA repositories; Kafka producer | Backend team |
| `forecasting-service` | FastAPI/worker container | Direct SQL reads of product/inventory/shipment/movement data; owns forecast output writes; Kafka consumer | Forecasting owner |
| `messaging-service` | Python worker container | Direct SQL reads/writes for notifications, reviews, review rollups, users, displays and coin configuration; Kafka consumer/producer | Messaging owner |
| Kafka | One persistent KRaft broker | `inventory-changes` and DLQ traffic | Platform |
| Caddy | Edge reverse proxy and TLS | No business data | Platform |
| `forecast-cron` | Alpine cron container | Calls forecasting HTTP trigger daily | Platform/forecasting |
| Supabase | Hosted PostgreSQL and authentication | Shared database and identity provider | Platform with logical service ownership |
| DigitalOcean | Current single compute host | Runs backend Compose stack | Platform; replaced in Phase 10 |

The web client is independently deployable and is not embedded in the Spring artifact.

## 2. Spring persistence inventory

The service contains 37 repository interfaces and 33 mapped entities. Proposed logical ownership is:

| Domain owner | Current mapped tables or persistence objects |
| --- | --- |
| Catalog | `products`, `categories`, `suppliers`, `mv_lead_time_stats` |
| Sites | `sites`, `storage_locations`, `locations` |
| Identity | `users`, `invitations` |
| Inventory | `location_inventory`, `stock_movements` |
| Shipments | `shipments`, `shipment_items`, `shipment_item_allocations`, `webhook_events` |
| Displays | `machine_display` |
| Kuji | `kuji_boxes`, `kuji_box_tiers` |
| Lootbox | `lootboxes`, `lootbox_tiers`, `lootbox_prizes`, `lootbox_plays`, `coin_adjustments`, `coin_economy_config` |
| Analytics/forecasting | `analytics_daily_rollup`, `analytics_category_demand_rollup`, `forecast_predictions`; Phase 7 deletes dead dev-only `MonthlyPerformanceRollup` code (no `analytics_monthly_rollup` table exists) |
| Notifications/reviews | `notifications`, `reviews`, `review_daily_counts` |
| Audit/event infrastructure | `audit_logs`, `event_outbox`, `event_dead_letter` |

`subcategories` and legacy `review_employees` appear in bootstrap SQL but have no current mapped
entity. They require production-schema verification and an explicit keep/migrate/drop decision.

## 3. Cross-service database access

| Service | Reads | Writes | Target contract |
| --- | --- | --- | --- |
| Forecasting | `products`, `categories`, `location_inventory`, `stock_movements`, `shipments`, `shipment_items`, `forecast_predictions`, `mv_lead_time_stats` | `forecast_predictions`; also updates reorder fields on `products` | Own forecast outputs; replace inventory/catalog access with stable views or event-derived projections; stop catalog writes |
| Messaging | `products`, `users`, `review_daily_counts`, `coin_economy_config`, `machine_displays` | `notifications`, `reviews`, `review_daily_counts` | Own delivery/review state; obtain display/catalog/user facts through explicit read contracts |

All three backend deployables currently use the same high-privilege connection-variable pattern.
Phase 1 and later migrations must introduce least-privilege users without requiring separate databases.

## 4. Schedulers and background execution

| Process | Schedule | Activity | Replica status |
| --- | --- | --- | --- |
| Spring `EventOutboxService` | Fixed delay, 10 seconds | Reads and publishes pending outbox rows | Unsafe for concurrent publishers until Phase 2 claims rows atomically |
| Spring `AnalyticsRollupScheduler` | Five daily/weekly/monthly cron expressions | Analytics rollups and cleanup | Dev profile only; process-local |
| `forecast-cron` | Daily at 00:00 UTC | Calls `/forecasts/trigger` | Single Compose process |
| Messaging scheduler | Daily configured fetch hour | Fetches reviews from Apify | Process-local |
| Messaging scheduler | 30 minutes after fetch | Sends daily review summary | Process-local |
| Messaging scheduler | First day monthly, 08:00 configured timezone | Sends monthly review summary | Process-local |
| Messaging scheduler | Daily 09:00 configured timezone | Detects stale displays and creates notifications | Process-local |

## 5. Event consumers and topics

| Topic/stream | Producer | Consumer group/use |
| --- | --- | --- |
| `inventory-changes` | Spring outbox publisher | `forecasting-service` inventory-event batching and forecast updates |
| `inventory-changes` | Spring outbox publisher | `messaging-service` inventory alert processing |
| Review Kafka stream | Messaging review fetcher | Messaging review ingestion consumer uses a `-reviews` group suffix |
| `inventory-changes.DLQ` | Python consumer adapters | Failed event preservation for both Python services |

Delivery is at least once. Persisted consumer event-id deduplication is not yet established.

## 6. Current HTTP surface

Spring exposes 213 annotated mappings across 27 controllers, including 24 development seed mappings.
The full classification and v1 destination are in [the API map](api-v1-map.md).

## 7. Current deployment path

The single workflow reacts to changes under `services/**` or `infra/**`, connects to DigitalOcean by
SSH, updates the host checkout and builds services there. No immutable registry artifact, required PR
test workflow or recorded deployment duration exists. Phase 2 replaces the build artifact path with
GHCR; Phase 10 changes the host.
