# Execution log

## Current handoff

- Status: Specification complete; migration design selected.
- Next action: Write test fixtures that reproduce SECOND's missing NOT_ASSIGNED rows and a concurrent duplicate location, then add migrations.
- Surviving decisions: Use a trigger because the predicate spans two tables. Reserve V67 for the constraint migration: V61 is below already-present V66 and would be out-of-order after Flyway baseline.
- Last verified command: Slice B H2 legacy security regression — 45 tests passed.
- Open risks: Never apply C migrations before F1 and explicit production approval; final constraint depends on production V60 backfill verification.
