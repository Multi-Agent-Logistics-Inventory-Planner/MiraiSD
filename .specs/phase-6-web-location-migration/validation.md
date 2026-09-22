# Validation

## Commands and results
From `apps/web`:
- `npm run test:run -- src/lib/api/locations.test.ts src/hooks/queries/__tests__/use-site-locations.test.tsx src/hooks/mutations/__tests__/use-site-location-mutations.test.tsx`: before implementation, 10 failures / 9 passes (missing scoped exports and legacy hook paths; the pending legacy call also produced an unhandled mock error); after implementation all 19 pass.
- `npm run test:run -- src/components/stock/__tests__/location-selector.test.tsx`: before selector migration 5 fail; after migration 5 pass.
- `npm run test:run`: 62 files, 441 tests pass after the review fix (439 before its two additional cases).
- `npx tsc --noEmit -p tsconfig.json`: pass.
- `npx eslint .`: 0 errors, 51 existing warnings. The one new effect-dependency warning found in a focused pass was fixed before this full run.
- `git diff --check`: pass.

## Acceptance evidence
- AC-1: scoped hook tests cover unresolved sites/type/NOT_ASSIGNED, storage-code mapping, site switch and late old response isolation. Real selector test opens its Radix dropdown, selects a location and proves the legacy API is not called; tests also cover clearing an old selection and retrying errors.
- AC-2: generated helper tests verify POST/PUT/DELETE paths/bodies/DTO mapping, empty successful DELETE, 403 and malformed responses. Mutation tests cover missing site/category and selected-site rename/delete.
- AC-3: deferred category lookup test changes sites mid-request, then verifies create and invalidations retain the original site; shared picker test clears the old selection on site change.
- AC-4: explicit legacy selector test verifies Phase 7 uses legacy reads only. Caller audit leaves shipment receiving, machine displays/analytics, and Kuji inventory reads on their documented adapters. Removed legacy create/update/delete/storage-code helpers have no remaining callers.
- AC-5: native web gates above passed; independent review recorded separately.

## Limits
No backend or contract shape changed, so no contract regeneration or Java tests were needed. No live browser, production API or Supabase policy verification was performed. Current site UI still resolves MAIN; site switching is exercised in tests. PR gate remains independent proof after push. Backend compatibility security gaps and other audit findings remain outside this frontend-only scope.

## Review regression
`npm run test:run -- src/hooks/mutations/__tests__/use-site-location-mutations.test.tsx`: legacy MAIN-cache assertion failed before the fix (1 failed, 4 passed), all 5 passed afterward. Final full test, typecheck and lint commands above rerun successfully after the fix. MAIN/SECOND coexistence and original MAIN invalidation after a site switch are covered.

## Display-transfer follow-up
- Red: rendered transfer-display-dialog test failed because closed dialog fetched legacy locations. Green: passed after implementation.
- `npm run test:run` (apps/web): 63 files / 442 tests passed.
- `npx tsc --noEmit -p tsconfig.json`: passed.
- `npx eslint .`: passed, 0 errors / 51 existing warnings.
- `git diff --check`: passed.
- AC-6: real query hooks with mocked API boundaries verify closed mount makes no requests, opening uses main-site lookup without legacy fallback, closing plus invalidation causes no additional requests.

## Hidden UI and realtime cleanup
- Initial focused regressions: 2 failed / 16 passed (closed ProductForm still supplied ID; broadcast made 2 reads instead of 1). Both corrected.
- Added AddDisplayDialog closed/open/close-invalidation test, sheet non-mount test with retained location, and deferred old-product-response regression.
- `npm run test:run` in apps/web: 65 files / 447 tests passed.
- `npx tsc --noEmit -p tsconfig.json`: passed.
- `npx eslint .`: 0 errors / 51 baseline warnings.
- `git diff --check`: passed.
- No container rebuild or browser runtime verification; no backend or Kafka change.
