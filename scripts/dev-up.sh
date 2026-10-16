#!/usr/bin/env bash
# Brings up the dev stack and seeds it. Safe to re-run: seed/all skips when products exist.
set -euo pipefail

cd "$(dirname "$0")/.."
API=http://localhost:4000/api/dev

# The web app normally runs locally on :3000, so the containerized frontend is opt-in.
services=("$@")
if [[ ${#services[@]} -eq 0 ]]; then
  services=(postgres-dev kafka kafka-ui inventory-service forecasting-service messaging-service)
fi
docker compose -f infra/docker-compose.dev.yml up -d --build "${services[@]}"

echo "waiting for inventory-service"
until curl -fsS -X POST "$API/seed/core" >/dev/null 2>&1; do sleep 3; done

python3 scripts/setup_dev_db.py

# Fresh volumes keep the legacy employee_id columns, which the user-based review entities never write.
docker exec postgres-dev psql -U postgres -d mirai_inventory -q \
  -c "ALTER TABLE review_daily_counts DROP COLUMN IF EXISTS employee_id" \
  -c "ALTER TABLE reviews DROP COLUMN IF EXISTS employee_id"

curl -fsS -X POST "$API/seed/all" >/dev/null

# Each seed runs only while its own table is empty, so a half-finished earlier run is completed.
for pair in reviews:reviews audit-logs:audit_logs notifications:notifications; do
  path=${pair%%:*}
  table=${pair##*:}
  rows=$(docker exec postgres-dev psql -U postgres -d mirai_inventory -Atc "SELECT count(*) FROM $table")
  if [[ "$rows" == "0" ]]; then
    curl -fsS -X POST "$API/seed/$path" >/dev/null
  fi
done

python3 scripts/setup_dev_db.py
python3 scripts/setup_dev_db.py --check
