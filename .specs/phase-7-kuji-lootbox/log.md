# Implementation log

## Current handoff

- Post-merge verification (2026-10-15, local dev container, not production): running the merged feature against a migrated Postgres found one live bug the 1169-test suite missed. `KujiBoxService.openBox` and `addTier` built `KujiBoxTier` rows without `site_id`, so every open-box with tiers and every add-tier failed with a NOT NULL violation once V78 was applied. Fixed in both builders with a failing-first IT (`KujiBoxTierSiteIT`). The test gap that hid it is closed: the eight V78/V79-constrained `site_id` columns are now `nullable = false` on their entities, so the H2 test schema enforces NOT NULL. That guard surfaced one more stale fixture (`StockMovementRepositoryIT`), now fixed; no other production write path was missing a site. Full backend suite after both changes: 1171 tests, 0 failures, 0 errors. See "Post-merge verification" in the Task record for detail.
- Status: **T-1 through T-5 complete, plus an independent-review fix round.** The user ran the merged feature through an independent review tool (codex); it found four real, live gaps that all the earlier test suites had missed, and one process gap (stale `review.md`/`validation.md`). All five are now fixed — see `review.md` for the disposition of each and the Task record below for implementation detail. The four code findings: (1) `executeKujiSourceRemoval` used an unscoped `findInventory`, letting a caller drain another site's stock into their own kuji counters via open/transfer-in/add-tier/patch-tier; (2) `SiteKujiBoxController` trusted a client-supplied `actorId` on every mutation except reopen/undo, letting a caller misattribute audit/stock-movement rows; (3) `IdempotencyService`'s cache key had no site component, so replaying an idempotency key at a second site could return the first site's cached prize/balance; (4) six Kuji audit broadcasts (`undoDraw`/`addSlip`/`moveSlips`/`deletePrize`/`addTier`/`recordDraw`) still used the site-less broadcast overload despite T-5's "fully site-aware" claim. All four have new regression tests (`IdempotencyServiceTest`, `SiteKujiBoxControllerActorSpoofingTest`, plus additions to `KujiBoxServiceTest`/`KujiBoxServiceCatalogFacadeMigrationTest`).
- Next action: **code complete; only the human-gated deploy steps remain (V78/V79 constrain after the messaging-service redeploy, and the manual UI sweep listed under Post-merge verification).** A second independent-review pass confirmed all four code findings closed with no new issues, verified the diff itself is clean, and confirmed the two pre-existing failures reproduce independent of this fix. Per an explicit follow-up decision, those two were then also investigated and fixed (see the Task record's "Pre-existing architecture-test fixes" entry) so AC-6 is fully green rather than formally partial. Full combined suite (`./mvnw -o test -Dtest='*'`): 1169/1169 pass, 0 failures, 0 errors.
- **T-5 Custom Kuji gate decision (resolved, no code change needed): the gate stays.** It is not a Kuji-specific backend mechanism at all — grepped the entire backend for "Custom Kuji"/"non-MAIN"/"CUSTOM_KUJI" and found zero matches outside `apps/web`. The actual mechanism is `apps/web/src/hooks/queries/use-current-site.ts`'s `useCurrentSite()`, which resolves EVERY user to the MAIN site only, by an explicit, already-recorded product decision in `.specs/track-d-web-client-adoption` — there is no site-switcher UI anywhere in the app yet. Lifting the Kuji-specific restriction would do nothing on its own, since the whole web app is MAIN-only regardless of Kuji. Further evidence the gate should stay: `.specs/second-site-realtime-and-events/` (the coordination record T-2/T-1 both flagged as a prerequisite) does not exist yet — nobody has opened it. Lifting a non-MAIN restriction for one feature while the platform-wide MAIN-only decision and its coordination record are both still in force would be incoherent. This is a durable finding, not a to-do: any future work to enable non-MAIN Kuji access must first land a site switcher (out of scope here) and open the realtime-coordination record — neither is this feature's job.
- Decisions that must survive compaction: `products.is_active` remains global stock-derived state; site assortment is `site_products.is_stocked`; no non-MAIN Custom Kuji enablement until tenancy/realtime readiness is proved. Kuji's stock-write path already routes through `InventoryOperations` — T-3's Kuji work was repository/query-edge cleanup and event site-propagation, not a stock-write rewrite (confirmed). Lootbox has no product/inventory coupling at all. **User-confirmed 2026-09-27: coin balance is site-of-action, per-site, not per-user-home-site.** `coin_adjustments` and `review_daily_counts` get a `site_id` captured from the authorized site context at write time going forward; all historical rows backfill to MAIN. **T-2 decision: every one of the eight tables gets its own explicit `site_id` column** (no join-only "derived-site" tables), because several hot queries (`findByIdForUpdate`, `decrementQuantity`) operate on the child row directly and a join-derived predicate would change their locking/shape, and the durable spec (multi-site-data-and-api.md §2) rejects "site derivable via parent" as sufficient. Mapped as a raw `UUID siteId` field (matching `Lootbox.siteId`'s existing style), not a `@ManyToOne Site`, to avoid a new `kuji -> sites` / `lootbox -> sites` module edge (AC-4 requires new lootbox edges to be reviewed first). Migrations V75-V79 are written, IT-tested, and considered done — do not rewrite them; extend forward (V80+) instead. **All entity `site_id` fields (`KujiBox`, `KujiBoxTier`, `Lootbox`, `LootboxTier`, `LootboxPrize`, `LootboxPlay`, `CoinAdjustment`, `ReviewDailyCount`) are now populated and read by service code — no longer inert scaffolding anywhere.** **Legacy `/api/kuji-boxes`, `/api/lootbox*`, and `/api/lootbox/admin/*` routes resolve MAIN via `LegacyMainSiteContextResolver.requireMain()` (matching the shipments precedent); dev-mode requests with no authenticated principal resolve MAIN directly via `SiteDirectory`, mirroring the existing dev-mode fallback.** **`UpsertLootboxRequestDTO.siteId` is now a dead/ignored request field** (kept for wire-shape compatibility; removing it is a T-4 contract-regen task). **`DevSeedController.seedReviews()` now sets `siteId` to MAIN** (was flagged as a NOT NULL violation risk; now fixed).
- Last verified: `./mvnw -o test` (unit tests, default surefire include patterns) — 534 tests, 0 failures. `./mvnw -o -Dtest='*IT' test` (integration tests — this project's `mvn test` alone silently skips every `*IT.java`; the explicit `-Dtest='*IT'` override is required for real full coverage, matching the standing project memory note) — 615 tests, 0 failures. **Combined: 1,149 tests, 0 failures, 0 errors**, run 2026-09-28 on `refactor/phase-7` with all of T-3 merged (migrations, both modules' site-scoping, the module move, the v1 routes, and the contract regen). This two-command combination is the actual verification of record — a single `./mvnw test` run undercounts by skipping ITs entirely; do not trust a bare `./mvnw test` result alone as "full suite" again (an earlier handoff in this log claimed "933 tests, full suite" from a bare `./mvnw test` run, which was itself incomplete in the same way — corrected here).
- Open risks/questions: `LootboxDevSeed.seedDefaultCrate()`/`seedTestCrate()` are already dead code (commented out) and untouched — would need `siteId` if ever re-enabled. Two lessons recorded: (1) a targeted/sub-suite test selection can miss cross-file architecture regressions that only the full suite catches; (2) on this project, "full suite" means both `./mvnw test` AND `./mvnw -Dtest='*IT' test` — a bare `./mvnw test` silently excludes every integration test and must never be reported as full coverage by itself.

## T-3 remaining work (updated after the module-move pass)

The migrations pass (first session) deliberately stopped before service/controller work; that piece was split into a Kuji pass and a concurrent Lootbox pass (both done, merged). The physical package move (this pass) is also done.

Concretely still open for T-3:
- Add v1 `Site*Controller`s (`SiteKujiBoxController` in `kuji.api`, `SiteLootboxController`/`SiteLootboxAdminController` in `lootbox.api`), following `displays.api.SiteMachineDisplayController`'s pattern: resolve `AuthorizedSiteContextHolder.require().siteId()`, delegate to the same site-parameterized service methods the legacy controllers already call, distinct handler names so springdoc doesn't collide operation IDs. This is the only piece of AC-5 not yet covered for Kuji/Lootbox.

Migrations, entity scaffolding, Lootbox and Kuji service/controller site-scoping, the messaging-service Python writer change, and the physical module move are all done (see Task record). Once v1 routes land, T-4 (contract regen, web migration of the callers T-1 found, the fuller test matrix T-2 specified — balance-preservation IT, per-site isolation, concurrency including the `createAdjustment` advisory-lock gap, ArchUnit) and T-5 (site-aware realtime, the Custom Kuji gate decision, and implementing `V80__drop_global_kuji_open_box_uniqueness.sql` for real) remain entirely not started.

## Assumptions and decisions

- Treating T-1 as a discovery/audit task per `docs/sdd-workflow.md`'s expand-before-migrate guidance and `docs/baseline/phase-7-inventory.md` line 7 ("refresh from deployed-schema evidence before writing migrations"); no code or migration written for T-1.
- Coin-balance site policy (material product decision, user-confirmed): `UserSiteMembership` is many-to-many, so "per-user home site" does not describe every user; the org was single-site until this migration, so every historical `coin_adjustments`/`review_daily_counts` row can be backfilled to the MAIN site without ambiguity. Chosen design: capture the authorized site context at write time on every new row (site-of-action), backfill history to MAIN, and compute balances per site. Consequence for T-2/T-3: `coin_adjustments`/`review_daily_counts` need a non-nullable `site_id` (post-constrain), `computeBalance()` needs a site parameter and per-site grouping, and any lootbox UI assuming a single global balance needs a T-4 web check.
- T-2 findings changed the picture from T-1 in ways worth recording as decisions:
  - `review_daily_counts`' only production writer is the Python messaging-service (`services/messaging-service/src/adapters/supabase_repo.py:624`, called from `application/review_processor.py:147`), a Kafka/cron batch with no request-scoped `AuthorizedSiteContext`. Site-of-action there must come from a server-side mapping of the configured review source to a site (there is currently one `GOOGLE_PLACE_URL`, so effectively MAIN today), via a new `REVIEW_SITE_CODE` config value — never from the payload. Java only ever writes this table in dev seeds. Added to AC-1 in spec.md accordingly.
  - `lootboxes.site_id` is currently client-writable (`LootboxAdminService.createCrate:128`, `updateCrate:154` both take `req.siteId()` off the request body, and the web echoes it back). This is an IDOR-style trust gap: T-3 must stop reading `siteId` from the request and derive it from authorized site context instead.
  - `lootbox_prizes.tier_id` can be repointed to any tier via `updatePrize:307-311`'s unscoped `findById` — a real path to move a prize into another site's crate. This is the concrete justification for giving `lootbox_prizes` its own `site_id` plus a composite `(tier_id, site_id)` FK rather than relying on the parent join.
  - `locations` has no `site_id` column (per V67's comment), so `kuji_boxes.location_id` cannot use a composite FK for site consistency — a validating (not deriving) trigger is required instead; see Task record below.

## Task record

### T-1 — Inventory lifecycle transitions, stock writers, legacy web callers, site-less broadcasts

- Changed: `docs/baseline/phase-7-inventory.md` (corrected stale broadcast line numbers at row "Kuji / lootbox" and the "KujiBoxService" broadcast row; annotated that Kuji stock writes already route through `InventoryOperations`). No application code changed.
- Tests: None (read-only audit).
- Result: Pass — findings recorded below.

**Kuji lifecycle transitions** (`services/KujiBoxService.java`, 2593 lines, all `@Transactional`):
  - `openBox` (151-348): creates `KujiBox`/`KujiBoxTier`; births inventory (auto-created products, `INITIAL_STOCK`) or pulls from a source location (`SALE` reason).
  - `closeBox` (350-474): zeroes tier counters, soft-deletes auto-created products, returns leftover linked-product slips to a destination location (`TRANSFER` reason), ends any attached machine display.
  - `reopenBox` (476-643): reverses close-time movements, restores tier counters, reactivates soft-deleted auto-created products.
  - `recordDraw`/`undoDraw` (647-980): zero-quantity `KUJI_PRIZE_WON`/`KUJI_DRAW_REVERSED` audit rows; prize stock lives only on `KujiBoxTier.activeCount`.
  - `addSlip`/`moveSlips`/`deletePrize` (985-1150): pure tier-counter operations.
  - `transferInMore`/`transferInInventoryOnly` (1153-1243), `mintAutoCreatedAtBox` (1250-1310), `addTier` (1312-1451), `patchTier` (1453-1650): mint/transfer-in/relink flows.

**Stock writers**: Every Kuji stock mutation already calls `InventoryOperations` (`findInventory`, `saveInventory`, `deleteInventory`, `recordMovement`, `saveMovement`, `adjustQuantity`, `syncProductTotals`) — no direct `LocationInventory`/`StockMovement` repository writes found. This narrows AC-3/AC-4 for Kuji: the remaining edges to shrink are direct `AuditLogRepository`/`UserRepository`/`LocationRepository` access plus raw `EntityManager` JPQL (`findKujiBoxMovementsAtOrAfter`, `findKujiReversalsForAuditLog`) that bypasses the `InventoryQueries` facade — not the inventory-write path itself.

Lootbox (`LootboxService`, `LootboxAdminService`, `LootboxTierLifecycle`) never touches `InventoryOperations`/`Product`/`LocationInventory`. Its "stock" is `LootboxPrize.quantity`, decremented via `LootboxPrizeRepository.decrementQuantity()` (`LootboxService.java:217`) — a fully self-contained coin-economy system decoupled from catalog/inventory.

**Legacy web callers** (`apps/web`):
  - `lib/api/kuji-boxes.ts` — all `/api/kuji-boxes/...` calls, no site param on any of them (open/close/reopen/draw/undo/tier/slip/allocation endpoints).
  - `lib/api/lootbox.ts` — `/api/lootbox/*` (balance, wallet, catalog, play, my-prizes/history, recent) and `/api/lootbox/admin/*` (catalog, crates/tiers/prizes CRUD, bulk-probabilities, redeem, adjustments, coin-config/stats, activity).
  - Consumers: `hooks/queries/use-kuji-box.ts`, `use-kuji-daily-payouts.ts`, `use-lootbox.ts`; `hooks/mutations/use-kuji-box-mutations.ts`, `use-lootbox-mutations.ts`; `components/products/kuji-tab-panel.tsx`, all of `components/lootbox/**`, `app/(dashboard)/team/_components/tab-lootbox.tsx`.
  - `lib/api/inventory.ts:66` — `getProductInventory` calling `/api/inventory/by-product/{productId}` (the legacy caller named in AC-5).

**Nine site-less broadcasts** — confirmed present and still site-less; line numbers have shifted from the 2026-09-17 baseline capture (file grew). Current: 342 (`openBox`), 468 (`closeBox`), 637 (`reopenBox`), 785 (`recordDraw`), 975 (`undoDraw`), 1192 (`transferInMore`), 1238 (`transferInInventoryOnly`), 1427 (`addTier`), 1644 (`patchTier`). All use the no-arg `broadcastInventoryUpdated()` overload, which calls the 4-arg form with `siteId=null` (`SupabaseBroadcastService.java:77-78`).

**Site-derivable parent references**:
  - `kuji_boxes` / `kuji_box_tiers` — via `location.storageLocation.site`.
  - `lootboxes` — already has a raw `site_id UUID` column (`Lootbox.java:56-57`), no FK, nullable/unenforced. Changes AC-1's lootbox work from "add a column" to "make the existing column authoritative and enforced."
  - `lootbox_tiers` / `lootbox_prizes` — via parent `lootbox.siteId`.
  - `lootbox_plays` — via `lootbox`, but that FK is **not** `nullable=false` (`LootboxPlay.java:37-40`); weakens any backfill strategy that depends on it.
  - `coin_adjustments` — **no site-derivable parent**, only `user_id`.

**Coin balance / `coin_adjustments`**: Entirely global per user today. `CoinAdjustment` and `ReviewDailyCount` both carry only `user`, no site reference. `LootboxService.computeBalance()` (`LootboxService.java:82-120`) sums `review_daily_counts` + `coin_adjustments` + `lootbox_plays` purely by `user_id`. Users are multi-site (`identity/domain/UserSiteMembership.java` is many-to-many), so there is no single site to mechanically backfill onto these rows.

Authorization gap confirming AC-2 is unmet: `KujiBoxController`, `LootboxController`, `LootboxAdminController` are role-only (`@PreAuthorize`) — none resolve or check a site context today.

### T-2 — Site ownership and migration sequence

- Changed: `docs/baseline/tenant-migration-worksheet.md` (coin_adjustments row updated to reflect the confirmed per-site policy). `spec.md` AC-1 updated to add `review_daily_counts`. No application code or migrations written yet — this is a design record for T-3 to implement.
- Tests: None (design phase).
- Result: Pass — plan recorded below.

**Site ownership — every table gets an explicit `site_id` column** (rationale in Current handoff). Mapped as raw `UUID siteId`, not a JPA relation.

| Table | site_id source | Parent consistency |
| --- | --- | --- |
| `kuji_boxes` | `location -> storage_locations.site_id`, else MAIN; new writes from authorized site context | Validating (not deriving) trigger against the location's and machine display's site — no composite FK possible because `locations` has no `site_id` |
| `kuji_box_tiers` | Inherit `kuji_boxes.site_id` | Composite FK `(box_id, site_id) -> kuji_boxes(id, site_id)` |
| `lootboxes` | Existing column made authoritative; dangling/NULL -> MAIN; request-body `siteId` no longer trusted (closes the IDOR gap above) | FK to `sites` |
| `lootbox_tiers` | Inherit `lootboxes.site_id` | Composite FK `(lootbox_id, site_id) -> lootboxes(id, site_id)` |
| `lootbox_prizes` | Inherit `lootbox_tiers.site_id` | Composite FK `(tier_id, site_id) -> lootbox_tiers(id, site_id)` — closes the prize-repointing gap above |
| `lootbox_plays` | `lootboxes.site_id` via `lootbox_id`, else MAIN | Composite FK `(lootbox_id, site_id) -> lootboxes(id, site_id)`; `prize_id` stays a plain FK, enforced at app level (see below) |
| `coin_adjustments` | Site of action at write time; history -> MAIN | Plain FK to `sites`; app-level check that the target user has an active `user_site_memberships` row at that site |
| `review_daily_counts` | Site mapped from review source at write time; history -> MAIN | Plain FK to `sites`; uniqueness becomes `(site_id, user_id, date)` |
| `coin_economy_config` | Unchanged — stays global/org-wide | n/a |

**Migration sequence** (source dir `services/inventory-service/src/main/resources/db/migration/`, next free version V75, following the exact expand/backfill/index/constrain style of `V59-V62`/`V68`-`V74`):

1. `V75__add_site_id_to_kuji_lootbox_and_coin_ledger.sql` (expand) — nullable `site_id UUID`, no FK/default, on every table above except `lootboxes` (already has it).
2. `V76__backfill_kuji_lootbox_site_main.sql` (backfill) — MAIN guard (V60-style `DO $$ RAISE EXCEPTION` if no MAIN site), idempotent `WHERE site_id IS NULL` updates: kuji chain from location, lootbox chain from `lootboxes`, everything else (including any dangling `lootboxes.site_id` not in `sites`) to MAIN.
3. `V77__kuji_lootbox_site_indexes.sql` + `.conf` (verify/index, `CREATE INDEX CONCURRENTLY`) — per-site indexes plus the `(id, site_id)` unique targets the constrain-phase composite FKs will reference, and `uniq_review_daily_counts_site_user_date` / `uniq_lootbox_plays_site_user_idem` ahead of dropping the old uniques.
4. `V78__constrain_kuji_lootbox_catalog_site.sql` (constrain, human-gated) — FK + `NOT NULL` on the catalog tables, swap plain parent FKs for composite same-site FKs, add the `kuji_boxes` location/display consistency trigger.
5. `V79__constrain_coin_ledger_site.sql` (constrain, human-gated, after V78 — references `lootboxes(id, site_id)`) — FK + `NOT NULL` on `lootbox_plays`/`coin_adjustments`/`review_daily_counts`; drop the legacy `review_daily_counts` unique (`uq_review_daily_counts_user_date`, confirmed name — see pre-flight below) and `ux_lootbox_plays_user_idem`; requires the messaging-service redeploy to land between V77 and V79.
6. `V80__drop_global_kuji_open_box_uniqueness.sql` (T-5 gate) — drops `uniq_kuji_boxes_open_per_product` only once open-box reads are site-scoped end to end.

**Nullability**: make `lootbox_plays.lootbox_id` `NOT NULL` in V79 (pre-flight confirmed 0 NULL rows — see below).

**Coin-balance call sites needing a site parameter** (planning only, not yet changed): `LootboxService.computeBalance/play/getCatalog/getAdminCatalog/getUserPrizes/getUserHistory/getWalletBreakdown/listRecentPlays`; `LootboxAdminService` crate/tier/prize CRUD (drop `req.siteId()` trust at `createCrate`/`updateCrate`, site-scope `updatePrize`'s tier lookup), `createAdjustment` (add membership check), `getUserProfile`; `CoinAdminDashboardService.getStats/getPlayers`; repositories `CoinAdjustmentRepository`/`LootboxPlayRepository`/`ReviewDailyCountRepository` (site-scoped finders); `supabase_repo.py::increment_daily_count` (add `site_id` arg, new conflict target, resolve site from `REVIEW_SITE_CODE` config, never from payload).

**Sequencing**: land V75-V77 plus entity `site_id` fields and site-writing code as T-3's first commit (module move is a near-pure refactor on top and doesn't itself depend on the columns); V78 needs T-3 verified in prod; V79 needs T-3 + messaging-service both deployed; V80 needs T-5.

### T-2 pre-flight checks (run against live Supabase DB, 2026-09-27, read-only)

- `lootboxes.site_id` distribution: 1 total row, currently NULL, 0 dangling, 0 valid non-MAIN. Backfilling everything to MAIN is safe.
- `lootbox_plays.lootbox_id` NULL count: 0 of 32 rows. Safe to set `NOT NULL` in V79.
- `review_daily_counts` legacy unique constraint name: `uq_review_daily_counts_user_date` on `(user_id, date)`. This is the name V79 drops when adding `uniq_review_daily_counts_site_user_date`.
- Postgres version: 17.6. PG15+ `ON DELETE SET NULL (prize_id)` column-list syntax is available if ever needed, though `lootbox_plays.prize_id` stays an app-level invariant per T-2.
- `sites` table: `MAIN` (`e898b072-7e51-4399-96fa-173eaa57eded`) and `SECOND` (`7e173c98-288a-4261-93c4-9a9ac13c540f`) both exist.

### T-3 (partial) — Migrations and entity scaffolding

- Changed:
  - `services/inventory-service/src/main/resources/db/migration/V75__add_site_id_to_kuji_lootbox_and_coin_ledger.sql` through `V79__constrain_coin_ledger_site.sql`, plus `V77__kuji_lootbox_site_indexes.conf`, implementing the T-2 migration sequence exactly (expand → backfill-to-MAIN → CONCURRENTLY indexes/composite-FK targets → two gated constrain migrations).
  - `site_id` fields added to `KujiBox`, `KujiBoxTier`, `LootboxTier`, `LootboxPrize`, `LootboxPlay`, `CoinAdjustment`, `ReviewDailyCount` (raw `UUID`, matching `Lootbox.siteId`'s existing style — no new module edges).
  - New test: `kuji/infrastructure/KujiLootboxSiteMigrationIT.java` (standalone Testcontainers, no Spring context, matching `SiteIntegrityMigrationIT`'s pattern).
- Tests: `KujiLootboxSiteMigrationIT` — 3/3 pass: (1) backfill correctness and row-count preservation across all seven tables including a dangling/NULL `lootboxes.site_id` and a NULL-lootbox-id play; (2) V76 raises loudly with no MAIN site; (3) V78/V79 reject a cross-site tier, reject repointing a prize into another site's tier, the `kuji_boxes` trigger rejects a site/location mismatch, and `lootbox_plays.lootbox_id`/`site_id` NOT NULL is enforced. `./mvnw -q -o compile` clean. `ArchitectureTest` (ArchUnit) passes unchanged.
- Result: Pass for what was implemented. See "T-3 remaining work" above for what's deliberately not done yet in this pass.

### T-3 (continued) — Lootbox service/controller site-scoping

- Changed:
  - **Closed both T-2 gaps**: `LootboxAdminService.createCrate`/`updateCrate` now take `siteId` from the caller and never read `req.siteId()`; `updatePrize`'s tier-repoint lookup uses `LootboxTierRepository.findByIdAndSiteId`, so a foreign-site `tierId` 404s instead of silently repointing the prize.
  - **`LootboxService`**: `computeBalance`, `play`, `getCatalog`, `getAdminCatalog`, `getUserPrizes`, `getUserHistory`, `getWalletBreakdown`, `listRecentPlays` all take `siteId` and compute/filter per site; the native balance query and every repository call site got a `site_id` predicate. `play()` stamps `LootboxPlay.siteId`.
  - **`LootboxAdminService`**: every crate/tier/prize CRUD method, `getPendingRedemptions`/`getPlaysByStatus`/`markRedeemed`, `createAdjustment`, `getUserProfile` take `siteId` and use `findByIdAndSiteId`-style lookups throughout. `createAdjustment` now takes the same `pg_advisory_xact_lock(hashtext(userId))` lock `play()` uses (closes the T-2 concurrency gap) and checks `UserSiteMembershipRepository.existsByUserIdAndSiteIdAndIsActiveTrue` before crediting/debiting a foreign-site user.
  - **`CoinAdminDashboardService`**: `getStats`, `getPlayers`, `getRecentActivity` all take `siteId`; every per-user CTE and the activity-feed UNION got a `site_id` predicate — these previously aggregated across all sites.
  - **Repositories**: `LootboxRepository` (`findBySiteIdOrderBySortOrderAscNameAsc`, `findByIdAndSiteId`, site-filtered `findOpen`/`findByIdOpen`), `LootboxTierRepository`/`LootboxPrizeRepository` (`findByIdAndSiteId`), `LootboxPrizeRepository` (site-scoped `findAllWithTierBySiteId`/`findAllActiveOrSoldOutWithTierBySiteId` — these two were previously unscoped and leaked every site's catalog into both the player and admin views), `LootboxPlayRepository` (site-scoped user/status/idempotency/recent-plays finders, `findByIdAndSiteId`), `CoinAdjustmentRepository`/`ReviewDailyCountRepository` (site-scoped `findExpiringSoon`/history finders).
  - **Controllers**: `LootboxController` and `LootboxAdminController` resolve site via a new `resolveSiteId(Authentication)` helper — `LegacyMainSiteContextResolver.requireMain().siteId()` for authenticated requests, direct `SiteDirectory.findByCode(MAIN)` for the existing dev-mode no-principal fallback (mirrors `resolveUserId`'s existing pattern; did not add separate v1 `Site*Controller`s this slice — see handoff).
  - `UpsertLootboxRequestDTO.siteId` javadoc updated to document it's now ignored server-side.
  - Fixed two now-stale unit test suites (`LootboxAdminServiceTest`, `LootboxBalanceFormulaTest`) and one IT (`CoinAdminDashboardServiceIT`) for the new signatures; updated `archunit_store/0858803e-...`'s two frozen `LootboxController`/`LootboxAdminController` constructor-signature entries (harmless textual staleness from adding constructor params, not a new violation — `ArchitectureTest` passes clean).
- Tests: `LootboxAdminServiceTest` (27, incl. new advisory-lock/membership-check assertions), `LootboxBalanceFormulaTest` (17), `LootboxServiceTest` (4), `ArchitectureTest` (9), `CoinAdminDashboardServiceIT` (5, real Postgres via Testcontainers, V78/V79 constraints active), `KujiLootboxSiteMigrationIT` (3, re-verified unaffected) — all pass. `./mvnw -q -o compile`/`test-compile` clean.
- Result: Pass. Kuji site-scoping, the module move, the messaging-service writer, and T-4/T-5 remain — see Current handoff.

### T-3 (Kuji site-scoping) — Done

- Changed (Kuji-only; no Lootbox files touched, per directive avoiding collision with the concurrent Lootbox pass):
  - `services/KujiBoxService.java`: every public method (`openBox`, `closeBox`, `reopenBox`, `recordDraw`, `undoDraw`, `addSlip`, `moveSlips`, `deletePrize`, `transferInMore`, `transferInInventoryOnly`, `addTier`, `patchTier`, `getBox`, `getActiveBoxByProduct`, `getBoxHistory`, `cloneTiersFromLastClosedBox`, `getDailyPayouts`, `getAllocationsByLocation`, `getAllocationsByProduct`) now takes `siteId` as its first parameter. Added `requireBoxInSite`/`requireBoxSimpleInSite` private helpers (a box in another site 404s identically to a missing box, so a caller can never distinguish the two) and `requireDisplayInSite` (rejects a caller-supplied `machineDisplayId` from another site before it's ever attached, in addition to — not instead of — the V78 DB trigger). `openBox` now sets `KujiBox.siteId` and resolves its location via `LocationService.getLocationById(siteId, ...)` (site-scoped, 404s on a foreign-site location) instead of raw `LocationRepository.findById`.
  - Replaced direct `AuditLogRepository`/`UserRepository`/`LocationRepository` field access with `AuditLogService`/`UserService`/`LocationService` (matching the already-approved `displays -> services`/`identity`/`sites` facade pattern). Added `AuditLogService.findById(UUID)` (new method — `save` already existed) since `KujiBoxService.undoDraw` needed to load-then-resave an `AuditLog` entity, which no existing facade method covered.
  - The nine `broadcastInventoryUpdated()`/seven `broadcastAuditLogCreated()` calls now pass the resolved `siteId` via the existing 4-arg/2-arg overloads instead of the site-less no-arg ones.
  - The two raw `EntityManager` native queries (`findKujiBoxMovementsAtOrAfter`, `findKujiReversalsForAuditLog`) now also filter `AND site_id = :siteId` on `stock_movements` as defense-in-depth (they're still native SQL — no `InventoryQueries` facade method exists for JSON-metadata lookups on `stock_movements`, so full facade extraction is left to the module-move pass, not attempted here).
  - `KujiBoxRepository`/`KujiBoxTierRepository`: added site-scoped finder variants (`findByIdAndSiteId(WithTiers)`, `findByProductIdAndSiteIdAndStatus(WithTiers)`, `findByProductIdAndSiteIdOrderByOpenedAtDesc`, `findOpenAllocationsBy{Location,Product}` now take `siteId`) alongside the existing unscoped ones (left in place — `kuji.application`'s two existing adapters still use unscoped `KujiBoxRepository` methods that are out of this pass's scope).
  - `controllers/KujiBoxController.java`: legacy routes now resolve `LegacyMainSiteContextResolver.requireMain()` once per request and pass that site into every service call — this is a real behavior change (previously the box lookups were global; a box from any site was reachable via the legacy `/api/kuji-boxes/{boxId}` route) but matches the multi-site spec's rule that legacy routes mean MAIN, not "no tenant boundary at all".
  - **Deliberately NOT added**: a v1 `SiteKujiBoxController` — attempted, but ArchUnit's `legacyTechnicalLayerPackagesDoNotGrow` rejects any new class in the legacy `controllers` package, and `displays`/`shipments` precedent is to move the module's controllers into `<module>.api` before adding a v1 sibling. Moving only the new v1 controller without moving `KujiBoxController` too would create a `kuji -> services`/`kuji -> shared` edge split across two packages for no good reason. Deferred to the physical module-move step, alongside T-4's route migration.
- Tests: extended `KujiBoxServiceTest` and `KujiBoxServiceCatalogFacadeMigrationTest` (constructor/mock fields renamed `locationRepository`→`locationService`, `auditLogRepository`→`auditLogService`, `userRepository`→`userService`; every service call site given a `siteId` argument) plus `KujiBoxControllerPriceVisibilityTest` (added a mocked `LegacyMainSiteContextResolver`). Added two new regression tests in `KujiBoxServiceCatalogFacadeMigrationTest`: `getBox_fromAnotherSite_isReportedAsNotFound` (cross-site box lookup 404s) and `openBox_withMachineDisplayFromAnotherSite_isRejectedBeforeAttaching` (foreign-site machine display rejected, no box persisted). Fixed two other test files that called into the changed signatures: `DisplayOperationsIT`, `InventoryOperationsSharedCallerPathIT`.
- Result: Pass. `./mvnw -q -Dtest='KujiBoxServiceTest,KujiBoxServiceCatalogFacadeMigrationTest,KujiBoxControllerPriceVisibilityTest,ArchitectureTest,DisplayOperationsIT,InventoryOperationsSharedCallerPathIT,KujiLootboxSiteMigrationIT' test` — all green. `./mvnw -q -o compile` and `-o test-compile` clean.

### T-3 (messaging-service review-coin site) — Done

- Changed (Python messaging-service + one small, unrelated-file Java fix; no Kuji/Lootbox Java files touched, per directive avoiding collision with the concurrent module-move pass):
  - `services/messaging-service/src/config.py`: added `REVIEW_SITE_CODE` (default `"MAIN"`), config-driven rather than hardcoded so a second review-source/site pairing can be added later without a code change.
  - `services/messaging-service/src/adapters/supabase_repo.py`: `increment_daily_count(employee_name, review_date, site_id, rate=1)` now takes a required `site_id`, inserts it, and its `ON CONFLICT` target is `(site_id, user_id, date)` — matching the `uniq_review_daily_counts_site_user_date` constraint `V77`/`V79` already added on the Java side (confirmed by reading both migration files before writing the SQL). Added `get_review_site_id()` next to `get_review_coin_rate()`: resolves `REVIEW_SITE_CODE` to a UUID via `SELECT id FROM sites WHERE code = :code`, raising `ValueError` (fail loud, not a silent default) if the code doesn't match any site — `review_daily_counts.site_id` is NOT NULL post-V79, so a batch must never proceed with an unresolved site.
  - `services/messaging-service/src/application/review_fetcher.py`: both `fetch_and_publish_daily` and `fetch_and_publish_backfill` now resolve `site_id` once per batch (same "read once, travels on the Kafka event" pattern already used for `coin_rate`, with the same override parameter shape) and stamp it into each published event's payload.
  - `services/messaging-service/src/application/review_processor.py`: `ReviewEvent` gained `site_id: str | None = None` (defaults to `None`, unlike `coin_rate`'s `1`, because there's no safe implicit site value); `parse_kafka_message` reads it off the payload; `process_review` uses `event.site_id or self._repo.get_review_site_id()` — the fallback exists only for Kafka messages already queued before this shipped, not as a normal-path lookup (asserted in tests via `get_review_site_id.assert_not_called()` on the happy path).
  - `services/inventory-service/.../controllers/DevSeedController.java`: fixed the previously-flagged gap — `seedReviews()` now resolves the MAIN `Site` once (reusing the existing `siteRepository`/`DEFAULT_SITE_CODE` pattern already used elsewhere in this file) and sets it on the `ReviewDailyCount` builder, so the dev-only endpoint no longer violates the NOT NULL constraint if invoked. Grepped first to confirm the module-move pass isn't touching this file (it's in `controllers/`, unrelated to `kuji`/`lootbox`).
- Tests: `services/messaging-service/tests/test_coin_rate.py` — updated 3 existing tests for the new `increment_daily_count` signature and to stub `get_review_site_id`. New `services/messaging-service/tests/test_review_site.py` (6 tests, mirroring `test_coin_rate.py`'s structure): `get_review_site_id` resolves the configured code / raises on an unmatched code; `increment_daily_count` sends `site_id`; `ReviewFetcher` reads the site once per run and honors an explicit override; `ReviewProcessor` carries `site_id` through from the Kafka payload and falls back to the repo lookup only when the field is missing. Full messaging-service suite: `python -m pytest tests/` — 78/78 pass. `ruff check` clean on every changed file. Inventory-service: `./mvnw -q -o compile` clean after the `DevSeedController` fix (not otherwise tested — outside this pass's owned module, and it's a 3-line dev-only fix already covered by the existing NOT NULL constraint as the real safety net).
- Result: Pass. This closes T-3's last item outside the Java module-move. Remaining for T-3: the physical `kuji`/`lootbox` package move (separate concurrent pass). T-4/T-5 not started.

### T-3 (module move) — Done, v1 routes deferred

- Changed: physically moved every Kuji/Lootbox production class out of the legacy `controllers`/`services`/`repositories`/`models`/`dtos` packages into `kuji.{api,application,domain,infrastructure}` and `lootbox.{api,application,domain,infrastructure}`, mirroring the `displays` module template:
  - `kuji.api`: `KujiBoxController` plus its ten request DTOs and five response DTOs.
  - `kuji.application`: `KujiBoxService` (joins the existing `KujiOpenBoxAdapter`/`KujiBoxCleanupAdapter`).
  - `kuji.domain`: `KujiBox`, `KujiBoxTier`.
  - `kuji.infrastructure`: `KujiBoxRepository`, `KujiBoxTierRepository`.
  - `lootbox.api`: `LootboxController`, `LootboxAdminController`, plus their sixteen request/response DTOs.
  - `lootbox.application`: `LootboxService`, `LootboxAdminService`, `LootboxTierLifecycle`, `CoinAdminDashboardService`, and `CoinEconomyService` — a class the prior two site-scoping passes both missed (it manages `CoinEconomyConfig` and is used exclusively by `LootboxAdminController`; found via `CoinEconomyServiceTest` failing to compile after the DTO/entity move, then confirmed it had no other caller).
  - `lootbox.domain`: `Lootbox`, `LootboxTier`, `LootboxPrize`, `LootboxPlay`, `CoinAdjustment`, `CoinEconomyConfig`.
  - `lootbox.infrastructure`: `LootboxRepository`, `LootboxTierRepository`, `LootboxPrizeRepository`, `LootboxPlayRepository`, `CoinAdjustmentRepository`, `CoinEconomyConfigRepository`.
  - Exceptions (`KujiAllocationViolationException`, `LootboxException`) and shared enums (`KujiBoxStatus`, `LocationType`, etc.) stay in their legacy packages, matching the `shipments`/`displays` precedent of not moving exceptions.
  - Moved the corresponding test files alongside their production classes (`kuji.api`/`kuji.application`/`lootbox.application` test packages), including `CoinEconomyServiceTest`, since one test (`LootboxServiceTest`) exercises a package-private static method and would silently stop compiling/running correctly if left behind in the old package.
  - Updated ~40 now-implicit same-package references across the moved files and their remaining-in-`services`/`controllers`/`repositories` callers that needed new explicit imports (`AuditLogService`, `NotificationService`, `SupabaseBroadcastService`, `UserCoinProfileResponseDTO`'s two Lootbox DTO fields, `StockMovementRepositoryIT`'s Kuji repository fixtures).
  - `module-dependency-edges-baseline.txt`: added a Thirteenth reviewed addition documenting every new edge this move exposes (`kuji -> {displays,identity,inventory,services,shared,sites}`, `lootbox -> {dtos,exceptions,identity,models,repositories,services,shared,sites}`, `config -> lootbox`, `dtos -> lootbox`) — every one is a mechanical carry-across of an already-approved `services -> X`/`controllers -> X`/`config -> X` edge under the moved classes' new package name, confirmed individually against the actual current imports (not assumed from the pre-move report), with two exceptions that are genuinely new *directions* (`kuji -> services`, `lootbox -> services`, mechanical fallout of leaving the `services` package, same shape as the already-approved `shipments -> services`/`displays -> services`) and one new bidirectional pair (`dtos <-> lootbox`, same already-accepted legacy-technical-package-tangle character as the file's existing `models <-> inventory` etc. pairs). Pruned `kuji -> repositories` (confirmed by grep, zero remaining references); kept `kuji -> models` and `kuji -> catalog` (still real, just renamed source package).
  - Regenerated the two frozen ArchUnit stores (`archunit_store/0858803e-...` for `repositoriesAreOnlyAccessedByServicesOrRepositories`, `archunit_store/c1d9f1c8-...` for `legacyTechnicalLayerPackagesDoNotGrow`): for the first, ArchUnit's own `allowStoreUpdate=true` mechanism only prunes obsolete entries and does not persist newly-renamed ones, so — matching this project's own established precedent from the prior Lootbox site-scoping merge (commit `beb1fc0`, which hand-edited this exact file for a signature change) — manually renamed every stale Kuji/Lootbox FQCN in the frozen text to its new package rather than letting entries silently vanish or get duplicated; for the second, the automatic prune was actually correct (moved classes no longer reside in any legacy package, so their "resides in legacy package" violations are genuinely obsolete, not renameable) and its store update was kept as-is. `archunit.properties`'s `allowStoreUpdate` flag was flipped to `true` only for the regeneration commands and restored to `false` before the final verification run.
  - **Deliberately NOT done**: the v1 `Site*Controller`s for Kuji and Lootbox. The physical move (needed before any v1 controller could exist at all, per the frozen-package-growth constraint the prior Kuji pass already hit) is a large, mechanical, high-blast-radius change on its own — every Kuji/Lootbox file, ~40 call sites needing new imports, the ArchUnit baseline and both frozen stores. Adding three new controllers with real routing/authorization logic on top of that in the same pass risked exactly the "half-finished, under-tested state" the T-2 architect and the first migrations pass both flagged as worse than stopping at a coherent, fully-tested checkpoint. The move itself is independently complete and verified (432/432 tests green); the v1 routes are additive, self-contained work for the next pass.
- Tests: `./mvnw -q -o compile`/`test-compile` — clean. `./mvnw -q -Dtest=ArchitectureTest test` — 9/9 pass (edge-baseline test passes against the updated baseline; both frozen-store tests pass against the regenerated stores). `./mvnw -q -Dtest='com.mirai.inventoryservice.kuji.**,com.mirai.inventoryservice.lootbox.**' test` — 76/76 pass across `KujiBoxControllerPriceVisibilityTest`, `KujiBoxServiceCatalogFacadeMigrationTest`, `KujiBoxServiceTest`, `KujiLootboxSiteMigrationIT`, `CoinAdminDashboardServiceIT`, `CoinEconomyServiceTest`, `LootboxAdminServiceTest`, `LootboxBalanceFormulaTest`, `LootboxServiceTest`. `StockMovementRepositoryIT`/`DisplayOperationsIT`/`InventoryOperationsSharedCallerPathIT` (adjacent files touched for imports) — all pass. Full `./mvnw -q test` — 432/432 pass, 0 failures, 0 errors.
- Result: Pass. Module move complete for AC-4 (Kuji/Lootbox own their repositories; the `kuji -> repositories` edge is closed; no new Lootbox edge went unreviewed). v1 route addition (part of AC-5) remains before T-4/T-5.

### T-3 (v1 site routes) — Done; T-3 fully complete

- Changed:
  - `kuji.api.SiteKujiBoxController` (`/api/v1/sites/{siteId}/kuji-boxes/**`), `lootbox.api.SiteLootboxController` (`/api/v1/sites/{siteId}/lootbox/**`), `lootbox.api.SiteLootboxAdminController` (`/api/v1/sites/{siteId}/lootbox/admin/**`) — thin routing layers reusing `SiteAccessAuthorizationFilter` (already resolves and site-authorizes `/api/v1/sites/{siteId}/**` generically; no new wiring needed) and delegating to the already site-parameterized service methods from the earlier passes. No new business logic.
  - Kuji route actors always come from `AuthorizedSiteContextHolder`, never a client-supplied `actorId`, matching the `SiteMachineDisplayController` actor-spoofing-fix precedent.
  - `coin-config`/`coin-economy` deliberately has no v1 site-scoped equivalent: `coin_economy_config` stays a single global/org-wide row per the T-2 decision, so exposing it under `/sites/{siteId}/` would falsely imply a per-site rate. It stays reachable only via the legacy `/api/lootbox/admin/coin-config` route.
  - Regenerated `packages/contracts/openapi.json` via `OpenApiContractExportTest` (the documented process in `packages/contracts/README.md`) and `packages/api-client/src/schema.d.ts` via `npm run generate` + `npm run typecheck` in `packages/api-client` (clean) — this is the contract-regeneration half of T-4, done here since it's a direct, mechanical consequence of adding the new endpoints.
- Tests: new `SiteKujiBoxControllerSecurityTest` (8 tests), `SiteLootboxControllerSecurityTest` (5), `SiteLootboxAdminControllerSecurityTest` (5) — 401 with no token, 403 for a caller with no membership at the path's site (foreign-site rejection via `SiteAccessAuthorizationFilter` before any `@PreAuthorize` role check runs), 403 for the wrong role, and "allowed through auth" (not 401/403) for a correctly-membered, correctly-roled caller, mirroring `SiteShipmentControllerSecurityTest`'s pattern exactly.
- Result: Pass. Full verification: `./mvnw -o test` 534/534, `./mvnw -o -Dtest='*IT' test` 615/615, 0 failures across both (see Current handoff for why both commands are required). **T-3 is now fully complete** — every AC-4/AC-5 item for Kuji/Lootbox module ownership and v1 routing is done.

### T-5 (non-UI pieces) — Done

- Changed:
  - No application code changes for realtime or the gate — both were already correct/decided (see Current handoff for the evidence trail).
  - New: `services/inventory-service/src/main/resources/db/migration/V80__drop_global_kuji_open_box_uniqueness.sql` + `.conf` (`executeInTransaction=false`, matching V77's CONCURRENTLY precedent). Creates `uniq_kuji_boxes_open_per_site_product (site_id, product_id) WHERE status='OPEN'` CONCURRENTLY first, then drops the old global `uniq_kuji_boxes_open_per_product`, so there's no window with neither constraint enforced. Verified the code-level prerequisite first: grepped for callers of the unscoped `KujiBoxRepository.findByProductIdAndStatus` — none exist in production code; every open-box read already goes through a `*AndSiteId*` variant (`KujiBoxService` lines 192, 513, 1669, 1752, 1762).
  - `KujiLootboxSiteMigrationIT`: added the pre-existing global unique index to the stub schema's setup (it wasn't there before, since V80 didn't exist when that test was written), and a new `v80ReplacesGlobalOpenBoxUniquenessWithPerSite` test proving two sites can each hold an OPEN box for the same product post-V80, while a second OPEN box for the same product at the same site is still rejected.
- Tests: `KujiLootboxSiteMigrationIT` — 4/4 pass (3 existing + 1 new). `ArchitectureTest` — 9/9 pass. `./mvnw -q -o test-compile` clean.
- Result: Pass. T-5 has no remaining work of its own; the feature's only open item is T-4's web migration.

### T-4 (web migration) — Done

- Changed:
  - New `apps/web/src/lib/api/site-kuji-boxes.ts`, `site-lootbox.ts`, `site-lootbox-admin.ts` under `/api/v1/sites/{siteId}/...`. **Pattern judgment call**: modeled on `site-machine-displays.ts` (plain `apiGet`/`apiPost` from `./client`, existing `@/types/api`/`@/types/lootbox` interfaces reused as-is), not `site-inventory.ts` (generated `webApiClient` + a DTO-mapping layer). The reason: `site-inventory.ts` needed a mapping layer because its v1 response shape is genuinely slimmer than the legacy one; Kuji/Lootbox's v1 routes are pure site-scoping additions with no request/response shape change from legacy, exactly the situation `site-machine-displays.ts`'s own comment already justifies avoiding a parallel mapping layer for. Documented inline in each new file's header comment.
  - Migrated every consumer: `use-kuji-box.ts`, `use-kuji-daily-payouts.ts`, `use-lootbox.ts`, `use-kuji-box-mutations.ts`, `use-lootbox-mutations.ts` (all via `useCurrentSite()`, mirroring `use-machine-display-mutations.ts`'s pattern — a mutation with no resolved site throws instead of calling an undefined path), and the Kuji dialogs `tier-edit-dialog.tsx`, `transfer-in-dialog.tsx`, `tier-draft-ui.tsx` (these three previously called the unscoped, site-blind `useProductInventoryEntries`/`getProductInventoryEntries` — the actual AC-5 "Kuji legacy inventory-by-product caller" this feature owns; corrected mid-task after an earlier scoping mistake mistakenly excluded them), plus `reopen-box-dialog.tsx`/`undo-draw-dialog.tsx` (dropped their now-server-derived `actorId` prop).
  - Every hook kept its external signature unchanged (e.g. `useKujiBox(boxId)` still takes just a `boxId`) — `siteId` is resolved internally via `useCurrentSite()`, not threaded through every call site — so `kuji-tab-panel.tsx`, the whole `components/lootbox/**` tree, and `tab-lootbox.tsx` needed zero changes of their own; verified via `git diff --stat` showing no delta on any of them.
  - `reopenSiteKujiBox`/`undoSiteKujiDraw` no longer send a client-supplied `actorId` — the v1 backend route always derives it from the resolved site context, matching `SiteKujiBoxController`'s actor-spoofing-fix precedent.
  - `flushInventorySiteRefresh` (`inventory-refresh.ts`) drops its legacy, unscoped two-element `productInventoryEntries` cache-invalidation key now that the Kuji dialogs above read the site-qualified key instead.
  - Legacy `apps/web/src/lib/api/kuji-boxes.ts` deleted (grep-confirmed zero remaining callers). `lootbox.ts` trimmed to just the coin-config/coin-economy functions, which stay on the legacy route since `coin_economy_config` is a single global row, not per-site.
- Tests: fixed one stale test (`inventory-refresh.test.ts`'s "Required 4" case asserted the now-removed legacy invalidation key was still called — this was caught by the coordinator running the full suite before finalizing, matching the T-3 lesson about not trusting a partial run). No new dedicated test files were added for the three new API-client modules themselves — matching this codebase's established precedent, since neither `site-machine-displays.ts` nor `site-shipments.ts` has one either; correctness is exercised transitively through the hook/component/mutation tests. Full `apps/web` suite: `npm run test:run` — 468/468 pass. `npx tsc --noEmit` — clean.
- Result: Pass. This completes T-4 and the entire `.specs/phase-7-kuji-lootbox` feature (T-1 through T-5).

### Independent-review fix round — Done

- Changed:
  - `KujiBoxService.executeKujiSourceRemoval` now takes `siteId` and calls the site-scoped
    `InventoryOperations.findInventory(siteId, locationId, productId)` instead of the unscoped
    two-argument overload; all 5 call sites (`openBox`, `transferInMore`,
    `transferInInventoryOnly`, `addTier`, `patchTier`) updated to pass it.
  - `SiteKujiBoxController`: every mutation handler except `reopenSiteBox`/`undoSiteDraw` (which
    never took a client actor at all) now calls `request.setActorId(contextActorId())` before
    delegating to the service — `openSiteBox`, `closeSiteBox`, `addSiteTier`, `patchSiteTier`,
    `transferInMoreToSiteTier`, `transferInInventoryOnlyToSiteTier`, `addSlipToSiteTier`,
    `moveSlipsInSiteTier`, `deleteSitePrize`, `recordSiteDraw` (10 handlers).
  - `IdempotencyService.get`/`put` now take `siteId` as the first parameter and fold it into the
    cache key (`siteId:userId:key`, was `userId:key`); both callers (`LootboxController`,
    `SiteLootboxController`) updated. Doc comment corrected to reference the actual V79 unique
    constraint (`UNIQUE(site_id, user_id, idempotency_key)`), which had drifted stale.
  - `KujiBoxService`: 6 site-less `broadcastAuditLogCreated(parentProduct.getId().toString())`
    calls (in `recordDraw`, `undoDraw`, `addSlip`, `moveSlips`, `deletePrize`, `addTier`) switched
    to the 2-arg, site-aware overload.
  - Updated two now-stale `findInventory` mock stubs in `KujiBoxServiceTest`/
    `KujiBoxServiceCatalogFacadeMigrationTest` to also stub the 3-arg overload the fixed code
    actually calls (kept the 2-arg stubs too, since a couple of unrelated, already-safe call
    sites — reopen/undo reversal paths, where the location was already resolved through a
    site-scoped lookup before reaching `findInventory` — still legitimately use it).
  - `review.md`/`validation.md` rewritten with the actual findings/disposition and a real
    validation run, replacing the stale kickoff-only placeholder text (finding 5).
- Tests: new `IdempotencyServiceTest` (4), new `SiteKujiBoxControllerActorSpoofingTest` (12), new
  `KujiBoxServiceTest.transferInInventoryOnly_rejectsASourceLocationBelongingToAnotherSite` plus
  broadcast-site assertions added to 3 existing `KujiBoxServiceTest` tests
  (`addSlip_writesAuditLogOnlyAndNoStockMovementOrOutbox`,
  `recordDraw_incrementsDrawnCountByDrawnQuantity`, `undoDraw_decrementsDrawnCountAndClampsAtZero`)
  and 2 new tests (`moveSlips_deactivate_broadcastsAuditLogCreatedWithRealSite`,
  `deletePrize_broadcastsAuditLogCreatedWithRealSite`), plus one broadcast assertion added to
  `KujiBoxServiceCatalogFacadeMigrationTest.addTier_existingLinkedProduct_...`. `./mvnw -q -o
  compile` clean. `./mvnw -o test -Dtest='!CatalogEntityAccessCallerSetTest,!ArchitectureTest'` —
  1158/1158 pass (see Current handoff for why those two are excluded — pre-existing, unrelated,
  confirmed by stashing this round's changes and re-running against the clean tree).
- Result: Pass. All four codex findings fixed and regression-tested; `review.md`/`validation.md`
  now reflect real implementation review and validation evidence instead of kickoff placeholders.

### Pre-existing architecture-test fixes — Done (explicit follow-up decision, out of feature scope but fixed on request)

- Changed:
  - `CatalogEntityAccessCallerSetTest.java`: replaced `topLevelMethodName`'s lambda-name-parsing
    heuristic (assumed `lambda$<enclosingMethod>$<N>`) with a line-range resolver
    (`namedMethodStartLines`/`enclosingNamedMethod`) using `JavaMember.getSourceCodeLocation()`.
    Root cause verified empirically first (a throwaway diagnostic test, discarded): on this
    codebase's compiled output, javac names every lambda's synthetic method `lambda$<N>` — a
    bare, class-wide counter with no enclosing-method segment at all — so the old heuristic
    silently mis-attributed any lambda-wrapped access. The new resolver is immune to whatever
    naming scheme javac uses, since it works from source line ranges instead.
  - `archunit_store/0858803e-...`: re-froze 3 already-accepted `DevSeedController` violations
    (`ensureDevEmployee`/`ensureMachinesExist`/`seedAll` calling `UserRepository.save`/
    `LocationRepository.save` directly) under their new `lambda$N`-named text — an unrelated
    change wrapped those specific calls in lambdas sometime after the store was last frozen.
    Confirmed same repository methods and near-identical line numbers (398→405, 1476→1503,
    266→269) before re-freezing, using the project's established manual re-freeze precedent
    (`allowStoreUpdate=true` prunes obsolete entries but doesn't persist renamed ones).
    `DevSeedController` itself was not touched — its direct repository access remains
    pre-existing, unreviewed technical debt, unchanged.
- Tests: `CatalogEntityAccessCallerSetTest` 2/2 pass. `ArchitectureTest` 9/9 pass. Full combined
  suite `./mvnw -o test -Dtest='*'` — 1169/1169 pass, 0 failures, 0 errors.
- Result: Pass. AC-6 is now fully satisfied, not formally partial.

### Post-merge verification (2026-10-15) — Done for code, manual UI sweep pending

Scope: load a small anonymized slice of Supabase data into the local `postgres-dev` container, apply V69-V80 by hand, then run the app against it.

- **Fixture.** 5 kuji boxes (3 open, 2 closed) with 80 tiers, the full lootbox set (1 crate, 6 tiers, 7 prizes, 36 plays), 9 coin adjustments, 40 review daily counts, and the 85 products and locations they reference. Users were anonymized at the source; Slack webhook URLs were dropped. All kuji data on the remote is in MAIN.
- **Migrations.** V69-V80 applied cleanly after aligning the container to production constraint names. Result: no NULL `site_id` in any kuji, lootbox or coin table, row counts unchanged, no invalid indexes, 4 composite FKs, the site-consistency trigger and the per-site open-box index in place, old global open-box index gone.
- **Environment finding.** The dev container had drifted from production: Hibernate-generated FK names (the dev compose sets `SPRING_JPA_HIBERNATE_DDL_AUTO=update`), no `lootbox_prizes.tier_id` FK, and the old `uniq_kuji_boxes_open_per_product`, `ux_lootbox_plays_user_idem` and `uq_review_daily_counts_user_date` objects missing. V78/V79/V80 hardcode the production names, so they fail on such a database. Supabase has exactly the expected names, so the migrations themselves are correct. Flyway is not on the classpath; migrations are applied by hand.
- **Bug found by running the app.** `POST /api/v1/sites/{siteId}/kuji-boxes` returned 500: `null value in column "site_id" of relation "kuji_box_tiers"`. Root cause: both `KujiBoxTier.builder()` call sites in `KujiBoxService` (`openBox`, `addTier`) omitted `siteId`; the box got its site, its tiers did not. Fix: `.siteId(box.getSiteId())` on both. Regression test: `KujiBoxTierSiteIT` (2 tests, both failed before the fix with `site_id` null).
- **Why the suite missed it.** The Spring ITs run on H2 with `ddl-auto=create-drop`, so the schema comes from the entity mappings, where `site_id` was nullable. A null tier site produced no error and no test asserted the value. Closed by marking the eight constrained columns `nullable = false` (`KujiBox`, `KujiBoxTier`, `Lootbox`, `LootboxTier`, `LootboxPrize`, `LootboxPlay`, `CoinAdjustment`, `ReviewDailyCount`). The only other failure this exposed was a fixture in `StockMovementRepositoryIT` that seeded a box and tier without a site.
- **Correction.** An earlier note in this session claimed `LootboxDevSeed` would hit the same NOT NULL. It will not: `seedDefaultCrate()` and `seedTestCrate()` are commented out in `seed()`, so that code never runs.
- **Verified.** `./mvnw -o test -Dtest='*'`: 1171 tests, 0 failures, 0 errors. Web: typecheck clean, 468/468 vitest. messaging-service pytest 78, forecasting-service pytest 355, `tests/contracts` 31, all passing.
- **Not verified.** `tests/e2e` could not start: the forecasting container's healthcheck uses `curl`, which the image does not have (same files on `main`, so pre-existing). Web lint has no ESLint config. The UI write-path sweep against the migrated container, the messaging-service review-count write against the constrained schema, and a run of V76/V78 against a full production dump are still manual steps.
- **Reviews scope (for the next slice).** `review_daily_counts` is site-scoped and written by the messaging service using `REVIEW_SITE_CODE` (default MAIN). The `reviews` table itself has no `site_id`; that belongs to `phase-7-notifications-reviews-audit`.

## Test plan

- AC-1: PostgreSQL migration/backfill/constraint ITs (see T-2's migration sequence above) — **now implemented and passing** in `KujiLootboxSiteMigrationIT`, not just planned.
- AC-2-3: Lifecycle, concurrency, site authorization and atomic inventory/outbox tests.
- AC-4: ArchUnit baseline review.
- AC-5: Contract and rendered web tests.
