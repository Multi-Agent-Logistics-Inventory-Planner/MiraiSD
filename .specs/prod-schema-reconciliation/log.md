# Implementation log

## Current handoff

- Status: **F1 complete.** Production is at V68. V58–V67 were applied manually in the Supabase SQL
  editor, PR #328 merged and deployed, the post-deploy NULL recheck returned zero, and V68 was applied.
- Next action: none in F1. F2 (Flyway canonicalization) is deferred by user decision on 2026-09-22;
  it is not blocking Phase 7. When F2 resumes, baseline at V68 — every migration through V68 is applied.
- Decisions that must survive compaction: production has no `flyway_schema_history`; all migrations
  through V68 were applied by hand. Until F2 lands, every new migration is a manual, approved apply.
- Last verified: read-only production checks at 2026-09-22 04:55 UTC (see `validation.md`).
- Open risks/questions: backup snapshot ID not yet recorded here — see Execution record.

## Execution record — 2026-09-22

| Step | Result |
| --- | --- |
| Backup | Taken by the user in the Supabase dashboard before any apply. Snapshot ID: **TODO — record** |
| Drift check | Production confirmed at V57: V58/V59/V63/V65/V67 markers all absent, no `flyway_schema_history` |
| Apply V58, V59, V60, V62, V63, V64, V65, V66, V67 | Applied manually in the SQL editor, in order, before the merge |
| V60 NULL check | 0 NULL `site_id` before merge |
| Merge PR #328 (`dev` → `main`) | `7a5008b`; `deploy.yml` run started 04:36:49 UTC, finished 04:43:18 UTC, success |
| Post-deploy writer check | 2 movements written after 04:43:18 UTC, both stamped MAIN by the new code (not the backfill) |
| Post-deploy NULL recheck | 0 NULL `site_id` of 16,416; 0 orphaned site IDs; 0 invalid indexes |
| Apply V68 | Applied by the user; confirmed `site_id` NOT NULL and `fk_stock_movements_site` present |

## Assumptions and decisions

- The V60 source split in `validation.md` was reconstructed after the fact by re-running V60's
  sign-aware resolution predicate against pre-deploy rows. Location rows could in principle have
  changed between the apply and the reconstruction; the outcome is unaffected because every
  resolved row and every fallback row lands on MAIN.
- Exact apply timestamps for V58–V67 and V68 were not captured; the deploy window brackets them.
