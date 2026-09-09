# Implementation log

## Current handoff

- Status: delivery structure specified and independently reviewed; 6a–6e implementation not started.
- Next action: inventory callers, entity associations, tables, endpoints and event consumers for
  6a; record baseline and concrete tasks before implementation.
- Surviving decisions: one branch/PR and one Full-tier record; five logical commits/review
  checkpoints with fix commits allowed. A production prerequisite can require a separate PR.
- Last verified: `git diff --check` and inline Python local-link/record checks — pass;
  six documents, 26 local link targets, four Full-tier files and five checkpoints. No runtime tests run.
- Open risks: actual migration mechanism and old-writer compatibility; global quantity/activity
  semantics and downstream consumers; event payload coverage and targeted-refresh baseline.

## Assumptions and decisions

- 2026-09-09: user chose five checkpoints within one branch/PR, superseding the proposed separate
  slice PRs. Updated both durable plans and created this single execution record.
- Commit order cannot establish deployed backfill/writer prerequisites. Resolve rollout before 6b;
  do not deploy, drop columns, or redefine global activity as part of this documentation update.
- Branch creation, PR publication and implementation have not been performed by this update.

## Task record

6a–6e: not started. Planning update only.

Planning review: parallel Standards/Spec passes found no blocking issues. Updated both plans and
the Full-tier record; documentation checks pass. No branch/PR creation, commit or production action.
