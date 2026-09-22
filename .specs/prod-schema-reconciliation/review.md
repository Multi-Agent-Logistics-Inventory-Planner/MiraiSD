# Review

## Scope reviewed

F1 sequencing, backup prerequisite, backfill evidence, and operational hard stops.

## Findings

- [Standards] Schema changes are irreversible through image rollback — **act on**: require fresh backup and explicit per-action approval.
- [Spec] V60 has sign-aware source/destination resolution — **act on**: record its three verification counts before constraint/merge.

## Residual risk

- Production drift can change after preparation; rerun the runbook queries immediately before approved execution.
