# Multi-Site Data and API Specification

- Status: Draft
- Date: 2026-08-11
- Scope: PostgreSQL schema, Spring application behavior, API v1 and inter-site operations
- Security dependency: [Authentication and authorization](authentication-and-authorization.md)

## 1. Tenancy model

MiraiSD represents one organization with multiple physical sites. Sites are authorization and
operational-data boundaries, not independent organizations. Products have one global identity;
operational state belongs to a site.

Every site-owned record MUST have one unambiguous site. Every site-owned query, mutation, cache key,
event and realtime message MUST retain that identity.

## 2. Global and site-owned data

Global data includes product identity, SKU, canonical name and global classification. A
`site_products` record keyed by `(site_id, product_id)` owns assortment status, local price/cost
overrides, reorder policy, forecasting enablement/configuration and local operational settings.

Three related but distinct terms govern whether a product is available, and MUST NOT be conflated:

- **Product active** (`products.is_active`): whether a product currently has stock at any site —
  recomputed on every stock movement as `total quantity across all sites > 0`. It does not mean
  "retired from the catalog"; a product with zero stock everywhere is inactive even though it
  remains a fully valid catalog entry that can be restocked. Kuji's Active/Closed tabs and
  forecasting both depend on this exact meaning today, so it is preserved as-is rather than
  redefined.
- **Stocked** (`site_products.is_stocked`): whether one site currently carries a product in its
  local assortment, independent of Product active. A product can be Stocked at one site and not
  another. Un-stocking (de-assorting) a product retains that site's saved price, cost and reorder
  overrides rather than discarding them — they apply again if the site re-stocks the product
  later.
- **Effective availability**: the resolved, per-site read that combines Stocked with override
  fallback against the global product. A site that has never carried a product (no `site_products`
  row) always resolves as not available, falling back to the global product's settings for every
  other field; a site that has un-stocked a product (Stocked = false, row retained) resolves its
  own saved overrides instead of the global fallback.

Site-owned operational data includes:

- locations and inventory;
- stock movements and inventory audit context;
- shipments and allocations;
- machine displays;
- Kuji boxes and lootboxes where operated by site;
- reviews, notifications and audit entries;
- forecasts, prediction inputs/outputs and analytics rollups;
- transfer source/destination effects;
- event and realtime payload context.

Before migration, an owner and tenant strategy MUST be recorded for every table. No table may be
declared migrated solely because an indirect parent happens to contain a site ID.

## 3. Tenant consistency invariants

- Site-owned repository methods require `siteId`.
- Foreign-site IDs are returned as inaccessible; APIs SHOULD use `404` where revealing existence
  would be inappropriate.
- Site-specific codes and uniqueness constraints include `site_id`.
- Composite foreign keys enforce same-site parent/child relationships where feasible.
- Cross-site joins are prohibited except in explicit system-admin reporting or transfer workflows.
- Cache and idempotency keys include site identity.
- Database indexes begin with `site_id` when the dominant access path is tenant scoped.
- PostgreSQL RLS MAY provide defense in depth only after pooled-connection context safety is proven.

## 4. Required schema additions

At minimum the migration introduces:

- `user_site_memberships`;
- `site_products`;
- site ownership on all operational tables;
- transfer aggregates, lines, transitions and inventory movements;
- site/time-aware forecast and analytics uniqueness;
- event version, site, correlation and idempotency context where persisted.

A table-by-table migration worksheet MUST be produced before the first broad tenant migration. For
each table it records current owner, target owner, site source, backfill query, nullability,
foreign-key changes, unique/index changes, API readers/writers and rollback compatibility.

## 5. Migration procedure

Tenant columns follow expand/backfill/verify/constrain:

1. Add nullable `site_id` and non-blocking indexes where supported.
2. Backfill existing rows to the existing `MAIN` site through deterministic SQL.
3. Verify row counts, orphans, cross-site relationships and uniqueness collisions.
4. Deploy code that always writes and queries site identity.
5. Add foreign keys, composite constraints and non-null requirements.
6. Remove compatibility behavior only after all clients and workers have migrated.

Flyway is canonical in development, E2E and production. Hibernate `ddl-auto=update` MUST be removed.
PostgreSQL Testcontainers validates JSONB, enum, index, constraint and locking behavior.

## 6. Site context and IDOR resistance

All site-owned application commands and queries require the trusted `AuthorizedSiteContext` defined
by the security specification. Entity lookup combines entity ID and site ID at the repository query;
loading globally and checking afterward is not the normal pattern.

Background jobs, webhooks and event consumers have an equivalent trusted site-resolution mechanism.
Webhooks MUST derive site from a server-controlled external-reference mapping, not arbitrary payload
metadata.

## 7. API v1

Site operations use:

```text
/api/v1/sites/{siteId}/inventory
/api/v1/sites/{siteId}/shipments
/api/v1/sites/{siteId}/displays
/api/v1/sites/{siteId}/analytics
```

Global catalog and identity operations remain outside the site route. An endpoint inventory MUST
classify every current endpoint as global, site-owned, cross-site, webhook or administrative and map
it to its v1 replacement.

During migration, legacy endpoints MAY resolve to MAIN only for documented compatible operations.
They MUST emit `Deprecation`, `Sunset` when known, and a `Link` to migration documentation. New
clients MUST not use legacy endpoints.

OpenAPI is the source for the generated client. Compatibility checks block accidental breaking
changes. API DTOs and Kafka schemas remain distinct contracts.

## 8. Inter-site transfer aggregate

Transfers contain source site, destination site, lines, optimistic version and lifecycle state:

```text
DRAFT → DISPATCHED → RECEIVED
  │          │           └→ DISCREPANCY when expected and actual differ
  └→ CANCELLED
```

- Dispatch requires source permission and moves accepted quantity from source on-hand to in-transit.
- Receipt requires destination permission and moves actual accepted quantity from in-transit to
  destination on-hand.
- Expected, received, damaged and missing quantities remain distinct.
- Dispatch and receipt require idempotency keys and optimistic version checks.
- Post-dispatch edits are forbidden; corrections are explicit commands and ledger movements.
- Every transition writes audit and versioned outbox records in the same transaction.
- Viewing rules explicitly define whether source membership, destination membership or both are
  sufficient.

Inventory effects are immutable movements. Mutable current balances are projections guarded by the
same transaction and concurrency controls.

## 9. Realtime

Realtime channels and messages are site-specific. Subscription authorization resolves active
membership server-side. Payloads contain no data from another site. Client invalidation keys include
site ID, and site switching unsubscribes or invalidates the previous site's state.

## 10. Logical service ownership

- Inventory owns catalog, sites, memberships, inventory, shipments, transfers, displays, audits and
  outbox data.
- Forecasting owns forecast output and its processing state.
- Messaging owns notification-delivery and review-ingestion state.
- Cross-service writes to another owner's tables are prohibited.
- Cross-service reads migrate to stable database views with grants or event-derived projections.
- Database users receive least privilege consistent with the declared ownership.

## 11. Required tests

- Role/site/endpoint authorization matrix and foreign-site UUIDs.
- MAIN backfill row counts, orphans, non-null and uniqueness constraints.
- Same product with independent site settings, stock and forecasts.
- Webhook site resolution.
- API compatibility and deprecation headers.
- Realtime foreign-site subscription rejection.
- Concurrent and duplicate transfer transitions, partial receipt, damage, cancellation and
  discrepancy correction.

## 12. Acceptance criteria

- Every operational table and endpoint has an explicit tenant classification.
- No ordinary code path reads or writes site-owned data without a site predicate.
- Database constraints reject representable cross-site corruption.
- Existing production data is deterministically assigned to MAIN.
- Web and mobile use API v1 before MAIN compatibility behavior is removed.
