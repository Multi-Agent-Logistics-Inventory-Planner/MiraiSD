# Implementation log

## Current handoff

- Status: **Complete.** All acceptance criteria met.
- Next action: none for this record.
- Decisions that must survive compaction:
  - Consolidated key is `["products", {}]` (always empty options object), not a boolean or a
    stripped key -- keeps the length-2/object-second-element shape the realtime patcher
    (`use-realtime-broadcast.ts`) already relies on, so that file needed zero changes.
  - `kujiOnly` filtering was implemented client-side too, even though no current caller uses it
    (only `rootOnly`/`excludeCustomKuji` are live combinations) -- `GetProductsOptions` still
    declares the field, and leaving it silently unimplemented would be a footgun: a future
    `useProducts({ kujiOnly: true })` caller would otherwise get an unfiltered list with no error.
- Last verified command: `npx tsc --noEmit` (clean), `npx vitest run --pool=threads` (67 files /
  460 tests, all passing), `npm run lint` (0 errors, 53 pre-existing warnings unrelated to any
  touched file).
- Open risks/questions: none.

## Assumptions and decisions

### W1 -- consolidated useProducts

- `applyProductListFilters` mirrors each server-side filter's exact semantics against the one
  unfiltered fetch, not just its predicate:
  - `rootOnly`: filters to `!parentId`, then explicitly re-sorts by name. The server's dedicated
    root-products queries (`findRootAsListItems` et al.) are `ORDER BY p.name`; the unfiltered
    `findAllAsListItems` query has no `ORDER BY` at all, so skipping the sort would have silently
    reordered every root-only picker (shipment product select, add-inventory, transfer/add
    display, bulk-assign-modal, product-picker).
  - `kujiOnly` (only meaningful combined with `rootOnly`): filters to `hasChildren === true`,
    matching the server's `rootOnly && kujiOnly` branch. Not currently exercised by any live
    caller but implemented per the footgun note above.
  - `excludeCustomKuji`: applied last as a plain filter, no re-sort -- matches the server, which
    filters the controller's already-assembled list without touching its order.
- Added an `enabled` second parameter (`{ enabled?: boolean }`) to `useProducts` -- it had none
  before, since every existing caller either always fetched or (for the two dialogs using raw
  `useQuery`) needed no `enabled` at all.
- TDD: `use-products.test.ts` (new file, no prior coverage existed) covers: one fetch shared by
  three differently-optioned concurrent callers (AC-1's actual claim), rootOnly's name-sort,
  excludeCustomKuji's order-preserving filter, kujiOnly+rootOnly, and the `enabled: false` gate.

### W2 -- route the 4 direct callers through useProducts

- Two dialogs (`select-shipment-product-dialog.tsx`, `add-inventory-dialog.tsx`) had **no**
  `enabled` gating at all before this change -- they fetched regardless of dialog open state.
  Preserved that as-is (omitted the second `useProducts` argument, defaulting `enabled` to
  `true`); adding gating that didn't exist before would be an unrelated behavior change, not part
  of this record's scope, however much it might look like an improvement.
- Two dialogs (`transfer-display-dialog.tsx`, `add-display-dialog.tsx`) had `enabled: open`.
  Preserved exactly via `useProducts(opts, { enabled: open })`.
- No new tests needed for W2 itself: it is a call-site substitution with no behavior change beyond
  what W1 already covers, and existing component tests for these four files (confirmed via the
  full suite run) continue to pass unchanged.

### AC-2 -- realtime patcher

- Existing `use-realtime-broadcast.test.ts` tests (16, seeded with the legacy
  `["products", { rootOnly: true }]` shape) continued passing unchanged -- the patcher's logic is
  generic over the options-object shape, so this was expected, not incidental.
- Added one new test seeding the actual `["products", {}]` shape now in use, proving both an
  update-in-place and an append-of-a-not-yet-cached-product against the real post-fix key, rather
  than relying solely on the old shape's continued (if structurally guaranteed) compatibility.

### W3 -- staleTime

- No change made, per AC-4/non-goals. Follow-up recorded here for whoever eventually migrates
  display fields (name, category, imageUrl, kujiType) onto v1 catalog (`.specs/catalog-product-
  egress/spec.md` gave that endpoint the same fields): once the legacy list is retired in favor of
  pure master data, its `staleTime` can be raised well past the 5-minute app default, since master
  data changes far less often than the volatile fields (`isActive`, `quantity`, `reorderPoint`)
  the legacy endpoint still carries today.

## Verification record

| Command | Result |
| --- | --- |
| `npx tsc --noEmit` | clean |
| `npx vitest run --pool=threads` | 67 files / 460 tests passed |
| `npm run lint` | 0 errors, 53 pre-existing warnings (none in touched files) |

Note: a `vitest run` invocation using the default forks pool intermittently hit
`[vitest-pool-runner]: Timeout waiting for worker to respond` on unrelated files under load from
concurrent backend Maven runs in the same session -- an environment/resource-contention issue, not
a test regression. Re-run with `--pool=threads` (and in isolation for the specifically-flagged
files) confirmed all green; recorded here so a future flaky run isn't mistaken for a real failure.
