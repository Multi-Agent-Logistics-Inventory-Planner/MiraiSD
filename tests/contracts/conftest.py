"""Shared fixtures for contract tests."""

import json
import sys
import types
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

import pytest

SCHEMA_DIR = Path(__file__).parent / "schemas"

# Set up forecasting-service src as importable package.
# The events.py module does `from . import config` which requires package context.
# We pre-create the package in sys.modules so the relative import resolves.
FORECASTING_SRC = (
    Path(__file__).parent.parent.parent / "services" / "forecasting-service" / "src"
)

_src_package = types.ModuleType("src")
_src_package.__path__ = [str(FORECASTING_SRC)]
_src_package.__package__ = "src"
sys.modules.setdefault("src", _src_package)

# Import config first so events.py can find it
import importlib.util

_config_spec = importlib.util.spec_from_file_location(
    "src.config", FORECASTING_SRC / "config.py"
)
_config_mod = importlib.util.module_from_spec(_config_spec)
sys.modules["src.config"] = _config_mod
_config_spec.loader.exec_module(_config_mod)

# Now import events
_events_spec = importlib.util.spec_from_file_location(
    "src.events", FORECASTING_SRC / "events.py"
)
_events_mod = importlib.util.module_from_spec(_events_spec)
sys.modules["src.events"] = _events_mod
_events_spec.loader.exec_module(_events_mod)

# Make them importable as top-level too for convenience in test files
sys.modules["events"] = _events_mod
sys.modules["config"] = _config_mod


@pytest.fixture(scope="session")
def event_envelope_schema() -> dict:
    """Load the shared JSON Schema for inventory-change events."""
    schema_path = SCHEMA_DIR / "event_envelope.json"
    with schema_path.open() as f:
        return json.load(f)


@pytest.fixture()
def sample_full_payload() -> dict:
    """A complete event envelope matching what EventOutboxService produces.

    All fields populated (non-null) to test the happy path.
    """
    item_id = str(uuid4())
    return {
        "event_id": str(uuid4()),
        "topic": "inventory-changes",
        "event_type": "CREATED",
        "entity_type": "stock_movement",
        "entity_id": str(uuid4()),
        "created_at": datetime.now(timezone.utc).isoformat(),
        "payload": {
            "product_id": item_id,
            "product_name": "Test Product",
            "sku": "TST-001",
            "item_id": item_id,
            "quantity_change": -3,
            "reason": "sale",
            "at": datetime.now(timezone.utc).isoformat(),
            "from_location_code": "B1",
            "to_location_code": None,
            "previous_location_qty": 10,
            "current_location_qty": 7,
            "previous_total_qty": 50,
            "current_total_qty": 47,
            "reorder_point": 20,
            "actor_id": str(uuid4()),
            "stock_movement_id": str(uuid4()),
        },
    }


@pytest.fixture()
def sample_minimal_payload() -> dict:
    """A minimal event envelope with only required fields.

    Tests that the consumer tolerates missing optional fields.
    """
    item_id = str(uuid4())
    return {
        "event_id": str(uuid4()),
        "payload": {
            "item_id": item_id,
            "quantity_change": -1,
            "at": datetime.now(timezone.utc).isoformat(),
        },
    }


@pytest.fixture()
def sample_null_optionals_payload() -> dict:
    """Event with all optional fields explicitly set to null.

    Tests that the consumer handles null values correctly.
    """
    item_id = str(uuid4())
    return {
        "event_id": str(uuid4()),
        "topic": "inventory-changes",
        "event_type": "CREATED",
        "entity_type": "stock_movement",
        "entity_id": str(uuid4()),
        "created_at": datetime.now(timezone.utc).isoformat(),
        "payload": {
            "product_id": item_id,
            "product_name": None,
            "sku": None,
            "item_id": item_id,
            "quantity_change": 5,
            "reason": None,
            "at": datetime.now(timezone.utc).isoformat(),
            "from_location_code": None,
            "to_location_code": None,
            "previous_location_qty": None,
            "current_location_qty": None,
            "previous_total_qty": None,
            "current_total_qty": None,
            "reorder_point": None,
            "actor_id": None,
            "stock_movement_id": None,
        },
    }
