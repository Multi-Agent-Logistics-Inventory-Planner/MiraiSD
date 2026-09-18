# Production schema reconciliation

## Tier

Full

## Problem and outcome

Production remains at V57 while application code depends on V58-V66. F1 prepares an approved, auditable manual apply and PR #328 merge; F2 is deliberately blocked until F1 completes.

## Durable context

- Plan: `docs/plans/phase-4-6-closeout.md`, Slice F
- Runbook: `docs/runbooks/phase-4-6-production-schema-apply.md`

## Acceptance criteria

- AC-1: Runbook has a fresh-backup requirement, exact migration order, drift checks, and rollback limits.
- AC-2: V60 verification records totals, location-resolved rows, MAIN fallback rows, and null-site rows.
- AC-3: No production action, PR merge, or push occurs without explicit per-action approval.
