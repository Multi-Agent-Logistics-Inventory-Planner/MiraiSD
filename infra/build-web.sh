#!/usr/bin/env bash
set -euo pipefail

# Interim manual build step for the web frontend image.
#
# Run this ON THE HETZNER HOST, not locally: the `web` service in
# docker-compose.yml uses `pull_policy: never` with no `build:` key, so
# Compose expects `web:latest` to already exist in the local Docker image
# cache of whichever daemon runs `docker compose up`.
#
# This script is a temporary stand-in for the CI/GHCR pipeline described in
# docs/runbooks/frontend-consolidation.md section 5 ("Build and publish via
# CI"), which is deliberately being implemented last. Once that pipeline
# exists, this script and the manually maintained env file it depends on
# should be retired in favor of digest-pinned GHCR images.
#
# Expects apps/web/.env.production.local to already exist on the host
# (gitignored, never committed — copy it over with scp, then `chmod 600`).
# It must contain PRODUCTION values, not local-dev ones:
#   NEXT_PUBLIC_SUPABASE_URL
#   NEXT_PUBLIC_SUPABASE_ANON_KEY
#   NEXT_PUBLIC_BACKEND_URL       (e.g. https://api.mirai-inventory.com)
#   NEXT_PUBLIC_R2_PUBLIC_BASE_URL
#
# NEXT_PUBLIC_API_URL is deliberately not passed: lib/api/backend-url.ts
# checks NEXT_PUBLIC_BACKEND_URL first, so as long as that is set, the
# API_URL fallback is unreachable. See the Dockerfile's ARG for it if a
# future caller ever needs it.

cd "$(dirname "$0")/.."

ENV_FILE="apps/web/.env.production.local"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "error: $ENV_FILE not found. Copy it from your local machine first:" >&2
  echo "  scp apps/web/.env.prod.local <user>@<host>:$(pwd)/$ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

for var in NEXT_PUBLIC_SUPABASE_URL NEXT_PUBLIC_SUPABASE_ANON_KEY \
           NEXT_PUBLIC_BACKEND_URL NEXT_PUBLIC_R2_PUBLIC_BASE_URL; do
  if [[ -z "${!var:-}" ]]; then
    echo "error: $var is empty in $ENV_FILE" >&2
    exit 1
  fi
done

if [[ "$NEXT_PUBLIC_BACKEND_URL" == *localhost* ]]; then
  echo "error: NEXT_PUBLIC_BACKEND_URL is $NEXT_PUBLIC_BACKEND_URL — looks like" >&2
  echo "a local-dev value, not production. Refusing to build." >&2
  exit 1
fi

echo "Building web:latest with:"
echo "  NEXT_PUBLIC_SUPABASE_URL=$NEXT_PUBLIC_SUPABASE_URL"
echo "  NEXT_PUBLIC_BACKEND_URL=$NEXT_PUBLIC_BACKEND_URL"
echo "  NEXT_PUBLIC_R2_PUBLIC_BASE_URL=$NEXT_PUBLIC_R2_PUBLIC_BASE_URL"
echo "  NEXT_PUBLIC_SUPABASE_ANON_KEY=<redacted>"

docker build \
  --build-arg NEXT_PUBLIC_SUPABASE_URL \
  --build-arg NEXT_PUBLIC_SUPABASE_ANON_KEY \
  --build-arg NEXT_PUBLIC_BACKEND_URL \
  --build-arg NEXT_PUBLIC_R2_PUBLIC_BASE_URL \
  -t web:latest \
  apps/web

echo "Built web:latest. Bring it up with: docker compose -f infra/docker-compose.yml up -d"
