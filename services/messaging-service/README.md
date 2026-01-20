# Messaging Service

Messaging service for inventory alerts and notifications. Monitors inventory levels and sends Slack alerts when quantities drop below reorder points.

## Structure

```
messaging-service/
  src/
    __init__.py
    config.py              # Configuration settings
    events.py              # Event models
    worker.py              # Main worker loop
    adapters/
      __init__.py
      kafka_consumer.py    # Kafka event consumer
      supabase_repo.py     # Database queries
      slack_notifier.py    # Slack notification sender
    application/
      __init__.py
      alert_checker.py     # Reorder point alert logic
    api/
      __init__.py
      main.py              # Optional FastAPI health endpoint
  tests/
    __init__.py
    test_*.py
  requirements.txt
  Dockerfile
  README.md
```

## Setup

1. Create a virtual environment:

```bash
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
```

2. Install dependencies:

```bash
pip install -r requirements.txt
```

## Configuration

Set the following environment variables:

```bash
# Kafka settings
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
KAFKA_TOPIC=inventory-changes
KAFKA_CONSUMER_GROUP=messaging-service

# Supabase connection (for querying product data)
SUPABASE_DB_URL=postgresql://...
SUPABASE_DB_USERNAME=postgres
SUPABASE_DB_PASSWORD=...

# Slack settings
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...
SLACK_CHANNEL=#inventory-alerts
SLACK_ENABLED=true

# Alert settings
ALERT_DEBOUNCE_SECONDS=300  # 5 minutes default
```

## Usage

### Run the worker:

```bash
python -m src.worker
```

The worker will:
1. Consume events from Kafka `inventory-changes` topic
2. Check if product quantity is below reorder_point
3. Send Slack alerts when thresholds are breached
4. Debounce alerts to avoid spam (5 min default)

### Run API server (optional):

```bash
uvicorn src.api.main:app --host 0.0.0.0 --port 5001
```

## How It Works

1. **Event Consumption**: Consumes `inventory-changes` events from Kafka (same topic as forecasting-service, but different consumer group)

2. **Alert Checking**: For each event:
   - Queries database to get current product inventory across all location types
   - Compares current quantity to `reorder_point` from `products` table
   - Checks debounce to avoid duplicate alerts

3. **Notification**: Sends formatted Slack message with:
   - Product name and SKU
   - Current quantity vs reorder point
   - Alert status

## Database Schema

The service expects:
- `products` table with `id`, `name`, `sku`, `reorder_point` columns
- Inventory tables: `rack_inventories`, `box_bin_inventories`, `cabinet_inventories`, `keychain_machine_inventories`, `single_claw_machine_inventories` with `item_id` and `quantity` columns

## Development

### Code Formatting & Linting

**Check for issues:**

```bash
ruff check src tests
black --check src tests
```

**Fix issues automatically:**

```bash
ruff check --fix src tests
black src tests
```

### Testing

```bash
pytest
```

## Docker

Build and run with Docker:

```bash
docker build -t messaging-service:latest .
docker run -e SLACK_WEBHOOK_URL=... messaging-service:latest
```

