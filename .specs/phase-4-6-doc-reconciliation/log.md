# Implementation log

## Current handoff

- Status: Reconciliation and Phase 7 artifact corrections complete.
- Next action: Commit the documentation slice; F1 remains a hard stop pending approval.
- Decisions that must survive compaction: `products.is_active` remains the global stock-derived legacy flag; `site_products.is_stocked` is the per-site assortment flag. `analytics_monthly_rollup` is dead dev-only code, not a production table.
- Last verified: `git diff --check` — pass; focused V67 PostgreSQL migration test — 2 passed.
- Open risks/questions: Do not delete analytics code in this documentation slice; Phase 7 analytics owns it. The gated realtime record has not yet been opened, so its scheduler-observability finding is preserved in the closeout work order rather than creating Slice E work.

## Task record

- Corrected stale phase/runbook/route status claims, the three phantom analytics-table references, and the verified `machine_display` name.
- Made durable data/API §2 the single activity-semantics source; historical records and Phase 7 gate 6 now point to it.
- Added missing Phase 5b/5c Full-tier review/validation artifacts and recorded the 1,772 MAIN-only site-product backfill.
- Expanded Phase 7 inventory with the missing controllers, web route-family consumers, and site-less-versus-no-event producer classification.
