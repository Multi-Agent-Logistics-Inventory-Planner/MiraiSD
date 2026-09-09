# Validation

## Command and scope

Documentation-only update, checked from the repository root on 2026-09-09:

- `git diff --check` — PASS.
- `python3` inline documentation check using `pathlib`/`re` — PASS: six documents, 26 local
  Markdown link targets exist, all four Full-tier files present, and five checkpoint rows in spec.md.
  Checked file targets, not generated heading anchors. The six documents are both changed plans
  and this record's spec, log, review and validation files.

## Result

Documentation checks pass. No application code, schema, API or event changes. Native runtime tests are not applicable to this
planning update; Phase 6 implementation acceptance criteria remain unverified.

## Planned implementation evidence

- 6a: JDK 21 with `./mvnw`; relevant unit/integration and ArchUnit checks, including legacy callers.
- 6b/6c: PostgreSQL migration, authorization, concurrency and atomicity tests; affected event
  producer/consumer suites; generated OpenAPI/client checks and contract compatibility.
- 6d/6e: web native test/typecheck/lint commands, rendered workflow/site-switch and recovery tests,
  controlled query/egress measurements, then the full phase exit gate and authoritative PR gate.

Record exact commands, results, evidence paths and remaining limitations as each checkpoint runs.
