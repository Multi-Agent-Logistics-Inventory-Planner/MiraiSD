# Implementation log

## Current handoff

- Status: implemented and reviewed; all required local checks passed; ready to commit.
- Next action: commit on `temp/restore-legacy-inventory-counts`.
- Decisions: temporary MAIN-only legacy totals; existing stock UI restored and
  migration text removed, with no new labels. Site settings and permissions unchanged.
- Last verified: focused final regression run — 13/13 pass; `git diff --check` clean.
- Open risks: legacy totals remain site-blind. Replace this temporary exception
  when Phase 6 supplies scoped inventory. The MAIN condition is a display gate,
  not an authorization boundary. PR gate has not run.

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
