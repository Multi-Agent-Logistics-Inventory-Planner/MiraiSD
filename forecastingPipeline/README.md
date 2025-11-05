# MALP Forecast Pipeline

Forecasting pipeline for inventory management and demand prediction.

## Structure

```
malp-forecast/
  src/
    __init__.py
    config.py              # Configuration settings
    repo.py                # Data adapter (CSV now, Supabase later)
    events.py              # Event source (NDJSON now, Kafka later)
    features.py            # Feature engineering
    models.py              # Forecasting models
    reorder.py             # Reorder policy computation
    forecast_job.py        # Orchestrator (CLI)
  data/
    items.csv
    inventories.csv
  events/
    inventory-changes.ndjson
  out/                     # Generated artifacts (forecasts, metrics)
  tests/
    test_*.py
  requirements.txt
  pyproject.toml
```

## Setup

1. Create a virtual environment:

```bash
python -m venv env
source env/bin/activate  # On Windows: env\Scripts\activate
```

2. Install dependencies:

```bash
pip install -r requirements.txt
```

## Usage

Run the forecasting job:

```bash
python -m src.forecast_job
```

### Event Source testing scripts

Load a window of events (validated, time-sorted):

```bash
python -c "from src.events import load_events_window as f; import pandas as pd; print(f('2025-11-01','2025-11-30').head())"
```

Stream events (bounded example):

```bash
python -c "from src.events import stream_events as s; print(list(s(from_ts='2025-11-05T00:00:00Z', to_ts='2025-11-05T23:59:59Z'))[:3])"
```

## Development

### Code Formatting & Linting

Before committing, run these commands to check and fix code quality:

```bash
pytest --cache-clear
find . -type d -name "__pycache__" -not -path "./env/*" -exec rm -r {} + 2>/dev/null || true
```

**Check for issues:**

```bash
ruff check src tests
black --check src tests
isort --check src tests
```

**Fix issues automatically:**

```bash
ruff check --fix src tests
black src tests
isort src tests
```

**Run all checks at once:**

```bash
ruff check src tests && black --check src tests && isort --check src tests
```

**Fix all issues at once:**

```bash
ruff check --fix src tests && black src tests && isort src tests
```

### Testing

Run tests:

```bash
pytest
```

Run tests with coverage:

```bash
pytest --cov=src
```
