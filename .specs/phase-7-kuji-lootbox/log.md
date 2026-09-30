# Implementation log

## Current handoff

- Status: T-1 and T-2 complete. T-3 partially complete: migrations V75-V79 written and IT-verified against real PostgreSQL (Testcontainers); `site_id` fields added to all six affected JPA entities plus `ReviewDailyCount`. The service/controller/repository move and site-scoping (the bulk of T-3), and all of T-4/T-5, are NOT started — see "T-3 remaining work" below for exactly what's left and why it stopped here.
- Next action: T-3 continued — implement the site-scoping and module-move work itemized below, in this order: (1) close the two concrete security gaps (`LootboxAdminService.createCrate`/`updateCrate` trusting `req.siteId()`; `updatePrize`'s unscoped tier lookup) since they're the highest-value, most contained fix; (2) thread `siteId` through `LootboxService`/`LootboxAdminService`/`CoinAdminDashboardService`/`KujiBoxService` per the T-2 call-site list; (3) add v1 `Site*Controller`s mirroring `displays.api.SiteMachineDisplayController`; (4) the physical package move into `kuji`/`lootbox` module dirs; (5) the messaging-service Python writer change; (6) T-4 tests and web migration; (7) T-5 realtime + Custom Kuji gate decision.
- Decisions that must survive compaction: `products.is_active` remains global stock-derived state; site assortment is `site_products.is_stocked`; no non-MAIN Custom Kuji enablement until tenancy/realtime readiness is proved. Kuji's stock-write path already routes through `InventoryOperations` — T-3's Kuji work is repository/query-edge cleanup and event site-propagation, not a stock-write rewrite. Lootbox has no product/inventory coupling at all. **User-confirmed 2026-09-27: coin balance is site-of-action, per-site, not per-user-home-site.** `coin_adjustments` and `review_daily_counts` get a `site_id` captured from the authorized site context at write time going forward; all historical rows backfill to MAIN. **T-2 decision: every one of the eight tables gets its own explicit `site_id` column** (no join-only "derived-site" tables), because several hot queries (`findByIdForUpdate`, `decrementQuantity`) operate on the child row directly and a join-derived predicate would change their locking/shape, and the durable spec (multi-site-data-and-api.md §2) rejects "site derivable via parent" as sufficient. Mapped as a raw `UUID siteId` field (matching `Lootbox.siteId`'s existing style), not a `@ManyToOne Site`, to avoid a new `kuji -> sites` / `lootbox -> sites` module edge (AC-4 requires new lootbox edges to be reviewed first). **New: migrations V75-V79 are written, IT-tested, and considered done** — do not rewrite them; extend forward (V80+) instead. **New: the entity `site_id` fields are added but NOT yet populated or read by any service code** — they are inert scaffolding until the service-layer work below lands.
- Last verified: `./mvnw -q -Dtest=KujiLootboxSiteMigrationIT test` — 3/3 pass (backfill correctness + row-count preservation, V76 MAIN-guard failure, V78/V79 composite-FK/trigger/NOT-NULL enforcement). `./mvnw -q -o compile` — clean, entity changes compile. Both run 2026-09-27 in this worktree.
- Open risks/questions: None new. All four T-2 pre-flight checks came back clear (recorded below) and the migrations built on them are now verified, not just planned.

## T-3 remaining work (why this stopped here, and what's left)

This session (a forked implementation pass) completed the migrations and entity scaffolding — the part of T-3 that is self-contained, independently testable via Testcontainers, and carries the actual tenant-safety payload (the composite FKs and the `kuji_boxes` trigger close two real gaps T-2 found: `lootboxes.site_id` client-trust and `lootbox_prizes.tier_id` repointing). It deliberately stopped before the service/controller/package-move work because that remaining piece is large (~1,600 lines across `KujiBoxService`/`LootboxService`/`LootboxAdminService`/`CoinAdminDashboardService` and three controllers, per the T-2 call-site list already in this log) and doing it hastily in the same pass risked landing a half-site-scoped, under-tested state that is worse than clearly stopping at a coherent checkpoint. This is a scope judgment call, not a blocked/uncertain one — the T-2 plan is concrete enough to execute; it is simply more implementation than one pass should rush.

Concretely still open for T-3:
- Close `LootboxAdminService.createCrate`/`updateCrate` (stop trusting `req.siteId()`) and `updatePrize` (site-scope the tier lookup so a foreign-site `tierId` 404s instead of repointing).
- Thread a `siteId` parameter through every method on the T-2 call-site list (`LootboxService`, `LootboxAdminService`, `CoinAdminDashboardService`) and their repositories.
- Site-scope `KujiBoxController`/`LootboxController`/`LootboxAdminController` via `AuthorizedSiteContextHolder`, following `displays.api.SiteMachineDisplayController`'s v1-route pattern (new `Site*Controller`s delegating to now-site-parameterized service methods; legacy routes keep working, resolving MAIN via the existing `LegacyMainSiteContextResolver` pattern).
- The physical move of `controllers/KujiBoxController.java`, `services/KujiBoxService.java`, `repositories/KujiBox*Repository.java`, `models/kuji/*`, `dtos/**/kuji/**` (and the Lootbox equivalents) into the `kuji`/`lootbox` module directories (api/application/domain/infrastructure split, per the `displays` module template already read this session).
- Removing Kuji's direct `AuditLogRepository`/`UserRepository`/`LocationRepository` access and the raw `EntityManager` JPQL (`findKujiBoxMovementsAtOrAfter`, `findKujiReversalsForAuditLog`), replacing with facades/an `InventoryQueries`-style projection; updating `module-dependency-edges-baseline.txt` to shrink `kuji -> repositories`/`kuji -> models`.
- The nine site-less `broadcastInventoryUpdated()` calls need the resolved site threaded through once the surrounding methods are site-parameterized.
- `services/messaging-service/src/adapters/supabase_repo.py`'s `increment_daily_count`: add `site_id` arg, new `(site_id, user_id, date)` conflict target, resolve site from a new `REVIEW_SITE_CODE` config next to `get_review_coin_rate()`.

Then T-4 (contract regen, web migration of the callers T-1 found, the fuller test matrix T-2 specified — balance-preservation IT, per-site isolation, concurrency including the `createAdjustment` advisory-lock gap, ArchUnit) and T-5 (site-aware realtime, the Custom Kuji gate decision, and implementing `V80__drop_global_kuji_open_box_uniqueness.sql` for real) remain entirely not started.

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

## Test plan

- AC-1: PostgreSQL migration/backfill/constraint ITs (see T-2's migration sequence above) — **now implemented and passing** in `KujiLootboxSiteMigrationIT`, not just planned.
- AC-2-3: Lifecycle, concurrency, site authorization and atomic inventory/outbox tests.
- AC-4: ArchUnit baseline review.
- AC-5: Contract and rendered web tests.
