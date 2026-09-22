# Tenant-Migration Worksheet

- Status: Baseline proposal
- Captured: 2026-08-12

## Classification rules

- **Global**: one organization-wide identity/master record; no `site_id` ownership column.
- **Site-owned**: every row belongs to exactly one site and all access requires membership.
- **Derived-site**: ownership is currently reachable through a parent, but an explicit `site_id` is
  added when it materially strengthens constraints, indexing or authorization.
- **Platform**: technical delivery state, but business events still carry `site_id` where applicable.

Migrations use expand, backfill to `MAIN`, verify, constrain and only then enforce site routes.

## Current and target tables

| Table/object | Current ownership | Target class | Logical owner | Migration action |
| --- | --- | --- | --- | --- |
| `sites` | Global registry | Global | Sites | Keep `MAIN`; create the committed second site in Phase 4 using an owner-selected immutable code and editable display name |
| `users` | Global with one global role | Global | Identity | Keep identity; move ordinary authority to memberships; add backend system-admin flag |
| `invitations` | Global | Site-owned | Identity | Add target site and site role; audit lifecycle |
| `products` | Global master plus local operational settings | Global | Catalog | Keep SKU identity; move local settings to `site_products` |
| `categories` | Global | Global | Catalog | Keep |
| `suppliers` | Global | Global | Catalog | Keep; site-specific purchasing policy belongs elsewhere if required |
| `storage_locations` | Already references site | Site-owned | Sites | Verify non-null/index/composite uniqueness |
| `locations` | Site reachable through storage location | Site-owned | Sites | Add/verify explicit ownership and composite constraints |
| `location_inventory` | Has site plus location/product | Site-owned | Inventory | Backfill/verify; enforce composite site/location references |
| `stock_movements` | Site association is incomplete/contextual | Site-owned | Inventory/audit | Add non-null `site_id`, actor, correlation and idempotency context |
| `shipments` | Defaults operationally to `MAIN` | Site-owned | Shipments | Add/backfill/require `site_id` |
| `shipment_items` | Through shipment | Derived-site | Shipments | Enforce parent ownership; add explicit site only if query/constraint evidence requires it |
| `shipment_item_allocations` | Through shipment/location | Derived-site | Shipments | Enforce same-site shipment and location constraints |
| `webhook_events` | Through tracker/shipment resolution | Site-owned | Shipments | Resolve and persist site before applying a mutation |
| `machine_display` | Location/product based | Site-owned | Displays | Add/backfill/require site and same-site location constraints |
| `kuji_boxes` | Location/product based | Site-owned | Kuji | Add/backfill/require site |
| `kuji_box_tiers` | Through box | Derived-site | Kuji | Enforce parent box ownership |
| `lootboxes` | Nullable forward-compatible `site_id` | Site-owned | Lootbox | Backfill and require site if crates differ by store |
| `lootbox_tiers` | Through lootbox | Derived-site | Lootbox | Enforce parent ownership |
| `lootbox_prizes` | Through lootbox/tier | Derived-site | Lootbox | Enforce parent ownership |
| `lootbox_plays` | Through lootbox and user | Site-owned | Lootbox | Persist site for audit/reporting |
| `coin_adjustments` | User/global economy oriented | Site-owned event | Lootbox | Persist originating site; balance policy remains global unless business decides otherwise |
| `coin_economy_config` | Singleton | Global | Lootbox | Keep global initially |
| `notifications` | Operational entity references | Site-owned | Notifications | Add/backfill/require site; user-specific delivery remains recipient scoped |
| `reviews` | User/review source | Site-owned | Reviews | Persist store attribution or reject if source cannot identify a site |
| `review_daily_counts` | User/date | Site-owned | Reviews | Change uniqueness to site/user/date |
| `audit_logs` | Entity/context dependent | Site-owned | Audit | Add/backfill/require site, actor and correlation context |
| `forecast_predictions` | Unique by product/time | Site-owned | Forecasting | Add site; unique/index by site/product/time |
| `analytics_daily_rollup` | Organization-wide | Site-owned | Analytics | Add site to dimensions and uniqueness |
| `analytics_monthly_rollup` | Does not exist | None | Analytics | Do not create; Phase 7 deletes the dead dev-only mapped code path |
| `analytics_category_demand_rollup` | Category/time | Site-owned | Analytics | Add site to dimensions and uniqueness |
| `mv_lead_time_stats` | Organization-wide view | Site-aware view | Forecasting | Redefine by site/product/supplier dimensions as required |
| `event_outbox` | Technical, payload not universally site-aware | Platform + site context | Shared events | Add claim/lease state and require site context for site events |
| `event_dead_letter` | Technical | Platform + site context | Shared events | Preserve envelope site/event/correlation identifiers |
| `subcategories` | Legacy bootstrap-only | Undecided | Catalog | Verify production usage; merge with hierarchical categories or retire through migration |
| `review_employees` | Legacy bootstrap-only | Undecided | Reviews/identity | Verify production usage; migrate to users/memberships or retire |

## New tables

| Table | Classification | Owner | Required key/constraint |
| --- | --- | --- | --- |
| `user_site_memberships` | Site-owned | Identity | Unique `(user_id, site_id)`; role and active status |
| `site_products` | Site-owned | Catalog | Primary/unique `(site_id, product_id)`; assortment and local settings |
| `transfers` | Cross-site aggregate | Transfers | Source and destination sites, state, optimistic version, idempotency |
| `transfer_lines` | Cross-site aggregate child | Transfers | Transfer/product expected, received, damaged and missing quantities |
| Consumer processed-event ledger(s) | Platform + consumer-owned | Forecasting/messaging | Unique consumer/event ID |

## Verification required before schema work

- Export the production schema and row counts without data values.
- Confirm `machine_display` row counts and site source.
- Confirm whether bootstrap-only tables still exist or contain rows.
- Confirm every current `site_id` nullable/non-null state and foreign key.
- Confirm Supabase functions, triggers, views and realtime publications not represented by JPA.

## Initial membership backfill

The membership migration creates one active `MAIN` membership for every current backend user and
copies that user's existing backend role into the membership. It does not require a manually listed
staff roster. No current user receives access to the second site automatically.
