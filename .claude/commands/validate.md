---
description: Run relevant local checks and record Full-tier PR-gate evidence.
---

Read `AGENTS.md`, `docs/sdd-workflow.md`, and the affected CI jobs. Run the
native local checks relevant to the changed deployables and boundaries. If Java
inventory or contract checks are selected, require JDK 21 rather than using a
different installed JDK. The PR gate remains the independent proof.

For Full work, record the commands, result, and acceptance-criteria evidence in
`validation.md`. Report a concrete failing command or environment blocker; do
not claim success from compilation alone.
