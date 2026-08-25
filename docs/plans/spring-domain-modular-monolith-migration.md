# Spring Domain-Modular Monolith Migration Plan

- Status: Draft
- Date: 2026-08-12
- Specification: [Spring domain-modular monolith](../specs/spring-domain-modular-monolith.md)
- Decision: [ADR-0001](../adr/0001-spring-domain-modular-monolith.md)
- Parent delivery plan: [Enterprise modernization](enterprise-modernization.md)

This is the Spring modularization workstream, not the complete MiraiSD modernization plan. When a
phase changes authentication, tenancy, events or API behavior, the corresponding dedicated
specification is authoritative.

The folder/package refactor is part of this plan. It is performed vertically: when a domain is
migrated, its controller, application logic, domain model and persistence adapter move together.
There is no bulk rename of all controllers/services/repositories at the start.

## 1. Migration principles

- Preserve behavior unless a phase explicitly introduces a versioned behavior change.
- Move vertical domains, not technical layers.
- Keep changes reviewable and independently releasable.
- Establish enforcement before broad movement so architecture debt only decreases.
- Implement the multi-site identity boundary before migrating site-owned business workflows.
- Do not combine package relocation, schema redesign and API redesign in one unbounded change.
- Keep the service buildable and deployable after every phase.

## 2. Work tracking

Each stage should be delivered through small pull requests. Every PR records:

- module and use case affected;
- classes/tables moved or introduced;
- old and new dependency edges;
- API, database and event compatibility impact;
- tests added or changed;
- rollback considerations.

No stage is complete until its exit gate is green in CI.

## 3. Stage A: Baseline and safety rails (parent Phase 3)

### Tasks

1. Capture current unit, integration, contract and build status.
2. Add ArchUnit as a test dependency.
3. Add architecture tests that inventory the current packages and freeze known violations.
4. Prohibit new production classes in the legacy technical-layer packages.
5. Document the current database table owner and major cross-package dependency edges.
6. Add required PR CI for inventory-service compile, tests and architecture tests.
7. Ensure PostgreSQL Testcontainers tests are available for persistence-sensitive behavior.

### Exit gate

- CI reliably builds and tests the existing service.
- Architecture tests detect an intentionally introduced forbidden dependency.
- Known violations are explicit and cannot increase silently.
- No production behavior has changed.

## 4. Stage B: Shared application foundations (parent Phase 3)

### Tasks

1. Create the module package skeletons without moving business behavior wholesale.
2. Introduce trusted authentication identity and correlation primitives in `shared`.
3. Move the authenticated principal completed in parent Phase 1 into the target shared security
   package without changing its validated behavior.
4. Define `AuthorizedSiteContext` and one resolver used at HTTP application boundaries.
5. Move generic exception handling, persistence configuration and outbox infrastructure into narrow
   `shared` packages.
6. Keep business-specific helpers out of `shared`.

### Exit gate

- Authentication tests reject invalid issuer, audience, signature and expiration.
- Authorization does not derive privileges from user-editable metadata.
- Correlation IDs propagate through HTTP logs and outbox creation.
- `shared` has no dependency on a business module.

## 5. Stage C: Sites and identity modules (parent Phase 4)

These modules establish the tenancy boundary required by every later site-owned module.

### Tasks

1. Move Site, Location and StorageLocation behavior into `sites`.
2. Move User, Invitation and membership behavior into `identity`.
3. Introduce `user_site_memberships` and the site-selection query API.
4. Expose narrow application facades such as `SiteDirectory` and `MembershipAuthorizer`.
5. Replace direct SiteRepository/UserRepository usage outside the owning modules.
6. Add membership lifecycle, system-administrator bypass and audit behavior.
7. Add cross-site and foreign-UUID authorization tests.

### Exit gate

- Site and identity repositories are internal to their modules.
- Every site selection is resolved from an authenticated backend user and active membership.
- Membership changes are audited.
- Cross-site access tests pass for all defined roles.

## 6. Stage D: Catalog module (parent Phase 5)

### Tasks

1. Move Product, Category and Supplier behavior into `catalog`.
2. Define the global product/SKU master contract.
3. Add site assortment (`site_products`) through a clearly assigned owner; prefer `catalog` for
   assortment and commercial overrides while forecasting policies remain in their owning context.
4. Replace cross-domain ProductRepository access with `CatalogQueries` or immutable product
   references.
5. Generate or update OpenAPI contracts without exposing JPA entities.

### Exit gate

- SKU and product invariants have focused domain tests.
- Other modules cannot use catalog repositories.
- Global and site-specific product semantics are unambiguous and tested.

## 7. Stage E: Inventory module (parent Phase 6)

### Tasks

1. Move LocationInventory, StockMovement, aggregate queries and stock operations into `inventory`.
2. Require `AuthorizedSiteContext` on every site-owned command/query.
3. Add `site_id` using expand/backfill/verify/constrain migrations.
4. Add composite constraints and site-scoped repository methods.
5. Publish a narrow `InventoryOperations` facade for receipt, adjustment, reservation and movement.
6. Include site, actor, correlation, event version and idempotency context in outbox events.
7. Use the outbox claim and consumer-idempotency foundation completed in parent Phase 2.
8. Migrate existing `/api` behavior through compatibility adapters while adding `/api/v1`.

### Exit gate

- Foreign-site IDs cannot read or mutate inventory.
- Inventory mutations, movement records, audits and outbox records commit atomically.
- Concurrent publisher tests show exclusive claims; crash/retry tests show consumer idempotency.
- No external module accesses inventory repositories.

## 8. Stage F: Shipments module (parent Phase 7)

### Shipments tasks

1. Move shipment, allocation, tracking and EasyPost behavior into `shipments`.
2. Replace direct stock persistence with `InventoryOperations`.
3. Scope shipments and webhook resolution to a site.
4. Preserve atomic receipt, audit and outbox behavior.

### Exit gate

- The shipment module owns its repositories.
- Shipment receipt changes inventory only through `InventoryOperations`.
- Shipment and webhook operations are site-scoped and tested.

## 9. Stage G: Remaining operational modules (parent Phase 7)

Migrate one module per bounded series of pull requests in this recommended order:

1. `displays`
2. `kuji`
3. `lootbox`
4. `notifications`
5. `reviews`
6. `audit`
7. `analytics`

For each module:

- establish table and rule ownership;
- move a complete vertical slice;
- replace foreign repository access with a facade, event or declared read projection;
- add site scope and tenant-consistency constraints;
- add module-level tests;
- shrink the architecture baseline.

Analytics migration includes defining stable read contracts for forecasting-owned output. Messaging
and forecasting cross-service table access is handled through stable views or event-derived
projections, not Java module imports.

### Exit gate

- Every listed module meets the specification's module definition of done.
- Forecasting and messaging access inventory-owned data through declared contracts.
- Scheduler/notification replica coordination remains deferred until the affected process is
  replicated, unless current single-process reliability testing exposes a correctness issue.

## 10. Stage H: Transfers module (parent Phase 8)

### Tasks

1. Add the transfer aggregate with `DRAFT`, `DISPATCHED`, `RECEIVED`, `CANCELLED` and
   `DISCREPANCY` states.
2. Represent source on-hand, in-transit and destination on-hand changes as immutable inventory
   movements.
3. Require source authorization for dispatch and destination authorization for receipt.
4. Require idempotency keys and optimistic versions for state transitions.
5. Make post-dispatch corrections explicit audited commands.
6. Emit versioned transition events in the mutation transaction.

### Exit gate

- The transfer module owns its aggregate and repositories.
- Duplicate and concurrent dispatch/receipt requests cannot double-apply inventory.
- Partial, damaged and missing receipts remain separately auditable.
- Cross-site permissions are tested at both ends of a transfer.

## 11. Stage I: Remove the legacy structure (parent Phase 11)

### Tasks

1. Remove compatibility adapters and obsolete technical-layer packages once no caller remains.
2. Replace frozen ArchUnit rules with strict zero-violation rules.
3. Verify that the module dependency graph is acyclic and matches the documented graph.
4. Delete superseded DTOs, mappers and repository methods.
5. Update OpenAPI, operating documentation and architecture diagrams.
6. Run the complete regression, migration, contract and security suite.

### Exit gate

- No business implementation remains in legacy top-level technical packages.
- Architecture tests have no frozen violations.
- The Spring Boot service still produces one artifact and passes deployment smoke tests.
- Existing clients remain compatible or have completed an explicitly versioned migration.

## 12. Suggested pull-request slices

Avoid PRs titled only "move package." Prefer a complete slice such as:

1. Add architecture test harness and freeze baseline.
2. Add authenticated principal and JWT claim validation tests.
3. Add membership schema and repository.
4. Add `AuthorizedSiteContext` and site-selection endpoint.
5. Migrate location listing to the `sites` module.
6. Migrate one catalog query and its controller.
7. Migrate inventory lookup with foreign-site rejection.
8. Migrate stock adjustment with audit/outbox transaction tests.
9. Add atomic outbox claim/lease behavior.
10. Migrate shipment receipt through `InventoryOperations`.

Each slice should reduce legacy dependencies while leaving production deployable.

## 13. Risk controls

| Risk | Control |
| --- | --- |
| Package moves create large merge conflicts | Move one vertical use case at a time and coordinate ownership of hotspot files |
| Hidden cross-module entity relationships | Inventory relationships in Stage A and forbid new ones |
| Authorization becomes scattered | One `AuthorizedSiteContext` resolver plus module application-boundary checks |
| Site columns contain inconsistent relationships | Composite database constraints and PostgreSQL integration tests |
| Public API changes accidentally | Contract snapshots/OpenAPI diff and compatibility controller tests |
| Events drift during internal moves | Versioned JSON schemas and producer/consumer contract tests |
| `shared` becomes a dumping ground | No business concepts in `shared`; architecture review for additions |
| Long-running migration branch diverges | Small releasable PRs merged continuously |
| Outbox is mistaken for exactly-once delivery | Document at-least-once semantics and test persisted consumer idempotency |

## 14. Workstream completion checklist

- [ ] ADR-0001 is accepted.
- [ ] Required CI and architecture tests are active.
- [ ] JWT and backend-controlled authorization baseline is complete.
- [ ] Sites and identity modules enforce membership.
- [ ] Catalog and site assortment ownership is established.
- [ ] All operational data access is site-scoped.
- [ ] Inventory exposes a narrow application facade.
- [ ] Shipment receipt uses the inventory facade.
- [ ] Inter-site transfer state machine and ledger behavior are complete.
- [ ] Remaining domains meet the module definition of done.
- [ ] Outbox claims and consumer idempotency are complete.
- [ ] No forbidden legacy package or repository access remains.
- [ ] API and Kafka contracts pass compatibility checks.
- [ ] Full test, migration, build and deployment smoke suites pass.
