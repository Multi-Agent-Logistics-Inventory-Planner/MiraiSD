# Validation

## Command and scope

Backend (`services/inventory-service`), after applying the review fixes in `review.md`
(the four Kuji/Lootbox findings plus the two pre-existing, unrelated architecture-test fixes):

```
./mvnw -q -o compile
./mvnw -o test -Dtest='*'
```

## Result

Backend: compile clean. `1169` tests, `0` failures, `0` errors — the full suite, every class,
in one combined run (`-Dtest='*'` pulls in both unit and integration tests, unlike a bare
`./mvnw test` — see the standing project note in `.specs/phase-7-kuji-lootbox/log.md` about
`*IT.java` coverage). This includes `CatalogEntityAccessCallerSetTest` and `ArchitectureTest`,
both now passing.

Frontend (`apps/web`), unchanged by this review round (no web files touched): typecheck clean,
`468/468` tests pass (carried over from the T-4 validation, re-confirmed unaffected).

New regression tests added for the four Kuji/Lootbox findings (all included in the `1169` total):

- `IdempotencyServiceTest` — 4 tests
- `SiteKujiBoxControllerActorSpoofingTest` — 12 tests
- `KujiBoxServiceTest` — 4 new tests (cross-site source-location rejection, plus broadcast
  assertions folded into 3 existing tests)
- `KujiBoxServiceCatalogFacadeMigrationTest` — 1 new assertion folded into an existing test

No new tests for the two pre-existing architecture-test fixes — both are test-infrastructure
fixes (a more robust method-attribution algorithm, and re-freezing 3 already-accepted violations
under their shifted synthetic names), not new production behavior to cover.

## Acceptance criteria evidence

- AC-1 (site ownership + expand/backfill/verify/constrain): unchanged by this round — V75-V80,
  `KujiLootboxSiteMigrationIT` (4/4), see prior T-3/T-5 validation record in `log.md`.
- AC-2 (site context resolved before lookup; tenant-consistent child/location/product
  relationships): **strengthened this round.** The cross-site inventory-drain gap
  (`executeKujiSourceRemoval`) and the actor-spoofing gap (`SiteKujiBoxController`) were both
  live violations of this criterion; both are closed and regression-tested (see `review.md`).
- AC-3 (inventory changes via `InventoryOperations`; atomic audit/outbox; site-aware
  events/realtime): **strengthened this round.** Six Kuji audit broadcasts were site-less,
  contradicting this criterion; all now carry the real site id, regression-tested.
- AC-4 (module ownership; ArchUnit edges): unaffected by the Kuji/Lootbox fixes — no new
  cross-module dependency introduced. `ArchitectureTest` (which enforces this) now passes in
  full, including the pre-existing violation-store fix.
- AC-5 (v1 route/web migration): unaffected — no route shape or web code changed this round.
- AC-6 (PostgreSQL, lifecycle/concurrency, authorization, contract and architecture tests pass):
  **fully satisfied.** Authorization tests pass (`SiteKujiBoxControllerActorSpoofingTest`,
  existing `Site*SecurityTest` suites). PostgreSQL/migration tests pass
  (`KujiLootboxSiteMigrationIT`). `ArchitectureTest` and `CatalogEntityAccessCallerSetTest` now
  both pass. The full suite (1169 tests) is green in one combined run.
