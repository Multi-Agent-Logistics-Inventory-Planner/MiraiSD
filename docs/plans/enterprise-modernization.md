# Ordered Multi-Site Modernization Plan

- Status: Draft
- Date: 2026-08-12
- Roadmap: [Multi-site modernization](../roadmap/enterprise-modernization.md)

## 1. Execution rule

Phases are completed in the order below. A later phase may be researched while an earlier phase is
underway, but production implementation does not depend on unfinished foundations. Every phase ends
with a releaseable system, an exit gate and a cost-impact statement.

Package/folder restructuring happens inside the relevant vertical phases. There is no repository-wide
package move.

## 2. Completion order and status

| Order | Phase | Status |
| --- | --- | --- |
| 0 | Approve scope and record baseline | Complete |
| 1 | Security, CI and database safety | Substantially complete (Flyway not yet canonical) |
| 2 | Reliable events and GHCR artifacts | Substantially complete (4 gaps reviewed and deferred, §5) |
| 3 | Architecture and contract foundation | Not started |
| 4 | Identity and sites | Not started |
| 5 | Catalog and site assortment | Not started |
| 6 | Inventory and stock movements | Not started |
| 7 | Shipments and remaining site operations | Not started |
| 8 | Audited inter-site transfers | Not started |
| 9 | Focused Expo mobile client | Not started |
| 10 | Lean Hetzner cutover | Complete |
| 11 | Close the migration | Not started |

Phases 1, 2 and 10 were substantially delivered together as one CI/CD and hosting migration effort
(PRs #299, #300, #303-#310) rather than strictly in the order this plan lists, since the security,
event-reliability and hosting-cutover deliverables shared the same underlying pipeline work. Phase 3
onward has not started and still follows this plan's stated execution order.

## 3. Phase 0 — Approve scope and record the baseline

### Deliverables

- Accept or revise ADR-0001.
- Confirm the two planned sites, their codes and initial staff/roles.
- Inventory endpoints, tables, schedulers, event consumers and current database access.
- Create the table tenant-migration worksheet and API v1 endpoint map.
- Record current lint, typecheck, tests, build time, deployment time and monthly stack cost.
- Create a cost-impact template for every later phase.

### Exit gate

- Every table and endpoint has a proposed global/site classification and owner.
- Current recurring cost and the $300/month ceiling are documented.
- The active/deferred scope is approved.

### Phase 0 records

- [Measured engineering baseline](../baseline/phase-0-baseline.md)
- [System and database-access inventory](../baseline/system-inventory.md)
- [Tenant-migration worksheet](../baseline/tenant-migration-worksheet.md)
- [API v1 endpoint map](../baseline/api-v1-map.md)
- [Per-phase cost-impact template](../templates/phase-cost-impact.md)

Phase 0 completed on 2026-08-12. All current staff will be assigned to `MAIN` with their existing
roles. The second site's code and display name are selected when its record is created in Phase 4;
they are not hardcoded application constants. Current recurring infrastructure cost is USD 24/month.

## 4. Phase 1 — Security, CI and database safety

This phase addresses present-day risks before structural or tenant changes.

### Deliverables

- Stop deriving authority from `user_metadata.role`.
- Validate JWT signature, expiration, issuer, audience and subject.
- Derive mutation actor identity from the authenticated principal, not request DTOs.
- Restrict actuator and administrative exposure.
- Make Flyway canonical; remove `ddl-auto=update`.
- Add PostgreSQL Testcontainers coverage where H2 is not faithful.
- Add required CI for web, Spring, Python services, contracts and migrations.
- Fix blocking frontend lint, typecheck, tests and lifecycle violations.
- Normalize Python dependency declarations and pytest collection.
- Add secret, dependency and container scanning using existing/free CI capabilities.

### Exit gate

- Forged metadata cannot elevate permissions.
- Migration and application tests pass against PostgreSQL.
- Required CI checks are green and branch-protected.
- No new paid recurring service has been introduced.

### Phase 1 status (verified 2026-08-28)

- [x] JWT issuer/audience/signature validation (`JwtService.java`); role authority derived from a
      backend `User` record looked up by email, not `user_metadata.role`.
- [x] `ddl-auto=update` removed; both `application.properties` and `application-dev.properties` are
      `ddl-auto=none`.
- [x] Actuator exposure restricted to `health,info,metrics`.
- [x] Testcontainers coverage added (PostgreSQL-faithful tests exist, not full parity).
- [x] Required CI (`ci.yml`) wired into `pr-gate.yml`'s `gate` job, which is the sole required check.
      Branch protection/rulesets themselves cannot be enforced without upgrading to GitHub Team on the
      current free private-repo plan; checks still run on every PR via `on: pull_request` regardless.
- [x] Dependency and secret scanning via Trivy (`fs` scan in `ci.yml`, image scan in `deploy.yml`;
      Trivy `fs` mode scans for both vulnerabilities and secrets by default).
- [ ] **Flyway is not canonical.** `pom.xml` has no Flyway dependency despite `V1`-`V49` migration
      files existing under `db/migration`. Deliberately deferred: production schema was confirmed at
      parity through V28 via direct query, but with no `flyway_schema_history` tracking, so wiring
      Flyway in now would need a one-time baseline reconciliation first. This is the one open item
      blocking a full Phase 1 close.

## 5. Phase 2 — Reliable events and immutable build artifacts

These are single-store reliability improvements and should land before multi-site event volume grows.

### Deliverables

- Verify business mutation and outbox creation share one transaction.
- Atomically claim outbox rows with recoverable leases.
- Persist event IDs in forecasting and messaging consumers for idempotency.
- Version event schemas and test all producers/consumers.
- Preserve dead-letter events and expose basic lag/outbox-age health signals in existing logs.
- Build versioned Docker images in GitHub Actions.
- Push images to GHCR and identify them by commit plus immutable digest.
- Prove deployment and rollback of a GHCR digest in a non-production environment or the current host.
- Add CI/GHCR budgets, retention and pruning rules.

### Exit gate

- Duplicate delivery creates one durable consumer effect.
- Concurrent publishers cannot own the same row simultaneously.
- The exact tested image can be deployed and rolled back without building on the server.
- GHCR/Actions usage remains within the approved budget.

### Phase 2 status (verified 2026-08-28)

- [x] Business mutation and outbox creation share one transaction: every
      `eventOutboxService.createStockMovementEvent(...)` call site sits inside a `StockMovementService`
      method already annotated `@Transactional`, so it joins that transaction rather than opening its
      own (`StockMovementService.java`, `EventOutboxService.java`).
- [x] Duplicate outbox rows are prevented at the database level: `V16__fix_duplicate_notifications.sql`
      adds a unique index on `event_outbox` keyed by `payload->>'stock_movement_id'`, and
      `EventOutboxService.createStockMovementEvent` catches `DataIntegrityViolationException` and
      logs-and-continues on a race.
- [x] Consumer-side idempotency is real, not just logged: messaging-service writes notifications via a
      `dedupe_key` unique index with `ON CONFLICT DO NOTHING`, and forecasting-service upserts
      predictions via `ON CONFLICT (item_id, computed_at) DO UPDATE` (`worker.py`, `supabase_repo.py`).
- [x] Dead-letter handling exists on both ends: failed inventory-side outbox events move to
      `event_dead_letter` after 3 attempts instead of being dropped; forecasting/messaging consumers
      route malformed or failing records to a Kafka DLQ via `DLQProducer` (`kafka_consumer.py`).
- [x] Versioned, immutable build artifacts: images are commit-SHA and digest tagged, pushed to GHCR,
      and `deploy.yml` deploys/rolls back by digest, not by building on the host (proven in production).
- [x] GHCR retention/pruning: `prune-ghcr.yml` added, weekly, keeps last 10 versions per package.
### Phase 2 open gaps — all deliberately deferred (decided 2026-08-28)

Four deliverables are not implemented. All four were reviewed and consciously deferred rather than
skipped: each is latent rather than active, each has a trigger that would make it real, and the
mitigation for the meantime is recorded below. Triggers are duplicated in §15.

**1. No atomic row-claiming with a recoverable lease.**
`EventOutboxService.publishPendingEvents` polls with a plain
`findByPublishedAtIsNullAndPublishAttemptsLessThan...` query — no `SELECT ... FOR UPDATE SKIP LOCKED`
and no lock/lease columns. Two instances would both fetch the same unpublished row and double-publish.

- *Why deferred:* exactly one `inventory-service` container runs, and `docker compose up -d` recreates
  rather than running two side by side, so there is no transient overlap either. The failure mode does
  not currently exist. Consumer-side idempotency (dedupe_key / `ON CONFLICT`) would also absorb a
  duplicate today, though relying on that is not the intended guarantee.
- *Trigger:* adding a second `inventory-service` replica, or any rolling-deploy strategy.
- *Preferred fix when triggered:* ShedLock (`shedlock-spring` + `shedlock-provider-jdbc-template`) with
  `@SchedulerLock(name="publishPendingEvents", lockAtMostFor="5m")`. Needs one new `shedlock` table.
  `lockAtMostFor` supplies the "recoverable" half of the lease. Preferred over `SKIP LOCKED` because it
  is less code and protects every `@Scheduled` job, and the parallel-throughput advantage of
  `SKIP LOCKED` is not needed at this volume.

**2. No event schema versioning.**
Neither the `EventOutbox` payload nor the Python `EventEnvelope` carries a `version`/`schema_version`
field, so producer/consumer payload drift has no explicit contract.

- *Why deferred:* the exposure is only events **in flight** at the moment of a deploy — unpublished
  outbox rows plus unconsumed Kafka messages. With consumers keeping up, that window is seconds and
  quite possibly zero events. (Kafka's 7-day retention governs how long messages persist, not how long
  they stay unconsumed, so it does not widen this window while consumers are healthy.) Additive payload
  changes are already tolerated by the Pydantic models' defaults; only renames, retypes and removals
  break. There is currently one event type and one payload shape.
- *What actually happens without it:* a mismatched event fails `EventEnvelope.model_validate`, goes to
  the DLQ, and the consumer moves on. There is no retry — and retrying would not help, since a schema
  mismatch is a deterministic failure, not a transient one. Nothing is lost: `DLQMessage` preserves
  `raw_value` base64-encoded plus original topic/partition/offset, so manual replay is possible.
- *Mitigation in the meantime — operational rule:* before deploying a breaking change to the event
  payload shape, confirm `event_outbox` has no unpublished rows and consumer lag is zero, then deploy
  inventory, forecasting and messaging together. Draining takes seconds given the 10-second poll.
- *Trigger:* Phase 7 (multiple event types sharing one topic), or any additional consumer.

**3. No lag/outbox-age health signal.**
Per-event logging exists, but nothing surfaces "oldest pending event age" or consumer lag, so a stalled
poller would be silent until someone queried the table.

- *Why deferred:* this is one metric, and building a bespoke channel for it creates a snowflake. Doing
  it properly means a metrics stack, and Phases 3-8 restructure the backend enough that dashboards and
  alert rules built now would describe a system about to change underneath them.
- *Trigger:* after Phase 8, when the system stops moving.
- *Note:* `spring-boot-starter-actuator` is present but there is **no Micrometer registry** and
  `/actuator/prometheus` is not exposed, so metrics are on-demand JSON that nothing collects.

**4. No CI/GHCR budget check.**
`prune-ghcr.yml` caps storage growth, but no automated Actions-minutes or GHCR-usage budget alert exists.

- *Why deferred:* Actions minutes are free to a generous quota and GHCR storage is cheap at this volume.
  Manual review of GitHub's billing page is sufficient; automating it now would be monitoring a number
  that is not moving.
- *Trigger:* usage trending toward a real line item.

## 6. Phase 3 — Architecture and contract foundation

### Deliverables

- Add ArchUnit and freeze the current dependency baseline.
- Prohibit new classes in legacy technical-layer packages unless explicitly exempted.
- Create the target Spring domain package skeleton.
- Introduce OpenAPI in `packages/contracts`.
- Generate a platform-neutral TypeScript client in `packages/api-client`.
- Adopt the client in one read-only web workflow.
- Add correlation ID propagation through HTTP and events.

### Folder outcome

Only foundational code moves now: shared security, site context, event infrastructure and one reference
vertical slice. Existing business domains stay in place until their phase.

### Exit gate

- Architecture violations cannot increase.
- One endpoint works through the generated client.
- Builds remain deployable with no behavioral rewrite.

## 7. Phase 4 — Identity and sites

### Deliverables

- Create `identity` and `sites` domain packages with `api`, `application`, `domain` and
  `infrastructure` subpackages as needed.
- Add backend users, global system-admin status and `user_site_memberships`.
- Backfill current users into the current MAIN site.
- Add the second planned site and explicit site-selection/effective-permission APIs.
- Introduce trusted `AuthorizedSiteContext`.
- Migrate locations/storage locations as the reference site-owned vertical slice.
- Add membership lifecycle, invitation and foreign-site UUID tests.

### Exit gate

- Active backend membership controls site access.
- Users with no membership receive no implicit MAIN access.
- `identity` and `sites` repositories are not accessed outside their modules.

## 8. Phase 5 — Catalog and site assortment

### Deliverables

- Move product, category and supplier behavior into `catalog` domain folders.
- Define the global product/SKU fields.
- Add `site_products` for assortment, local pricing/cost, reorder and forecasting settings.
- Migrate catalog APIs and web queries to API v1/generated client.
- Replace external ProductRepository access with a catalog facade/query contract.

### Exit gate

- One global product can be active/configured independently at both sites.
- Catalog repository ownership and API contracts are enforced by ArchUnit/tests.

## 9. Phase 6 — Inventory and stock movements

### Deliverables

- Move inventory entities, repositories and workflows into `inventory` domain folders.
- Add and backfill site ownership using expand/backfill/verify/constrain.
- Require site-scoped repository methods and composite tenant constraints.
- Add an `InventoryOperations` facade.
- Add site/version/correlation/actor context to inventory events.
- Migrate inventory API and web workflows to API v1.
- Run foreign-site, concurrency, idempotency and migration tests.

### Exit gate

- Inventory cannot be read or mutated through a foreign-site ID.
- Mutation, movement, audit and outbox records commit atomically.
- The legacy inventory technical-layer files have been removed or reduced to documented adapters.

## 10. Phase 7 — Shipments and remaining site operations

Migrate in this completion order:

1. Shipments, allocations, receiving, tracking and webhook site resolution.
2. Displays.
3. Kuji and lootbox.
4. Notifications, reviews and audit.
5. Analytics and forecasting projections.

Each vertical slice moves into its domain folder, adds tenant constraints, uses module facades rather
than foreign repositories, migrates API/web usage and shrinks the ArchUnit baseline.

Forecasting and analytics MUST define stable view/event ownership before the analytics slice is
complete. Cross-service writes to another service's tables are removed.

### Exit gate

- All existing operational workflows are site-aware.
- Every migrated module meets the modular-monolith definition of done.
- No client developed during the program uses a legacy endpoint.

## 11. Phase 8 — Audited inter-site transfers

### Deliverables

- Add `transfers` domain folders and the transfer aggregate/line ledger.
- Implement `DRAFT`, `DISPATCHED`, `RECEIVED`, `CANCELLED` and `DISCREPANCY` transitions.
- Represent source on-hand, in-transit and destination on-hand as auditable movements.
- Require source/destination permissions, optimistic versions and idempotency keys.
- Add explicit corrections and versioned events.
- Deliver the web workflow.

### Exit gate

- Concurrent or duplicate requests cannot double-apply stock.
- Partial, damaged and missing receipts remain independently auditable.
- Both ends of transfer authorization are tested.

## 12. Phase 9 — Focused Expo mobile client

This phase starts only after the store-floor workflows and API v1 are stable.

### Deliverables

- Add `apps/mobile` using Expo Router, TypeScript, TanStack Query and Supabase auth.
- Use the generated client and secure token storage.
- Implement site selection, inventory lookup/barcode, adjustment, receiving and transfers first.
- Add notifications/audit confirmation only if validated for launch.
- Cache safe reads; never queue offline inventory mutations.
- Add connectivity, token-expiry, membership-revocation and core-workflow E2E tests.
- Start with Expo Free; any paid tier requires a usage/cost decision.

### Exit gate

- Agreed workflows pass over Wi-Fi and cellular and fail safely offline.
- Mobile does not duplicate authoritative backend rules.
- Distribution fees and ongoing support ownership are approved.

## 13. Phase 10 — Lean Hetzner cutover

### Deliverables

- Select one measured Hetzner host with operating-system headroom.
- Reuse Docker Compose and Caddy; keep only required public ports.
- Pull digest-pinned images from GHCR.
- Store host secrets securely outside Git and images using the simplest approved mechanism.
- Gate Flyway migrations, health checks and smoke tests.
- Retain previous compatible image digests for rollback.
- Use included Supabase backups initially and complete a restoration rehearsal.
- Add basic external uptime monitoring and actionable structured logs.
- Rehearse cutover and time-box DigitalOcean overlap, normally to 2–4 weeks.

Terraform, Ansible, SOPS, full OpenTelemetry, Grafana Cloud and Sentry are not requirements for this
cutover. They may be added later through separate costed decisions.

### Exit gate

- Production deploys and rolls back an exact GHCR digest.
- Restoration and host-loss runbooks are exercised.
- DigitalOcean shutdown criteria and date are recorded.
- Projected recurring stack cost remains below the approved ceiling.

### Phase 10 status (verified 2026-08-28)

Production is on Hetzner now: `infra/docker-compose.yml` resource limits are tuned for a Hetzner
CPX21, `deploy.yml` deploys to the Hetzner host by SSH, and the DigitalOcean-specific
`deploy-backend.yml` workflow was deleted (PR #309) after `deploy.yml` proved a successful real
deploy. Digest-pinned deploy/rollback and GHCR retention are both in place. `docs/runbooks/hetzner-cutover.md`
itself is still marked `Status: Draft` with its checklist unchecked — worth updating separately, but
the cutover it describes has already happened in practice.

Two exit-gate items remain open and are tracked here rather than silently closed:

- [ ] **Basic external uptime monitoring.** Nothing currently watches the host from outside it. This is
      the one failure mode internal monitoring structurally cannot catch: if the box is gone, nothing is
      left to report it, and silence looks identical to health. Any free-tier checker (UptimeRobot,
      Healthchecks.io, Better Stack, Grafana Cloud synthetics) pinging the public URL closes this. No
      code and no container — roughly five minutes of setup, deferred only because it was bundled into
      the wider observability decision below.
- [ ] **Restoration rehearsal.** Supabase's included backups are in use, but a restore has not been
      exercised. Untested backups are an assumption, not a recovery plan.

### Observability decision (2026-08-28)

A metrics stack (Micrometer registry, Prometheus, Grafana, alerting) was scoped and **deferred until
after Phase 8**. Reasoning:

- Phases 3-8 restructure packages, add site ownership to every table, and change event payloads.
  Dashboards and alert rules built now would target a system about to change underneath them.
- Self-hosting Prometheus + Grafana on the CPX21 is the option to avoid regardless: container limits
  already total ~2.7GB of 4GB, and a TSDB plus Grafana would consume 400-750MB of the remaining
  headroom. When this is revisited, Grafana Cloud's free tier (a ~100MB local agent remote-writing to
  managed storage, with alerting included) is the better fit and keeps the $300 ceiling untouched.
- Alerting only ever reports conditions someone wrote a rule for. The work is choosing a small set of
  rules — outbox age, no-data/target-down, JVM heap headroom, restart loops, 5xx rate — not the install.

Note the distinction this rests on: infrastructure monitoring answers "is the system running," while
"is the system *correct*" is domain logic that already lives in the app. Forecast accuracy is tracked
via `ForecastController` `GET /accuracy` and the analytics Accuracy tab (`tab-accuracy.tsx`,
`components/analytics/accuracy/`), with `buildHealthBanner` already producing a good/warn/bad verdict
from WAPE, bias, under-prediction rate and week-over-week delta. That evaluation logic exists; it is
**pull-only**, rendering a banner when someone opens the page. A scheduled job in messaging-service
reusing the existing Slack webhook and APScheduler would make it push-based with no new
infrastructure — tracked as follow-up work, not part of the metrics stack.

## 14. Phase 11 — Close the migration

- Remove MAIN compatibility routes after both clients have migrated.
- Remove remaining legacy Spring technical-layer packages and ArchUnit exemptions.
- Verify every table, endpoint, event and query key has correct site ownership.
- Publish final API/event contracts and operational runbooks.
- Record actual monthly costs and compare them with the approved model.

### Exit gate

The ten initial completion criteria in the roadmap are satisfied.

## 15. Deferred trigger-based work

These are not phases required for initial completion:

| Capability | Trigger |
| --- | --- |
| Redis/shared rate limiting | More than one API process or demonstrated need for global limits |
| Worker/API process separation and distributed locks | More than one relevant process or observed duplicate scheduling risk |
| Outbox lease/atomic claiming (ShedLock) | A second `inventory-service` replica, or a rolling-deploy strategy |
| Event schema versioning | Phase 7 (multiple event types on one topic), or any additional consumer |
| Metrics stack and lag/outbox-age signal | After Phase 8, once the backend has stopped moving |
| CI/GHCR usage budget alerting | Actions minutes or GHCR storage trending toward a real line item |
| Second host and load balancer | Approved availability objective or measured capacity need |
| Terraform/Ansible/SOPS | Manual host management becomes risky or repetitive enough to justify tooling |
| OpenTelemetry/Grafana/Sentry paid tiers | Current logs/monitoring cannot meet incident needs within free/basic limits |
| Supabase PITR | Approved RPO cannot be met by included backups/export strategy |
| Managed/three-node Kafka | Kafka availability objective justifies independent failure domains |

Every triggered item needs a scoped design, recurring and migration cost, exit criteria and owner.
