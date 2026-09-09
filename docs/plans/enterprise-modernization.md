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
| 3 | Architecture and contract foundation | Substantially complete (Track D deferred behind Phase 4, §6) |
| 4 | Identity and sites | Substantially complete (all deliverables landed, not yet merged, §7) |
| 5 | Catalog and site assortment | Not started |
| 6 | Inventory and stock movements | Planned: one PR, five implementation/review checkpoints; code not started (§9) |
| 7 | Shipments and remaining site operations | Not started |
| 8 | Audited inter-site transfers | Deferred - no second site actually operating yet, §11 |
| 9 | Focused Expo mobile client | Deferred - gated behind Phases 5-8, §12 |
| 10 | Lean Hetzner cutover | Complete |
| 11 | Close the migration | Not started |

Phases 1, 2 and 10 were substantially delivered together as one CI/CD and hosting migration effort
(PRs #299, #300, #303-#310) rather than strictly in the order this plan lists, since the security,
event-reliability and hosting-cutover deliverables shared the same underlying pipeline work. Phase 3
onward follows this plan's stated execution order, with one documented exception: Phase 3's Track D
(adopting the generated client in a web workflow) is deferred until Phase 4 provides real
`/api/v1/sites/{siteId}/...` routes to adopt (§6).

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

### Phase 3 status (2026-08-30)

- [x] ArchUnit added (`archunit-junit5` 1.5.0) under `ArchitectureTest.java` with six rules:
      three frozen against the legacy codebase (legacy technical-layer packages — `controllers`,
      `services`, `repositories`, `models`, `dtos`, `converters` — cannot gain new classes;
      repositories may only be accessed from `services`/`repositories`; top-level packages must
      stay free of cycles), and three enforced in full from day one against the new domain-module
      skeleton (`domain` must not depend on any module's `api` or another module's
      `infrastructure`; a module must not depend on another module's `api`; `shared` must not
      depend on a business module) — these have no baseline to freeze since no code has moved
      into the skeleton yet. The three frozen rules' current violations (465, 147 and 156
      respectively) live in `archunit_store/`; `allowStoreUpdate`/`allowStoreCreation` are both
      `false` so the store can only change via a deliberate, reviewed regeneration, never as a
      side effect of a CI run. Every rule was verified against injected probe violations (added,
      confirmed the failure, removed) before being accepted.
- [x] Target Spring domain package skeleton created: `catalog`, `sites`, `identity`, `inventory`,
      `transfers`, `shipments`, `displays`, `kuji`, `lootbox`, `analytics`, `notifications`,
      `reviews`, `audit`, `shared` — each an empty package with a `package-info.java` recording
      its ownership per the module-ownership table. No business classes moved yet; that happens
      per-module in Phases 4-8.
- [x] OpenAPI in `packages/contracts`: added `springdoc-openapi-starter-webmvc-api` (docs
      endpoint only, no bundled UI). The endpoint is disabled by default
      (`springdoc.api-docs.enabled=false`) and enabled only in the `test` profile, where a new
      `OpenApiContractExportTest` (`@SpringBootTest`, random port) fetches `/v3/api-docs` and
      writes it to `packages/contracts/openapi.json`, checked into the repo rather than generated
      only at build time. `SecurityConfig` permits `/v3/api-docs/**` — harmless in production
      since springdoc never registers the endpoint there. Currently 159 endpoints. Regenerate via
      the command in `packages/contracts/README.md` after any endpoint shape change.
      A new `OpenApiConfig` fixes `servers[0].url` to `/` instead of springdoc's default
      request-derived host:port (random per test run, which made every regeneration dirty the
      committed file for no reason), and adds a `bearerAuth` security requirement plus `401`/
      `403` responses to every operation whose path isn't in `SecurityConfig`'s permitAll list —
      the generated contract previously only documented the `200` shape and said nothing about
      auth. Verified deterministic by regenerating twice in a row and diffing byte-for-byte.
      `.github/workflows/ci.yml`'s new `contracts-check` job regenerates both this file and
      `packages/api-client/src/schema.d.ts` on every relevant PR and fails the build if either
      drifts from what's checked in, so an endpoint change without a regeneration step is caught
      instead of shipping a stale contract. The same job resolves the pre-change contract via
      `git show <base>:packages/contracts/openapi.json` and runs `oasdiff breaking --fail-on ERR`
      against it, failing the build on removed/renamed routes, tightened requirements or other
      breaking changes — the compatibility check required by `multi-site-data-and-api.md` §7 and
      `client-applications.md` §4. Verified locally (via Homebrew's `oasdiff`) both that the
      current contract reports no breaking changes and that deliberately deleting an operation is
      correctly caught and fails. Skips gracefully when there's no base contract to compare
      against (e.g. the PR that first introduces the contract). Upload-for-review is disabled
      (`review: false`) so the contract never leaves CI.
- [x] Generated TypeScript client in `packages/api-client`: types generated by
      `openapi-typescript` from the contract above (`npm run generate`, checked-in
      `src/schema.d.ts`), wrapped by a hand-written `createApiClient({ baseUrl, fetch,
      getAccessToken, onUnauthorized, correlationId })` built on `openapi-fetch`, matching the
      signature required by `docs/specs/client-applications.md` §4. The package imports no
      browser/Node/React Native globals — callers inject `fetch` and token/redirect behavior — and
      injects `Authorization` and `X-Correlation-Id` headers via an `openapi-fetch` middleware.
      Typechecks clean (`npm run typecheck`).
- [ ] Adopt the client in one read-only web workflow — **blocked, not merely unstarted.**
      `multi-site-data-and-api.md` §7 requires site-owned operations to live under
      `/api/v1/sites/{siteId}/...` and says "new clients must not use legacy endpoints." The
      contract generated above has zero `/api/v1/...` paths — only the 159 existing legacy
      `/api/...` routes, none of which carry a site ID because the `sites`/`user_site_memberships`
      model doesn't exist yet (that's Phase 4). Adopting the generated client into a real web
      workflow today would mean either violating that "no legacy endpoints for new clients" rule,
      or building throwaway `/api/v1` routes ahead of the site model they're meant to scope by.
      Track D is deferred until Phase 4 lands enough of the site model for genuine `/api/v1`
      routes to exist; doing it earlier would need redoing once site scoping lands.
- [x] Correlation ID propagation through HTTP and events: a new `shared/correlation` package
      generates or reuses an `X-Correlation-Id` header per request, stores it in MDC for the
      request's lifetime, and echoes it back on the response. `application.properties` sets
      `logging.pattern.level` so every log line actually renders `correlationId=...` from MDC
      (verified with a `@SpringBootTest` + `OutputCaptureExtension` test asserting on real log
      output, not just that the filter sets MDC — the first version of this shipped without the
      pattern change and silently didn't appear in logs). `EventOutboxService` captures it from
      MDC into the outbox payload at creation time (since the ID is otherwise gone by the time
      the scheduled publisher runs later) and promotes it to a top-level `correlation_id` field
      on the Kafka envelope. Forecasting-service and messaging-service's `EventEnvelope` models
      and consumer logs were updated to read and log it. Not yet done: no correlation ID for
      work started by a scheduler rather than an HTTP request, and the Kafka envelope shape
      itself is still undocumented as a schema (the OpenAPI contract above covers the HTTP API,
      not the event envelope).

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
- Decide and record user lifecycle semantics (deactivate vs delete), since a membership's
  `active` flag is only meaningful once the account-level answer is settled.

### Exit gate

- Active backend membership controls site access.
- Users with no membership receive no implicit MAIN access.
- `identity` and `sites` repositories are not accessed outside their modules.
- A departed user's past actions remain attributable.

### Phase 4 status (2026-09-01)

- [x] `identity` and `sites` domain packages created with `api`/`application`/`domain`/
      `infrastructure` subpackages (PR #312). 31 classes relocated: `User`, `Invitation`,
      `UserRole`, their services/controllers/mappers/DTOs/repositories and
      `SupabaseAdminService`/`UserRoleConverter` into `identity`; `Site`, `Location`,
      `StorageLocation`, their exceptions, `LocationService`, both location controllers and
      three repositories into `sites`. Deliberately not moved: `LocationInventory*` (belongs to
      `inventory`, Phase 6) and `LocationAggregateController/Service/Repository` (a cross-module
      read model). The three strict module-boundary ArchUnit rules passed unchanged throughout;
      the frozen legacy stores were regenerated only because renaming classes rewrites the
      violation text of already-accepted debt.
- [x] Identity resolves by Supabase `sub` rather than email (PR #312). `V50` adds a nullable
      `users.supabase_user_id` plus a partial unique index; applied to live Supabase 2026-08-31.
      Existing rows are backfilled lazily on each user's first authenticated request rather than
      by a bulk admin-API script. Two properties are load-bearing and easy to regress, so they
      are recorded here rather than left to the diff: (1) an email match NEVER rebinds a row
      already bound to a different non-null `sub` - it returns no match and logs, because email
      is reusable and would otherwise let a signed token for one Supabase account inherit
      another's role; (2) the backfill is an atomic conditional `UPDATE ... WHERE
      supabase_user_id IS NULL`, and a zero-row result re-resolves by `sub` instead of trusting
      the request's now-stale read, since `User` has no version column or row lock.
- [x] Mutable `Map<String,String>` principal replaced by the immutable `AuthenticatedPrincipal`
      record required by `authentication-and-authorization.md` §4, carrying the backend user ID
      so downstream controllers stop re-querying by email.
- [x] `user_site_memberships` (`V52`, access-only per the role model decision below - unique
      `(user_id, site_id)`, `is_active`, `version` for optimistic locking), MAIN backfill
      (`V53`, expand/backfill per `multi-site-data-and-api.md` §5, fails loudly rather than
      silently backfilling zero rows if no MAIN site exists yet), and a second site seeded with
      placeholder `code = 'SECOND'` (`V54` - real name/code still needed from the business, safe
      to rename later since nothing else references it). `V55` adds `users.is_system_admin` as a
      flag orthogonal to `UserRole`, per the "or the separate global SYSTEM_ADMIN flag" wording in
      `authentication-and-authorization.md` §2. Implemented on `refactor/multi-site`, not yet
      merged.
- [x] `AuthorizedSiteContext` (`shared.web`, permission keys as `Set<String>` so `shared` stays
      free of a business-module dependency) resolved by a new `identity`-owned
      `SiteAccessAuthorizationFilter` for every `/api/v1/sites/{siteId}/**` request, stashed in a
      `ThreadLocal` holder (`AuthorizedSiteContextHolder`) that `sites` controllers read without
      creating a `sites -> identity` dependency. `MembershipAuthorizer` (`identity`) and
      `SiteDirectory` (`sites`) are the facades named in the migration doc. System-admin bypass is
      explicit, logged, and never creates a membership row (`AuthorizedSiteContextFactoryTest`
      asserts this).
- [x] Site-selection/effective-permission APIs: `GET /api/v1/me`, `GET /api/v1/me/sites` (empty
      list for a user with no active memberships, never implicit MAIN access), and
      `GET /api/v1/sites/{siteId}/permissions` - the first real `/api/v1/sites/{siteId}/...`
      routes, unblocking Phase 3's deferred Track D. Covered by
      `MeAndSitePermissionsIT` (active/inactive/absent/foreign-site membership, unknown site,
      system-admin bypass) and `UserSiteMembershipRepositoryIT` (unique constraint, cascade
      delete, optimistic-lock conflict - needs Docker/Testcontainers to run, not exercised by
      plain `mvn test`).
- [x] The locations vertical slice: `sites.application.LocationService` gained explicit-`siteId`
      overloads of every method that previously resolved `DEFAULT_SITE_CODE` internally
      (`sites.infrastructure.LocationRepository`/`StorageLocationRepository` gained the matching
      `findByIdAndSite_Id`/`existsByIdAndSite_Id` queries), exposed via new
      `sites.api.SiteLocationController`/`SiteStorageLocationController` under
      `/api/v1/sites/{siteId}/locations`/`.../storage-locations` - the second reference vertical
      slice after `/permissions`, and the first real business resource gated by
      `SiteAccessAuthorizationFilter`. Also fixes a latent foreign-site-UUID leak: the old
      unscoped `getLocationById(UUID)`/`getLocationsByStorageLocation(UUID)` trusted any caller's
      UUID regardless of site; the new site-scoped overloads 404 when the UUID belongs to a
      different site (`SiteLocationControllerIT`). The legacy `/api/locations`/
      `/api/storage-locations` routes, and the other four independent `DEFAULT_SITE_CODE` copies
      in `LocationInventoryService`/`StockMovementService`/`ShipmentService`/`DevSeedController`,
      are deliberately untouched - those resolve a default/"not-assigned" location for
      inventory/shipments/dev-seeding, which is Phase 6 territory, not this slice.
- [x] Membership lifecycle mutation endpoints: `UserSiteMembershipRepository` gained `activate`/
      `deactivate` (JPQL bulk updates - `updatedAt`/`version` are bound parameters, not
      `CURRENT_TIMESTAMP`, since that resolves to `java.sql.Timestamp` and Hibernate refuses to
      assign it to the entity's `OffsetDateTime` field) and `findByUserId`.
      `MembershipAuthorizer` gained `grantMembership`/`revokeMembership` (pairing
      `insertActiveIfAbsent` with `activate` to reactivate a previously revoked row - the former
      alone is a no-op on conflict, not a true upsert) and `membershipsFor`. New
      `identity.api.UserSiteMembershipController` at `/api/admin/users/{userId}/site-memberships`
      (`GET`/`PUT /{siteId}`/`DELETE /{siteId}`) is ADMIN-only throughout, including the list
      endpoint - `SecurityConfig` gates all of `/api/admin/**` to `ADMIN` at the URL-matcher
      level, ahead of any controller's own `@PreAuthorize`, so a broader
      `hasAnyRole('ADMIN','ASSISTANT_MANAGER')` read gate (as `InvitationController`'s `GET` uses)
      would be unreachable there too - an existing, previously undetected inconsistency, not
      something newly introduced here. Deliberately not nested under
      `/api/v1/sites/{siteId}/**`: `SiteAccessAuthorizationFilter` would require the acting ADMIN
      to already be a member of the target site, which is wrong for granting a user's first
      access to one. Grant/revoke are logged (actor, target user, site, action), not written to
      `AuditLogService` - that service's `StockMovementReason`/location/shipment-shaped fields
      don't fit a membership event, matching how the system-admin bypass is already only logged,
      not audited into a table.
      Covered by `UserSiteMembershipControllerIT` (role gates, not-found handling, list) and
      `UserSiteMembershipControllerKafkaIT` (grant success path, revoke-then-grant reactivation -
      split out because granting exercises `insertActiveIfAbsent`'s native `ON CONFLICT` clause,
      which needs real Postgres and isn't supported by H2 outside compatibility mode).
      This closes Phase 4's exit gate for a real business resource, not just the `/permissions`
      introspection endpoint. Implemented on `refactor/multi-site`, not yet merged.

### Role model decision (2026-09-01)

Role stays a single global property of the user (`users.role`), not a per-membership field. The
draft spec originally put `ADMIN`/`ASSISTANT_MANAGER`/`EMPLOYEE` on each `user_site_membership`
row, allowing a user's role to differ by site. Revisited: at this org's scale, role reflects a
person's job function, not their location, and per-site role only adds a bookkeeping hazard
(promoting someone requires updating every membership row, or their role silently diverges across
sites). `user_site_memberships` is access-only - `(user_id, site_id, active, timestamps, version)`,
no `role` column - answering "which sites can this user reach," while `AuthorizedSiteContext`'s
role/permissions come from the user record. Company-wide override for someone who should bypass
per-site access entirely remains the separate global `SYSTEM_ADMIN` flag, not a repeated per-site
`ADMIN` role. Revisit only if a real case emerges where trust should NOT follow the person across
sites (e.g. a temporary or restricted assignment at one location).

### User lifecycle decision (2026-09-01)

Hard delete is retained as the offboarding path for now; deactivation is deferred, not rejected.

`UserService.deleteUser` deletes the backend row, the invitation record and the Supabase auth
account. `V47`/`V48` were written specifically to make that survivable: `lootbox_plays` and
`coin_adjustments` owned by the user CASCADE-delete, acting-admin references and
`shipments.created_by` become NULL, and the `audit_logs`/`stock_movements` actor FKs were
dropped so history rows survive as bare UUIDs.

The enterprise-standard choice is deactivation (SCIM's deprovisioning signal is `active: false`,
not `DELETE`), and it is the better fit for audit integrity, temporary revocation and rehire
continuity. It is deferred because at this scale the one problem that actually bites - losing
the ability to attribute past actions - is fixed far more cheaply by capturing the actor's name
at write time, which is now done (see below). What remains accepted: lootbox/coin history is
destroyed on delete, there is no way to suspend access without destroying the record, and a
returning employee starts as a new person.

Revisit when suspension or rehire continuity is actually needed. If deactivation is adopted
later, two constraints found while scoping it must carry forward:

- Ban the Supabase account (`ban_duration`), never delete it. Deleting it while keeping the
  backend row means a returning user signs up to a NEW `sub`, and the rebind guard above will
  correctly refuse to match them by email - leaving an account no normal flow can repair.
- Backend must reject inactive users on every request. Banning in Supabase does not invalidate
  already-issued JWTs, so an unexpired token would otherwise keep working. `JwtAuthenticationFilter`
  already does a fresh per-request lookup, so the check belongs there.
- An ADMIN-only, audited "rebind identity" action is the one legitimate way to point an existing
  backend user at a new Supabase account.

### Actor attribution on stock movements (2026-09-01)

`V51` adds `stock_movements.actor_name` and backfills it from `users`, mirroring what
`audit_logs` has had since `V1`. The name is captured at write time in `StockMovement`'s
`@PrePersist`, inherited from the linked `AuditLog` (which `AuditLogService` always resolves),
so all ~31 movement creation sites are covered without touching each one; the four KujiBox paths
that persist a movement with no parent audit log set it explicitly.

Ordering constraint, not merely a nice-to-have: a name cannot be backfilled for a user who has
already been deleted. With hard delete retained above, this had to land before any further
offboarding, or that history would be permanently unattributable. It also fixes the Actor column
in the web movement history table, which rendered truncated UUIDs even for existing users.
`multi-site-data-and-api.md`'s worksheet already anticipated this (`stock_movements` "add
non-null `site_id`, actor, correlation and idempotency context"); the `site_id` half remains
Phase 6 work.

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

### Delivery structure (agreed 2026-09-09)

Use one working branch and one draft PR for Phase 6. Work through five logical commits in order;
6a–6e are implementation and review checkpoints, not separately merged releases. Additional fix
commits are allowed. Keep mechanical movement distinct from behavioral edits within the history.
Each checkpoint must pass its relevant native checks and review before the next begins; the whole
phase exit gate must pass before merging the PR.

One Full-tier execution record, [Phase 6 inventory](../../.specs/phase-6-inventory/spec.md), owns
the spec, log, review and validation for this mergeable unit. Refine each checkpoint's concrete
tasks from the caller/schema inventory before implementing it; do not create five sibling records
unless the delivery actually splits into multiple PRs.

| Checkpoint | Scope |
| --- | --- |
| 6a — Inventory module boundary | Move the vertical inventory module, introduce narrow operation/read facades, migrate external callers, and enforce architecture ownership while preserving behavior. |
| 6b — Site ownership foundation | Expand schema, update writers to supply trusted site ownership, provide deterministic backfill and verification, and preserve legacy compatibility. |
| 6c — Scoped inventory backend | Enforce site-scoped operations and tenant constraints; add compatible event context and v1 endpoints, including slim and bounded/batched totals contracts. |
| 6d — Web adoption | Migrate inventory reads and stock workflows; restore Products quantity/status from scoped totals and prove site-switch behavior. |
| 6e — Targeted refresh and exit proof | Coalesce mutation/realtime refreshes, retain full-refresh recovery, measure egress savings, and complete compatible cleanup and phase validation. |

**Commit order is not deployment order.** Before 6b implementation, record the migration mechanism,
old/new writer compatibility, backfill verification and constraint-enforcement sequence. Constraints
in 6c cannot rely on 6b having run in production merely because it is an earlier commit. If safe
enforcement requires a previously deployed writer/backfill release, split that release into a
separate PR and execution record before proceeding; do not silently weaken the rollout gate to keep
one PR. No production migration or deployment is authorized by this plan.

Before changing stock-state semantics, reconcile Phase 5b's planned removal of `products.quantity`
and `ProductStockStateWriter` with the durable global `is_active` behavior and its Kuji/forecasting
consumers. Inventory all readers/writers and record compatible replacements or explicit retained
adapters. This plan does not authorize a column drop or redefine global activity as site assortment.
Kuji/lootbox site migration stays in Phase 7; audited inter-site transfers stay in Phase 8.

### Deliverables

- Move inventory entities, repositories and workflows into `inventory` domain folders.
- Add and backfill site ownership using expand/backfill/verify/constrain.
- Require site-scoped repository methods and composite tenant constraints.
- Add an `InventoryOperations` facade.
- Add site/version/correlation/actor context to inventory events.
- Migrate inventory API and web workflows to API v1.
- Reduce inventory-totals database egress in separately reviewable Phase 6 slices: first a slim,
  site-scoped totals query/contract, then targeted web refreshes for affected products. Preserve
  legacy compatibility and full-refresh recovery when affected IDs are unknown. Detailed scope and
  measurement gates live in [Stage E: Inventory totals egress reduction](spring-domain-modular-monolith-migration.md#inventory-totals-egress-reduction).
- Run foreign-site, concurrency, idempotency and migration tests.

### Exit gate

- Inventory cannot be read or mutated through a foreign-site ID.
- Mutation, movement, audit and outbox records commit atomically.
- The legacy inventory technical-layer files have been removed or reduced to documented adapters.
- Inventory totals omit duplicated catalog metadata at the database projection and v1 response;
  known-product updates avoid whole-catalog totals refetches. Before/after measurements demonstrate
  lower returned data for the same workload, with site isolation and recovery tests passing.

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

**Deferred (2026-09-04).** The second site (seeded `code = 'SECOND'` in Phase 4) is still a
placeholder - no real name from the business yet, no memberships granted, no data owned by it.
An audited transfer ledger between two sites is speculative work while only one site actually
operates. *Trigger:* the second site goes live with real staff/inventory and stock genuinely
needs to move between locations.

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

**Deferred (2026-09-04).** This phase starts only after the store-floor workflows and API v1 are
stable - i.e. after Phases 5-8 land, per its own gate below. Not an independent decision until
then. *Trigger:* Phases 5-8 complete and there is an actual staff-workflow driver for a native
mobile client over the existing web app.

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
