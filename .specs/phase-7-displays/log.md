# Implementation log

## Current handoff

- Status: T-1, T-2, T-3, T-4 complete. `validation.md` is written and all commands in it have been independently re-run and confirmed by the coordinating session (not taken on any subagent's word). A second independent pass (requested directly by the user after the first validation.md was written) found one more real functional bug plus an ineffective regression test and a dead client parameter; all three are now fixed (see "Second review pass fixes" below). The feature is ready for commit pending the user's decision on commit granularity.
- Next action: commit (granularity — single commit vs. split by schema/module-move/routes — is the user's call, asked and pending). After merge, two items remain as separate, later, human-gated work — neither is part of this feature's T-5 or blocks this commit: (1) optional further ArchUnit housekeeping noted during review (folding per-module infrastructure rules into one helper, an outbound cross-module-infrastructure rule) and (2) the constrain migration (NOT NULL + FK on `machine_display.site_id`) once this release has been deployed and production data reverified with no NULLs, per V68's precedent for stock_movements/shipments.

## Second review pass fixes (2026-09-25)

A second independent review (after `validation.md` was first written) found:
- **Real bug**: `SiteMachineDisplayController.setSiteDisplay` (v1 POST) discarded the just-saved
  `MachineDisplay` and re-fetched "the" active display for the machine via `getCurrentDisplay`,
  which returns the oldest active display first. On a machine that already showed product A,
  adding product B returned a 201 whose body was still product A's display. Fixed by adding
  `MachineDisplayService.toResponseDTO(MachineDisplay)` (maps an already-loaded entity directly,
  no re-query) and having the controller call it with the entity `setDisplay` already returned.
  `SiteMachineDisplayControllerAuthorizationIT`'s multi-product test now asserts the second POST's
  response body (`$.productId`) matches the product just added, not just that both ended up active
  — the prior version of that test would have passed against the buggy code.
- **Ineffective test**: the DELETE actor-spoofing regression test asserted
  `stored.get(0).getActorId()`, but clearing a display only sets `endedAt` — it never rewrites the
  row's `actorId` (set once, at creation). The clear operation's own actor is recorded on the
  `DISPLAY_REMOVED` audit log instead, so the old assertion would keep passing even if
  `clearDisplay` started trusting a spoofed `actorId` query param again. Fixed to assert
  `AuditLogRepository.findByReasonOrderByCreatedAtDesc(DISPLAY_REMOVED, ...)`'s actor instead.
- **Dead client parameter**: `apps/web/src/lib/api/site-machine-displays.ts`'s
  `clearSiteMachineDisplay`/`clearSiteDisplayById` and the corresponding mutation hooks
  (`useClearMachineDisplayMutation`/`useClearDisplayByIdMutation`) still accepted and transmitted
  an `actorId` even though the v1 DELETE routes never read one (removed from the controller's
  method signature entirely, not just overwritten) and the regenerated OpenAPI contract no longer
  declares it. Not exploitable (Spring ignored the extra query param), but a misleading client
  contract. Removed `actorId` from both API functions, both hooks' variable types, and
  `location-detail-sheet.tsx`'s one call site (`useClearDisplayByIdMutation`'s only consumer). Left
  `actorId` alone on the other hooks (set/swap/batch/renew) — those still exist as real DTO fields
  the backend accepts-and-overwrites via `contextActorId()`, not removed-from-the-contract fields,
  so they were out of scope for this specific finding.
- **Test bug found while fixing the above** (not one of the review's findings, caught during
  re-verification): the DELETE actor-spoofing test's first fix attempt asserted
  `AuditLog.getActorId()`, which failed with `expected: <adminId> but was: null`.
  `AuditLog.actorId` is a read-only shadow column (`insertable = false, updatable = false`)
  mirroring the `user` association's FK; `AuditLogService` only ever sets `.user(...)` on the
  builder, never `.actorId(...)`, and the test's fresh repository query returns the
  same-persistence-context identity-cached instance rather than a re-hydrated row, so the shadow
  field is never populated within that transaction. Fixed by asserting
  `getUser().getId()` instead, which is the field actually set.
- Re-verified after all of the above, independently by the coordinating session: backend
  `./mvnw -o compile` clean, `./mvnw -o test` 514/514, `./mvnw -o test -Dtest='*IT'` 612/612
  (unchanged counts — no tests added or removed, only fixed); web `npx vitest run` 468/468 (one
  test needed a matching update after the `actorId` parameter removal —
  `use-machine-display-mutations.test.tsx`'s `clearSiteMachineDisplay` assertion expected a fourth
  `undefined` argument that no longer exists), `npx tsc --noEmit` clean.
- Decisions that must survive compaction: `machine_display` is the verified production table (singular, confirmed live against Supabase `information_schema`, 879 rows, 2026-09-24); `machine_display` has no `site_id` column today, and `locations` itself has no `site_id` — site is derived two hops away through `storage_locations.site_id`; all 879 production rows resolve to the single MAIN site with zero cross-site or missing-`site_products` inconsistencies (verified by join, see T-1 evidence below); site constraints follow verified production evidence; constrain (NOT NULL + FK on `machine_display.site_id`) is explicitly held for a later, separately released migration per T-2's own scope and spec.md's AC-2 wording — it is not part of any task in this feature record, including T-5 (T-5 is ArchUnit-baseline review and native validation only, per spec.md). `MachineDisplay`/`MachineDisplayRepository`/`MachineDisplayService`/`MachineDisplayController`/the seven display DTOs now live in `displays.{domain,infrastructure,application,api}`; `DisplayQueries`/`DisplayOperations` are the only sanctioned way for `AnalyticsService`, `ProductReportBundleService`, and `KujiBoxService` to reach display data — a new consumer touching `MachineDisplayRepository` directly fails `noProductionClassOutsideDisplaysDependsOnDisplaysInfrastructure`. Kuji's auto-attach/detach still does not write DISPLAY_SET/DISPLAY_REMOVE audit or stock-movement entries the way the direct API does — preserved intentionally, not fixed by this move, flagged for the kuji slice as a product decision. `KujiBox.machineDisplay` is still a `@ManyToOne` into `displays.domain.MachineDisplay` (the `models -> displays` edge); replacing it with a UUID column is the kuji slice's job. `SiteMachineDisplayController` (`/api/v1/sites/{siteId}/machine-displays`) now exists alongside the legacy (still-working, now-deprecated) `MachineDisplayController`; every `MachineDisplayService` mutation/read the new controller calls has a site-scoped overload (`UUID siteId` as the first parameter) that verifies the machine/display belongs to that site via `LocationService.getLocationById(siteId, id)` or `MachineDisplayRepository.findByIdAndSite_Id` *before* delegating to the existing (unscoped, unchanged) legacy method body — a foreign-site id is rejected before any further entity lookup proceeds, matching AC-3. The legacy overloads and routes are untouched.
- Last verified (post-review-fix, independently re-run by the coordinating session, not taken on the fix pass's own report): `./mvnw -o compile` clean; `./mvnw -o test` — 514/514 pass; `./mvnw -o test -Dtest='*IT'` — 612/612 pass (2026-09-24, includes the expanded 9-case `SiteMachineDisplayControllerCrossSiteAccessIT`, new `MachineDisplaySiteBackfillIT`, new `LegacyMachineDisplayDeprecationHeadersIT`, new `SiteMachineDisplayControllerAuthorizationIT` proving the actor-id fix). `./mvnw -o test -Dtest='*Architecture*,*CallerSetTest'` — 16/16 in isolation. `apps/web`: `npx vitest run` — 468/468 pass; `npx tsc --noEmit` — clean. `SiteMachineDisplayController` source read directly to confirm `contextActorId()`/`AuthorizedSiteContextHolder.require().backendUserId()` overrides every client-supplied actor value and the DELETE routes carry no `actorId` query param at all. `use-machine-display-mutations.ts` source read directly to confirm all 9 `invalidateQueries` calls use `["machine-displays", variables.siteId]`. See `.specs/phase-7-displays/validation.md` for the full validation record.
- Open risks/questions: None blocking commit. T-1's endpoint count for the legacy controller was corrected from 17 to 19 while building the v1 mirror (see the amended T-1 evidence note below) — the api-v1-map.md row already said 19, so this was a T-1 write-up error, not a functional gap. Two further items are deliberately left for later, separately released work — neither is part of this feature's T-5 (T-5 is ArchUnit-baseline review and native validation only): folding per-module ArchUnit infrastructure rules into one helper plus an outbound cross-module-infrastructure rule (noted during review, not required by any AC here), and the constrain migration (NOT NULL + FK on `machine_display.site_id`, held per T-2/AC-2's own wording). See "Next action" above.

## T-1 evidence (2026-09-24)

- **Table**: `MachineDisplay.java` maps `@Table(name = "machine_display")`; confirmed live via Supabase against `information_schema.columns` — columns `id, location_type, machine_id, product_id, started_at, ended_at, actor_id, location_id`. No migration file creates it (it predates the Flyway migration history at `services/inventory-service/src/main/resources/db/migration`); `V25`/`V26` only reference it in passing.
- **Row count / site distribution**: 879 rows, all resolving through `location_id -> locations.storage_location_id -> storage_locations.site_id` to one site (MAIN, `e898b072-7e51-4399-96fa-173eaa57eded`). Zero rows join to a `site_products` row missing for `(resolved site, product_id)` — same-site consistency is already clean on production data.
- **Routes**: `MachineDisplayController` (`/api/machine-displays`, `@PreAuthorize` ADMIN/ASSISTANT_MANAGER/EMPLOYEE) — 19 endpoints (corrected from an earlier miscount of 17; matches `docs/baseline/api-v1-map.md`'s row): set/batch/swap/batch-swap/batch-clear/renew/clear-by-machine/clear-by-id, active-by-machine, current-by-machine, all-active, all-active-paged, by-type, stale, stale-by-type, machine-history(+paged), product-history, delete-history. None accept or resolve a site parameter; `LocationAggregateController` (`/api/locations`, `sites` module) is the precedent to follow — it resolves site via `LegacyMainSiteContextResolver` before reads.
- **Web consumers**: `apps/web/src/lib/api/machine-displays.ts`, `hooks/queries/use-machine-displays.ts`, `hooks/mutations/use-machine-display-mutations.ts`, consumed by `components/machine-displays/transfer-display-dialog.tsx`, `components/locations/location-detail-sheet.tsx`, `components/products/product-modal.tsx`, and `app/(dashboard)/analytics/_components/longest-running-displays-card.tsx` (this last one and `product-modal.tsx` were not named in the phase-7 baseline's web-consumer table — add them to that inventory). No dedicated realtime channel for `machine_display`; refresh rides the generic inventory-refresh event, consistent with the baseline note that displays emit no inventory event.
- **Java-side consumers of `MachineDisplay`/`MachineDisplayRepository` beyond the controller/service**: `catalog.application.CatalogEntityAccess`, `MachineDisplayCleanupPort` (+ `displays.application.MachineDisplayCleanupAdapter`), `ProductDeletionCoordinator`, `AnalyticsService`, `ProductReportBundleService`, `KujiBoxService`/`KujiBox` (kuji boxes track an associated display), `InventoryOperations`, `DevSeedController`. These are the foreign-repository edges T-3 needs to replace with `displays`-owned facades/contracts.

## T-2 evidence (2026-09-24)

- Migrations `V72__add_site_id_to_machine_display.sql` (expand, nullable, no FK), `V73__backfill_machine_display_site_main.sql` (backfill via `location_id -> locations.storage_location_id -> storage_locations.site_id`, MAIN fallback for orphans, matching V60/V70's fail-loudly-if-no-MAIN precedent), `V74__machine_display_site_indexes.sql` (+`.conf` disabling the wrapping transaction) with `CONCURRENTLY` indexes on `(site_id, location_type, machine_id, ended_at)` and `(site_id, product_id)` — mirrors V59/V60/V62 and V69/V70/V71's shipments precedent exactly. Constrain (NOT NULL + FK) deferred to T-5, matching V68's precedent.
- `models/MachineDisplay.java` gained a nullable `@ManyToOne Site site` field (JPA-nullable pending the later constraining migration, same doc-comment pattern as `Shipment.site`).
- All 7 real `MachineDisplay` construction sites in `MachineDisplayService` now set `.site(location.getStorageLocation().getSite())` (or `currentLocation`/`targetLocation` for the swap paths), plus the one real write path in `KujiBoxService` (auto-attach display when opening a kuji box at a machine). `DevSeedController`'s two dev-only seed endpoints do not set `.location(...)` either today (pre-existing gap) and are left untouched — out of scope, not a production write path.
- `./mvnw -o compile` passes clean after these changes.

## T-3 evidence (2026-09-24)

Design produced by mirai-spring-architect first (facade/port shapes, edge changes, ArchUnit plan), then implemented directly. The architect's review corrected the T-1 consumer list: `catalog.application.CatalogEntityAccess`, `ProductDeletionCoordinator`, and `inventory.application.InventoryOperations` only ever referenced `MachineDisplay` in Javadoc, not code — the real foreign consumers were `AnalyticsService`, `ProductReportBundleService`, and `KujiBoxService`, plus one structural JPA relationship (`KujiBox.machineDisplay`) T-1 had missed entirely.

- **Move**: `MachineDisplay` -> `displays.domain`, `MachineDisplayRepository` -> `displays.infrastructure` (+ new `existsByProduct_IdAndEndedAtIsNull`), `MachineDisplayService` -> `displays.application` (class name kept — matched by simple name in `CatalogEntityAccessCallerSetTest`), `MachineDisplayController` -> `displays.api`, and all seven request/response DTOs -> `displays.api` (safe: they only imported `models.enums.LocationType` and Lombok). Route stays `/api/machine-displays`, zero behavior change. `MachineDisplayCleanupAdapter` (existing, implements catalog's `MachineDisplayCleanupPort`) repointed to the new repository package.
- **`MachineDisplayService`'s own foreign-repository use** (rule 6.1.5): `LocationRepository` -> `sites.application.LocationService` (added two minimal unscoped methods, `findById`/`findCodesByIds`, explicitly documented as legacy-migration-only, not for new site-scoped callers), `UserRepository` -> `identity.application.UserService` (added a batch `findAllByIds`). All existing custom exception messages ("Location not found: …", "Target location not found: …") preserved verbatim by keeping the same `.orElseThrow(...)` shape against the new services' `Optional`-returning methods.
- **New facades in `displays.application`**:
  - `DisplayQueries` (read-only): `displayDaysByProduct`, `isProductOnDisplay`, `findActiveByProduct` (returns the new `ActiveDisplaySummary` record, never the entity). Callers: `AnalyticsService` (was raw `Object[]` rows from `calculateDisplayDaysByProduct`), `ProductReportBundleService` (was a full entity-graph load just to compute a boolean, and a raw `List<MachineDisplay>` mapped by hand into `DetailBundleDTO.ActiveDisplay`).
  - `DisplayOperations` (commands, every method `@Transactional(propagation = MANDATORY)` so it joins the caller's transaction): `requireActive`, `attachProductAtMachine`, `endIfActive`, and a transitional `getReference` (kept only because `KujiBox.machineDisplay` is still a `@ManyToOne`; removing it is the kuji slice's job). Caller: `KujiBoxService.openBox`/`closeBox`, replacing its direct `MachineDisplayRepository` access and direct `attachedDisplay.setEndedAt(...)` entity mutation. Kuji's pre-existing behavior gap (no DISPLAY_SET/DISPLAY_REMOVE audit log or stock movement on auto-attach/detach) is preserved exactly, not closed by this move.
- **Edges** (`module-dependency-edges-baseline.txt`): removed `displays -> repositories` (confirmed by grep, nothing under `displays/**` imports the legacy package anymore); added `displays -> {inventory, sites, identity, models, services}`, `services -> displays`, `models -> displays` (the `KujiBox.machineDisplay` association — rule 6.1.8 transitional carry-across, same character as the existing `models -> inventory` R-2 precedent), `controllers -> displays` (forced by `DevSeedController`'s dev-only seeding, which still touches `MachineDisplayRepository`/`MachineDisplay` directly and is explicitly exempted from the new infrastructure-access rule below). `displays -> catalog` unchanged. Full reviewed justification recorded inline in the baseline file's header (Eleventh reviewed addition).
- **ArchUnit**: added `noProductionClassOutsideDisplaysDependsOnDisplaysInfrastructure` (+ `outsideDisplaysToDisplaysInfrastructureRule()` factory, `DevSeedController`-only exemption) to `ArchitectureTest.java`, mirroring the catalog/inventory precedents exactly; added `DisplaysFacadeCallerSetTest` pinning `DisplayQueries`' callers to `{AnalyticsService, ProductReportBundleService}` and `DisplayOperations`' to `{KujiBoxService}`; updated `InventoryOperationsCallerSetTest`'s `MachineDisplayService` entry to its new FQN; added a `DisplayOperations.attachProductAtMachine` entry to `CatalogEntityAccessCallerSetTest` (the auto-attach path's `catalogEntityAccess.getReference` call moved out of `KujiBoxService` into this facade). Updated the two frozen `archunit_store/` files: re-recorded `DevSeedController`'s repository-access violations with the new `displays.infrastructure.MachineDisplayRepository` FQN (24 lines), and pruned every `legacyTechnicalLayerPackagesDoNotGrow` violation line for the moved classes (24 lines removed) since they no longer reside in a legacy package at all.
- **Tests**: moved `MachineDisplayServiceBatchQueryGuardTest`, `MachineDisplayServiceNotificationTest`, `MachineDisplayServiceCrossSiteSwapIT` into `displays.application`, re-pointing their mocks from `LocationRepository`/`UserRepository` to `LocationService`/`UserService`. Re-pointed `KujiBoxServiceTest`, `KujiBoxServiceCatalogFacadeMigrationTest` (mock `DisplayOperations` instead of `MachineDisplayRepository`; neither test's fixtures exercise a machine-type location, so the auto-attach branch was never hit and no additional stubbing was needed) and `AnalyticsServiceTest` (mock `DisplayQueries`, stub returns `Map` not `List<Object[]>`). `ProductLifecycleTransactionBehaviorIT` and `InventoryOperationsTest` needed no changes (the former uses the untouched `catalog`-owned `MachineDisplayCleanupPort`; the latter only mentions `MachineDisplayService` in a comment).
- **New tests**: `DisplayQueriesTest` (unit, 6 cases: display-days mapping including a display that started before the window and is clamped to it, empty-range, `isProductOnDisplay` true/false, `findActiveByProduct` mapping and empty case). `DisplayOperationsIT` (Spring context, H2, `@ActiveProfiles("test")` — matching `InventoryOperationsSiteIT`/`MachineDisplayServiceCrossSiteSwapIT`'s own precedent for why this isn't Testcontainers-Postgres: no native SQL is involved). Covers: `attachProductAtMachine` idempotent for an existing active (machine, product) pair; site set from the location's storage location; `endIfActive` false on an already-ended display; `MANDATORY` propagation throws `IllegalTransactionStateException` when called with no open transaction (proven directly on the Spring-proxied bean, no wrapping transaction); a caller-transaction-failure rollback proof via a `@TestComponent` harness (mirrors `InventoryOperationsCallerTransactionIT`'s harness pattern) showing `attachProductAtMachine`'s write disappears when the caller's transaction rolls back; and a real `KujiBoxService.openBox` -> `closeBox` round trip proving the auto-attached display ends. Assumption recorded: the round-trip test proves the *ends-on-close* behavior; the rollback-together proof is done via the harness rather than forcing a failure mid-`closeBox`, since engineering a precise failure injection point inside `KujiBoxService.closeBox` wasn't needed to prove `DisplayOperations`'s own transactional contract.
- **Contract**: `packages/contracts/openapi.json` not regenerated — verified instead that regeneration would be a no-op: OpenAPI schema component names and path keys are both keyed by simple class name / literal route string (confirmed by inspecting the existing spec — `MachineDisplayDTO`, `/api/machine-displays`, etc., no FQN anywhere), and this move changed zero route mappings, zero DTO fields, zero annotations. AGENTS.md's regeneration requirement is scoped to REST endpoint changes; there are none in T-3.
- **Test results**: `./mvnw -o compile` and `./mvnw -o test-compile` clean. `./mvnw -o test -Dtest='DisplayQueriesTest,MachineDisplayServiceBatchQueryGuardTest,MachineDisplayServiceNotificationTest,InventoryOperationsCallerSetTest,CatalogEntityAccessCallerSetTest,DisplaysFacadeCallerSetTest,ArchitectureTest,KujiBoxServiceTest,KujiBoxServiceCatalogFacadeMigrationTest,AnalyticsServiceTest'` — 70/70 pass. `./mvnw -o test -Dtest='DisplayOperationsIT,MachineDisplayServiceCrossSiteSwapIT'` — 11/11 pass. Full suite `./mvnw -o test` — 514/514 pass. Full IT suite `./mvnw -o test -Dtest='*IT'` — 591/591 pass.

## T-4 evidence (2026-09-24)

- **Repository site-scoped finders** (`displays.infrastructure.MachineDisplayRepository`): added `findByIdAndSite_Id`, `findActiveByLocationTypeAndMachineIdAndSite_Id`, `findByLocationTypeAndMachineIdAndSite_IdAndEndedAtIsNull`, `findByEndedAtIsNullAndSite_Id(OrderByStartedAtAsc)` (+ paged), `findByLocationTypeAndEndedAtIsNullAndSite_IdOrderByStartedAtAsc`, `findByLocationTypeAndMachineIdAndSite_IdOrderByStartedAtDesc` (+ paged), `findByProduct_IdAndSite_IdOrderByStartedAtDesc`, `findStaleDisplaysBySite_Id`, `findStaleDisplaysByLocationTypeAndSite_Id` — one derived/JPQL query per legacy read method, each scoped by `site.id`.
- **`MachineDisplayService` site-scoped overloads** (AC-3): every one of the legacy controller's 19 methods now has a `(UUID siteId, ...)` overload that either (a) queries the new site-scoped repository finder directly (all 12 read methods, `clearDisplayById`, `batchClearDisplays`, `deleteDisplayHistory` — a foreign-site id returns empty/not-found before any further lookup), or (b) for the 6 mutation methods that resolve a machine's `Location` as their first step (`setDisplay`, `setDisplayBatch`, `swapDisplay`, `batchSwapDisplay`, `renewDisplays`, `clearDisplay`), calls the existing `sites.application.LocationService.getLocationById(siteId, machineId)` site-scoped overload (already present in that service, previously unused — its own Javadoc says "do not add new callers without a site check," which this satisfies) before delegating to the unchanged legacy method body. `batchSwapDisplay`'s target-machine branch gets the same check for `targetMachineId` when present. None of the existing legacy (no-`siteId`) method signatures or bodies changed.
- **`SiteMachineDisplayController`** (`displays.api`, `/api/v1/sites/{siteId}/machine-displays`): 19 handlers, one per legacy endpoint, distinct method names from `MachineDisplayController` (springdoc operation-id collision avoidance, matching `SiteShipmentController`'s precedent), `@PathVariable UUID siteId` kept on every handler for client visibility even though the trusted value always comes from `AuthorizedSiteContextHolder.require().siteId()`. Same `@PreAuthorize` roles as the legacy controller.
- **Legacy deprecation**: `LegacyMachineDisplayDeprecationFilter`/`LegacyMachineDisplayDeprecationConfig` (`displays.api`), cloned from `shipments.api.LegacyShipmentDeprecationFilter`/`Config` — same `Deprecation`/`Link` header shape, same slice deprecation date (`@1790121600`, matching shipments' since both moved to v1 in the same phase), registered only for `/api/machine-displays` and `/api/machine-displays/*`. `/api/v1/sites/{siteId}/machine-displays/**` untouched.
- **Edge**: added `displays -> shared` to `module-dependency-edges-baseline.txt` (Twelfth reviewed addition) for `SiteMachineDisplayController` reading `AuthorizedSiteContextHolder` — mirrors the already-approved `catalog -> shared`/`inventory -> shared`/`shipments -> shared` pattern; `shared` remains a pure leaf, no new cycle.
- **AC-3 proof**: new `displays.api.SiteMachineDisplayControllerCrossSiteAccessIT` (Testcontainers, MockMvc, mirrors `shipments.api.SiteShipmentControllerCrossSiteAccessIT`'s shape exactly) — 3 cases: GET current-display for a foreign-site machine id is 404; DELETE (history) for a foreign-site display id is 400 (matches `deleteDisplayHistory`'s existing `IllegalArgumentException` -> 400 mapping, not a new 404 contract) and leaves the row intact; DELETE (clear) for a foreign-site machine id is 404 and leaves the display un-ended. All 3 pass.
- **Contract regeneration**: this time a real diff (unlike T-3's zero-diff) — ran `services/inventory-service`'s `OpenApiContractExportTest` (per `packages/contracts/README.md`) to regenerate `packages/contracts/openapi.json` (+1331 lines, purely additive: 15 new `/api/v1/sites/{siteId}/machine-displays*` path entries, zero changes to existing paths/schemas), then `npm run generate` in `packages/api-client` to regenerate `src/schema.d.ts` (+1227 lines, additive).
- **Web migration**: new `apps/web/src/lib/api/site-machine-displays.ts` (19 functions, `basePath(siteId)` pattern matching `site-shipments.ts`, reusing existing request/response types from `./machine-displays.ts` rather than duplicating them). `hooks/queries/use-machine-displays.ts` and `hooks/mutations/use-machine-display-mutations.ts` rewritten to pull `siteId` from `useCurrentSite()` and call the site-scoped client (`skipToken`/mutate-wrapper pattern matching `use-shipments.ts`/`use-shipment-mutations.ts`). No component needed direct changes — `transfer-display-dialog.tsx`, `location-detail-sheet.tsx`, `product-modal.tsx`, and the analytics `longest-running-displays-card.tsx` all go through the hooks. Updated the two affected tests (`use-machine-displays.test.ts`, `transfer-display-dialog.test.tsx`) to mock `@/lib/api/site-machine-displays` and `useCurrentSite` instead of the legacy client.
- **`docs/baseline/api-v1-map.md`**: added a Phase 7 displays completion bullet mirroring the shipments one, naming `SiteMachineDisplayController`, the AC-3 proof test, and the migrated web files.
- **Test results**: backend `./mvnw -o test` — 514/514 pass; `./mvnw -o test -Dtest='*IT'` — 594/594 pass (591 prior + 3 new cross-site cases); `ArchitectureTest`/`DisplaysFacadeCallerSetTest`/`InventoryOperationsCallerSetTest`/`CatalogEntityAccessCallerSetTest` all pass, including `moduleDependencyEdgesMatchApprovedBaseline` with the new edge. Web: `npx vitest run` (full suite) — 463/463 pass; `npx tsc --noEmit` — 0 errors.
- **Assumption recorded**: rather than threading `siteId` through every internal step of the 6 mutation methods (full shipments-style signature rewrite of ~1300 lines of business logic), the site check is a single guard at the top of each new overload before delegating to the unchanged legacy body. This satisfies AC-3's requirement (foreign-site id rejected before the operation touches the entity) with a much smaller diff, at the cost of the guard's own lookup happening via `LocationService` rather than the mutation's eventual repository call being the single source of truth. Judged acceptable because `LocationService.getLocationById(siteId, id)` is itself the project's own established site-scoped-lookup primitive (already used this way elsewhere), not a bespoke check.

## Test plan

- AC-1–2: PostgreSQL schema/migration checks.
- AC-3: Testcontainers authorization and foreign-site tests.
- AC-4: ArchUnit baseline review.
- AC-5: OpenAPI/client/web tests.

## Review findings and fixes (2026-09-24)

Parallel Standards+Spec review (backend: mirai-spring-reviewer, ran a throwaway Testcontainers
probe against 9 real cross-site/authorization cases; web: mirai-next-reviewer, ran a throwaway
vitest probe against the rewritten hooks) found one blocker and several evidence gaps. Full
findings and dispositions are in `review.md`. All "act on" items are fixed and reverified here;
"consider"/"dismissed" items are recorded in `review.md` only.

- **[Fixed] Security blocker — client-supplied `actorId` spoofing.** `SiteMachineDisplayController`
  now overrides every DTO's `actorId` field (and drops the `actorId` query parameter on
  `clearSiteDisplay`/`clearSiteDisplayById` entirely) with
  `AuthorizedSiteContextHolder.require().backendUserId()` before calling `MachineDisplayService`,
  matching `SiteShipmentController`'s pattern. The legacy `MachineDisplayController` is unchanged
  (still accepts client `actorId`, deprecated, unscoped, explicitly out of scope).
- **[Fixed] Bug — 500 on multi-product machines.** `MachineDisplayService.getCurrentDisplay(UUID
  siteId, ...)` (the v1-only overload; the legacy unscoped overload is untouched) now uses
  `findActiveByLocationTypeAndMachineIdAndSite_Id(...).stream().findFirst()` instead of the
  single-result `findByLocationTypeAndMachineIdAndSite_IdAndEndedAtIsNull` finder, which threw
  `IncorrectResultSizeDataAccessException` whenever a machine already showed more than one active
  product. This fixes both the v1 GET current-display route and v1 `set`'s controller-side re-fetch
  (which calls the same method). The now-unused single-result finder declaration is left in place
  (dead but harmless; removing it wasn't requested and isn't worth a separate diff).
- **[Fixed] AC-3 evidence gap.** `SiteMachineDisplayControllerCrossSiteAccessIT` grew from 3 to 9
  cases: a same-site control (200), `batch-swap` with a foreign `targetMachineId`+
  `displayIdsFromTarget` (404), foreign display ids in `batch-swap`'s `displayIdsToRemove`,
  `swap`'s `outgoingDisplayId`, and `renew` (400 each), foreign display ids on `batch-clear` and
  `DELETE by-id` (400 each), each asserting the foreign display is still active afterward. The
  mislabeled test (named/described as "GET ... 404" while actually sending DELETE and asserting
  400) is renamed/relabeled to match its real behavior.
- **[Fixed] AC-2/AC-6 evidence gap.** Added `MachineDisplaySiteBackfillIT` (standalone
  Testcontainers Postgres, no Spring context, mirrors `ShipmentSiteBackfillIT`'s shape exactly):
  runs V72 then V73 verbatim, covering a row resolving through `location_id` to a real non-MAIN
  site, an orphan row falling back to MAIN, V73 failing loudly with no MAIN site row, and
  idempotency on rerun.
- **[Fixed] AC-5 evidence gap.** Added `LegacyMachineDisplayDeprecationHeadersIT` (mirrors
  `LegacyShipmentDeprecationHeadersIT`): the bare `/api/machine-displays` path and `/stale`
  sub-path carry `Deprecation`/`Link` headers with no `Sunset`; the v1 route does not.
- **[Documented, no code change] AC-4 correction.** The dependency-edge *count* in
  `module-dependency-edges-baseline.txt` grew net (one edge removed, nine added) rather than
  shrinking, contrary to AC-4's literal wording. The reviewer independently verified every new edge
  against real imports: all are genuine and necessary, and the foreign-repository requirement (the
  substantive half of AC-4) is fully met — `displays` only reaches other modules through
  `LocationService`, `UserService`, and `InventoryOperations`, never a foreign repository. AC-4 is
  considered satisfied in substance; the edge-count wording is a spec-writing artifact from before
  this slice's real consumer list was known (see T-3's architect-corrected consumer list above).
  `displays -> services` (`AuditLogService`/`NotificationService`) and `models -> displays`
  (`KujiBox.machineDisplay`) are temporary edges: the former is expected to shrink if/when
  audit/notification gain their own facades, the latter is removed by the kuji slice's planned
  UUID-column migration replacing the `@ManyToOne` association.
- **[Fixed] Web — weak query test.** `use-machine-displays.test.ts`'s "stays disabled" case now
  also asserts `getSiteActiveDisplays` was never called, plus a new case for a resolved-but-
  `undefined` site with `isLoading: false` (fetch still not called) — the old assertion
  (`isLoading === true` alone) passed against both the old and new implementation equally.
- **[Fixed] Web — missing mutation hook test file.** Added
  `use-machine-display-mutations.test.tsx` (modeled on `use-shipment-mutations.test.tsx`) covering
  a payload-shaped hook, a spread-variables hook, the string-variable
  `useDeleteDisplayHistoryMutation`, and the "no active site" rejection path.
- **[Fixed] Web — unscoped invalidation.** Every mutation's `onSuccess` now invalidates
  `["machine-displays", siteId]` instead of the bare `["machine-displays"]` key.
- **[Fixed] Web — comment style.** Header comments in `site-machine-displays.ts` and the mutations
  file no longer cite `.specs/phase-7-displays T-4, AC-3/AC-5`; they state the invariant directly.
- **Not touched (per review's own disposition):** the `batchClearDisplays` N+1 query (consider,
  correctness unaffected), `DisplayOperations.requireActive`'s missing kuji-site check (dismissed,
  pre-existing, kuji slice's job), V73's location-only backfill derivation (consider, defensive
  only, zero affected production rows), the `isError`-vs-site-failure gap in
  `longest-running-displays-card`/`product-modal` (dismissed, identical pre-existing pattern shared
  with `use-shipments.ts`).

- **Also added beyond the review's explicit list**: findings 1 and 2 asked for Testcontainers proof
  and none existed yet, so `SiteMachineDisplayControllerAuthorizationIT` (4 cases) was added:
  a spoofed `actorId` in a `set` request body is ignored and the stored actor is the authenticated
  principal; the `actorId` query parameter on `clearSiteDisplay` is accepted (Spring ignores unknown
  params) but has no effect, the stored actor is still the principal; a machine with two active
  displays does not 500 on `set` (both end up active) or on `getCurrentDisplay` (200, not 500).
- **Contract regeneration**: dropping the `actorId` query parameter from the v1 clear/clear-by-id
  routes changes the OpenAPI surface again after T-4's regeneration, so `OpenApiContractExportTest`
  and `npm run generate` (api-client) were rerun; confirmed via direct JSON inspection that
  `actorId` no longer appears as a parameter on either v1 DELETE route.
- **Test results after fixes (final, independently reverified)**: backend `./mvnw -o compile`/
  `test-compile` clean; `./mvnw -o test` — 514/514 pass; `./mvnw -o test -Dtest='*IT'` — 612/612 pass
  (608 after the review-finding IT additions, +4 more for `SiteMachineDisplayControllerAuthorizationIT`);
  `ArchitectureTest` (9/9), `DisplaysFacadeCallerSetTest` (2/2), `InventoryOperationsCallerSetTest`
  (1/1), `CatalogEntityAccessCallerSetTest` (2/2) all pass, no frozen-store updates needed (the new
  test files live inside the `displays` module itself, not outside it). Web: `npx vitest run` —
  468/468 pass (463 + 5 new); `npx tsc --noEmit` — clean, including after the api-client schema
  regeneration.
