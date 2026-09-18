# Phase 4–6 closeout and Phase 7 kickoff

- Status: Approved work order
- Date: 2026-09-18
- Continues: [Phase 6 gap audit and proposed closure plan](phase-6-gap-closure.md)
- Baseline commit: `53ca9e2` (`refactor/remaining-endpoints` == `dev` == `origin/dev`);
  `origin/main` is at `78c8bbf`, **53 commits behind**

Slices A–F close the Phase 4–6 exit gate. §3 records the Phase 7 kickoff that runs in parallel.

> **Executing agent:** the ready-to-paste task prompt is in §5 at the end of this file.

---

## 0. Context

Phases 4–6 are code-complete and integrated. The next durable milestone is Phase 7 (shipments +
remaining operational modules), defined at `docs/plans/enterprise-modernization.md:568-588` and
`docs/plans/spring-domain-modular-monolith-migration.md:203-248`.

This plan closes the Phase 4–6 exit gate and produces the Phase 7 specification set.

### Four corrections to the premises this plan was requested under

All four were verified against `53ca9e2` and against the live database. They are load-bearing:

1. **A green PR already exists on this exact commit.** PR #328 (`dev` → `main`) is open at
   `53ca9e2`; its full PR Gate passed at 2026-09-17T04:27, including
   `mvn -B test -Dtest='*IT'` on JDK 21 with Testcontainers (`.github/workflows/pr-gate.yml:30-49`).
   The eight analytics/forecasting IT failures did **not** reproduce there. What is missing is
   native local evidence and the specific PostgreSQL authorization cases — not a CI run.

2. **The Phase 5 production migration is already applied.** Production `site_products` has
   **1772 rows**, exactly matching 1772 `products`. V56/V57 landed with the PR #325 deploy.
   Phase 5c T-5 needs a *record*, not an apply.

3. **The real, unrecorded blocker is different and more serious.** Production schema is at
   **V57**. `dev` carries V58–V66 and **none are applied**:

   | Migration | Production state |
   | --- | --- |
   | V58 `location_inventory_site_product_index` | index absent |
   | V59 `add_site_id_to_stock_movements` | **column absent** |
   | V60 `backfill_stock_movements_site_main` | not run |
   | V62 `stock_movements_site_indexes` | indexes absent |
   | V63 `add_envelope_context_to_event_outbox` | **4 columns absent** |
   | V64 `backfill_event_outbox_envelope_context` | not run |
   | V65 `create_command_idempotency` | **table absent** |
   | V66 `add_event_outbox_entity_id_index` | index absent |

   `StockMovement` maps `site_id` (`inventory/domain/StockMovement.java:62`), `EventOutbox` maps
   the envelope columns, `CommandIdempotency` maps the missing table. `deploy.yml` fires on push
   to `main`. **Merging PR #328 today deploys code that cannot write a stock movement, cannot
   drain the outbox, and fails every v1 idempotent mutation.** Flyway does not run at all — no
   `flyway_schema_history`, no Flyway dependency, no Flyway config.

   Decision taken: **hold #328 unmerged**; apply V58–V66 as the final step (Slice F).

4. **"Producers emit no site ID" is about Supabase broadcasts, not Kafka.** Kafka has exactly one
   producer path — the outbox drain at `services/EventOutboxService.java:271` — and it already
   carries `site_id` in the envelope (`:264`). The site-less emitters are realtime broadcasts in
   kuji (9 call sites) and shipments (2). Separately, neither Python consumer parses `site_id`.

### Also verified against the live database

Site `SECOND` is an empty shell: **0** storage locations, **0** locations, **0** site_products,
**0** memberships, no `NOT_ASSIGNED` row. Nothing can operate there yet.

---

## 1. Already closed — do not redo

The commits between `66dadc9` and `53ca9e2` closed the G-series from
`docs/plans/phase-6-gap-closure.md`, plus P5. Verify before touching any of this:

| Item | Commit | Evidence |
| --- | --- | --- |
| G1/G2 — site-blind location picker, legacy location CRUD | `f99a95b` | `apps/web/src/lib/api/locations.ts` (+93/-93), `locations.test.ts` |
| G3/G4/G5 — legacy route auth, forged actor, wrong parent | `87e06b8`, `738ed72`, `53ca9e2` | new `sites/application/LegacyMainSiteContextResolver.java`; `LocationInventoryController`, `InventoryAggregateController`, `StockMovementController`, `LocationController`, `StorageLocationController` |
| G6/G7/G8 — local refresh, dialog error states | `1797fc1`, `2cf2e13` | hidden-UI fetch removal, consolidated product refresh, dialog query gating |
| P5 — persisted uncertain-submission retry | `1797fc1` | new `apps/web/src/lib/stock-submission-recovery.ts`, wired into `use-stock-mutations.ts` + `product-form.tsx`, with tests |
| D3 local half (partial) | `a6b628e` | `scripts/setup_dev_db.py`, `scripts/sql/dev-db-{prepare,defaults,verify}.sql`. Its own record notes the baseline uses Hibernate `update`, not Flyway, and skips V61 constraints. |

Everything below was re-checked at `53ca9e2` and is genuinely open.

---

## 2. Dependency order

```
A (native gates)  ─┐
B (postgres authz) ┼─→ can run in any order, independently
C (integrity)     ─┤     C's migration is WRITTEN here, APPLIED after F1
D (docs)          ─┘

F1 (prod apply V58–V66) ──→ merge PR #328 ──→ apply C's constrain migration ──→ F2 (Flyway canonical)

E (realtime/events)  — parallel, gated on second-site enablement, blocks nothing
P7 (Phase 7 specs)   — start immediately, parallel with A–E
```

---

## Slice A — Native gate run and local unblock (Standard)

**Record:** `.specs/phase-4-6-exit-gate/{spec.md,log.md}`

1. **Unblock Mockito on Homebrew JDK 21.** `.specs/phase-6-inventory/log.md:16-22` records local
   controller tests blocked by a Mockito/Byte Buddy self-attachment error. Verified at HEAD:
   `grep -c surefire services/inventory-service/pom.xml` → `0`; there is **no
   `maven-surefire-plugin` block at all**. Reproduce the failure first, then add a minimal block
   with `<argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>`. The `@{argLine}` is
   mandatory — JaCoCo's `prepare-agent` (`pom.xml:255`) sets `argLine` and a bare override
   silently disables coverage.
2. **Run both Maven suites.** `mvn test` alone silently skips every `*IT.java` — there is no
   failsafe plugin and Surefire's default includes are `*Test`/`Test*`/`*Tests`/`*TestCase`.
   Run `./mvnw -B test` **and** `./mvnw -B test -Dtest='*IT'`. Use `./mvnw`, not a global `mvn`
   (AGENTS.md:41). The enforcer plugin fails the build on any JDK but 21.
3. **Settle the eight analytics/forecasting failures.**
   `controllers/security/AnalyticsControllerSecurityIT.java` and `ForecastControllerSecurityIT.java`
   fail on H2 because `AnalyticsService`/`ForecastService` issue Postgres-only native SQL — a
   `year` alias colliding with an H2 reserved word, and a JSONB `->>'demand_segment'` operator —
   and only once prior tests have populated `analytics_daily_rollup`/`forecast_predictions` in the
   shared `DB_CLOSE_DELAY=-1` H2 instance (`application-test.properties:8`). They are order-fragile,
   not deterministic. They **passed in CI on this commit**. Reproduce locally; if they fail, fix
   the fixture isolation. Do **not** add an exclude — `docs/plans/phase-6-gap-closure.md:53`
   explicitly forbids it. If they pass, retire the standing risk note at
   `.specs/phase-6-inventory/log.md:335-358`.
4. **Web and contract gates:** `npm run test:run` and `npx tsc --noEmit` in `apps/web` (there is no
   `typecheck` script — CI calls `tsc` directly), `npm run lint`, plus the contract regeneration
   pair.
5. **The two suites CI never runs:** `tests/contracts` (pytest; validates the Kafka event envelope
   against `tests/contracts/schemas/event_envelope.json`) and `tests/e2e`
   (`cd tests/e2e && pytest -v --timeout=120`; Docker Compose + pytest, **not** Playwright).
   `grep -rn "tests/e2e\|tests/contracts" .github/` returns nothing.

**Done when:** both Maven suites, both web gates, both Python suites and the contract freshness
check are green locally on JDK 21, with output recorded in `log.md`, and the eight-failure note is
either fixed or formally retired.

## Slice B — PostgreSQL-backed authorization proof (Full)

**Record:** `.specs/phase-4-6-legacy-authz-postgres/{spec.md,log.md,review.md,validation.md}`

Verified at HEAD: `0` files under `controllers/security/` reference `PostgreSQLContainer`;
`application-test.properties:8` is still `jdbc:h2:mem:testdb`. The 19 existing Testcontainers
classes are migration/egress/concurrency tests, not HTTP authorization.

**Cost note:** this is not greenfield. `87e06b8`/`738ed72`/`53ca9e2` substantially rewrote
`LocationControllerSecurityIT` (+80/-42), `LocationInventoryControllerSecurityIT` and
`StockMovementControllerSecurityIT`. This slice ports and extends that recent work onto Postgres.

1. Add `BasePostgresMockMvcIntegrationTest` — `PostgreSQLContainer` + `@AutoConfigureMockMvc`, no
   Kafka. Model the container lifecycle on `integration/BaseKafkaIntegrationTest.java`: start it in
   a `static {}` block, **not** `@Container`, so a cached Spring context never points at a dead
   container. Wire via `@DynamicPropertySource`.
2. Add `controllers/security/LegacyRouteAuthorizationPostgresIT.java` against `/api/locations`,
   `/api/locations/{id}/inventory`, `/api/inventory`, `/api/stock-movements`, covering the cases
   `docs/plans/phase-6-gap-closure.md:46` says are missing:
   - **revoked membership** (`is_active = false`) — grep for revoked-membership coverage in
     `controllers/security/` returns **zero hits**; it exists only for the v1 site-product surface
     (`catalog/api/SiteProductControllerIT`) and `/me/permissions` (`identity/api/MeAndSitePermissionsIT`);
   - absent membership;
   - foreign-site resource ID;
   - **wrong parent** — `locationId` naming location A, row belonging to B (the G5 defect,
     `LocationInventoryController.java:42` and its DELETE handler);
   - **forged actor ID** in the request body (G4);
   - system-admin handling under its explicit policy (cf.
     `systemAdminBypassesMembershipWithoutCreatingAMembershipRow`).
3. **State this limitation in `validation.md`:** `application-integration.properties:26` sets
   `ddl-auto=create-drop`, so even a Postgres-backed test builds its schema from Hibernate, not
   Flyway. This proves Postgres *semantics*, not migration parity. Slice F2 closes that.

**Done when:** all six scenario classes pass on Testcontainers Postgres, the H2 originals still
pass, and the Hibernate-vs-Flyway limitation is recorded.

## Slice C — Canonical NOT_ASSIGNED and the constrain migration (Full)

**Record:** `.specs/phase-4-6-site-integrity/{spec.md,log.md,review.md,validation.md}`
Closes D1 and D2 (`docs/plans/phase-6-gap-closure.md:59-61`).

Verified at HEAD: the only migrations mentioning `NOT_ASSIGNED` are V3/V10/V17, all `location_type`
CHECK enum edits. No uniqueness, no trigger.

1. **Seed `SECOND`'s NOT_ASSIGNED rows.** It has none, so `LocationService.getNotAssignedLocation`
   cannot resolve for that site at all. Pattern to follow:
   `infra/migrations/007-seed-standard-storage-locations.sql:29`.
2. **Enforce one canonical `NOT_ASSIGNED` location per site.** `locations` carries only
   `UNIQUE(storage_location_id, location_code)` (`infra/init-db/20-unified-locations.sql`), which
   forbids two rows *both* coded `NA` but not a second row under the same NA storage location with
   a different code — after which `LocationService.getNotAssignedLocation` resolves one of several
   via an unordered `.stream().findFirst()`. `NotAssignedInventoryReadParityIT.java:106-125`
   documents this precisely, and explicitly corrects an earlier claim that the invariant was
   enforced. A unique index **cannot** express the predicate (it spans `locations` →
   `storage_locations.code`), so use a trigger or a backfilled discriminator column. Pick one in
   the slice, test the concurrent-insert case. Production has no duplicates today (MAIN = 1 NA
   storage location / 1 location row), so this is a guard, not a repair.
3. **Write the deferred constrain migration** — `NOT NULL` + FK on `stock_movements.site_id`.
   V59's header defers this to "V61". **Do not use V61.** The slot sits below V66; once Slice F2
   baselines Flyway, an out-of-order version would never run. Use the next free version and note
   in its header that the V61 naming is historical. Unlike V59 this is **not** revertible by
   `deploy.yml`, which rolls back images only, never schema.
   **Write and test it here; apply it only after F1** (it depends on the V60 backfill being
   verified against real production data).

**Done when:** SECOND has canonical NA rows, the guard rejects a second NA location under
concurrency, and the constrain migration exists with a passing Testcontainers migration IT but is
not yet applied.

## Slice D — Documentation reconciliation (Standard)

**Record:** `.specs/phase-4-6-doc-reconciliation/{spec.md,log.md}`
Closes P7. Every row below is a verified contradiction at `53ca9e2`.

| File | Problem |
| --- | --- |
| `docs/plans/enterprise-modernization.md:18-31` | Status table says Phase 5 "Not started", Phase 6 "code not started", Phase 4 "not yet merged". All three shipped (PRs #320, #322, #327). |
| `.specs/phase-6-inventory/spec.md:6` | "Planning record; implementation has not started" — above a 6201-line log with all ACs closed. |
| `.specs/phase-6-web-location-migration/log.md:5,28` | "changes remain uncommitted" — committed as `f99a95b`, `2cf2e13`, `1797fc1`; tree is clean. Same record has self-superseding entries (top handoff claims independent review complete; `:20-28` describes later follow-ups still being planned). |
| `docs/baseline/api-v1-map.md:75-77` | Calls `LocationInventoryController`'s v1 row unimplemented; `SiteInventoryController.java:77` implements `/api/v1/sites/{siteId}/inventory/locations/{locationId}`. |
| `docs/runbooks/ci-cd-pipeline.md:55-58` | Says production "is at parity through V28". It is at **V57**. |
| `docs/README.md:9-26` | Omits `plans/phase-6-gap-closure.md` and all three runbooks; still marks roadmap and plan `Draft`. |
| `docs/runbooks/hetzner-cutover.md` | Still `Status: Draft` with an unchecked checklist for a cutover that already happened (`docs/plans/enterprise-modernization.md:665-667`). |
| `.claude/CLAUDE.md:136` + File Structure | Claims Playwright E2E in `tests/e2e` (it is pytest + Docker Compose) and that `tests/contracts` verifies OpenAPI (it validates the Kafka event envelope). Migration path `infra/db/migrations/` does not exist — migrations live in `services/inventory-service/src/main/resources/db/migration/`. |
| `.specs/phase-5b-catalog-facade/`, `.specs/phase-5c-site-products/` | Both self-declare Full tier (`spec.md:3-6`); both missing `review.md` and `validation.md`. |
| `apps/web` unused dep | `@playwright/test@^1.58.2` is a devDependency with no `playwright.config.ts` and no `*.spec.ts` anywhere. |
| `services/inventory-service/src/test/resources/archunit.properties.bak` | Checked-in copy with `allowStoreCreation=true`/`allowStoreUpdate=true` sitting next to the frozen config — a flip-to-update escape hatch. Decide: delete or document. |

Also resolve the `is_active` semantics contradiction, stated three incompatible ways across
`.specs/phase-5b-catalog-facade/spec.md:32-40` ("has stock somewhere"),
`.specs/phase-5c-site-products/spec.md:51-60` ("not retired from the master catalog", with
`site_products.is_stocked` for the other meaning) and
`.specs/temp-restore-legacy-inventory-counts/spec.md:39-45` (restores the global legacy flag as the
UI's Active/Inactive). Record the winning definition in `docs/specs/multi-site-data-and-api.md`;
point the others at it.

Add `.specs/phase-5c-site-products/validation.md` recording the T-5 apply that already happened:
1772 `site_products` rows = 1772 `products`, MAIN only, SECOND zero — exactly as V57 intends.

**Done when:** every row above is corrected or explicitly dispositioned, 5b/5c have their missing
Full-tier artifacts, and no durable doc contradicts the repo.

## Slice E — Realtime and event tenancy (Full; gated, parallel)

**Record:** `.specs/second-site-realtime-and-events/{spec.md,log.md,review.md,validation.md}`
Closes D4–D6. Blocks nothing in Phase 7.
**Trigger: must close before any non-MAIN user receives a login.**

1. **Server-side channel authorization.** There is exactly one `.channel()` call in the whole web
   app — `db-changes` (`apps/web/src/hooks/realtime/use-realtime-broadcast.ts:104-108`), published
   with the service-role key (`services/SupabaseBroadcastService.java:54-55,254,264-266`). No
   realtime RLS anywhere — `grep` over `infra/migrations/*.sql` finds no `realtime` references.
   Every authenticated browser receives every site's payloads, which carry raw site/product/item
   UUIDs (`:103-119`, `:141-147`, `:167-174`, `:223-233`). Move to per-site private channels with
   authorized subscription; prove a foreign-site subscribe is **denied by the configured service**,
   not by a JS predicate. Classify global catalog events separately —
   `broadcastProductUpdated` is site-less by design (`:122-147`).
2. **Tighten `isRelevantToCurrentSite`.** `apps/web/src/hooks/realtime/site-relevance.ts:11-13`
   returns `true` when the payload has no `siteId`, and `:14-16` when the client has no current
   site. Only an explicit *differing* `siteId` is dropped. Also note
   `use-realtime-broadcast.ts:159-168`: `product_updated`, `shipment_updated`,
   `notification_created` and `audit_log_created` bypass the site check entirely.
3. **Migrate the site-less inventory broadcasts.** `services/KujiBoxService.java` — 9 no-arg
   `broadcastInventoryUpdated()` calls at `:359, 487, 656, 804, 994, 1211, 1257, 1446, 1663`.
   `services/ShipmentService.java:711,860` — two genuine inventory mutations emitting site-less
   events. Add site-aware overloads for notifications
   (`SupabaseBroadcastService.java:180-189` takes no parameters at all) and audit
   (`services/AuditLogService.java:128,174`).
4. **Fix the alerting bug before the Kafka cutover.** messaging-service compares
   `previous/current_total_qty` against `reorder_point` keyed on `item_id` only
   (`src/application/alert_checker.py:105-133`), and the producer computes those totals
   **product-globally** (`EventOutboxService.java:148-153` calls `calculateTotalInventory(productId)`
   with no site filter). With two sites this is already wrong, independent of partitioning. Add
   `site_id` to both consumers' `EventEnvelope`/`NormalizedEvent`
   (`forecasting-service/src/events.py:38-48`, `messaging-service/src/events.py:111-127` — Pydantic
   `extra="ignore"` currently drops the field the producer already sends), thread it into the
   dedupe key (`messaging-service/src/worker.py:357`), and decide whether `current_total_qty`
   becomes site-scoped.
5. **Then** cut over `kafka.partitioning.site-scoped-key.enabled` (default `false`,
   `EventOutboxService.java:52-53`, read at `:216`). It is currently **unreachable in a deployed
   container** — absent from both `infra/docker-compose.yml` and `infra/docker-compose.dev.yml` —
   so wire an env override first. `integration/AdjustToKafkaIT.java:267-268,474` asserts the old
   bare-`item_id` key and must move with it. Expect a mixed-key window: `partitionKeyFor` falls
   back to the bare key whenever `siteId` is null (`:216-219`), and `EventOutbox.siteId` is
   nullable by design (`:177-181`).
6. Make `site_id` required in `tests/contracts/schemas/event_envelope.json` once producers and
   consumers agree.

**Done when:** a foreign-site subscribe is denied by Supabase itself, no inventory broadcast is
site-less, both Python consumers carry `site_id`, and the Kafka key cutover has offset evidence
and a rollback plan.

## Slice F — Flyway canonical and the production apply (Full, operational) — LAST

**Record:** `.specs/prod-schema-reconciliation/{spec.md,log.md,review.md,validation.md}`
This is the gate on merging PR #328.

### F1 — Production apply

Requires a fresh Supabase backup and explicit user go-ahead (AGENTS.md scope-and-safety:
"Do not deploy, delete data, force-push, or change production configuration without explicit
approval").

1. Fresh backup; record the snapshot id in `log.md`.
2. Re-run the drift query (§Verification) to confirm production is still exactly at V57.
3. Apply in order: **V58, V59, V60, V62, V63, V64, V65, V66**. V58/V62/V66 carry `.conf` sidecars
   containing `executeInTransaction=false` (concurrent index builds) — run those outside a
   transaction.
4. Verify V60's backfill and record counts. It is a two-pass, **sign-aware** backfill: pass 1
   resolves via `locations → storage_locations.site_id`, choosing `from_location_id` first for
   negative `quantity_change` and `to_location_id` first otherwise (a plain `COALESCE` would
   mis-assign the destination's site to a transfer's withdrawal leg); pass 2 falls back to MAIN for
   rows with no resolvable location, valid only because SECOND was seeded with no locations. Record
   total rows, rows resolved per pass, and remaining `site_id IS NULL` count.
5. Merge PR #328 — this auto-deploys via `deploy.yml`. Then verify against the deployed service: a
   stock adjustment, an outbox drain, and an idempotent v1 mutation (`Idempotency-Key` required).
6. **Then** apply Slice C's constrain migration as its own separately-recorded step.

### F2 — Flyway canonical

Closes D3, the last Phase 1 open item (`docs/plans/enterprise-modernization.md:106-110`).

- Add `flyway-core` + `flyway-database-postgresql`; configure `baseline-on-migrate` with an
  explicit baseline version matching the reconciled production state. Note `V56`'s header: `sites`
  is **not** Flyway-managed — it comes from `infra/init-db/*.sql`. Reconcile that.
- Replace the `infra/init-db/*.sql` bootstrap used by dev (`infra/docker-compose.dev.yml:29`) and
  E2E (`tests/e2e/docker-compose.e2e.yml:24`) so dev, E2E and production share one schema source.
  `scripts/sql/dev-db-verify.sql` is a usable starting point, but its record notes the baseline
  uses Hibernate `update`, not Flyway.
- Move the migration-parity ITs off `ddl-auto=create-drop`
  (`application-integration.properties:26`) onto a Flyway-applied container, so Testcontainers
  validates the schema that actually ships.
- Wire migration gating into `deploy.yml` — `docs/runbooks/ci-cd-pipeline.md:55-58` records that it
  currently "ships without migration gating; schema changes stay a manual process".

**Done when:** `flyway_schema_history` exists in production and is authoritative, dev/E2E/test all
build their schema from the same migrations, and `ddl-auto` is `none` everywhere.

---

## 3. Phase 7 kickoff — start now, parallel with A–E

### 3.1 Inventory document — `docs/baseline/phase-7-inventory.md`

Consolidate, per slice: legacy endpoints (`docs/baseline/api-v1-map.md:22-50` — 27 controllers /
213 mappings — plus the ownership list at `docs/plans/phase-6-gap-closure.md:68-84`), tables
needing `site_id` (`docs/baseline/tenant-migration-worksheet.md:18-55`), broadcast/event producers
(Slice E's inventory), web consumers, and the ArchUnit baseline edges each slice must shrink
(`services/inventory-service/src/test/resources/module-dependency-edges-baseline.txt`, 266 lines —
adding an edge requires an explicit reviewed line; removals never fail).

### 3.2 Five Full-tier `.specs/` records, in the durable completion order

| Slice | Modules | Tables needing `site_id` | Legacy routes |
| --- | --- | --- | --- |
| `phase-7-shipments` | shipments, allocations, receiving, tracking, EasyPost webhook site resolution | `shipments`, `shipment_items`, `shipment_item_allocations`, `webhook_events` | `/api/shipments`, tracking/webhook flows |
| `phase-7-displays` | displays | `machine_display` (singular — verified in production) | `/api/machine-displays`, display transfer/location detail reads |
| `phase-7-kuji-lootbox` | kuji, lootbox | `kuji_boxes`, `kuji_box_tiers`, `lootboxes`, `lootbox_*`, `coin_adjustments` | `/api/kuji-boxes`, `/api/lootbox`, `/api/inventory/by-product/{productId}` |
| `phase-7-notifications-reviews-audit` | notifications, reviews, audit | `notifications`, `reviews`, `review_daily_counts`, `audit_logs` | `/api/audit-logs`, stock audit-log dashboard reads, notifications, reviews |
| `phase-7-analytics-forecasting` | analytics, forecasting projections | `forecast_predictions`, `analytics_daily_rollup`, `analytics_category_demand_rollup`, `mv_lead_time_stats` (matview). **`analytics_monthly_rollup` does not exist in production** despite a live JPA mapping — see §5 fix (a) | analytics/forecasting APIs |

Each spec must carry the Stage F/G exit gate from
`docs/plans/spring-domain-modular-monolith-migration.md:212-216,230-241`: the module owns its
repositories, writes inventory only through `InventoryOperations`, uses module facades rather than
foreign repositories, and is site-scoped and tested.

Three things every spec inherits:

- **Expand → backfill → verify → constrain** (`docs/specs/multi-site-data-and-api.md:89-95`), with
  constrain as a separate release, following the V59/V60/V61 precedent — and using the next free
  version number, never a reserved-looking gap.
- **The `is_active` hazard.** `StockMovementService` computes `shouldBeActive = total > 0` and
  writes it to the **global** `products.is_active`. Under multi-site, one site's stock hitting zero
  hides the product at the other site (`.specs/phase-5b-catalog-facade/spec.md:32-40`). This is a
  live correctness bug the kuji and shipments slices hit directly.
- **Remove the Phase 5d gate** at its owning slice: the Custom Kuji tab currently renders an
  explicit "not yet available per-site" state at any non-MAIN site
  (`.specs/phase-5d-catalog-v1-and-web/log.md:25-34`, AC-6e).

The analytics slice additionally owns defining stable view/event ownership for forecasting-owned
output, and removing cross-service writes to another service's tables — handled through stable
views or event-derived projections, not Java module imports
(`spring-domain-modular-monolith-migration.md:239-241`).

---

## 4. Verification

Per slice, before commit:

```bash
# inventory-service — JDK 21 required; the enforcer plugin fails on anything else
cd services/inventory-service
./mvnw -B test                     # unit + ArchUnit (skips every *IT — no failsafe plugin)
./mvnw -B test -Dtest='*IT'        # integration; needs Docker for Testcontainers

# contract freshness
./mvnw -B -Dtest=OpenApiContractExportTest test
cd ../.. && git diff --exit-code packages/contracts/openapi.json
cd packages/api-client && npm run generate && npm run typecheck
cd ../.. && git diff --exit-code packages/api-client/src/schema.d.ts

# web
cd apps/web && npm run test:run && npx tsc --noEmit && npm run lint

# the two suites no workflow runs
pytest tests/contracts
cd tests/e2e && pytest -v --timeout=120
```

**Production drift check** (read-only) — run before and after F1:

```sql
select
 (select count(*) from information_schema.columns
    where table_name='stock_movements' and column_name='site_id')          as v59,
 (select count(*) from information_schema.columns where table_name='event_outbox'
    and column_name in ('site_id','event_version','causation_id','idempotency_key')) as v63,
 (select count(*) from information_schema.tables
    where table_name='command_idempotency')                                as v65;
```

Expect `0,0,0` before; `1,4,1` after.

**Second-site readiness check** — all columns must be non-zero before Slice E's gate lifts:

```sql
select s.code,
 (select count(*) from storage_locations sl where sl.site_id=s.id)   as storage_locations,
 (select count(*) from site_products sp where sp.site_id=s.id)       as site_products,
 (select count(*) from user_site_memberships m where m.site_id=s.id) as memberships
from sites s order by s.code;
```

Currently `MAIN: 12 / 1772 / 19`, `SECOND: 0 / 0 / 0`.

The PR gate on #328 remains the authoritative independent proof. Do not treat compilation as proof
of runtime, authorization, contract, migration or event behavior.

---

## 5. Task prompt for the executing agent

```
You are finishing the Phase 4–6 closeout in /Users/mjpark019/code/MiraiSD.

Read these first, in order:
  1. AGENTS.md — sources of truth and the SDD lifecycle. Follow it.
  2. docs/sdd-workflow.md — tier definitions.
  3. docs/plans/phase-6-gap-closure.md — the audit these slices come from
     (G1–G8, P1–P7, D1–D7). Slices map onto its IDs.
  4. docs/plans/phase-4-6-closeout.md — this file. It is the work order.

CURRENT STATE — verify before trusting, all checked at commit 53ca9e2:
  - refactor/remaining-endpoints == dev == origin/dev == 53ca9e2.
  - origin/main is 53 commits behind. PR #328 (dev -> main) is OPEN and fully
    green on this exact commit, including the Testcontainers IT job.
  - Production schema is at V57. V58–V66 are NOT applied. Flyway does not run
    at all (no flyway_schema_history, no dependency, no config).
  - Site SECOND is an empty shell: 0 storage locations, 0 locations,
    0 site_products, 0 memberships, no NOT_ASSIGNED row.

SCOPE: Slices A, B, C, D, then F. Slice E is separately gated — do not start it.
Phase 7 spec artifacts already exist; apply the five fixes listed below to them,
but do not begin Phase 7 implementation.

ORDER:
  A, B, C and D are independent. Start with A — it unblocks local Java testing
  for the others. F is LAST and has a hard stop (below). C writes its constrain
  migration but does not apply it until after F1.

HARD STOPS — do not do any of these without explicit per-action approval:
  - Applying any migration to production.
  - Merging PR #328 or pushing to main. Merging before F1 deploys code whose
    entities map columns production does not have; stock writes, the outbox
    drain and every idempotent v1 mutation would fail.
  - git push of any kind. Ask first, every time.
  - Deleting data, force-pushing, or changing production configuration.

DO NOT REDO — closed by commits 66dadc9..53ca9e2, verify before touching:
  - G1/G2 site-scoped location CRUD and stock selectors (f99a95b)
  - G3/G4/G5 legacy route authorization, forged actor, wrong parent
    (87e06b8, 738ed72, 53ca9e2; see sites/application/LegacyMainSiteContextResolver.java)
  - G6/G7/G8 local refresh and dialog error states (1797fc1, 2cf2e13)
  - P5 persisted uncertain-submission retry (apps/web/src/lib/stock-submission-recovery.ts)

FIVE TRAPS — each of these has already caused a real defect in this repo:
  1. `mvn test` alone silently skips EVERY *IT.java. There is no failsafe plugin.
     Always run both `./mvnw -B test` and `./mvnw -B test -Dtest='*IT'`.
     Use ./mvnw, never a global mvn. JDK 21 only; the enforcer fails otherwise.
  2. When adding the maven-surefire-plugin block in Slice A, the argLine MUST be
     `@{argLine} -XX:+EnableDynamicAgentLoading`. Omitting @{argLine} silently
     disables JaCoCo coverage.
  3. Slice C's constrain migration must NOT be numbered V61, despite V59's header
     saying so. V61 sits below V66; once Flyway is baselined in F2 an out-of-order
     version never runs. Use the next free number and say so in the header.
  4. Do NOT add a -Dtest exclude for AnalyticsControllerSecurityIT or
     ForecastControllerSecurityIT. docs/plans/phase-6-gap-closure.md:53 forbids it.
     They are order-fragile H2 failures, not deterministic ones, and they passed in
     CI on this commit. Reproduce first; fix fixture isolation if they fail.
  5. Compilation is not proof. Not of runtime, authorization, contract, migration
     or event behavior.

PHASE 7 ARTIFACT FIXES (docs/baseline/phase-7-inventory.md and .specs/phase-7-*):
  a. `analytics_monthly_rollup` does not exist in production, yet
     models/analytics/MonthlyPerformanceRollup.java:23 maps it, a repository
     exists, and no migration creates it. Resolve: dead code to delete, or a
     missing migration. Correct the inventory, the tenant-migration-worksheet and
     system-inventory.
  b. The displays table is `machine_display`, singular. Replace the
     "must be verified" placeholder in the inventory and in
     .specs/phase-7-displays/spec.md AC-1.
  c. Three controllers are missing from the inventory entirely:
     ActivityFeedController (/api/activity-feed, analytics),
     EasyPostWebhookController (/api/webhooks, shipments),
     LootboxAdminController (/api/lootbox/admin, kuji-lootbox).
  d. Inventory the deferred dimensions rather than restating the task: the
     apps/web consumers per route family, and the realtime/event producers
     (KujiBoxService 9 no-arg broadcastInventoryUpdated calls; ShipmentService
     :711,860; EasyPostWebhookService:180; AuditLogService:128,174;
     NotificationService:113). Distinguish "emits without site_id" from
     "emits nothing at all" — displays, lootbox, reviews, analytics and transfers
     are the latter.
  e. phase-7-inventory.md gate 6 asserts an `is_active` definition that
     contradicts .specs/phase-5c-site-products/spec.md:51-60. Do not leave it as
     a fourth competing voice. Slice D owns the resolution; land it in
     docs/specs/multi-site-data-and-api.md and point 5b, 5c, the temp record and
     the Phase 7 inventory at it.

PER SLICE:
  - Classify the tier and create the .specs/<id>/ record before editing.
    A and D are Standard (spec.md + log.md). B, C and F are Full (add review.md
    and validation.md).
  - Write the failing test first where a meaningful local test exists.
  - Keep a Current handoff section in log.md updated at task boundaries: status,
    next action, surviving decisions, last verified command, open risks.
  - Record material assumptions rather than stopping to ask. Ask only for
    product, security, data-loss or irreversible-deployment decisions.
  - Run the native gates in §4 of the plan before claiming a slice is done.
  - Report honestly. If tests fail, say so with the output. If a step was
    skipped, say that.

Start with Slice A. Report what you find before changing the pom.
```
