# MiraiSD Architecture Documentation

This directory contains reviewed architecture decisions, implementation specifications, and
migration plans. Documents here describe intended behavior; executable tests and migrations remain
the final source of truth.

## Current documents

| Document | Purpose | Status |
| --- | --- | --- |
| [Multi-site modernization roadmap](roadmap/enterprise-modernization.md) | Defines the reduced active scope, deferred work, cost guardrails and completion criteria | Draft |
| [Ordered multi-site modernization plan](plans/enterprise-modernization.md) | Sequences work from security through domain folders, two-site workflows, mobile and lean deployment | Draft |
| [ADR-0001](adr/0001-spring-domain-modular-monolith.md) | Records the decision to keep one Spring deployment with enforced domain boundaries | Proposed |
| [Spring domain-modular monolith specification](specs/spring-domain-modular-monolith.md) | Defines modules, ownership, dependencies, conventions, and acceptance criteria | Draft |
| [Spring modularization migration plan](plans/spring-domain-modular-monolith-migration.md) | Defines the incremental implementation sequence and quality gates | Draft |
| [Authentication and authorization](specs/authentication-and-authorization.md) | Defines trusted Supabase authentication and backend-controlled site authorization | Draft |
| [Multi-site data and API](specs/multi-site-data-and-api.md) | Defines tenant ownership, schema migration, API v1 and transfers | Draft |
| [Client applications](specs/client-applications.md) | Defines web, Expo mobile and shared client packages | Draft |
| [Events and replica readiness](specs/events-and-replica-readiness.md) | Defines active event reliability and deferred replica prerequisites | Draft |
| [Lean production platform](specs/production-platform.md) | Defines GHCR, single-host Hetzner deployment, rollback and recovery | Draft |
| [Phase 0 baseline](baseline/phase-0-baseline.md) | Records measured quality gates, scope decisions, costs and the initial MAIN backfill | Complete |
| [System inventory](baseline/system-inventory.md) | Inventories deployables, database access, schedulers, consumers and ownership | Baseline |
| [Tenant-migration worksheet](baseline/tenant-migration-worksheet.md) | Classifies current and planned tables as global, site-owned or platform-owned | Baseline |
| [API v1 map](baseline/api-v1-map.md) | Classifies all current Spring controller mappings and defines target route families | Baseline |
| [Phase cost-impact template](templates/phase-cost-impact.md) | Required cost review for each implementation phase | Template |

## Document conventions

- Architecture decisions belong in `adr/` and are immutable after acceptance except for status and
  links to superseding decisions.
- Specifications belong in `specs/` and define the target state and verifiable requirements.
- Execution plans belong in `plans/` and may evolve as implementation reveals new constraints.
- Program roadmaps belong in `roadmap/` and provide scope and traceability across specifications.
- Material deviations from an accepted ADR require a new ADR.
- Every specification requirement uses `MUST`, `SHOULD`, or `MAY` in the RFC sense.
