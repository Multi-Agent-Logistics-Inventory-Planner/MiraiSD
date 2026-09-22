# Review

## Scope reviewed

V67 safe seed/guard, canonical-location trigger, and separately gated V68 stock-movement constraint.

## Findings

- [Spec] V61 could not be used because it is below V62-V67 — **act on**: V68 header documents the ordering and Flyway must not target it before its gate clears.
- [Standards] A check-then-insert trigger alone races under concurrent transactions — **act on**: transaction advisory lock serializes checks by storage-location UUID.
- [Standards] INSERT-only protection allows location reparenting to bypass the invariant — **act on**: V67 covers `UPDATE OF storage_location_id` too.

## Residual risk

- V67 is safe to apply in F1; V68 remains deferred until post-deploy NULL verification and explicit approval.
