# MiraiSD Spec-Driven Development workflow

This workflow makes feature intent, implementation, and proof reviewable while
keeping documentation proportional to the change. It applies to new work after
the pre-existing `refactor/multi-site` branch has merged; that in-flight work is
not retrofitted.

## Boundaries

`docs/specs/`, `docs/adr/`, and `docs/plans/` remain the durable architecture
record. Use `docs/plans/` for work that spans multiple features or phases. A
plan may link to several `.specs/<feature-id>/` records. Use `.specs/` only for
the execution record of one mergeable feature.

## Tiers

| Tier | Use for | Required artifacts |
| --- | --- | --- |
| Trivial | Copy changes, a contained UI adjustment, or one validator | No `.specs/` record; a conventional commit and relevant validation |
| Standard | Most features and behavior changes | `spec.md` and `log.md` |
| Full | Cross-service behavior; Kuji lifecycle; forecasting; RBAC/auth; public contracts; migrations; asynchronous delivery; or deployment changes | Standard artifacts plus `review.md` and `validation.md` |

Artifact-light work is not validation-light work. Every tier runs the relevant
native checks before completion, and the PR gate is the independent proof.

## Lifecycle

1. **Specify.** Classify the tier. For Standard or Full work, create the
   feature record and link the durable documents that constrain it.
2. **Review and plan.** Resolve requirements, identify affected boundaries,
   write acceptance criteria, and split the change into verifiable tasks.
3. **Implement.** Work one task at a time. Start with a failing test when a
   meaningful local test path exists. Record material assumptions and results
   as the work proceeds.
4. **Test and validate.** Run the native checks relevant to the changed
   deployables and boundaries. The PR gate runs the independent merge checks.
5. **Review and commit.** Full work records an explicit review and validation
   result. Commit only the feature artifacts and implementation that belong to
   the change.

Codex proceeds through routine implementation uncertainty and records its
assumptions. It pauses for material product, security, data-loss, or
irreversible-deployment decisions.

## Templates

### `spec.md`

```md
# <feature title>

## Tier

Standard | Full

## Problem and outcome

<What changes for the user or operator, and why?>

## Durable context

- Specs: <links>
- ADRs: <links>
- Plan: <link or "None">

## Acceptance criteria

- AC-1: <observable behavior>
- AC-2: <observable behavior>

## Tasks

- T-1: <small, verifiable task>
- T-2: <small, verifiable task>
```

### `log.md`

```md
# Implementation log

## Current handoff

- Status: <completed task and in-progress task>
- Next action: <one concrete action>
- Decisions that must survive compaction: <short bullets>
- Last verified: `<command>` — <result>
- Open risks/questions: <short bullets or "None">

## Assumptions and decisions

- <assumption, evidence, and outcome>

## Task record

### T-1 — <title>

- Changed: <files or behavior>
- Tests: <test added or changed>
- Result: <pass, fail, or blocker>

## Test plan

- AC-1: <test or runtime evidence>
- AC-2: <test or runtime evidence>
```

### `review.md` (Full only)

Findings come from two independent passes — a Standards pass (repo
conventions, correctness, tenant/site safety, security) and a Spec pass
(does the diff satisfy every `AC-N`) — run in parallel so neither pollutes
the other, then synthesized into one disposition per finding.

```md
# Review

## Scope reviewed

<Behavior, contracts, authorization, migration, or event boundaries reviewed.>

## Findings

- [Standards|Spec] <finding> — **act on** | **consider** | **dismissed**
  (<AC-N if this is a Spec-pass gap>)

## Residual risk

- <known risk, or "None identified">
```

### `validation.md` (Full only)

```md
# Validation

## Command and scope

`<commands run locally>`

## Result

<Pass, failure, or blocked state with evidence.>

## Acceptance criteria evidence

- AC-1: <evidence>
- AC-2: <evidence>
```

## Ratchet policy

The current CI checks are the starting validation contract. Add stricter
coverage, mutation, formatting, or architecture gates only after measuring the
current baseline. Raise enforced floors incrementally; do not make an
unmeasured aspirational threshold an immediate hard failure.
