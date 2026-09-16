# Implementation log

## Current handoff

- Status: count restoration already pushed; status restoration implemented and validated.
- Next action: commit and push status restoration to `temp/restore-legacy-inventory-counts`.
- Decisions: table/modal Active/Inactive and status sorting use legacy global isActive;
  MAIN totals, site settings, stored assortment and permissions remain unchanged.
- Last verified: full web suite 325/325; TypeScript clean; lint 0 errors / 51 warnings;
  `git diff --check` clean.
- Open risks: legacy status and totals remain global; this is temporary stabilization,
  not completed multi-site inventory. Independent PR gate remains required for merge.

## Assumptions and decisions

- User authorized this exception to Phase 5d AC-6b on 2026-09-10, explicitly
  requesting no extra UI copy. Phase 5d spec links to the exception.
- Created isolated worktree from local main at 672f503; existing refactor untouched.
- Reused inventoryTotals cache key and existing realtime invalidations; no API changes.
- Used existing workspace dependencies through local symlinks; these are not committed.

## Task record

- T-1: Changed rendered regressions first; 3 failed as expected before implementation.
- T-2: MAIN joins aggregate totals by product ID. Successful absent totals become
  zero, but pending/failed responses do not. Other/unresolved sites cannot join
  cached or late MAIN totals. Existing modal breakdown and table counts restored.
- T-3: Reviewed source selection, site switching, loading/error handling, stock
  sort compatibility and preserved settings/actions. No additional findings.

## Validation

- `npm run test:run --workspace=apps/web` — 43 files, 323 tests passed.
- `npm run test:run --workspace=apps/web -- 'src/app/(dashboard)/products/__tests__/page.test.tsx' src/hooks/queries/__tests__/use-site-product-inventory.test.ts`
  — final 13 tests passed, including R16 breakdown, MAIN totals, missing totals,
  failure/loading, late responses and site switch, and role/site settings.
- `npx tsc --noEmit` (apps/web) — passed.
- `npm run lint --workspace=apps/web` — passed, 0 errors / 51 warnings.
  Initial attempt lacked workspace-local eslint-config-next; linked the existing
  installed package and reran successfully.
- `git diff --check` — passed.

## Status restoration — validated

- Standard UI behavior fix authorized on `temp/restore-legacy-inventory-counts`.
- Reusing its clean existing worktree; remote and local start at a969bb2.
- Restore legacy status display/sorting, preserve site-owned settings and data.
- Regression baseline: 7 failures / 21 passes before implementation, including conflicting
  status flags in table/modal and ascending/descending sorting.
- `npm run test:run --workspace=apps/web`: 43 files / 325 tests passed.
- `npx tsc --noEmit` (apps/web): passed.
- `npm run lint --workspace=apps/web`: passed, 0 errors / 51 existing warnings.
- `git diff --check`: passed. Reviewed source diff: only status display/sort behavior changed.
- Reused existing workspace dependencies via a temporary node_modules symlink (removed after validation) after the
  first test command found no vitest executable in this worktree.
