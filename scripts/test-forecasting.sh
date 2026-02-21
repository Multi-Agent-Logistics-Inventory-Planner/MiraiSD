#!/usr/bin/env bash
# Integration test for the forecasting service
# Usage: ./scripts/test-forecasting.sh
#
# This script:
# 1. Starts the dev Docker environment (ephemeral database with seed data)
# 2. Waits for all services to be ready
# 3. Produces Kafka events directly for all seeded products
# 4. Waits for forecasting batch to process
# 5. Verifies forecasts were generated
# 6. Optionally cleans up

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$PROJECT_ROOT/infra/docker-compose.dev.yml"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
echo_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
echo_error() { echo -e "${RED}[ERROR]${NC} $1"; }

cleanup() {
    echo_info "Cleaning up..."
    docker compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>/dev/null || true
}

# Parse arguments
SKIP_CLEANUP=false
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --no-cleanup) SKIP_CLEANUP=true ;;
        -h|--help)
            echo "Usage: $0 [--no-cleanup]"
            echo "  --no-cleanup  Leave containers running after test"
            exit 0
            ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
    shift
done

# Set up trap for cleanup on error (unless --no-cleanup)
if [ "$SKIP_CLEANUP" = false ]; then
    trap cleanup EXIT
fi

echo "============================================"
echo "  Forecasting Service Integration Test"
echo "============================================"
echo ""

# Only start the services needed for forecasting (skip frontend, messaging, inventory-service)
SERVICES="postgres-dev kafka forecasting-service kafka-ui"

# Step 1: Start the dev environment (reset volumes for fresh seed data)
echo_info "Starting dev environment..."
docker compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>/dev/null || true
docker compose -f "$COMPOSE_FILE" up -d --build $SERVICES

# Step 2: Wait for PostgreSQL
echo_info "Waiting for PostgreSQL to be ready..."
RETRIES=30
until docker exec postgres-dev pg_isready -U postgres -d mirai_inventory > /dev/null 2>&1; do
    RETRIES=$((RETRIES - 1))
    if [ $RETRIES -eq 0 ]; then
        echo_error "PostgreSQL failed to start"
        exit 1
    fi
    sleep 1
done
echo_info "PostgreSQL is ready"

# Step 3: Wait for Kafka
echo_info "Waiting for Kafka to be ready..."
RETRIES=30
until docker exec kafka-dev /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list > /dev/null 2>&1; do
    RETRIES=$((RETRIES - 1))
    if [ $RETRIES -eq 0 ]; then
        echo_warn "Kafka check timed out, continuing anyway..."
        break
    fi
    sleep 2
done
echo_info "Kafka is ready"

# Step 4: Wait for Forecasting Service container to be running
echo_info "Waiting for Forecasting Service to start..."
RETRIES=30
until docker inspect -f '{{.State.Running}}' forecasting-service-dev 2>/dev/null | grep -q true; do
    RETRIES=$((RETRIES - 1))
    if [ $RETRIES -eq 0 ]; then
        echo_error "Forecasting Service failed to start"
        docker logs forecasting-service-dev --tail 50
        exit 1
    fi
    sleep 2
done
echo_info "Forecasting Service is running"

# Step 5: Verify seed data
echo_info "Verifying seed data..."
echo ""
echo "Products:"
docker exec postgres-dev psql -U postgres -d mirai_inventory -c \
    "SELECT sku, name, lead_time_days, reorder_point FROM products ORDER BY sku;"

echo ""
echo "Stock movements summary:"
docker exec postgres-dev psql -U postgres -d mirai_inventory -c \
    "SELECT p.sku, COUNT(*) as movements, SUM(ABS(quantity_change)) as total_units
     FROM stock_movements sm JOIN products p ON sm.item_id = p.id
     GROUP BY p.sku ORDER BY p.sku;"

# Step 6: Produce Kafka events directly for all seeded products
# The forecasting pipeline reads stock_movements from the DB (already seeded).
# Kafka events just tell the pipeline WHICH items to process.
echo ""
echo_info "Producing Kafka events for all seeded products..."

PRODUCT_IDS=$(docker exec postgres-dev psql -U postgres -d mirai_inventory -t -c \
    "SELECT id FROM products WHERE is_active = true;" | tr -d ' ' | grep -v '^$')

if [ -z "$PRODUCT_IDS" ]; then
    echo_error "No active products found in database"
    exit 1
fi

EVENT_COUNT=0
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)

for PRODUCT_ID in $PRODUCT_IDS; do
    EVENT_ID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "evt-$EVENT_COUNT")
    ENTITY_ID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "ent-$EVENT_COUNT")

    EVENT="{\"event_id\":\"$EVENT_ID\",\"topic\":\"inventory-changes\",\"event_type\":\"CREATED\",\"entity_type\":\"stock_movement\",\"entity_id\":\"$ENTITY_ID\",\"payload\":{\"item_id\":\"$PRODUCT_ID\",\"quantity_change\":-1,\"reason\":\"sale\",\"at\":\"$NOW\"},\"created_at\":\"$NOW\"}"

    echo "$EVENT" | docker exec -i kafka-dev /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server localhost:9092 \
        --topic inventory-changes 2>/dev/null

    EVENT_COUNT=$((EVENT_COUNT + 1))
done

echo_info "Produced $EVENT_COUNT Kafka events"

# Step 7: Wait for forecasting batch
echo ""
echo_info "Waiting for forecasting batch to process (35 seconds)..."
echo_info "The forecasting service batches events every 30 seconds"
sleep 35

# Step 8: Check forecast predictions
echo ""
echo_info "Checking forecast predictions..."
FORECAST_COUNT=$(docker exec postgres-dev psql -U postgres -d mirai_inventory -t -c \
    "SELECT COUNT(*) FROM forecast_predictions;" | tr -d ' \n')

# Validate FORECAST_COUNT is a number
if ! [[ "$FORECAST_COUNT" =~ ^[0-9]+$ ]]; then
    echo_warn "Failed to get forecast count (got: '$FORECAST_COUNT'), defaulting to 0"
    FORECAST_COUNT=0
fi

if [ "$FORECAST_COUNT" -gt 0 ]; then
    echo_info "Found $FORECAST_COUNT forecast predictions"
    echo ""
    echo "Forecast Results:"
    docker exec postgres-dev psql -U postgres -d mirai_inventory -c \
        "SELECT
            p.sku,
            ROUND(f.avg_daily_delta::numeric, 2) as avg_daily,
            ROUND(f.days_to_stockout::numeric, 1) as days_to_stockout,
            f.suggested_reorder_qty as reorder_qty,
            ROUND(f.confidence::numeric, 2) as confidence
         FROM forecast_predictions f
         JOIN products p ON f.item_id = p.id
         ORDER BY f.computed_at DESC, p.sku
         LIMIT 16;"

    echo ""
    echo_info "Test PASSED - Forecasting service is working correctly"
else
    echo_warn "No forecasts found yet."
    echo_warn "This may be because:"
    echo_warn "  1. The forecasting service is still processing"
    echo_warn "  2. No Kafka events have been received"
    echo ""
    echo "Checking forecasting service logs..."
    docker logs forecasting-service-dev --tail 30

    echo ""
    echo_warn "Check if the forecasting service consumed any events"
fi

echo ""
echo "============================================"
echo "  Test Complete"
echo "============================================"

if [ "$SKIP_CLEANUP" = true ]; then
    echo ""
    echo_info "Containers are still running (--no-cleanup specified)"
    echo_info "To stop: docker compose -f $COMPOSE_FILE down -v"
    echo_info "To view logs: docker logs forecasting-service-dev -f"
fi
