# ADR-0001: Adopt a Domain-Modular Spring Monolith

- Status: Accepted
- Date: 2026-08-10
- Owners: MiraiSD maintainers
- Scope: `services/inventory-service`

Accepted as part of Phase 0 of the multi-site modernization program. The second physical store is a
committed business requirement, while separate backend deployment units are not currently required.

## Context

The inventory service is one Spring Boot application, but its packages are primarily grouped by
technical role: controllers, services, repositories, models, DTOs, and mappers. A single business
change therefore crosses many top-level packages, and any service can directly use most repositories
or entities.

MiraiSD is adding multi-site identity, authorization, inventory, and inter-site transfers. Those
capabilities require clear ownership of business rules and a reliable site boundary. They do not
currently require independently deployed services or distributed transactions.

The application already benefits from single-process transactions for workflows that update stock,
shipment state, audit records, and outbox events together. Splitting these domains into networked
services now would add eventual consistency, retries, operational overhead, and failure modes without
an ownership or scaling requirement that justifies them.

## Decision

Keep `inventory-service` as one Spring Boot build artifact, JVM process, deployment, and primary
transaction boundary. Organize its implementation into business-domain modules with mechanically
enforced dependency rules.

The initial modules are:

- `catalog`
- `sites`
- `identity`
- `inventory`
- `transfers`
- `shipments`
- `displays`
- `kuji`
- `lootbox`
- `analytics`
- `notifications`
- `reviews`
- `audit`
- `shared`

Each business module uses `api`, `application`, `domain`, and `infrastructure` subpackages. A module
owns its business rules and persistence access. Other modules interact through a deliberately small
application facade or published domain events, not through its controller, repository, mapper, or
JPA implementation.

ArchUnit tests will enforce package dependencies and prevent new violations. Migration will proceed
one vertical domain at a time; a repository-wide package move is explicitly rejected.

## Consequences

### Positive

- Business behavior becomes discoverable by domain.
- Cross-domain dependencies become visible and testable.
- Site authorization can be applied consistently at application boundaries.
- Multi-domain workflows retain local ACID transactions.
- A module can be extracted later if independent ownership and deployment become necessary.
- Refactoring can be incremental and behavior-preserving.

### Negative

- The service is still released as a unit.
- Developers must maintain module boundaries and public contracts.
- Existing entity relationships and direct repository access will require staged cleanup.
- Some domain-layer classes may remain coupled to JPA during migration; perfect framework isolation is
  not an initial goal.
- Cross-module transactions can hide coupling unless application facades and dependency tests remain
  strict.

## Alternatives considered

### Keep technical-layer packages

Rejected because it does not provide an enforceable ownership boundary for multi-site and transfer
rules, and the existing hotspots will continue growing.

### Split domains into microservices

Rejected for the current phase. MiraiSD does not have separate teams, independent data ownership, or
release cycles that justify distributed workflows. Runtime scaling of the HTTP API can occur by
replicating the existing deployable service after scheduled work and local state are made replica-safe.

### Create separate Maven modules immediately

Deferred. Java package boundaries plus ArchUnit provide a lower-risk first step. Maven modules MAY be
introduced later if compilation-level isolation provides enough benefit to justify build complexity.

## Decision validation

This decision is successful when:

1. Every production class belongs to a named module or the constrained `shared` module.
2. Architecture tests fail on forbidden cross-module dependencies.
3. Controllers do not access repositories directly.
4. Cross-module business operations use public facades or events.
5. Existing HTTP and event behavior remains compatible during migration.
6. Multi-site access tests prove that foreign-site identifiers cannot escape the site boundary.

## Revisit triggers

Reconsider this decision if a domain obtains independent team ownership, release cadence, data
ownership, scaling characteristics, or availability requirements that cannot reasonably be met by the
single deployment.
