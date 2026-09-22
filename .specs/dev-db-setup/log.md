# Implementation log

## Current handoff
- Status: implemented, independently reviewed, and locally verified.
- Next action: commit scoped files; rerun setup after backend startup or new seed data.
- Decisions: manual setup after Hibernate initializes; existing data seeders preserved;
  no production connections; not a full historical migration runner; no V61 constraints.
- Last verified: four Python unit tests, PostgreSQL integration fixtures, and read-only --check passed.
- Risks: baseline uses Hibernate update; rerun after backend restarts; not full historical schema parity.

## Assumptions
User authorized dev-only setup and local testing, including MAIN membership backfill.
No role elevation, inventory reset, or production changes are authorized.
Existing seed_dev_inventory.py is a forecast-data seeder, not a schema setup script.

## Completed implementation and current handoff
- Status: guarded setup, SQL, documentation, unit and PostgreSQL integration checks complete.
- Next action: commit scoped files; use `python3 scripts/setup_dev_db.py` after backend startup
  and again after demo data seeding or backend rebuild/restart.
- Surviving decisions: reuse V53/V57/V58/V60/V62/V64/V65/V66, install location init triggers;
  no historical migration replay, production access, role elevation, or V61 constraints.
- Last verified: 4 Python tests passed; rollback-only integration checks passed; final --check passed.
- Review: independent Standards and Spec finding fixed and integration-tested (see review.md).
- Risk: Hibernate remains enabled in dev and may reapply VARCHAR mappings after restart;
  documented rerun requirement. This bridge is not complete production schema parity.
