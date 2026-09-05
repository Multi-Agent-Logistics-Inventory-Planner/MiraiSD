# MiraiSD agent instructions

## Sources of truth

Read the relevant durable documents before changing architecture, tenant data,
authorization, public APIs, events, migrations, or deployment behavior.

- `docs/specs/` defines target behavior and verifiable requirements.
- `docs/adr/` records accepted architecture decisions.
- `docs/plans/` owns work spanning multiple mergeable features or phases.
- `.specs/<feature-id>/` records execution for one new, mergeable feature. It
  does not replace the durable documents above.

Follow [the SDD workflow](docs/sdd-workflow.md) for all new work after the
pre-existing `refactor/multi-site` work has merged.

## SDD lifecycle

For non-trivial work, classify the tier before editing:

- Trivial work needs no `.specs/` record, but still needs relevant validation.
- Standard work uses `.specs/<feature-id>/spec.md` and `log.md`.
- Full work also uses `review.md` and `validation.md`. Use Full for
  cross-service behavior, Kuji lifecycle work, forecasting, RBAC/auth, public
  contracts, migrations, asynchronous delivery, or deployment changes.

Use the lifecycle: specify, review, plan, implement with TDD where a meaningful
local test is available, test, validate, review, and commit. Record material
assumptions in `log.md`. Ask the user only for material product, security,
data-loss, or irreversible-deployment decisions; otherwise proceed with a
recorded assumption.

## Verification

Run the relevant native tests and checks locally before declaring work complete.
The PR gate remains the authoritative independent proof. Java inventory and
contract validation requires JDK 21; do not substitute another JDK when it is
absent. Use the `./mvnw` wrapper for Maven commands, not a global `mvn`
install, so the pinned Maven version matches CI.

REST endpoint changes require regenerating `packages/contracts/openapi.json`
and `packages/api-client/src/schema.d.ts`. Schema work follows expand,
backfill, verify, constrain. Do not treat a successful compilation as proof of
runtime, authorization, contract, migration, or event behavior.

## Scope and safety

Keep changes minimal. Preserve the current dirty worktree unless a task
explicitly includes those files. Do not deploy, delete data, force-push, or
change production configuration without explicit approval.
