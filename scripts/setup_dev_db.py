#!/usr/bin/env python3
"""Reconcile Phase 6 schema on the local Compose postgres-dev database only."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS = ROOT / 'services/inventory-service/src/main/resources/db/migration'


def run(command: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(command, check=True, text=True, **kwargs)


def docker_target() -> list[str]:
    # Resolve once, reject remote contexts, then pin every command to this endpoint.
    if os.environ.get('DOCKER_HOST'):
        endpoint = os.environ['DOCKER_HOST']
    else:
        context = run(['docker', 'context', 'inspect'], capture_output=True)
        endpoint = json.loads(context.stdout)[0]['Endpoints']['docker']['Host']
    if not endpoint.startswith('unix:///'):
        raise RuntimeError('Refusing non-local Docker endpoint; select a local Unix-socket context.')
    docker = ['docker', '--host', endpoint]
    info = json.loads(run(docker + ['inspect', 'postgres-dev'], capture_output=True).stdout)[0]
    labels = info['Config'].get('Labels') or {}
    env = dict(value.split('=', 1) for value in info['Config'].get('Env', []) if '=' in value)
    if (info.get('Name') != '/postgres-dev' or not info['State']['Running']
            or labels.get('com.docker.compose.service') != 'postgres-dev'
            or not labels.get('com.docker.compose.project')
            or env.get('POSTGRES_DB') != 'mirai_inventory'
            or env.get('POSTGRES_USER') != 'postgres'):
        raise RuntimeError('Refusing target: expected running Compose postgres-dev with local dev database/user.')
    # Pin container ID as well, so a name replacement cannot redirect subsequent commands.
    return docker + ['exec', '-i', info['Id'], 'env', '-i',
                     'PATH=/usr/local/bin:/usr/bin:/bin', 'psql', '-X',
                     '-h', '/var/run/postgresql', '-p', '5432', '-U', 'postgres',
                     '-d', 'mirai_inventory', '-v', 'ON_ERROR_STOP=1']


def migration(version: int) -> str:
    files = list(MIGRATIONS.glob(f'V{version}__*.sql'))
    if len(files) != 1:
        raise RuntimeError(f'Expected exactly one V{version} SQL file.')
    return files[0].read_text()


def setup_sql() -> str:
    chunks = ['BEGIN; SET LOCAL lock_timeout = \'10s\';',
              "SELECT pg_advisory_xact_lock(727194621);",
              (ROOT / 'scripts/sql/dev-db-prepare.sql').read_text()]
    # The table may already exist from Hibernate; retain canonical V65 definitions otherwise.
    idem = migration(65).replace('CREATE TABLE command_idempotency (',
                                'CREATE TABLE IF NOT EXISTS command_idempotency (')
    idem = idem.replace('CREATE UNIQUE INDEX idx_', 'CREATE UNIQUE INDEX IF NOT EXISTS idx_')
    idem = idem.replace('CREATE INDEX idx_', 'CREATE INDEX IF NOT EXISTS idx_')
    chunks += [idem, (ROOT / 'scripts/sql/dev-db-defaults.sql').read_text()]
    # These init scripts skip inventory triggers before Hibernate creates products.
    chunks += [(ROOT / 'infra/init-db/20-unified-locations.sql').read_text(),
               (ROOT / 'infra/init-db/21-inventory-functions.sql').read_text()]
    for version in (53, 57, 58, 60, 62, 64, 66):
        # Local-only: ordinary index creation allows one atomic transaction. Production
        # SQL remains untouched and continues to use CONCURRENTLY.
        chunks.append(migration(version).replace('CREATE INDEX CONCURRENTLY', 'CREATE INDEX'))
    chunks += [(ROOT / 'scripts/sql/dev-db-verify.sql').read_text(), 'COMMIT;']
    return '\n'.join(chunks)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Read-only verification; do not apply changes.')
    args = parser.parse_args()
    try:
        target = docker_target()
        if args.check:
            sql = 'BEGIN READ ONLY;\n' + (ROOT / 'scripts/sql/dev-db-verify.sql').read_text() + '\nCOMMIT;'
        else:
            sql = setup_sql()
        print('Target: local Compose postgres-dev / mirai_inventory', flush=True)
        run(target, input=sql)
        print('Phase 6 dev database verification passed.' if args.check else
              'Phase 6 dev setup applied and verified. Rerun after seeding new users/products.')
        return 0
    except (RuntimeError, subprocess.CalledProcessError, OSError, KeyError, ValueError) as exc:
        print(f'Dev setup failed: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
