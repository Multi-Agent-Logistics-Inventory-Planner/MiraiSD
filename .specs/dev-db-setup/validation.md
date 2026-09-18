# Validation

- `python3 -m unittest discover -s scripts/tests -p 'test_setup_dev_db.py'` — 4 passed.
  Remote Docker context/DOCKER_HOST rejected, incorrect container/database rejected;
  pinned container/socket and cleared PostgreSQL environment verified (AC-1).
- `python3 scripts/setup_dev_db.py --check` before setup — failed as expected: MAIN missing.
- `python3 scripts/setup_dev_db.py` — applied against local Compose postgres-dev, committed
  only after schema/index/default/trigger/backfill assertions passed (AC-2–4).
- `python3 scripts/tests/check_dev_db_integration.py` — passed: two setups in one fixture
  transaction preserve explicit product override and inactive membership, avoid duplicate site
  product rows, backfill stock/outbox site/correlation without changing quantity, apply default
  event version, refuse non-MAIN movement/outbox history; all fixtures roll back (AC-3–4).
- `python3 scripts/setup_dev_db.py --check` final — passed read-only assertions (AC-2–5).

Observed backend restart reintroduced Hibernate VARCHAR mappings during testing. Reran setup
and documented the startup ordering. No live data was reset. MAIN and missing existing-user
membership were intentionally added by setup; no role or admin flag was changed.

No Java, endpoint, generated contract, or production migration files changed. Native checks
for this Python/SQL change are Python unit tests and real local PostgreSQL execution; a Java
suite is not evidence for this runner and was not rerun. Independent PR gate remains pending.
