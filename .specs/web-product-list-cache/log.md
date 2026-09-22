# Implementation log

## Current handoff

- Status: not started; blocked behind `.specs/catalog-product-egress/` landing
  (backend slim projections should ship first so this doesn't need a second pass).
- Next action: W1 (consolidate `useProducts`), W2 (route 4 dialogs through it,
  add `enabled` param), verify realtime patcher still works.
- Decisions that must survive compaction: keep `["products", <object>]` key shape
  (length-2, object second element) so `use-realtime-broadcast.ts`'s patcher
  still matches it.
