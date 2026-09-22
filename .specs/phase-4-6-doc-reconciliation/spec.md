# Phase 4-6 documentation reconciliation

## Tier

Standard

## Problem and outcome

Durable plans and execution records contain stale status, table-name, route, and activity-semantics claims. This slice establishes one authoritative `is_active` definition and corrects verified inventory contradictions without deleting Phase 7-owned code.

## Durable context

- Plan: `docs/plans/phase-4-6-closeout.md`, Slice D
- Spec: `docs/specs/multi-site-data-and-api.md`

## Acceptance criteria

- AC-1: Stale Phase 4-6 status, route, runbook, CI, and index claims are corrected or explicitly historical.
- AC-2: `products.is_active` and `site_products.is_stocked` have one referenced definition.
- AC-3: Dead `analytics_monthly_rollup` documentation is corrected while its Phase 7-owned code is retained.
- AC-4: Missing Full-tier records for Phase 5b/5c are supplied with historical validation evidence.
