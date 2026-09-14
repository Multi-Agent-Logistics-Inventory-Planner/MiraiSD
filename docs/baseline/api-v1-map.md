# API v1 Endpoint Classification Map

- Status: Baseline proposal
- Captured: 2026-08-12
- Inventory: all 213 annotated Spring mappings across 27 controllers

## Routing rules

- Site operations move under `/api/v1/sites/{siteId}/...` and resolve a trusted site context before
  entity lookup.
- Global catalog and identity routes stay outside the site path.
- A foreign-site UUID returns the authorization/not-found behavior defined by the auth specification;
  existence alone never grants access.
- Existing `/api/...` mappings may temporarily resolve `MAIN` and return deprecation headers.
- Development seed routes never receive a production v1 equivalent.

Every mapping in a controller row inherits that row's classification except where the notes state an
explicit split. The route list is complete as of the capture date.

## Controller map

| Current controller/base | Mappings | Class | Target v1 family | Notes |
| --- | ---: | --- | --- | --- |
| `AuthController /api/auth` | 4 | Global identity | `/api/v1/auth` | `validate`, `session`, `me`, `sync-user`; `me` includes memberships/effective permissions |
| `ActivityFeedController /api/activity-feed` | 1 | Site-owned | `/api/v1/sites/{siteId}/activity-feed` | All activity is site filtered |
| `AnalyticsController /api/analytics` | 12 | Site-owned | `/api/v1/sites/{siteId}/analytics` | Includes rollups, insights, demand and product reports |
| `AuditLogController /api/audit-logs` | 2 | Site-owned | `/api/v1/sites/{siteId}/audit-logs` | List and entity detail |
| `CategoryController /api/categories` | 8 | Global catalog | `/api/v1/catalog/categories` | All category CRUD/activation mappings |
| `DevSeedController /api/dev` | 24 | Development only | None | Must remain profile-restricted; no production compatibility route |
| `EasyPostWebhookController /api/webhooks` | 1 | Site-resolved | `/api/v1/webhooks/easypost` | Provider route cannot require a site path; resolve site from protected shipment/tracker data |
| `ForecastController /api/forecasts` | 7 | Site-owned | `/api/v1/sites/{siteId}/forecasts` | All list, risk, accuracy and explain mappings |
| `HealthController /health` | 1 | Platform | `/actuator/health` or retained `/health` | No tenant data or detailed public internals |
| `InventoryAggregateController /api/inventory` | 2 | Site-owned | `/api/v1/sites/{siteId}/inventory` | Totals and product inventory |
| `InvitationController /api/admin/invitations` | 4 | Site-owned identity | `/api/v1/sites/{siteId}/invitations` | Invite specifies a site role; system-admin operations may span sites explicitly |
| `KujiBoxController /api/kuji-boxes` | 19 | Site-owned | `/api/v1/sites/{siteId}/kuji-boxes` | All box, tier, draw, undo, allocation and intake mappings |
| `LocationAggregateController /api/locations` | 1 | Site-owned | `/api/v1/sites/{siteId}/locations/with-counts` | Site filter precedes aggregation |
| `LocationController /api/locations` | 5 | Site-owned | `/api/v1/sites/{siteId}/locations` | All location CRUD mappings |
| `LocationInventoryController` | 6 | Site-owned | `/api/v1/sites/{siteId}/locations/.../inventory` | Includes storage-location inventory mapping |
| `LootboxAdminController /api/lootbox/admin` | 21 | Site-owned operations | `/api/v1/sites/{siteId}/lootbox/admin` | Coin configuration may later move to global config; all operational reads/writes are site scoped now |
| `LootboxController /api/lootbox` | 7 | Site-owned operations | `/api/v1/sites/{siteId}/lootbox` | Balance/history results must identify or filter by site policy |
| `MachineDisplayController /api/machine-displays` | 19 | Site-owned | `/api/v1/sites/{siteId}/machine-displays` | All active/history/stale/batch/swap mappings |
| `NotificationController /api/notifications` | 11 | Site-owned recipient data | `/api/v1/sites/{siteId}/notifications` | All list/count/read/resolve/delete mappings |
| `ProductController /api/products` | 10 | Split global/site | `/api/v1/catalog/products` and `/api/v1/sites/{siteId}/products` | Master CRUD/lookup is global; activate/deactivate and local settings become site assortment operations |
| `ReviewController /api/reviews` | 7 | Site-owned | `/api/v1/sites/{siteId}/reviews` | Tracking, summaries, user reviews and stats are site filtered |
| `ShipmentController /api/shipments` | 11 | Site-owned | `/api/v1/sites/{siteId}/shipments` | All CRUD, status, receive and undo mappings |
| `StockMovementController /api/stock-movements` | 6 | Site-owned | `/api/v1/sites/{siteId}/stock-movements` | Existing same-site transfer is inventory movement; inter-site workflow uses `/transfers` |
| `StorageLocationController /api/storage-locations` | 4 | Site-owned | `/api/v1/sites/{siteId}/storage-locations` | All code/type list mappings |
| `SupplierController /api/suppliers` | 9 | Global catalog | `/api/v1/catalog/suppliers` | Supplier master and product assignment; later local purchasing policy belongs in site configuration |
| `TrackingController /api/tracking` | 2 | Site-owned | `/api/v1/sites/{siteId}/tracking` | Lookup only after shipment/site authorization |
| `UserController /api/users` | 9 | Split global/site | `/api/v1/identity/users` and `/api/v1/sites/{siteId}/users` | Identity lookup/admin is global; last-audit views and memberships are site scoped |

## New v1 families

| Route family | Purpose |
| --- | --- |
| `/api/v1/sites` | List the authenticated user's available sites; system-admin site management is explicit |
| `/api/v1/sites/{siteId}/memberships` | Site role/membership lifecycle |
| `/api/v1/sites/{siteId}/products` | Assortment, local price/cost, reorder and forecasting settings |
| `/api/v1/sites/{siteId}/transfers` | Draft, dispatch, receive, cancel, discrepancy and correction workflow |

## Implemented so far

- Phase 4 (reference slice): `/api/v1/sites/{siteId}/locations` (`SiteLocationController`).
- Phase 6c (.specs/phase-6-inventory, T-6c-11/T-6c-12): `/api/v1/sites/{siteId}/inventory/totals`,
  `/api/v1/sites/{siteId}/inventory/products/{productId}`,
  `/api/v1/sites/{siteId}/inventory/locations/{locationId}`,
  `/api/v1/sites/{siteId}/inventory/movements` (`SiteInventoryController`), and
  `/api/v1/sites/{siteId}/inventory/adjustments`, `/api/v1/sites/{siteId}/inventory/transfers`
  (`SiteInventoryMutationController`, `Idempotency-Key` required). Movement/mutation routes land
  under the `inventory` family rather than a separate `stock-movements` family the original
  `StockMovementController` row above predicted -- inventory quantity, movement history and
  movement mutation are one cohesive resource from the client's perspective, and a same-site
  transfer is itself an inventory operation (T-6c-4), not a distinct top-level resource. Legacy
  `/api/inventory/*` and `/api/stock-movements/*` gained `Deprecation`/`Link` headers (T-6c-13, no
  `Sunset`) pointing back at this document. `LocationInventoryController`'s row above remains
  unimplemented v1 (R-9, `.specs/phase-6-inventory/log.md`) pending a catalog-owned
  application-layer product-summary read contract.

## Compatibility removal gate

Legacy routes are removed only when web and mobile use v1, access logs show no supported legacy
traffic, contract tests are green and a rollback-compatible release has completed its stabilization
window.
