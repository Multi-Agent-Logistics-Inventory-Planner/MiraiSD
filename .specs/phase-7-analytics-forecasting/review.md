# Review

## Scope reviewed

Kickoff specification only; no implementation diff exists.

## Findings

- [Standards] Cross-service table writes need a stable view or event replacement before removal — **act on** (AC-3).
- [Spec] H2 is insufficient evidence for PostgreSQL-native projection SQL — **act on** (AC-6).

## Residual risk

- Ownership and refresh semantics are not yet chosen.
