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

3. Install pre-commit hooks (optional):

```bash
pip install pre-commit
pre-commit install
```

## Usage

Run the forecasting job:

```bash
python -m src.forecast_job
```

## Development

Code formatting is handled by `black`, `ruff`, and `isort` via pre-commit hooks.

Run tests:

```bash
pytest
```
