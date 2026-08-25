# MiraiSD Multi-Site Modernization Roadmap

- Status: Draft
- Date: 2026-08-12
- Scope: Secure two-site operation, maintainable application boundaries and reliable deployment
- Delivery plan: [Ordered implementation plan](../plans/enterprise-modernization.md)

## 1. Mission and business trigger

MiraiSD must support a second physical store that is already planned and coming soon. The active
program therefore prepares the existing product for secure two-site operation while correcting
current security, database and event-processing risks.

The program deliberately avoids unrelated “enterprise” expansion. It does not require microservices,
micro-frontends, multiple repositories, Kubernetes, Redis, multiple hosts or paid observability
products to succeed.

## 2. Active scope

The initial modernization is complete when MiraiSD has:

- backend-controlled authentication and per-site authorization;
- one global product/SKU master with site-specific assortment and settings;
- site-scoped operational data and API behavior;
- audited, idempotent inter-site transfers;
- a domain-modular Spring backend with enforced package boundaries;
- a versioned API and generated TypeScript client used by the web application;
- a focused Expo application for validated store-floor workflows;
- reliable at-least-once event handling with outbox claims and consumer idempotency;
- required CI checks and Flyway-only schema management;
- digest-pinned images built in CI and stored in GHCR;
- one cost-conscious Hetzner host using the existing Docker Compose and Caddy stack;
- basic external uptime monitoring, host/application logs and tested backups.

## 3. Folder and package restructuring

Folder restructuring is explicitly part of the active scope.

The Spring service moves from top-level technical folders such as `controllers`, `services`,
`repositories`, `models` and `dtos` to domain folders such as `identity`, `sites`, `catalog`,
`inventory`, `shipments` and `transfers`. Each domain uses `api`, `application`, `domain` and
`infrastructure` packages where needed.

This is not a bulk file move. Each domain is moved when its vertical workflow is migrated and tested.
ArchUnit first freezes existing violations, then prevents new ones, and the legacy folders shrink
until they can be removed.

The web and mobile clients are also organized by feature. They share generated contracts and pure
utilities, not UI components by default.

## 4. Required technology additions

Only these additions are part of the active program:

| Technology | Purpose | Timing |
| --- | --- | --- |
| ArchUnit | Enforce Spring module boundaries | Architecture foundation |
| OpenAPI generation | Produce one platform-neutral TypeScript client | Contract foundation |
| Expo/React Native | Focused store-floor mobile client | After API and site workflows stabilize |
| GHCR | Store the exact Docker images built and tested by CI | Deployment baseline |
| Hetzner | Cost-effective replacement compute host | Final infrastructure migration |

Flyway, Testcontainers, Kafka, PostgreSQL, Supabase, Docker Compose, Caddy, Next.js and TanStack Query
already exist and are strengthened rather than newly introduced.

## 5. Governing specifications

| Workstream | Governing document | Active outcome |
| --- | --- | --- |
| Spring structure | [Spring modular monolith](../specs/spring-domain-modular-monolith.md) | Domain folders and enforced dependencies |
| Security | [Authentication and authorization](../specs/authentication-and-authorization.md) | Trusted JWT identity and site membership authorization |
| Data/API | [Multi-site data and API](../specs/multi-site-data-and-api.md) | Two-site schema, API v1 and transfers |
| Clients | [Client applications](../specs/client-applications.md) | Site-aware web plus focused Expo client |
| Events | [Events and replica readiness](../specs/events-and-replica-readiness.md) | Reliable single-instance processing; future replica rules retained as deferred guidance |
| Production | [Production platform](../specs/production-platform.md) | GHCR artifacts and lean single-host Hetzner deployment |

## 6. Requirement traceability

| Requirement | Specification | Acceptance evidence |
| --- | --- | --- |
| Ignore `user_metadata.role` for authority | Authentication §3–4 | Forged metadata grants no elevated authority |
| Validate JWT issuer/audience | Authentication §3 | Invalid claim tests return 401 |
| Per-site memberships | Authentication §5 | Role/membership authorization matrix |
| Global catalog and local assortment | Multi-site §2–4 | One product has independent site settings |
| Site-scope operational data | Multi-site §3–6 | Migration matrix and foreign-site UUID suite |
| API v1 and generated client | Multi-site §7; Clients §4 | Web builds through generated API client |
| Audited inter-site transfers | Multi-site §8 | Duplicate/concurrent dispatch and receipt tests |
| Domain package structure | Spring modular monolith | ArchUnit passes and legacy folders shrink to zero |
| Reliable events | Events §3–5 | Crash/retry and duplicate-delivery tests |
| Focused mobile workflows | Clients §6 | Online store-floor E2E tests |
| Immutable artifacts | Production §4–6 | CI-built digest deployed from GHCR and rolled back |
| Backup recovery | Production §9 | Successful documented restoration rehearsal |

## 7. Financial constraints

- The target recurring stack-wide cost ceiling is approximately $300/month.
- Every phase introducing a paid product or larger resource MUST include expected monthly cost,
  one-time overlap cost, free-tier limit, cancellation path and budget owner.
- Free tiers are preferred for optional tooling, but the system MUST remain operable if an optional
  SaaS product is removed.
- DigitalOcean/Hetzner overlap is temporary and time-boxed before cutover approval.
- Supabase PITR requires a separate RPO and cost decision; existing included backups plus verified
  restoration are the initial baseline.
- Expo paid tiers, paid Grafana/Sentry, managed Redis, load balancers and extra hosts are not assumed.

## 8. Deferred scope

The following are explicitly outside initial completion:

- Redis or another distributed rate-limit store;
- a second API process or host and a load balancer;
- Terraform, Ansible and SOPS automation beyond what the team can justify after the first stable host;
- full OpenTelemetry tracing and paid Grafana/Sentry plans;
- Supabase PITR unless the approved recovery-point objective requires it;
- managed Kafka or a three-node Kafka cluster;
- separate databases, repositories or additional microservices;
- micro-frontends and offline inventory mutation replay.

Deferred does not mean rejected. Each item requires a measured trigger, cost estimate and a scoped
decision before entering the active plan.

## 9. Initial completion criteria

1. The two planned stores are represented with correct memberships and isolated data.
2. User-controlled metadata cannot grant permissions.
3. All required operational workflows are site-aware and tested against foreign-site IDs.
4. Transfers produce correct, auditable source, in-transit and destination movements.
5. Domain folders and ArchUnit rules replace the legacy technical-layer structure.
6. Web uses API v1/generated contracts; the agreed mobile workflows are operational.
7. Outbox retries and duplicate events cause one durable consumer effect.
8. Required CI checks block failures.
9. Production deploys a tested GHCR digest to one Hetzner host and can roll back.
10. Backup restoration succeeds and recurring costs remain within the approved ceiling.
