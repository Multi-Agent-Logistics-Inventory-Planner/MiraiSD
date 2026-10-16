# Validation

This record covers two validation passes: the first after the T-1 through T-4 implementation and
its parallel Standards+Spec review (findings fixed and reverified), and a second after the user
requested a further independent check, which found and fixed one more real bug (v1 `set` returning
the wrong display's DTO on a multi-product machine), one ineffective regression test (asserting a
column that's never actually populated), and one dead client parameter. Both passes' commands and
evidence are below; the second pass's numbers are the current, final state.

## Command and scope

All commands run locally against the uncommitted phase-7-displays worktree, independently re-run
and confirmed by the coordinating session in both passes — not taken on any subagent's word alone:

- `services/inventory-service$ ./mvnw -o compile` — clean
- `services/inventory-service$ ./mvnw -o test` — unit suite
- `services/inventory-service$ ./mvnw -o test -Dtest='*IT'` — Testcontainers integration suite
  (note: `./mvnw test` alone skips every `*IT.java`; both invocations are required for real
  coverage)
- `services/inventory-service$ ./mvnw -o test -Dtest='*Architecture*,*CallerSetTest'` — ArchUnit
  module-boundary suite specifically
- `apps/web$ npx vitest run` — full web unit/hook test suite
- `apps/web$ npx tsc --noEmit` — web typecheck
- Direct source read of `SiteMachineDisplayController` to confirm the actor-id spoofing fix and
  the DELETE-route query-param removal, rather than trusting the fix report's description
- Direct grep of `use-machine-display-mutations.ts` to confirm site-scoped query-key invalidation

## Result

Pass, as of the second pass (2026-09-25). All six checks are green, and the highest-risk fixes in
both passes were verified by reading the actual changed source, not just by re-running a test suite
that could itself have been written to (wrongly) confirm the bug:

- Pass 1: the actor-id spoofing fix (`SiteMachineDisplayController`'s `contextActorId()` override)
  and the site-scoped query-invalidation key were confirmed by direct source read/grep.
- Pass 2: the multi-product `set` response fix (`toResponseDTO`) was confirmed by adding a
  response-body assertion (`$.productId`) to `SiteMachineDisplayControllerAuthorizationIT` that
  would have failed against the pre-fix code; the DELETE actor test's corrected assertion
  (`getUser().getId()` instead of the never-populated `getActorId()` shadow column) was confirmed
  by first reproducing the assertion failure, then fixing it and re-running.

- Backend unit: **514/514**
- Backend integration (Testcontainers): **612/612** (594 after T-4, +18 from the review-fix pass:
  the expanded `SiteMachineDisplayControllerCrossSiteAccessIT`, new `MachineDisplaySiteBackfillIT`,
  new `LegacyMachineDisplayDeprecationHeadersIT`, new `SiteMachineDisplayControllerAuthorizationIT`)
  ArchUnit rules included, including the new `noProductionClassOutsideDisplaysDependsOnDisplaysInfrastructure`
  and `DisplaysFacadeCallerSetTest`
- Backend ArchUnit suite in isolation: **16/16**
- Web unit/hook tests: **468/468** (463 after T-4, +5 from the strengthened query test and the new
  mutation-hook test file)
- Web typecheck: clean

No production database migration was executed against Supabase as part of this validation —
V72-V74 were validated exclusively via Testcontainers (`MachineDisplaySiteBackfillIT`) against a
real ephemeral Postgres, matching the shipments precedent. The constrain migration (NOT NULL + FK
on `machine_display.site_id`) remains explicitly out of scope for this feature record, per T-2's
and the review's agreement — it is a separately released, human-gated migration once this code
rollout is deployed and production data is reconfirmed NULL-free.

## Acceptance criteria evidence

- **AC-1** (verified production table): `machine_display` confirmed live against Supabase
  `information_schema`, 879 rows, all resolving to the single MAIN site with zero cross-site or
  missing-`site_products` inconsistencies (T-1 evidence, log.md).
- **AC-2** (expand/backfill/verify site migration): V72 (expand)/V73 (backfill)/V74 (verify
  indexes, `CONCURRENTLY`) present; constrain correctly deferred. `MachineDisplaySiteBackfillIT`
  now proves the backfill's non-MAIN resolution, orphan-fallback, fail-loud-on-missing-MAIN, and
  idempotency behavior against real Postgres.
- **AC-3** (site-authorized reads/mutations, foreign-site inaccessible): `SiteMachineDisplayController`
  resolves site from `AuthorizedSiteContextHolder`, not the path variable trusted blindly.
  `SiteMachineDisplayControllerCrossSiteAccessIT` now covers 9 cases (same-site control plus
  foreign-machine/foreign-display-id rejection across set/batch-swap/swap/renew/batch-clear/by-id).
  `SiteMachineDisplayControllerAuthorizationIT` proves the actor-id spoofing fix with real HTTP
  calls.
- **AC-4** (module ownership, foreign-repository elimination): fully met — `displays` only reaches
  other modules through `LocationService`, `UserService`, `InventoryOperations`, and its own
  facades are the only sanctioned entry point for `AnalyticsService`/`ProductReportBundleService`/
  `KujiBoxService`. The dependency-edge *count* in `module-dependency-edges-baseline.txt` grew net
  (one edge removed, nine added) rather than shrinking; every edge was confirmed against real
  imports as necessary, and `displays -> services` / `models -> displays` are recorded as temporary,
  expected to be removed by a future audit/notification facade and the kuji slice's UUID-column
  migration respectively.
- **AC-5** (v1 routes replace new legacy use, legacy explicitly protected/deprecated): v1
  `/api/v1/sites/{siteId}/machine-displays` routes (19 handlers) live; `apps/web` fully migrated
  (`site-machine-displays.ts`, rewritten hooks; T-4 required no component changes for the routing
  migration itself — the second review pass's `location-detail-sheet.tsx` edit was a follow-on fix
  removing a dead `actorId` argument at that component's one call site, not a routing-migration
  change); legacy
  `/api/machine-displays` routes get `Deprecation`/`Link` headers via
  `LegacyMachineDisplayDeprecationFilter`, proven by `LegacyMachineDisplayDeprecationHeadersIT`
  (bare path, sub-path, and confirmation the v1 routes do *not* get the headers).
- **AC-6** (PostgreSQL authorization/migration and module-boundary tests pass): all six commands
  above green; see Result.

## Known accepted residual risk (not blocking)

- Dev-only seed displays (`DevSeedController`) still write a NULL `site_id` and won't appear on v1
  routes — pre-existing gap, dev-profile only, not a production path.
- `DisplayQueries` analytics reads remain cross-site (unscoped) by design until a future analytics
  slice adds site-scoped variants.
- `KujiBox.machineDisplay` remains a `@ManyToOne` into `displays.domain.MachineDisplay`
  (`models -> displays` edge); replacing it with a UUID column, and reconciling kuji's
  auto-attach/detach not writing DISPLAY_SET/DISPLAY_REMOVE audit/stock-movement entries the way
  the direct API does, are both explicitly deferred to the kuji/lootbox slice.
