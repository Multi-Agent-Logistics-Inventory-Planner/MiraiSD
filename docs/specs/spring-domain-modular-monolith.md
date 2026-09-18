# Spring Domain-Modular Monolith Specification

- Status: Draft
- Date: 2026-08-10
- Applies to: `services/inventory-service`
- Related decision: [ADR-0001](../adr/0001-spring-domain-modular-monolith.md)
- Delivery plan: [Spring modularization migration plan](../plans/spring-domain-modular-monolith-migration.md)
- Program roadmap: [Enterprise modernization](../roadmap/enterprise-modernization.md)

## 1. Purpose

This specification defines the target internal architecture of the MiraiSD Spring backend. It keeps
one deployable Spring application while establishing explicit business-module ownership, dependency
rules, transaction boundaries, and testable multi-site authorization boundaries.

This document specifies the internal Spring structure. Security, multi-site behavior, client
applications, event delivery and production infrastructure are governed by their dedicated linked
specifications. Modularization itself does not create new deployable services or split the database.

## 2. Goals

- Group code by business capability rather than technical layer.
- Make each domain's public surface small and intentional.
- Prevent direct access to another domain's repositories and implementation classes.
- Preserve local transactions for inventory, shipments, transfers, audits, and outbox events.
- Establish one mandatory site-aware application boundary for site-owned operations.
- Permit incremental migration without a flag day.
- Make future service extraction possible without designing for it prematurely.

## 3. Non-goals

- Independent deployment or versioning of Spring modules.
- A database per module.
- Network calls between modules in the same process.
- Framework-free domain models everywhere.
- Eventual consistency for workflows that currently require one transaction.
- Sharing backend entities or DTOs directly with web or mobile clients.
- Reorganizing forecasting-service or messaging-service under these Java package rules.

## 4. Target package layout

```text
com.mirai.inventoryservice
├── catalog/
├── sites/
├── identity/
├── inventory/
├── transfers/
├── shipments/
├── displays/
├── kuji/
├── lootbox/
├── analytics/
├── notifications/
├── reviews/
├── audit/
└── shared/
```

Every business module SHOULD use this internal layout:

```text
<module>/
├── api/              HTTP controllers and transport request/response models
├── application/      Use cases, transaction boundaries, commands, queries, public facade
├── domain/           Aggregates, value objects, policies, domain services and domain events
└── infrastructure/   JPA repositories, external adapters, persistence mapping and configuration
```

Small modules MAY omit an empty subpackage. A class MUST not be placed in `shared` merely because it
is used by two modules.

### 4.1 Visibility

- Cross-module callers MUST use a documented public type in the owning module's `application`
  package or consume a published event.
- Controllers and transport DTOs MUST NOT be cross-module APIs.
- Repository interfaces and persistence adapters MUST remain module-internal.
- Implementations SHOULD be package-private where Spring proxy requirements allow it.
- A public type is not automatically a supported module API; supported entry points MUST be listed
  in the owning module's package documentation or facade.

## 5. Module ownership

| Module | Owns | Initial code mapped into the module |
| --- | --- | --- |
| `catalog` | Global product master, categories, suppliers, SKU rules | Product, Category, Supplier and their controllers/services/repositories |
| `sites` | Sites and physical location topology | Site, Location, StorageLocation and location aggregate behavior. Also owns the `locations/with-counts` cross-module read projection (R-1, §7.4 and §6.2) even though its query reads `inventory`- and `displays`-owned tables. |
| `identity` | Backend users, invitations, memberships and authorization policies | User, UserRole, Invitation, UserService, InvitationService, Supabase admin adapter |
| `inventory` | Site stock, stock movements, adjustment/transfer primitives and totals | LocationInventory, StockMovement, inventory aggregates and stock services. `StockMovement` moved here from `models.audit` (R-2, Phase 6a T-3); its `AuditLog` association is an accepted existing relationship carried across the boundary (§6.1 rule 8) until `audit` itself migrates. |
| `transfers` | Audited inter-site transfer aggregate and workflow | New transfer aggregate, commands, policies and APIs |
| `shipments` | Inbound shipments, allocations, receiving and carrier tracking | Shipment, ShipmentItem, ShipmentItemAllocation, tracking and EasyPost webhook behavior |
| `displays` | Machine display assignments and lifecycle | MachineDisplay and related behavior |
| `kuji` | Kuji boxes, tiers, draws and lifecycle | KujiBox, KujiBoxTier and Kuji behavior |
| `lootbox` | Lootboxes, prizes, tiers and coin economy | Lootbox models and coin administration behavior |
| `analytics` | Operational analytics, rollups, forecast read APIs and report bundles | Rollup entities, analytics services and forecast query adapters |
| `notifications` | In-app notification definitions and state | Notification and notification application behavior |
| `reviews` | Review ingestion, summaries and daily counts | Review, ReviewDailyCount and review behavior |
| `audit` | Audit log, activity feed and audit query model | AuditLog, activity feed and audit specifications |
| `shared` | Security plumbing, site context primitive, persistence configuration, clocks, IDs and event/outbox infrastructure | JWT/filter configuration, common exceptions, event publisher infrastructure |

`ForecastPrediction` remains an externally owned projection from the forecasting service. The
`analytics` module MAY expose it through a read adapter but MUST NOT assume ownership of forecast
calculation rules.

## 6. Dependency rules

### 6.1 Universal rules

1. `api` MAY depend on its own `application` package and transport-safe types.
2. `application` MAY depend on its own `domain`, its own infrastructure ports, and another module's
   documented application facade.
3. `domain` MUST NOT depend on controllers, transport DTOs, or another module's infrastructure.
4. `infrastructure` MAY implement ports defined by its own module and depend on its own domain.
5. No module may import another module's repository, JPA adapter, controller, or internal mapper.
6. `shared` MUST NOT depend on a business module.
7. Circular module dependencies are forbidden.
8. New cross-module JPA entity relationships are forbidden. Existing relationships MUST be recorded
   and removed or encapsulated as their owning domains migrate.
9. A module MUST NOT write another module's tables through SQL, JPA, or Supabase clients.

### 6.2 Intended high-level dependencies

```text
catalog ────────────────► shared
sites ──────────────────► shared
identity ───────────────► sites, shared
inventory ──────────────► catalog, sites, identity, shared
shipments ──────────────► inventory, catalog, sites, audit, shared
transfers ──────────────► inventory, sites, audit, shared
displays ───────────────► inventory, catalog, sites, audit, shared
kuji ───────────────────► inventory, catalog, sites, audit, shared
lootbox ────────────────► catalog, sites, identity, audit, shared
analytics ──────────────► published read contracts/events, shared
notifications ──────────► identity, sites, shared
reviews ────────────────► identity, sites, shared
audit ──────────────────► shared
```

This graph is a starting constraint, not permission to couple freely. A dependency MUST correspond
to an actual use case and a narrow contract.

`inventory ──► identity` is narrow and one-directional by construction: `identity.application`
declares `LastActorActivityPort` (a two-method read contract — a user's last stock-movement
activity timestamp, single and bulk), and `inventory.application.LastActorActivityAdapter`
implements it, backed by inventory's own stock-movement storage. `identity` depends on nothing
from `inventory` — the port lives in the consumer, the adapter in the provider, per the
synchronous-facade/port pattern in section 7.1 — so this cannot combine with any dependency in the
other direction to form a cycle (rule 7), and `identity` gained no new outgoing edge from this
change. See .specs/phase-6-inventory/log.md (T-2, R-3) for the caller this replaced
(`identity.application.UserService` importing `inventory`'s `StockMovementRepository` directly,
before this port existed).

`sites.api.LocationAggregateController` (R-1, Phase 6a) is the one approved exception to "one
module MUST NOT query another module's repository" (§7.4): its native SQL joins
`locations`/`storage_locations` (`sites`), `location_inventory` (`inventory`), and
`machine_display` (`displays`) in a single statement to avoid an N+1 read. This is a documented
cross-module read projection owned by `sites`, not decomposed across the three modules or
reassigned to any single one of them — see .specs/phase-6-inventory/log.md (2026-09-09 R-1
decision, and T-6 for the actual move into `sites.api`/`sites.application`/`sites.infrastructure`).

## 7. Module interaction patterns

### 7.1 Synchronous facade

Use an application facade when the caller requires an immediate result or the operation must share
one database transaction.

```java
public interface InventoryOperations {
    ReceiptResult receive(ReceiveStock command, AuthorizedSiteContext siteContext);
}
```

Commands SHOULD use IDs and immutable values rather than exposing JPA entities. The owning module
validates its own invariants and performs its own persistence.

### 7.2 In-process domain event

Use an in-process event for secondary behavior that belongs to a different module but must complete
before the transaction commits. Listener ordering and failure behavior MUST be tested.

### 7.3 Outbox event

Use the transactional outbox for integration with forecasting-service, messaging-service, or other
processes. Integration events MUST contain a stable event ID, event type, event version, site ID when
site-owned, correlation ID, actor ID, occurred-at time, and idempotency key where applicable.

Publication is at least once. Consumers MUST persist processed event IDs or otherwise prove
idempotency. Row claiming prevents concurrent publishers but does not create exactly-once delivery.

### 7.4 Direct reads

One module MUST NOT query another module's repository. Read-heavy composition SHOULD use one of:

- a narrow query facade owned by the source module;
- a dedicated read projection with declared ownership; or
- an event-derived projection when eventual consistency is acceptable.

## 8. Transaction boundaries

- Public mutating application use cases MUST define the transaction boundary.
- Controllers MUST NOT open or coordinate transactions.
- Repositories MUST NOT be called from controllers.
- A cross-module transaction is allowed when it preserves a required invariant, but each module MUST
  be invoked through its facade.
- Audit and outbox records that describe a successful mutation MUST be written in the same database
  transaction as that mutation.
- External HTTP, email, Slack, or Kafka calls MUST NOT be required to complete while holding the
  business transaction open. Record intent and deliver asynchronously where practical.

## 9. Multi-site boundary

Every site-owned application command or query MUST accept an `AuthorizedSiteContext` containing at
least:

- authenticated backend user ID;
- selected site ID;
- the user's role or system-level authorization;
- correlation ID.

The context MUST be produced by trusted authentication and membership resolution. It MUST NOT be
constructed solely from a request header or unverified JWT metadata.

Site-owned repositories MUST expose site-scoped lookup methods. For example:

```java
Optional<LocationInventory> findByIdAndSiteId(UUID inventoryId, UUID siteId);
```

The following are required:

- A foreign-site UUID is treated as inaccessible even when it exists.
- Database constraints prevent a child row from referencing a parent in another site.
- Site-specific uniqueness includes `site_id`.
- Cache, idempotency, realtime and event keys include site identity where collisions are possible.
- System-administrator bypasses are explicit, audited and tested.
- Cross-site joins are prohibited except in explicit, audited transfer workflows. Until `transfers`
  exists (this table's own module, above), `inventory.application.StockMovementService` rejects a
  transfer whose source and destination sites differ (.specs/phase-6-inventory 6c, T-6c-4) rather
  than writing a silent cross-site movement.
- `products.quantity`/`products.is_active` remain deliberately **global** (summed/derived across
  every site), not site-scoped, until `inventory` fully owns quantity and per-site assortment
  (`site_products`) fully owns activity (.specs/phase-6-inventory 6c, T-6c-15, Row 4 of the 6b
  worksheet). `catalog.application.ProductStockStateWriter` is the sole write surface for these two
  columns; its caller set is pinned to exactly
  `inventory.application.StockMovementService`/`services.KujiBoxService`
  (`ProductStockStateWriterCallerSetTest`) so a future per-site reinterpretation of either column is
  a loud test failure, not a silent behavior change.
- Durable command idempotency (`Idempotency-Key` on a v1 mutation route) is `shared.idempotency`'s
  table, keyed `(site_id, user_id, idempotency_key)` with the trusted `AuthorizedSiteContext`'s
  site/user, never a client-supplied value (.specs/phase-6-inventory 6c, T-6c-10/T-6c-12).

## 10. API and DTO rules

- `/api/v1` transport DTOs belong to the owning module's `api` package.
- JPA entities MUST NOT be returned directly from controllers.
- Bean validation covers transport shape; domain validation covers business invariants.
- OpenAPI is the contract source for generated web and mobile clients.
- API DTOs MUST NOT be reused as Kafka event schemas.
- Renames and removals follow the versioning and deprecation policy; package migration alone MUST NOT
  change an endpoint.

## 11. Persistence rules

- Flyway is the canonical schema migration mechanism in every environment.
- Production-like integration tests use PostgreSQL through Testcontainers for JSONB, enum, locking,
  index and constraint behavior.
- Each table has one owning module documented in this specification or a later ADR.
  `location_inventory` and `stock_movements` are owned by `inventory` (Phase 6a T-3/R-2); writes
  flow only through `inventory.application.InventoryOperations` (§7.1). `sites.api
  .LocationAggregateController`'s native query is the one documented read-only exception reading
  across module-owned tables in one statement (R-1, §6.2, §7.4) — it does not write any of them.
- Cross-module foreign keys MAY exist inside the monolith, but writes flow through the owning module.
- Expand/backfill/verify/constrain is required for non-null tenant migrations.
- Migration scripts MUST be forward-safe for a rolling or rollback-capable deployment; destructive
  cleanup occurs only after all deployed versions stop using the old shape.

## 12. Enforcement

ArchUnit tests MUST be added under the inventory-service test tree. At minimum they enforce:

- controllers do not depend on repositories;
- domain packages do not depend on `api` or another module's `infrastructure`;
- business modules do not depend on controllers in other modules;
- `shared` does not depend on business modules;
- declared module dependencies are acyclic;
- no new classes are added to legacy top-level `controllers`, `services`, `repositories`, `models`,
  `dtos`, or `converters` packages after the migration gate is enabled.

During transition, known legacy violations MAY be frozen with an explicit baseline. New violations
MUST fail CI, and the baseline MUST only shrink.

## 13. Testing strategy

Each module requires:

- domain unit tests for invariants and state transitions;
- application tests for orchestration, authorization and transaction behavior;
- controller tests for validation, authorization and response contracts;
- PostgreSQL integration tests for repositories and locking;
- architecture tests for package boundaries.

Cross-module workflows require integration tests. Multi-site workflows additionally require tests
for authorized membership, missing membership, inactive membership, foreign-site UUIDs and system
administrator behavior.

## 14. Operational requirements

- Logs include correlation ID, actor ID and site ID for site-owned work.
- Basic logs/metrics identify the module or use case; tracing spans follow the same convention if
  distributed tracing is later enabled.
- Scheduled work and outbox publication MUST be separable from HTTP-only processes before replicas
  are introduced; that separation is not an initial deployment requirement.
- Process-local caches MUST use site-aware keys now and MUST be reviewed for replica behavior before
  replicas are introduced.
- Module boundaries do not imply independent runtime health; the Spring service retains one health
  and deployment boundary.

## 15. Definition of done for a migrated module

A module is migrated only when:

1. Its production classes live under its domain package.
2. Its owned tables and public facade are documented.
3. Controllers use application use cases and never repositories.
4. Other modules no longer access its repositories or infrastructure.
5. Site-owned operations require `AuthorizedSiteContext` and scoped repository access.
6. Unit, integration, API and architecture tests pass.
7. Existing API/event compatibility is retained or formally versioned.
8. Legacy-package and architecture baselines shrink.
9. Observability contains correlation and site context where applicable.

## 16. Completion criteria

The restructuring is complete when all production classes have an owner, legacy technical-layer
packages contain no business implementation, architecture rules pass without a frozen legacy
baseline, and the application still builds and deploys as one Spring Boot artifact.
