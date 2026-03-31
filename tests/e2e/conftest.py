"""E2E test fixtures: Docker lifecycle, health checks, data seeding, DB connection."""

import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import pytest

from helpers.api_client import health_check_forecasting, health_check_inventory, seed_all
from helpers.db_client import create_e2e_engine, find_test_inventory
from helpers.wait import WaitTimeout, wait_for_condition

COMPOSE_FILE = Path(__file__).parent / "docker-compose.e2e.yml"


@pytest.fixture(scope="session")
def docker_compose_up():
    """Start the E2E Docker stack. Tears down after all tests complete.

    This is session-scoped: the stack is started once and shared across all tests.
    """
    compose_cmd = [
        "docker", "compose",
        "-f", str(COMPOSE_FILE),
        "up", "-d", "--build", "--wait",
    ]

    print(f"\nStarting E2E stack: {' '.join(compose_cmd)}")
    result = subprocess.run(
        compose_cmd,
        capture_output=True,
        text=True,
        timeout=300,
    )

    if result.returncode != 0:
        print(f"Docker compose up failed:\n{result.stderr}")
        pytest.fail(f"Failed to start E2E stack: {result.stderr[-500:]}")

    yield

    # Teardown
    print("\nTearing down E2E stack...")
    subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE_FILE), "down", "-v"],
        capture_output=True,
        timeout=60,
    )


@pytest.fixture(scope="session")
def wait_for_services(docker_compose_up):
    """Wait for all services to be healthy before running tests."""
    try:
        wait_for_condition(
            health_check_inventory,
            timeout_seconds=120,
            poll_interval=2,
            description="inventory-service health",
        )
    except WaitTimeout:
        pytest.fail("inventory-service did not become healthy within 120s")

    try:
        wait_for_condition(
            health_check_forecasting,
            timeout_seconds=120,
            poll_interval=2,
            description="forecasting-service health",
        )
    except WaitTimeout:
        pytest.fail("forecasting-service did not become healthy within 120s")


@pytest.fixture(scope="session")
def db_engine(wait_for_services):
    """Create a SQLAlchemy engine for direct DB assertions."""
    engine = create_e2e_engine()
    yield engine
    engine.dispose()


@pytest.fixture(scope="session")
def seed_data(wait_for_services):
    """Seed test data via the inventory-service dev endpoint."""
    resp = seed_all()
    if resp.status_code >= 400:
        pytest.fail(f"Failed to seed data: {resp.status_code} {resp.text[:200]}")
    # Give the DB a moment to settle
    time.sleep(1)
    return resp


@pytest.fixture(scope="session")
def test_inventory_record(db_engine, seed_data):
    """Find a location_inventory record suitable for test adjustments.

    Returns dict with: inventory_id, quantity, product_id, product_name, sku
    """
    record = find_test_inventory(db_engine)
    if record is None:
        pytest.fail("Seed data must include location_inventory with quantity > 5")
    return record


@pytest.fixture()
def test_start_time():
    """Record the current UTC time for filtering results created during the test."""
    return datetime.now(timezone.utc)
