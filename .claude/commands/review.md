---
description: Review a Full-tier MiraiSD SDD feature before commit.
---

Read `AGENTS.md`, `docs/sdd-workflow.md`, and the active
`.specs/<feature-id>/spec.md`. Run the review as two independent passes, in a
single message so they execute in parallel and neither pollutes the other:

- **Standards pass.** For inventory-service or cross-service work, invoke the
  project-local `mirai-spring-reviewer` agent. For `apps/web` or full-stack
  work, invoke the project-local `mirai-next-reviewer` agent. Also invoke the
  globally installed `code-reviewer` and `security-reviewer` when their
  boundary applies. None of these agents see the acceptance criteria below —
  they judge only conventions, correctness, tenant/site safety, and security,
  against the diff and the relevant durable specifications, contracts,
  authorization, migrations, and event behavior.
- **Spec pass.** Separately, check the diff against every `AC-N` in
  `spec.md`: does the implementation and its tests actually satisfy each
  acceptance criterion, not just compile against it. This pass does not see
  the Standards agents' findings and is not a restatement of `log.md`.

Synthesize both passes yourself. Do not just concatenate the two agents'
output. For each finding, categorize as **act on** (blocking), **consider**
(real but non-blocking), or **dismissed** (wrong, out of scope, or already
covered), and note which pass it came from. Cross-reference Spec-pass gaps
against the acceptance criteria they fail.

Write findings, dispositions, and residual risk to `review.md`. Do not create
this artifact for Trivial or Standard work unless the user explicitly asks.
