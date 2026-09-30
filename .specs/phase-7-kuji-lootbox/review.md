# Review

## Scope reviewed

Full implementation diff for `.specs/phase-7-kuji-lootbox` (T-1 through T-5, commits `dedf4c1`
through `e47a6c1` on `refactor/phase-7`, plus the fixes below): migrations V75-V80, entity
site-scoping, Kuji and Lootbox service/controller site-scoping, the physical module move, the v1
`Site*Controller` routes, the messaging-service Python writer, and the `apps/web` migration.
Reviewed externally (independent tool) after the feature was declared T-1-T-5 complete; findings
below were verified against the actual code and fixed in this session rather than taken on faith.

## Findings

- [Spec] Kuji can decrement inventory from another site — `executeKujiSourceRemoval` used the
  unscoped two-argument `findInventory`, reachable with a client-supplied `sourceLocationId` from
  open-box, transfer-in (both variants), add-tier, and patch-tier — **act on** (AC-2). Fixed:
  threaded `siteId` into `executeKujiSourceRemoval` and switched to the site-scoped three-argument
  `findInventory(siteId, locationId, productId)`, which returns empty (not another site's row) for
  a foreign-site location. Regression test:
  `KujiBoxServiceTest.transferInInventoryOnly_rejectsASourceLocationBelongingToAnotherSite`.
- [Spec] Most v1 Kuji mutations trusted a client-supplied `actorId` in the request body —
  `SiteKujiBoxController` passed DTOs straight through to `KujiBoxService`, and the service writes
  that field into audit logs and stock movements; only `reopen`/`undo` derived the actor from the
  authenticated principal — **act on** (AC-2, authentication spec). Fixed: every mutation handler
  now overwrites `request.setActorId(contextActorId())` before calling the service, so the field
  is validated but never trusted. Regression tests:
  `SiteKujiBoxControllerActorSpoofingTest` (12 tests, one per mutation handler plus the two that
  never accepted a client actor in the first place).
- [Spec] Lootbox's in-memory idempotency cache key was `userId:key` only, with no site component —
  replaying the same `Idempotency-Key` at a second site within the 5-minute TTL could return the
  first site's cached prize and balance without executing a play at the second site — **act on**
  (AC-2). Fixed: `IdempotencyService.get`/`put` now take `siteId` as part of the cache key, and
  both callers (`LootboxController`, `SiteLootboxController`) pass their already-resolved site.
  Regression tests: `IdempotencyServiceTest` (4 tests, including identical user+key at two sites).
- [Spec] Several Kuji audit broadcasts (`undoDraw`, `addSlip`, `moveSlips`, `deletePrize`,
  `addTier`, plus `recordDraw`) called the legacy one-argument `broadcastAuditLogCreated`
  overload, emitting no `siteId` — contradicting the T-5 "fully site-aware" conclusion and causing
  clients to treat these as relevant to every site — **act on** (AC-3, T-5). Fixed: all six now
  call the two-argument, site-aware overload. Regression coverage: broadcast assertions added to
  the existing `recordDraw`/`undoDraw`/`addSlip` tests and the existing `addTier` test, plus two
  new tests for `moveSlips`/`deletePrize` (previously untested).
- [Standards] `review.md`/`validation.md` still described kickoff-only planning work while the
  feature was recorded as T-1-T-5 complete — **act on** (SDD workflow, Full-tier closure
  artifacts). Fixed: this file and `validation.md` now reflect the actual implementation and the
  real validation run below.

## Residual risk (resolved)

- `CatalogEntityAccessCallerSetTest.catalogEntityAccessCallersMatchT0Inventory` and
  `ArchitectureTest.repositoriesAreOnlyAccessedByServicesOrRepositories` both failed on
  `refactor/phase-7` independent of this feature (confirmed by stashing every Kuji/Lootbox
  review-fix change and re-running both against the clean tree, where they failed identically).
  Neither touched Kuji or Lootbox code, so they were out of this feature's scope — but per an
  explicit follow-up decision, both were investigated and fixed anyway so AC-6 is fully green
  rather than formally partial. Root causes and fixes:
  - `CatalogEntityAccessCallerSetTest`: on this codebase's compiled output, javac names every
    lambda's synthetic method `lambda$<N>` — a bare, class-wide counter with no enclosing-method
    segment (not the commonly-assumed `lambda$<enclosingMethod>$<N>` shape). The test's
    `topLevelMethodName` helper name-parsed that assumed shape to recover a lambda's enclosing
    method, which silently breaks for every class where it actually matters. Replaced with a
    line-range resolver: source line ranges of declared methods never overlap in Java, so the
    enclosing method for any access is the nearest non-synthetic method declared at or before
    that access's line, found via `JavaMember.getSourceCodeLocation()` — robust regardless of how
    javac names the lambda. Verified against `MachineDisplayService`/`DisplayOperations`'s actual
    compiled lambda names before writing the fix, not guessed.
  - `ArchitectureTest.repositoriesAreOnlyAccessedByServicesOrRepositories`: the same lambda-naming
    behavior, applied to 3 already-accepted `DevSeedController` violations (`ensureDevEmployee`,
    `ensureMachinesExist`, `seedAll` calling `UserRepository.save`/`LocationRepository.save`
    directly) — an unrelated code change wrapped those calls in lambdas sometime after the store
    was last frozen, shifting them from named-method violation text to `lambda$N` text. Confirmed
    same repository methods and near-identical line numbers before re-freezing under the new
    text, using this project's established manual re-freeze precedent (`allowStoreUpdate=true`
    prunes obsolete entries but doesn't persist renamed ones, so renamed violations must be
    copied from the surefire report into the frozen store by hand); not a code change to
    `DevSeedController` itself — its pre-existing repository access remains unreviewed technical
    debt, unchanged.
- The `IdempotencyService` fix is the minimal, scoped one the review asked for (site added to the
  cache key). A durable, DB-backed replacement already exists and is used elsewhere
  (`shared.idempotency.CommandIdempotencyService`, used by `SiteInventoryMutationController`);
  migrating Lootbox's `play()` onto it would be a larger, independently-scoped change, not a
  security fix, and was not attempted here.
- `moveSlips`/`deletePrize` had no prior test coverage at all (not just missing the site-broadcast
  assertion) — the two new tests added here are each mostly-minimal happy-path coverage, not a
  full behavioral test suite for those two mutations.
