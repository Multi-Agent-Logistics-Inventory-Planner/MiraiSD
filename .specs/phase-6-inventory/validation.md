# Validation

## Review-driven fix: 6d P1/P2 findings (external review) (2026-09-14)

Commands and results for the fixes recorded in `review.md`'s "6d P1/P2 findings (external review)
— 2026-09-14" entry. Carries forward, rather than re-pastes, the prior sessions' baselines: backend
371 unit-test run / 509 IT run (8 pre-existing security-IT failures) from the "6d backend slice"
fix session below; web 51 files/363 tests, 0 errors/51 warnings from the "6d web slice" fix session
below.

### P1 backend — command and scope

```sh
cd services/inventory-service
./mvnw -q clean test-compile
./mvnw -q test -Dtest=StockMovementServiceSiteScopedConcurrentSourceCheckRaceIT
./mvnw -q test -Dtest='StockMovementServiceConcurrent*IT,StockMovementServiceMixedAdjustTransferLockOrderIT'
./mvnw -q test -Dtest='SiteInventoryMutationController*IT'
./mvnw -q clean test
./mvnw -q test -Dtest='*IT'
```

### P1 backend — result

- `./mvnw -q clean test-compile` — **clean, no output (BUILD SUCCESS).**
- Failing-test-first proof: ran the new
  `StockMovementServiceSiteScopedConcurrentSourceCheckRaceIT` against the code *before* the
  `requireInventoryBelongsToSite` fix (repository `existsByIdAndSite_Id` method present but unused
  by the service — confirmed by running the test against the stashed, pre-fix
  `StockMovementService.java`/`LocationInventoryRepository.java`). **Failed for the right reason:**
  `AssertionFailedError: expected: 23 but was: 53` — baseline quantity 100, `deltaA=30`,
  `deltaB=47`, expected combined debit `100-30-47=23`; actual `53 = 100-47`, meaning transfer A's
  debit was silently lost, exactly the lost-update failure mode the finding predicted (one debit
  overwritten by the other transaction's stale-cached-entity write). Restored the fix (switched
  `requireInventoryBelongsToSite` to the scalar `existsByIdAndSite_Id`) and reran: **passed, no
  errors.**
- `StockMovementServiceConcurrent*IT,StockMovementServiceMixedAdjustTransferLockOrderIT` (the
  existing concurrency/lock-order sibling suite, run alongside the new test) — **all green, no
  `[ERROR]` output** — confirms the fix does not regress the established lock-ordering discipline
  (`StockMovementServiceConcurrentTransferExistingDestinationRaceIT`,
  `StockMovementServiceConcurrentTransferNewDestinationIT`,
  `StockMovementServiceConcurrentBatchTransferCrossedDestinationsIT`,
  `StockMovementServiceConcurrentAdjustIT`, `StockMovementServiceMixedAdjustTransferLockOrderIT`,
  plus the new test).
- `SiteInventoryMutationController*IT` (security + atomicity family) — **all green, no `[ERROR]`
  output.**
- `./mvnw -q clean test` — **clean, no `[ERROR]` output.** Surefire text-summary total (summed
  across `target/surefire-reports/*.txt`): **371 run, 0 failures, 0 errors, 0 skipped** — same
  count as the prior (6d backend slice) session's own recorded number, consistent with that
  session's noted Surefire nested-test undercount quirk (`LocationInventoryServiceTest`'s XML
  report shows more than its text-summary count), unrelated to this fix.
- `./mvnw -q test -Dtest='*IT'` — **510 run** (up 1 from the prior session's 509, the new IT),
  **8 failures**, name-for-name identical to the prior session's recorded pre-existing set
  (`AnalyticsControllerSecurityIT.getDemandLeaders_adminRole_returns200`,
  `.getDemandLeaders_assistantManagerRole_returns200`,
  `.getInventoryByCategory_adminRole_returns200`, `.getInventoryByCategory_employeeRole_returns200`,
  `.getPerformanceMetrics_adminRole_returns200`, `.getPerformanceMetrics_employeeRole_returns200`,
  `ForecastControllerSecurityIT.getAllForecasts_adminRole_returns200`,
  `.getAllForecasts_employeeRole_returns200`, all `Status expected:<200> but was:<500>`) — no new
  failure, no inventory-related IT failed.

### P2 web — command and scope

```sh
cd apps/web
npx tsc --noEmit -p tsconfig.json
npx vitest run src/components/products/__tests__/product-modal.test.tsx
npx vitest run
npx eslint .
```

### P2 web — result

- Failing-test-first proof: ran the new `product-modal.test.tsx` against the pre-fix
  `product-modal.tsx`/`use-product-inventory-entries.ts` (temporarily stashed the fix). **2 of 3
  tests failed for the right reason:** the error-state test failed on
  `screen.getByText("Couldn't load inventory")` — `TestingLibraryElementError: Unable to find an
  element with the text` (the component rendered "No inventory at any location" instead, proving
  the bug: a failed read renders identically to the genuine-empty state); the disabled-buttons test
  failed with `Received element is not disabled` on the first Transfer button. The third
  (genuine-empty-state regression guard) test passed even pre-fix, as expected — it only pins
  existing behavior. Restored the fix (popped the stash) and reran: **all 3 passed.**
- `npx tsc --noEmit -p tsconfig.json` — **clean, exit 0.**
- `npx vitest run` — **52 test files passed, 366 tests passed, 0 failed** (up from the prior "6d
  web slice" session's 51 files/363 tests: +1 new file, `product-modal.test.tsx`, +3 tests).
- `npx eslint .` — **0 errors, 51 warnings** — identical set to the prior session's recorded
  baseline (all pre-existing `react-hooks/set-state-in-effect`/`react-hooks/exhaustive-deps`
  warnings on files this session did not touch, plus the one pre-existing `use-toast.ts` unused-var
  warning); no new warning introduced by this session's changes.

### Judgment call: gating Adjust/Transfer during an inventory-read error

Both Adjust and Transfer are now `disabled` (with an explanatory `title` tooltip) while
`useSiteProductInventoryEntries` reports an error, on both the desktop and mobile button rows.
Reasoning: `hasInventory`/`locations` becomes indistinguishable from a genuine zero-stock product
during a read failure (both are the empty array), so letting the buttons stay enabled would let a
user "successfully" open a transfer/adjust flow seeded with a table that is actually just wrong,
not actually empty — worse than blocking the action outright, since the flow itself offers no
signal that the underlying data never loaded. Transfer's `onClick` also gained an explicit
error-branch destructive toast ("Inventory failed to load" / "Retry loading inventory before
transferring stock.") ahead of its existing `hasInventory` check, for defense in depth (a disabled
button should not fire `onClick` in a real browser or in jsdom's `fireEvent`, confirmed by this
session's own test run, but the branch keeps the failure mode honest rather than silently falling
through to the misleading "No inventory to transfer" message if the disabled state is ever
bypassed). `useSiteProductInventoryEntries` gained a plain passthrough `refetch: query.refetch` (no
new wrapper logic) rather than a new invalidation helper, since `useQuery` already provides exactly
the retry semantics needed and the review's own text named this as the preferred option when
available.

### Disposition

Both findings fixed and re-verified with revert-verified new tests (P1: real-Postgres concurrency
IT, reproduced the lost update before the fix, confirmed fixed after; P2: rendered component test,
reproduced the silent-empty-state bug before the fix, confirmed fixed after). No regression in
either language's full suite; both failure counts match their respective pre-existing baselines
exactly. `packages/contracts`/`packages/api-client` untouched.

## Review-driven fix: 6d web slice findings (2026-09-14)

Commands and results for the fixes recorded in `review.md`'s "6d web slice (T-6d-1..T-6d-14) —
independent review, 2026-09-14" entry (findings 1/2/4/5/6/7/8 fixed; finding 3 resolved via a
production-data check, no code change). Carries forward, rather than re-pastes, the prior 6d web
implementation session's own baseline — see log.md's "6d implementation (T-6d-1..T-6d-14)"
section, "Full-suite verification" subsection, for that session's starting numbers (48 files/353
tests, 0 errors/51 warnings, `tsc --noEmit` clean).

### Command and scope

```sh
cd apps/web
npx tsc --noEmit -p tsconfig.json
npx vitest run
npx eslint .
```

### Result

- `npx tsc --noEmit -p tsconfig.json` — **clean, exit 0.**
- `npx vitest run` — **51 test files passed, 363 tests passed, 0 failed** (up from the prior
  session's 48 files/353 tests: 3 new files —
  `components/products/__tests__/product-form.test.tsx` (2),
  `components/stock/__tests__/adjust-stock-dialog.test.tsx` (2),
  `components/stock/__tests__/transfer-stock-dialog.test.tsx` (1) — plus new cases added to 3
  existing files —
  `hooks/queries/__tests__/use-site-product-inventory.test.ts` (+2: finding 5's loading-state
  test and finding 4's late-old-site-result test),
  `hooks/realtime/__tests__/use-realtime-inventory.test.ts` (+1: finding 2's
  children/with-children collision regression test),
  `lib/api/site-inventory.test.ts` (+2: finding 8's empty-body guard tests) — net +10 tests,
  matching 353 + 10 = 363).
- `npx eslint .` — **0 errors, 51 warnings** — same count as the prior session's baseline. One new
  warning was introduced mid-session by finding 5's fix (`use-product-inventory.ts`'s `useMemo`
  gained a `siteId` reference without adding it to the dependency array,
  `react-hooks/exhaustive-deps`); fixed by adding `siteId` to the deps array before this count was
  taken, verified by rerunning eslint immediately after (52 warnings transiently, then back to 51
  once fixed). Every one of the 51 final warnings is a pre-existing
  `react-hooks/exhaustive-deps`/`react-hooks/set-state-in-effect` pattern on lines this session
  did not touch (checked against `git diff` for each flagged file), plus the one pre-existing
  unused-var warning in `use-toast.ts` — identical set to the prior session's own recorded
  baseline.
- **Failing-test-first proof, finding 1** (`product-form.tsx`): before moving the `siteId` check
  inside the initial-stock block, the new "surfaces a destructive toast..." test failed because
  `createSiteLocationInventory`/`resolveSiteLocationId` were still called even without the fix in
  place being exercised as a guard — reverted the fix locally, reran the test, confirmed it failed
  (no destructive toast fired, and the call assertions did not hold), then restored the fix and
  reran green.
- **Failing-test-first proof, finding 2** (`legacy-products-query-filter.ts`): temporarily reverted
  the predicate from `query.queryKey.length === 2` back to `query.queryKey[2] !== "site"` and
  reran `use-realtime-inventory.test.ts`'s new "never appends into a different product's
  children/with-children cache entry" test in isolation — failed, with the seeded
  `["products", "other-product", "children"]` cache mutated by the realtime write; restored the
  fix, reran — passed.
- **Failing-test-first proof, finding 5** (`use-product-inventory.ts`): temporarily reverted the
  memo's early-return guard to the pre-fix `if (!products || !siteProducts) return null;` and
  reran the new "stays null while totals are still loading" test — failed (`result.current.data`
  was non-null with a fabricated `totalQuantity: 0` row before totals resolved); restored the fix,
  reran — passed.

### Finding 3 evidence

Ran the review-specified read-only query against the project's real production database (live
`mcp__supabase` access):

```sql
SELECT sl.site_id, count(*) AS hidden_rows
FROM location_inventory li
JOIN locations l ON l.id = li.location_id
JOIN storage_locations sl ON sl.id = l.storage_location_id
JOIN products p ON p.id = li.product_id
WHERE sl.code = 'NOT_ASSIGNED'
  AND (p.parent_id IS NOT NULL OR p.kuji_type = 'CUSTOM')
GROUP BY sl.site_id;
-- -> zero rows returned
```

**Result: zero rows returned** — no kuji-child or CUSTOM-kuji-parent row at a NOT_ASSIGNED
location exists in production today, so T-6d-9's NOT_ASSIGNED read-filter change (moving off the
legacy, unfiltered `findByStorageLocation_Id`-backed read onto the v1, root-product-filtered
route) hides nothing that is actually present in the live data. **Disposition: resolved, no code
change** — this closes the open item recorded in T-6d-9's "Behavior change this session is
explicitly flagging" note (log.md's 6d web implementation section) with real evidence rather than
leaving it as an unverified assumption.

### Acceptance criteria evidence

- **AC-5** (v1 DTO/contract correctness, safe handling of the generated client's response shape):
  finding 8 closed a latent gap where an ok-but-empty response body would throw a raw `TypeError`
  instead of the project's own `GeneratedApiError` — `site-inventory.test.ts`'s two new tests
  prove both `getSiteProductInventory` and `getSiteMovements` now fail the same, catchable way as
  every other function in that module.
- **AC-6** (rendered coverage and site-switch isolation): finding 4's three new tests are direct
  evidence for AC-6's previously-unmet rendered-workflow requirement
  (`AdjustStockDialog`/`TransferStockDialog` submit paths) and its late-result-rejection
  requirement (the site-switch race test). Finding 5's fix and test additionally close a real
  rendered-state gap (`location-detail-sheet.tsx`'s embedded `ProductModal` could have flashed a
  false out-of-stock state) that AC-6's rendered-coverage intent was meant to catch.
- **AC-4/R-9** (audited stock mutations, no untracked writes): finding 1's fix ensures a missing
  site never results in a *silently* dropped stock write — the user is now always told, which
  matters because R-9 already removed the legacy untracked "set exact quantity" path in favor of
  this audited create-then-adjust flow; silently losing the signal that it didn't run would have
  reintroduced an equivalent blind spot from the caller's perspective.

Result: **findings 1, 2, 4, 5, 6, 7, and 8 fixed and re-verified this session; finding 3 resolved
via a real production-data check, no code change needed or made.** No `services/inventory-service`,
`packages/contracts`, or `packages/api-client` file was touched (out of scope per the task).
363/363 tests passing (up from 353/353), `tsc` clean, `eslint` 0 errors/51 warnings (same baseline
as the prior session).

## Review-driven fix: 6d backend slice findings (2026-09-14)

Commands and results for the fixes recorded in `review.md`'s "6d backend slice (T-6d-be-1..T-6d-be-8)
— independent review, 2026-09-14" entry (findings 1/2/3/4). Carries forward, rather than re-pastes,
the prior 6d implementation slice's own verification — see log.md's "6d implementation
(T-6d-be-1..T-6d-be-8)" section, "Full-suite verification (checkpoint-slice gate)" subsection, for
that session's numbers (367 unit/component tests, 508 `*IT` tests with the same 8 pre-existing
failures, two independent clean `ArchitectureTest` reruns, stable `OpenApiContractExportTest`).
That slice's T-6d-be-8 contract delta, carried forward unchanged by this fix session (no route/DTO
shape was touched by findings 1/2/3/4): **3 paths added** (`POST`/`DELETE` on
`/api/v1/sites/{siteId}/inventory/locations/{locationId}/items[/{inventoryId}]`, plus `POST
/api/v1/sites/{siteId}/inventory/transfers/batch`), **0 paths removed**, **1 schema added**
(`CreateLocationInventoryRequestDTO`), and exactly **one intentional legacy-schema delta**
(`BatchTransferInventoryRequestDTO.transfers.maxItems`: `2147483647` to `50`, the shared-DTO
`@Size(max = 50)` cap applying to the legacy `/api/stock-movements/batch-transfer` route too, per
the 6d planning worksheet's user-confirmed decision).

### Command and scope

```sh
cd services/inventory-service
./mvnw -q clean test-compile
./mvnw -q -Dtest=SiteInventoryMutationControllerFingerprintTest test
./mvnw -q -Dtest=SiteInventoryMutationControllerSecurityIT,SiteInventoryMutationControllerAtomicityIT,SiteInventoryMutationControllerFingerprintTest,LocationInventorySiteScopedQueriesIT,NotAssignedInventoryReadParityIT,LegacyInventoryDeprecationHeadersIT,LocationInventoryServiceTest test
./mvnw -q clean test
./mvnw test -Dtest='*IT'
```

Finding-3 evidence (read-only, against the project's real Supabase database, via the session's live
`mcp__supabase` access — not H2, not Testcontainers):

```sql
SELECT sl.site_id, count(*) AS na_location_count
FROM locations l
JOIN storage_locations sl ON sl.id = l.storage_location_id
WHERE sl.code = 'NOT_ASSIGNED'
GROUP BY 1
ORDER BY 1;
-- -> [{"site_id":"e898b072-7e51-4399-96fa-173eaa57eded","na_location_count":1}]

SELECT s.id AS site_id, s.code AS site_code, sl.id AS storage_location_id, sl.code AS storage_location_code
FROM sites s
LEFT JOIN storage_locations sl ON sl.site_id = s.id AND sl.code = 'NOT_ASSIGNED'
ORDER BY s.code;
-- -> MAIN (e898b072-7e51-4399-96fa-173eaa57eded): storage_location_id 32ce6d52-3fe4-47f5-8381-647c33ce930a, code NOT_ASSIGNED
-- -> SECOND (7e173c98-288a-4261-93c4-9a9ac13c540f): storage_location_id NULL, code NULL
```

### Result

- `./mvnw -q clean test-compile` — PASS, clean, zero errors (confirms the two new
  `CreateInventoryFingerprintKey`/`DeleteInventoryFingerprintKey` records and the
  `NotAssignedInventoryReadParityIT` Javadoc-only change compile cleanly).
- **Failing-test-first proof, finding 1:** before pinning the delete-fingerprint hash literal, ran
  the new `deleteFingerprint_forFixedLocationAndInventoryId_matchesPinnedHash` with a placeholder
  literal — failed with `expected: "bd05f...adde" but was:
  "8210e6eb400c43f3226db439f1916da14bf6279834a94459f8793822d413cd59"`, i.e. a real, deterministic
  hash the guessed placeholder didn't match; pinned the actual value, reran green.
- **Failing-test-first proof, finding 2:** temporarily reverted
  `fingerprint(new CreateInventoryFingerprintKey(locationId, request))` back to `fingerprint(request)`
  and reran `SiteInventoryMutationControllerSecurityIT#createItem_sameKeySameBodyDifferentLocation_returns409AndDoesNotReuseFirstLocationResponse`
  in isolation — failed with `Status expected:<409> but was:<201>`, proving the new HTTP test
  actually catches the silent-reuse bug, not just re-deriving the (buggy) behavior. Restored the
  fix, reran — PASS (1/1).
- `SiteInventoryMutationControllerFingerprintTest` — PASS, **8/8** (4 pre-existing + 4 new: two
  pinned-hash tests for the delete/create carriers, one stability-across-repeated-calls test, one
  locationId-changes-the-hash test).
- `SiteInventoryMutationControllerSecurityIT`, `SiteInventoryMutationControllerAtomicityIT`,
  `SiteInventoryMutationControllerFingerprintTest`, `LocationInventorySiteScopedQueriesIT`,
  `NotAssignedInventoryReadParityIT`, `LegacyInventoryDeprecationHeadersIT`,
  `LocationInventoryServiceTest` (combined run) — PASS, exit 0. Per-class Surefire totals:
  `SiteInventoryMutationControllerSecurityIT` 24/24 (23 prior + 1 new finding-2 HTTP test),
  `SiteInventoryMutationControllerAtomicityIT` 15/15 (unchanged), `SiteInventoryMutationControllerFingerprintTest`
  8/8, `LocationInventorySiteScopedQueriesIT` 12/12 (unchanged), `NotAssignedInventoryReadParityIT`
  2/2 (unchanged; Javadoc-only change), `LegacyInventoryDeprecationHeadersIT` 6/6 (unchanged),
  `LocationInventoryServiceTest` 18/18 (unchanged; Surefire's text-summary line reports "Tests run:
  0" for this class because all its `@Test` methods live in `@Nested` classes — a pre-existing
  Surefire text-report quirk, not a regression; the XML report (`tests="18"`) and the full-suite
  totals below confirm the real count).
- `./mvnw -q clean test` (full unrestricted suite, skips `*IT.java`) — PASS, exit 0. **371 tests
  counted from Surefire `.txt` summaries, 0 failures, 0 errors** (up from 367 by the 4 new
  fingerprint unit tests; `LocationInventoryServiceTest`'s 18 nested tests are additionally present
  per its XML report but undercounted by the same text-summary quirk above — no failures in either
  count).
- `./mvnw test -Dtest='*IT'` — **509 tests, 8 failures, 0 errors** (up from 508 by the one new
  finding-2 HTTP test). All 8 failures are exactly the same pre-existing set as the prior 6d
  implementation slice's own verification: `AnalyticsControllerSecurityIT` (6 cases) and
  `ForecastControllerSecurityIT` (2 cases) — confirmed by name-for-name comparison against
  log.md's "6d implementation" entry, not merely by count. No inventory-related IT failed.

### Finding 3 evidence

Ran the review-specified read-only query against the project's real database (live `mcp__supabase`
access this session, not a local Testcontainers/H2 stand-in): of the organization's two sites,
`MAIN` has a `NOT_ASSIGNED`-coded `storage_locations` row with exactly one `locations` row beneath
it; `SECOND` has no `NOT_ASSIGNED` `storage_locations` row at all yet (unseeded). No site in the
live data has more than one NA `locations` row — **today's real data does not violate the
one-NA-location-per-site assumption.** This is empirical evidence from the one organization this
system actually runs, not a schema guarantee: `locations` only carries `UNIQUE(storage_location_id,
location_code)`, so nothing prevents a future `LocationService.createLocation` call from adding a
second row under MAIN's NOT_ASSIGNED storage location. **Disposition: open risk, not blocking
T-6d-9** — proceeding with T-6d-9 (NOT_ASSIGNED inventory on v1) is reasonable given the confirmed
real-data state, but the invariant should be treated as monitored, not guaranteed; a future session
should either add a partial-unique index/application-level guard, or a storage-location-scoped v1
read that tolerates more than one NA location, before this assumption is load-bearing for anything
beyond what T-6d-9 already plans. Recorded in log.md's Current handoff as a named open risk, per
the task's explicit instruction not to silently mark this closed.

### Acceptance criteria evidence

- **AC-3** (trusted site context, idempotent-retry correctness): findings 1/2 were both
  idempotency-contract defects on the v1 mutation surface AC-3 governs — a spurious-409 risk
  (finding 1) and a silent-no-op risk (finding 2). Both closed: `SiteInventoryMutationControllerFingerprintTest`'s
  pinned-hash tests prove the delete fingerprint no longer depends on JVM-randomized map ordering;
  `SiteInventoryMutationControllerSecurityIT`'s new HTTP test proves the create route now rejects,
  rather than silently reuses, a same-key-different-location retry.
- **AC-4** (durable envelope, atomicity, idempotency): unaffected in mechanism — `executeIdempotent`
  itself was not changed, only the fingerprint inputs it's given; the existing atomicity proofs in
  `SiteInventoryMutationControllerAtomicityIT` (15/15, unchanged) still hold.
- **AC-5** (v1 DTO/contract correctness): no DTO/route shape changed by this fix session (the
  fingerprint carriers are internal, package-private records never serialized to a client) — no
  `OpenApiContractExportTest`/`packages/contracts/openapi.json` regeneration was needed or run;
  confirmed by inspecting the diff (`CreateLocationInventoryRequestDTO`,
  `SiteInventoryMutationController`'s handler signatures, and `NotAssignedInventoryReadParityIT`'s
  Javadoc are the only non-test-assertion changes, none of them contract-visible).

Result: **findings 1, 2, and 4 fixed and re-verified this session; finding 3 resolved to the extent
real data allows — no violation found, disposition recorded as an explicit open risk rather than
"confirmed safe," with the real-database evidence above.** No production-code change beyond the two
new fingerprint-carrier records and the fingerprint call sites that use them; no test regression
introduced (509/509 `*IT` minus the 8 pre-existing, unrelated failures; 371+/371+ unit/component).

## Review-driven fix: 6c checkpoint P1 findings (independent review) — 2026-09-13

Commands and results for the fix recorded in `review.md`'s "Review-driven fix: 6c checkpoint P1
findings (independent review)" entry (actor-id spoofing on v1 mutation routes; null-site rows
hidden on the per-product movements branch).

```sh
cd services/inventory-service
./mvnw -q clean test-compile
./mvnw -q -Dtest=SiteInventoryMutationControllerAtomicityIT test
./mvnw -q -Dtest=SiteInventoryControllerSecurityIT,LocationInventorySiteScopedQueriesIT test
./mvnw -q -Dtest='SiteInventoryMutationController*,SiteInventoryController*,LocationInventorySiteScopedQueriesIT,StockMovementServiceSameSiteTransferTest' test
./mvnw -q -Dtest=SiteInventoryMutationCrossSiteDestinationIT test
./mvnw -q clean test-compile && ./mvnw -q -Dtest=ArchitectureTest test
./mvnw -q clean test
./mvnw test -Dtest='*IT'
./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'
./mvnw -q -Dtest=OpenApiContractExportTest test
```

## Result

- `./mvnw -q clean test-compile` — PASS, clean.
- `SiteInventoryMutationControllerAtomicityIT` — PASS, 8/8 (6 pre-existing + 2 new
  actor-spoofing-rejection tests for adjust and transfer).
- `SiteInventoryControllerSecurityIT`, `LocationInventorySiteScopedQueriesIT` — PASS, all green
  (includes the new null-site-inclusion tests at both the repository and HTTP level).
- `SiteInventoryMutationController*`, `SiteInventoryController*`,
  `LocationInventorySiteScopedQueriesIT`, `StockMovementServiceSameSiteTransferTest` — PASS,
  exit 0 (confirms the `StockMovementService` signature changes did not break any other caller of
  the changed overloads or the unaffected same-site-transfer unit tests).
- `SiteInventoryMutationCrossSiteDestinationIT` — PASS, real Postgres/Testcontainers, unaffected by
  the actor-id/fingerprint change (exercises the controller HTTP path, which already derives the
  actor from context).
- `ArchitectureTest`, run after an independent clean `test-compile` — PASS, `archunit_store` diff
  empty (no new module edge — the changed methods only touch types already inside `inventory`
  and `shared.web`).
- `./mvnw -q clean test` (full unrestricted suite) — PASS, exit 0.
- `./mvnw test -Dtest='*IT'` — **478 tests, 8 failures, 0 errors** (up from 474 tests by the 4 new
  IT methods; failure count unchanged). All 8 failures are exactly
  `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT`, confirmed pre-existing and
  unrelated by `./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'`
  — PASS, exit 0, 470/470.
- `OpenApiContractExportTest`, then `git diff --stat packages/contracts/openapi.json
  packages/api-client/src/schema.d.ts` — no diff. Both fixes changed only internal
  `StockMovementService`/`StockMovementRepository`/`InventoryQueries` method signatures and
  bodies, not any HTTP route or DTO shape.

## Acceptance criteria evidence

- **AC-3** (trusted site context): actor identity for every v1 mutation now derives from
  `AuthorizedSiteContext.backendUserId()`, never a client-supplied value, closing a real gap in
  the "trusted site context" requirement — proven by the two new
  `SiteInventoryMutationControllerAtomicityIT` tests asserting the persisted actor is the
  principal's id even when the request body names a different one.
- **AC-5**/Q-6c-5 (v1 route correctness, documented compatibility behavior): the per-product
  movements branch now honors the same "include and label unknown-site rows" contract the
  audit-log branch already had, closing the gap between the two branches of one endpoint — proven
  by `LocationInventorySiteScopedQueriesIT`'s repository-level test and
  `SiteInventoryControllerSecurityIT`'s HTTP-level test asserting the response body's
  `siteAttribution: "UNKNOWN"` marker.

## 6c — Scoped inventory backend (T-6c-11..T-6c-17 checkpoint) — 2026-09-13

Environment: JDK 21, `./mvnw` from `services/inventory-service`, `npm` from `packages/api-client`.
Full task-by-task detail (what changed, test names, self-review findings and fixes) is in
`.specs/phase-6-inventory/log.md`'s "6c implementation (T-6c-11..T-6c-17)" section and its
"Checkpoint self-review findings" subsection; this section records the final, independently-rerun
verification state after every fix from `review.md` landed.

## Command and scope

```sh
cd services/inventory-service
./mvnw -q clean test-compile
./mvnw -q clean test
./mvnw test -Dtest='*IT'
./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'
./mvnw -q clean test-compile && ./mvnw -q -Dtest=ArchitectureTest test   # run #1
./mvnw -q clean test-compile && ./mvnw -q -Dtest=ArchitectureTest test   # run #2, independent
./mvnw -q -Dtest=OpenApiContractExportTest test
cd ../../packages/api-client && npm run generate
```

## Result

- `./mvnw -q clean test-compile` — PASS, clean, zero errors.
- `./mvnw -q clean test` (full unrestricted suite, plain `test` — skips `*IT.java`) — PASS, exit 0,
  every surefire report green.
- `./mvnw test -Dtest='*IT'` — **474 tests, 8 failures, 0 errors.** All 8 failures are exactly
  `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` (pre-existing, order-fragile
  H2-native-SQL debt documented in log.md, confirmed unrelated to this checkpoint's changes — this
  session never touched `AnalyticsService`/`ForecastService`).
- `./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'` — PASS,
  exit 0, 466/466, confirming the 8 failures above are the sole, already-known source of red and
  this checkpoint introduces zero new failures.
- `./mvnw -q -Dtest=ArchitectureTest test`, run after two independent `./mvnw -q clean
  test-compile` rebuilds — PASS both times; `git status --porcelain` on
  `archunit_store/`/`archunit.properties` empty both times (no frozen-store regeneration needed).
  The one genuinely new module-dependency edge this checkpoint introduces (`inventory -> shared`,
  from the v1 controllers reading `shared.web`/`shared.idempotency`/`shared.correlation`) is
  recorded as the file's eighth reviewed addition in
  `src/test/resources/module-dependency-edges-baseline.txt`.
- `./mvnw -q -Dtest=OpenApiContractExportTest test`, then `git diff --stat
  packages/contracts/openapi.json` — 590 insertions, 0 deletions, stable across repeated runs. Six
  new paths added (`/api/v1/sites/{siteId}/inventory/{totals,products/{productId},
  locations/{locationId},movements,adjustments,transfers}`); every existing legacy
  path/operation/schema byte-identical (confirmed by inspecting the diff for any `-` line — none).
- `npm run generate` in `packages/api-client`, then `git diff --stat
  packages/api-client/src/schema.d.ts` — 465 insertions, 0 deletions.
- Individually (all real, this session, re-run after every self-review fix landed):
  - `SiteInventoryControllerSecurityIT` — 11/11 pass (role/site matrix, foreign-site `siteId` 403,
    unknown `siteId` 404, foreign-site entity id 404, per-site product isolation).
  - `SiteInventoryMutationControllerAtomicityIT` — 4/4 pass (rollback-together with no surviving
    idempotency row on failure; commit-together on success; replay does not re-invoke the command
    or duplicate effects; foreign-site source inventory rejected before any write).
  - `SiteInventoryMutationCrossSiteDestinationIT` — 1/1 pass, real Postgres (foreign-site implicit
    destination location rejected, the speculative destination-row insert rolls back with it).
  - `SiteInventoryMutationControllerSecurityIT` — 11/11 pass (401/403/400-missing-header/201 role
    matrix; outbox `idempotencyKey` matches the HTTP header end-to-end; HTTP-level replay does not
    duplicate the effect; a fingerprint-conflicting replay returns 409; foreign-site
    location/source-inventory 404s before any write on both adjustments and transfers; same-site
    transfer succeeds).
  - `LegacyInventoryDeprecationHeadersIT` — 3/3 pass (RFC 8941/RFC 8288 header shapes present with
    no `Sunset` on legacy inventory/stock-movement routes, absent on the v1 route).
  - `ProductStockStateWriterCallerSetTest` — 1/1 pass (caller set pinned to exactly
    `StockMovementService`/`KujiBoxService`).
  - `StockStateGlobalAggregationIT` — 2/2 pass, real Postgres, two sites (global sum/derivation
    across sites for `syncProductTotals`/`calculateTotalInventory`).
  - `InventoryEgressAfterIT` — 3/3 pass, real Postgres (AC-8 after-measurement numbers below).
- `git diff --check` — PASS, no whitespace errors introduced.

## Acceptance criteria evidence

- **AC-1** (facade boundary): unaffected by this slice beyond the new v1 controllers themselves
  depending only on already-approved `inventory.application`/`shared.*` — no new
  `inventory.infrastructure` leak, confirmed by the stable `ArchitectureTest` runs above.
- **AC-3** (trusted site context, foreign-site rejection, concurrent/idempotent-retry proof):
  `SiteInventoryControllerSecurityIT`/`SiteInventoryMutationControllerSecurityIT`/
  `...AtomicityIT`/`...CrossSiteDestinationIT` above — every v1 route reads the site off
  `AuthorizedSiteContextHolder`, a foreign-site id 404s before any write (both for an inventory id
  and an implicit destination location), and idempotent replay is proven both at the direct-call
  level (`@SpyBean` invocation-count assertion) and over real HTTP (duplicate-effect and
  fingerprint-conflict-409 assertions).
- **AC-4** (durable envelope, atomicity, idempotency context): the v1 mutation path's idempotency
  record commits/rolls back in the same transaction as inventory/movement/outbox
  (`SiteInventoryMutationControllerAtomicityIT`), and the outbox event's `idempotencyKey` matches
  the HTTP header end-to-end (`SiteInventoryMutationControllerSecurityIT`). Q-6c-4's Kafka
  partition-key cutover to production remains **not** part of this evidence — it is a
  separately-authorized deploy-time action per P-6, unchanged from T-6c-8's own recorded scope.
- **AC-5** (v1 DTOs, slim/batched totals, zero-stock correctness, contract regen, legacy
  compatibility): `SiteInventoryControllerSecurityIT`'s per-site isolation and empty-entries cases;
  `InventoryEgressAfterIT`'s full-catalog (25 rows, row-per-product guarantee preserved) and
  known-ids-batch (exactly the requested ids) cases; `OpenApiContractExportTest`'s pure-additive
  diff; `LegacyInventoryDeprecationHeadersIT`'s proof that legacy routes are unmodified in
  behavior/status and only gain headers.
- **AC-6**: not this slice's scope (6d, web adoption) — no claim made.
- **AC-7** (coalesced/bounded refresh): partial backend-side evidence only —
  `InventoryEgressAfterIT`'s known-ids case proves the batched query returns exactly the requested
  ids (proportional cost, not full-catalog, not one-request-per-product) and
  `InventoryTotalsRepository.MAX_PRODUCT_IDS_BATCH_SIZE` (T-6c-5) is the documented ceiling. The
  browser/realtime coalescing itself is 6e's; not claimed complete here.
- **AC-8** (before/after measurement): `InventoryEgressAfterIT`'s recorded numbers, diffed against
  the round-2 baseline in log.md:
  - Full-catalog totals: apiBytes 9445 → 2715 (−71%), DB-side projected bytes 4078 → 1023 (−75%),
    same row/statement counts (25 rows, 1 statement) both before and after.
  - Known-ids batch (3 of 25 products): apiBytes 355, dbRows 3, 1 statement — proportional to the
    requested ids, not the catalog size, and not one request per product.
  - Movements page vs. legacy audit-log page: recorded as a non-equivalent, honest data point (the
    two endpoints operate at different granularities — audit-log entries vs. raw movements — per
    log.md's explicit note), not forced into a false before/after pair.
  - The full phase-exit gate (all of AC-1–8, including the browser/realtime half of AC-7/AC-8)
    remains 6e's; this evidence is what 6e regresses against, not a claim that AC-8 is closed.

Result: **6c (T-6c-0 through T-6c-17) satisfies its checkpoint scope.** All four review findings
were fixed and re-verified above. Production readiness for Q-6c-1 (backfill/deploy confirmation)
and Q-6c-4 (Kafka partition-key cutover) remain explicitly outstanding, gated on separate
authorization, and are not claimed as validated by this entry.

## 6b re-review — 2026-09-10

From `services/inventory-service`, using JDK 21 and the project wrapper:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=StockMovementSiteBackfillIT,StockMovementSiteMigrationIT,InventoryOperationsSiteIT,InventoryOperationsTest,MachineDisplayServiceNotificationTest,MachineDisplayServiceBatchQueryGuardTest,ArchitectureTest test
```

PASS — exit 0; seven Surefire reports total 40 tests, zero failures/errors/skips. Counts:
backfill 6, expansion 2, site persistence 4, facade unit tests 10, display notifications 6,
display batch guards 4, architecture 8. Both migration classes executed against PostgreSQL 16
Testcontainers. No frozen-store or archunit.properties changes appeared.

The first sandboxed attempt failed on Mockito JVM attachment and Docker access. The identical
command passed after approved execution outside the sandbox. Output:
`/private/tmp/phase6b-rereview.log` (session-local evidence).

AC-2 evidence covers sign-aware transfer backfill, MAIN fallback/guard/idempotence, nullable
UUID expansion without FK/default, explicit writer site persistence and separate-thread site
isolation. Same-row contention, V58 execution and production verification are not proven by
this focused run. Earlier full-suite totals in log.md were not independently rerun here.

## Command and scope

Documentation-only update, checked from the repository root on 2026-09-09:

- `git diff --check` — PASS.
- `python3` inline documentation check using `pathlib`/`re` — PASS: six documents, 26 local
  Markdown link targets exist, all four Full-tier files present, and five checkpoint rows in spec.md.
  Checked file targets, not generated heading anchors. The six documents are both changed plans
  and this record's spec, log, review and validation files.

## Result

Documentation checks pass. No application code, schema, API or event changes. Native runtime tests are not applicable to this
planning update; Phase 6 implementation acceptance criteria remain unverified.

## Planned implementation evidence

- 6a: JDK 21 with `./mvnw`; relevant unit/integration and ArchUnit checks, including legacy callers.
- 6b/6c: PostgreSQL migration, authorization, concurrency and atomicity tests; affected event
  producer/consumer suites; generated OpenAPI/client checks and contract compatibility.
- 6d/6e: web native test/typecheck/lint commands, rendered workflow/site-switch and recovery tests,
  controlled query/egress measurements, then the full phase exit gate and authoritative PR gate.

Record exact commands, results, evidence paths and remaining limitations as each checkpoint runs.

## 6a — Inventory module boundary (AC-1)

Environment: JDK 21, `./mvnw` from `services/inventory-service`, as required above. Full detail
and intermediate/superseded runs are in `.specs/phase-6-inventory/log.md` (Current handoff,
per-task Result subsections, and the "Review-driven fix" entries); this section records the final
state after T-0 through T-8 and both post-T-5 review rounds.

- `./mvnw -q clean test-compile` — clean, verified stable across multiple independent clean
  rebuilds (required after the T-3/T-4 lambda-synthetic-naming fragility finding; see log.md).
- `./mvnw -q clean test` (full unrestricted suite) — exit 0, zero failures/errors across every
  surefire report, most recently re-run after the T-6/T-7/T-8 slice and again after the
  FQN-vs-simple-name fix to `InventoryOperationsCallerSetTest` below.
- `./mvnw -q -Dtest=ArchitectureTest test` — 8/8 rules pass (was 7 before T-7's new
  `noProductionClassOutsideInventoryDependsOnInventoryInfrastructure` rule), stable across
  independent clean rebuilds. Frozen stores (`legacyTechnicalLayerPackagesDoNotGrow`,
  `repositoriesAreOnlyAccessedByServicesOrRepositories`) were regenerated exactly twice this
  checkpoint (T-3's initial move, T-6's controller/DTO move) — each regeneration diffed against
  the prior saved store and confirmed to only shrink (moved-out classes, one retired
  direct-repository-access violation), never gain a violation.
- `./mvnw -q -Dtest=InventoryOperationsTest,InventoryOperationsCallerTransactionIT,
  InventoryOperationsSharedCallerPathIT test` — 14/14 pass: unit coverage of
  `InventoryOperations`/`InventoryQueries`, an `@SpringBootTest` (H2 `test` profile) proving
  `applyDelta` joins and rolls back/commits with a caller-opened transaction (AC-1's "facades
  preserve caller transactions"), and an `@SpringBootTest` proving the find-or-create/delete-on-zero
  path through real `ShipmentService`/`KujiBoxService` calls.
- `./mvnw -q -Dtest=InventoryOperationsCallerSetTest,CatalogEntityAccessCallerSetTest test` —
  2/2 pass; pins the exact, fully-qualified-name eight-class set of production callers reaching
  `InventoryOperations`/`InventoryQueries` from outside `inventory` (T-7; corrected from
  simple-name to fully-qualified-name comparison after independent review, 2026-09-10, to avoid a
  same-named class in a different package silently collapsing into an existing entry).
- `./mvnw -q -Dtest=OpenApiContractExportTest test` then `diff` against a pre-T6 copy of
  `packages/contracts/openapi.json` — byte-identical (AC-5's "no unintended contract break," for
  the endpoint moves T-6 made).
- `git diff --check` — passed, re-confirmed after every slice (T-5, T-6/T-7/T-8, and the two P3
  fixes below).
- Independent review passed three rounds against the T-5 deliverable (two P2s, both fixed same
  session: an infrastructure-layer type leak in `InventoryQueries.findHistoryByItemId`, and a
  missing 6a-closing IT proof that had been prematurely deferred to 6c) and one round against the
  completed T-6–T-8 slice (two P3s: `InventoryOperationsCallerSetTest`'s simple-name comparison,
  fixed as above; this file and review.md needing actual 6a results before checkpoint closure,
  addressed by this update). No runtime regression was found in any review round.

Result: AC-1 satisfied for 6a's scope — inventory owns its entities/persistence/workflows, external
production callers use `InventoryOperations`/`InventoryQueries` (verified live by ArchUnit, not
frozen), and facades preserve caller transactions (proven by a real-transaction IT, not only
Mockito). R-9 (see log.md) is recorded as open debt carried forward, not a 6a blocker: no
acceptance criterion required moving `LocationInventoryController`'s cluster in this checkpoint.
6b's own worksheet/rollout requirements (AC-2) are unaffected by and not claimed by this checkpoint.

## 6e — Targeted refresh and exit proof (AC-7/AC-8)

Environment: JDK 21 with `./mvnw` from `services/inventory-service` (backend, real Postgres/Kafka
Testcontainers for the ITs that need them); Node with `npx` from `apps/web` (web). Full detail is
in log.md's "6e planning" and "6e implementation" entries; this section records final,
independently-reproducible commands/results per spec.md's requirement.

### Backend

- `./mvnw -q -o compile` / `-o test-compile` — clean throughout every slice.
- `./mvnw -q clean test` (full unit/component suite) — 379 run, 0 failures, 0 errors (up from
  the 6d-close baseline of 371 — net effect of +8 new `SupabaseBroadcastServiceTest` cases and -6
  removed-with-the-legacy-controller `LocationInventoryServiceTest` nested-class cases; the
  reported top-level count does not track 1:1 with `@Nested` class removal, a pre-existing
  Surefire text-summary quirk this checkpoint's log entries reference from 6a).
- `./mvnw -q test -Dtest='*IT'` (full IT suite) — 502 run, 8 failures, name-for-name identical
  to the pre-existing `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` set, no new
  failure.
- `./mvnw -q clean test -Dtest=ArchitectureTest` run twice, independently, as clean rebuilds —
  both green. The frozen `legacyTechnicalLayerPackagesDoNotGrow` store shrank by exactly the
  predicted 7 lines (the four deleted `LocationInventory*` classes' entries) when regenerated
  with `allowStoreCreation=true`/`allowStoreUpdate=true` temporarily set, then reverted; stable
  across two further independent clean runs with the flags back at `false`/`false`. The other
  frozen store (`repositoriesAreOnlyAccessedByServicesOrRepositories`) was unaffected.
- `./mvnw -q test -Dtest=OpenApiContractExportTest` run twice — stable. Scripted set-diff (Python,
  not by hand) against the pre-6e contract: paths removed:
  `/api/locations/{locationId}/inventory`, `/api/locations/{locationId}/inventory/{inventoryId}`,
  `/api/storage-locations/{storageLocationId}/inventory` (3 templates, the first path removals
  recorded in this phase); paths added: none this run (the `SiteLocationAggregateController`
  route and `SiteLocationDTO` schema were added in the prior 6e commit's regen); schemas removed:
  `InventoryRequestDTO`, `LocationInventoryResponseDTO`; schemas added: none.
- `LocationAggregateEgressIT` (real Postgres) — 3/3 pass. Measured (Hibernate `Statistics` +
  `ObjectMapper.writeValueAsBytes`, one run): legacy `findAllLocationsWithCounts()` (no site
  filter) returned both seeded sites' location rows in one call — confirmed as a live cross-site
  leak, not merely a missing scope, and grew with every prior test method's fixture in the same
  JVM run (18 rows by the third test method, none ever excluded); the site-scoped
  `findAllLocationsWithCounts(siteId)` returned exactly the calling site's 3 rows, zero belonging
  to the other seeded site, and excluded a deliberately mismatched-`site_id`
  `location_inventory` row from the total (its 999 quantity never appeared).
- `StockMovementServiceBroadcastArgsIT` (real Postgres, review-driven fix) — 2/2 pass,
  revert-verified (see review.md's "6e" section): `batchAdjustInventory`/`batchTransferInventory`
  each emit exactly one `inventory_updated` notification carrying the calling site's ID and the
  full affected-product-ID set.
- `SupabaseBroadcastServiceTest` — 8/8 pass: payload assembly (siteId/productIds present vs.
  omitted), and after-commit dispatch (no active transaction dispatches immediately; an active
  transaction defers until `afterCommit()`; a transaction cleared without commit never dispatches).
- `SiteLocationAggregateControllerIT` — 5/5 pass (route precedence over `/{id}`, site isolation,
  mismatched-row exclusion, foreign-site 403, unauthenticated 401).
- `LegacyInventoryDeprecationHeadersIT` — 6/6 pass, including the rewritten
  `/api/locations/with-counts` deprecation-header cases replacing the deleted routes' cases.
- `NotAssignedInventoryReadParityIT` — 2/2 pass after rewriting its legacy-vs-v1 comparison to a
  v1-filter-only assertion (the legacy method it compared against was deleted this checkpoint).

### Web

- `npx tsc --noEmit -p tsconfig.json` — clean throughout every slice, including against the final
  regenerated `packages/api-client/src/schema.d.ts`.
- `npx vitest run` (full suite) — 57 files, 395 tests, 0 failed (up from the 6d-close baseline
  of 52 files/366 tests: net +9 test files after deleting the dead-hook test and adding
  `inventory-refresh.test.ts`, `site-relevance.test.ts`, `use-coalesced-inventory-refresh.test.ts`,
  `use-realtime-broadcast.test.ts`, `ac8-web-measurement.test.ts`,
  `use-locations-with-counts.test.ts`).
- `npx eslint .` — 0 errors, 51 warnings — identical warning set to the 6d-close baseline (no
  new warnings introduced; two `react-hooks` errors caught and fixed during implementation --
  see log.md -- before this final count).
- AC-7 web coverage: site-scoped broadcast handling and coalescing (`use-realtime-broadcast.test.ts`,
  7 cases: foreign-site drop, missing-siteId possibly-relevant, coalescing two rapid events into
  one flush, no-recovery-on-first-subscribe, exactly-one-recovery-refresh-after-a-real-error,
  non-inventory event passthrough, unknown-event-type safety) plus a dedicated site-switch race
  case (1 case: an event buffered for site-1 still flushes against site-1 even if the site
  switches to site-2 before the coalescing window elapses — never contaminates site-2's cache).
  Coalescing buffer mechanics (`use-coalesced-inventory-refresh.test.ts`, 6 cases) and the shared
  flush executor (`inventory-refresh.test.ts`, 5 cases) are unit-tested directly with fake
  timers/a real `QueryClient`.
- AC-8 web measurement (`ac8-web-measurement.test.ts`, T-6e-10): scripted workload through the
  actual post-6e coalescing path for five scenarios. **The request-count columns are the real
  web-side measurement evidence** (directly counted from the actual code path under test). **The
  byte columns are NOT an independent web-side measurement** -- they are derived by multiplying
  each scenario's request/row count by 6c's backend-measured per-row byte costs (legacy full
  totals 9445 bytes/25 rows, v1 full totals 2715 bytes/25 rows, v1 batch-of-3 355 bytes/3 rows),
  carried over as a proxy for "what this request would cost" rather than measured on this
  checkpoint's own web-side traffic (6e independent review, Advisory 9). "Before" throughout is
  the reconstructed pre-6e per-event full-refresh behavior (read from git history, not re-run):

  | Scenario | Before: requests (measured) | Before: bytes (derived, 6c-proxy) | After: requests (measured) | After: bytes (derived, 6c-proxy) |
  | --- | --- | --- | --- | --- |
  | single known ID | 1 | 9445 | 1 | 118 |
  | 5-ID batch | 1 | 9445 | 1 | 590 |
  | unknown-ID batch | 1 | 9445 | 1 | 2715 |
  | duplicate ID (2 events) | 2 | 18890 | 1 | 118 |
  | reordered pair (2 events) | 2 | 18890 | 1 | 236 |

  Result size follows affected IDs, not catalog size, in every known-ID scenario, per the
  measured request counts; the unknown-ID case correctly falls back to a full (but still
  site-scoped, still one-request) refresh with no regression claimed there. Explicitly not
  measured: a live browser/Supabase-websocket session, and no independent web-side byte
  measurement was taken -- this is jsdom + a real `QueryClient` + a counting stub, not an
  end-to-end browser test or a network-level byte capture.
- Regression of the 6d rendered suites named in the worksheet, run directly and unmodified:
  `kuji-tab-panel.test.tsx`, `product-modal.test.tsx`, `adjust-stock-dialog.test.tsx`,
  `transfer-stock-dialog.test.tsx`, `use-site-product-inventory.test.ts` — 5 files, 16 tests,
  all pass (subset of the full 395; confirms AC-6 regression specifically, not just aggregate
  count).

### Cost-impact statement (AC-8)

No new paid infrastructure, service, or third-party dependency was introduced. The
`SiteLocationAggregateController`/scoped `LocationAggregateRepository` query and the coalesced
web refresh reduce Supabase Postgres query egress (narrower aggregation input, fewer/smaller HTTP
responses per realtime burst) and reduce Supabase Realtime broadcast fan-out cost per mutation
(one coalesced flush instead of one full-catalog invalidation per event) at the existing
infrastructure tier; no measurable cost increase is expected, and the direction is a reduction
consistent with the phase's stated egress-reduction goal. Deleting the four obsolete backend
classes and the corresponding web functions is a maintenance-surface reduction with no runtime
cost effect.

### Post-independent-review fix round (final numbers)

Two independent reviews (`mirai-spring-reviewer` for backend, `mirai-next-reviewer` for web) ran
against the implementation recorded above and returned **block** verdicts; all Blocker/Required
findings and the cheap Advisories were fixed and revert-verified (full disposition in review.md's
"6e" section). Final counts after the fix round, reproduced independently by the coordinating
session (not merely re-reported):

- Backend: `./mvnw -q clean test-compile` clean. `./mvnw -q clean test` — 479 run (Surefire
  top-level count), 0 failures/errors. `./mvnw test -Dtest='*IT'` — 522 run, 8 failures,
  name-for-name identical to the pre-existing `AnalyticsControllerSecurityIT`/
  `ForecastControllerSecurityIT` set, no new failure. `./mvnw -Dtest=ArchitectureTest test` clean;
  frozen store restored to its pre-T-6e-be-9 content exactly (`git diff` on
  `archunit_store/c1d9f1c8-...` empty).
- Web: `npx tsc --noEmit -p tsconfig.json` clean. `npx vitest run` — 57 files/404 tests, 0 failed.
  `npx eslint .` — 0 errors, 51 warnings (baseline-identical).

The fix round's own headline change: the legacy inventory-at-location routes deleted in
T-6e-be-9 (`c8cfcd8`) were **restored** per a user decision on the reviewer's R-3 finding — the
documented compatibility-removal gate (`docs/baseline/api-v1-map.md`) requires access-log
evidence of no legacy traffic plus a stabilization window on a released version, neither of
which this unmerged branch could satisfy. The routes stay present and deprecated, not deleted;
`LocationInventoryRepository.findByStorageLocation_Id`'s previously-recorded missing-filter trap
was closed for real this time (the restored method now carries the same
parent-IS-NULL/non-CUSTOM-kuji-parent filter its `findByLocation_Id` sibling always had) rather
than re-shipped. Separately, `GET /api/locations/with-counts` (a different legacy route, `sites`
module) was found to be a genuine live cross-site data leak — not merely "resolves to MAIN" as
6d's note assumed — and was fixed to resolve the caller's default site and delegate to the
already-built scoped query, closing the leak while the route itself, also legacy, stays present
and deprecated.

### Phase exit gate (run by the coordinating session)

Per spec.md's 6e checkpoint row ("run the complete phase gate (AC-7-8 and regression of AC-1-6)"),
the coordinating session independently reproduced every command above from a clean state (not
trusting the implementing/review sessions' reported numbers) and confirmed identical results:
backend full unit/component suite green (479 run, 0 failures), full IT suite at the same
pre-existing 8-failure baseline (522 run, 8 failures, unchanged set), `ArchitectureTest` clean
with the frozen store confirmed unmodified relative to pre-6e, web `tsc`/`vitest`/`eslint` all
green and warning-baseline-identical (57 files/404 tests/0 failed, 0 errors/51 warnings). No
production apply or deployment was performed or authorized, consistent with this record's
delivery decisions. This closes AC-7/AC-8 (this checkpoint's own scope) and confirms no regression
of AC-1 through AC-6 (the 6a-6d suites remain green, unmodified, within the same full-suite runs).
The PR-gate authoritative CI run still occurs on the actual PR, not in this local record.

### Post-closure follow-up fix round (2026-09-15, amends the numbers above)

A fourth review pass (user-reported) found four more findings after the phase exit gate above
was recorded: two P1s (a mutually-exclusive invalidation gap in `use-realtime-broadcast.ts`
leaving `locationInventory` or `productInventoryEntries` stale depending on which branch fired;
and a totals-merge guard keyed on a non-monotonic server timestamp, replaced with request-
issuance sequencing) and two P2s (no cap on the coalescing buffer against the backend's 500-ID
batch limit; and the legacy unscoped `batchTransferInventory` stamping a mixed-site batch's
combined broadcast with only the first transfer's site). All four fixed and revert-verified;
full detail in log.md's "Post-closure follow-up review and fix" section and review.md's
"Follow-up review" section.

Re-verified, superseding the numbers above: backend `./mvnw -q clean test-compile` clean;
`./mvnw -q clean test` — 479 run, 0 failures; `./mvnw test -Dtest='*IT'` — **523 run** (up 1: the
new `StockMovementServiceBroadcastArgsIT.batchTransferInventory_mixedSites_
emitsOneNotificationPerAffectedSite`), 8 failures, the same pre-existing
`AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` set, no new failure;
`ArchitectureTest` clean, frozen store unchanged. Web: `npx tsc --noEmit` clean; `npx vitest run`
— **57 files/406 tests** (up 2: two new `use-realtime-broadcast.test.ts` cases; the two
`inventory-refresh.test.ts` cases that asserted the now-removed timestamp-based guard were
replaced with two cases asserting the new sequencing/chunking behavior, net-even in that file),
0 failed; `npx eslint .` — 0 errors/51 warnings, unchanged. This amends the phase exit gate's
numbers; it does not reopen 6e or Phase 6, and no acceptance criterion's disposition changed.

### Second post-closure follow-up fix round (2026-09-15, amends the numbers above again)

A fifth review pass found the sequencing mechanism from the round above did not cover the
full-refresh paths (reconnect recovery, unknown-ID batches, "nothing cached yet"), which still
bare-invalidated and so raced with targeted flushes in both directions; and that a flush which
superseded an earlier successful one and then itself failed left the cache permanently stale
with no recovery. Both fixed (`refreshAllInventoryTotals` brings full refreshes into the same
claim/apply scheme; `recoverOnFailure` triggers a corrective invalidation when the failing flush
was still the current claim holder). Full detail in log.md's "Second post-closure follow-up"
section and review.md's "Second follow-up review" section.

Re-verified, superseding the web numbers above: `npx tsc --noEmit` clean; `npx vitest run` —
**57 files/410 tests** (up 4 net: two existing full-refresh tests rewritten for the new
fetch-based behavior, plus four new tests — two P1 ordering-direction cases and two P2 recovery
cases), 0 failed; `npx eslint .` — 0 errors/51 warnings, unchanged. Backend numbers are
unaffected by this round (web-only fix); `StockMovementServiceBroadcastArgsIT` re-confirmed
green (4/4) since the user's own local attempt was blocked by Docker permissions. This amends
the phase exit gate's web numbers again; it does not reopen 6e or Phase 6.

### Third post-closure follow-up fix round (2026-09-16, amends the numbers above again)

A sixth review pass found recovery and the real `useQuery` behind `["inventoryTotals", siteId]`
still bypassed sequencing entirely (reproduced as a quantity regressing from 10 to 2), and that
the full-refresh merge deleted a newer product's entry when it was absent from an older,
in-flight full response for a legitimate reason (it simply didn't exist yet when that read's
snapshot was taken). Both fixed: `fetchSequencedInventoryTotals` is now the real query's
`queryFn`; a shared, claim-once `fetchAndMergeFullTotals` helper is used by the query, recovery,
and the explicit full-refresh path so no caller double-claims; `mergeFullTotals` preserves a
newer-owned absent entry instead of deleting it. Full detail in log.md's "Third post-closure
follow-up" section and review.md's "Third follow-up review" section.

Re-verified, superseding the web numbers above: `npx tsc --noEmit` clean; `npx vitest run` —
**57 files/412 tests** (up 2 net: two new tests targeting the query-sequencing and
absent-entry-preservation fixes; the existing P2 recovery tests rewritten to assert the new
mechanism, same count), 0 failed; `npx eslint .` — 0 errors/51 warnings, unchanged. Backend
numbers unaffected (web-only fix). This amends the phase exit gate's web numbers again; it does
not reopen 6e or Phase 6.
