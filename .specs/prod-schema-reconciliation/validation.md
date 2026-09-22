# Validation

## Command and scope

Read-only queries against production via the Supabase MCP connector, 2026-09-22 04:47–04:55 UTC,
after the user applied V58–V67, merged PR #328 and applied V68.

## Result

**Pass.** Production schema matches V68 and the deployed code writes site identity itself.

## Acceptance criteria evidence

- AC-1: Runbook `docs/runbooks/phase-4-6-production-schema-apply.md` was followed: backup first,
  drift check, ordered apply with `CONCURRENTLY` migrations run standalone, merge, recheck, V68.
- AC-2: V60 verification, reconstructed over the 16,414 rows that existed before the deploy:

  | Measure | Count |
  | --- | --- |
  | Total pre-deploy rows | 16,414 |
  | Resolved via location (sign-aware) | 13,442 |
  | MAIN fallback (no resolvable location) | 2,972 |
  | Resolved to a site other than the one stored | 0 |
  | Rows not on MAIN | 0 |
  | NULL `site_id` | 0 |

- AC-3: Every production action — backup, apply, merge, V68 — was performed by the user with
  explicit approval. No agent pushed, merged or wrote to production.

## Post-V68 state (04:55 UTC)

| Check | Value |
| --- | --- |
| `stock_movements.site_id` nullable | NO |
| `fk_stock_movements_site` | present |
| NOT_ASSIGNED guard triggers (insert + update) | 2 |
| SECOND `NOT_ASSIGNED` storage location | 1 |
| `command_idempotency` rows | 4 — v1 mutations are flowing through it |
| Stock movements / NULL `site_id` | 16,418 / 0 |
