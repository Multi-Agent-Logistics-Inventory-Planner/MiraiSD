# Forecasting Service

Forecasting pipeline for inventory management and demand prediction.

## Structure

```
forecasting-service/
  src/
    __init__.py
    config.py              # Configuration settings
    repo.py                # Data adapter (CSV now, Supabase later)
    events.py              # Event source (NDJSON now, Kafka later)
    features.py            # Feature engineering
    forecast.py            # Forecast estimators
    policy.py              # Inventory policy math
    forecast_job.py        # Orchestrator (CLI)
  tests/
    test_*.py
  data/                    # Optional sample inputs (local/dev)
  events/                  # Optional sample events (local/dev)
  out/                     # Generated artifacts (forecasts, metrics)
  requirements.txt
  pyproject.toml
```

## Setup

1. Create a virtual environment:

```bash
python -m venv venv
source venv/bin/activate  # On Windows: venv\\Scripts\\activate
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

### Event source testing scripts

Load a window of events (validated, time-sorted):

```bash
python -c "from src.events import load_events_window as f; import pandas as pd; print(f('2025-11-01','2025-11-30').head())"
```

Stream events (bounded example):

```bash
python -c \"from src.events import stream_events as s; print(list(s(from_ts='2025-11-05T00:00:00Z', to_ts='2025-11-05T23:59:59Z'))[:3])\"
```

## Development

### Code Formatting & Linting

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

### Testing

```bash
pytest
```
