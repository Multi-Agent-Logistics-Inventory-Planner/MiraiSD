# Web product list cache consolidation

- Tier: Standard
- Status: In progress
- Plan: see local plan `/Users/mjpark019/.claude/plans/yes-the-implementation-has-ancient-curry.md`
- Depends on: `.specs/catalog-product-egress/` (backend slim projections land first;
  this record consolidates client-side cache keys against the same contract)

## Problem

The web app fetches the product catalog through three distinct TanStack Query
cache keys (`{rootOnly:false}`, `{rootOnly:true}`, `{rootOnly:true,
excludeCustomKuji:true}`), each a separate full-catalog network call. Four
dialogs additionally bypass the `useProducts` hook with ad hoc `useQuery` calls.

## Acceptance criteria

- AC-1: `useProducts` always fetches a single cache key and derives
  `rootOnly`/`excludeCustomKuji` views via `select`, preserving the server's
  root-only name ordering.
- AC-2: The realtime product patcher in `use-realtime-broadcast.ts` continues to
  update the single shared cache key correctly (no regression to insert/update/
  delete patching).
- AC-3: The four direct `getProducts` callers route through `useProducts`,
  preserving their existing open-state gating (no fetch while a dialog is closed).
- AC-4: `staleTime` is left at the app default; the long-staleTime idea for pure
  master data is recorded as a follow-up once display fields move to v1 catalog.

## Non-goals

- Changing `staleTime` policy.
- Migrating dialogs off legacy `/api/products` onto v1 catalog.
