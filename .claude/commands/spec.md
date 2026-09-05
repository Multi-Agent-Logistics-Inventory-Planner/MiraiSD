---
description: Start a MiraiSD SDD feature record and specification.
---

Read `AGENTS.md` and `docs/sdd-workflow.md`. Classify the request as Trivial,
Standard, or Full before changing implementation files.

For Standard or Full work, create `.specs/<feature-id>/spec.md` from the shared
template. Include the problem, durable-doc links, observable acceptance
criteria, and small verifiable tasks. Ask only for a material product,
security, data-loss, or irreversible-deployment decision. Otherwise record an
assumption and proceed.

Use the globally installed `planner` for task decomposition. For inventory
service or cross-service scope, invoke the project-local `mirai-spring-architect`
agent within this phase. Do not duplicate either agent's behavior here.
