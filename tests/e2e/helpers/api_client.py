"""HTTP client for inventory-service REST API during E2E tests."""

from datetime import datetime, timedelta, timezone
from typing import Any

import jwt
import requests

# E2E test endpoints
INVENTORY_SERVICE_URL = "http://localhost:4001"
FORECASTING_SERVICE_URL = "http://localhost:5011"

# JWT secret from application-test.properties / .env
# This must match the SUPABASE_JWT_SECRET configured in docker-compose.e2e.yml
JWT_SECRET = None  # Set dynamically from .env or override


def _load_jwt_secret() -> str:
    """Load JWT secret from .env file or use a fallback."""
    global JWT_SECRET
    if JWT_SECRET is not None:
        return JWT_SECRET

    import os
    from pathlib import Path

    # Try loading from .env
    env_path = Path(__file__).parent.parent.parent.parent / ".env"
    if env_path.exists():
        with env_path.open() as f:
            for line in f:
                line = line.strip()
                if line.startswith("SUPABASE_JWT_SECRET="):
                    JWT_SECRET = line.split("=", 1)[1].strip().strip('"').strip("'")
                    return JWT_SECRET

    raise RuntimeError(
        "Cannot determine JWT secret. Set SUPABASE_JWT_SECRET in .env "
        "or override api_client.JWT_SECRET in conftest.py"
    )


def make_admin_token() -> str:
    """Generate a valid admin JWT token for E2E test requests."""
    secret = _load_jwt_secret()
    payload = {
        "sub": "e2e-admin-id",
        "user_metadata": {"name": "E2E Admin", "role": "ADMIN"},
        "email": "e2e-admin@test.com",
        "iat": datetime.now(timezone.utc),
        "exp": datetime.now(timezone.utc) + timedelta(hours=1),
    }
    return jwt.encode(payload, secret, algorithm="HS256")


def make_employee_token() -> str:
    """Generate a valid employee JWT token for E2E test requests."""
    secret = _load_jwt_secret()
    payload = {
        "sub": "e2e-employee-id",
        "user_metadata": {"name": "E2E Employee", "role": "EMPLOYEE"},
        "email": "e2e-employee@test.com",
        "iat": datetime.now(timezone.utc),
        "exp": datetime.now(timezone.utc) + timedelta(hours=1),
    }
    return jwt.encode(payload, secret, algorithm="HS256")


def adjust_inventory(
    location_type: str,
    inventory_id: str,
    quantity_change: int,
    reason: str,
    actor_id: str | None = None,
    token: str | None = None,
) -> requests.Response:
    """POST /api/stock-movements/{locationType}/{inventoryId}/adjust."""
    if token is None:
        token = make_admin_token()

    body: dict[str, Any] = {
        "quantityChange": quantity_change,
        "reason": reason,
    }
    if actor_id:
        body["actorId"] = actor_id

    return requests.post(
        f"{INVENTORY_SERVICE_URL}/api/stock-movements/{location_type}/{inventory_id}/adjust",
        json=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
        timeout=10,
    )


def seed_all() -> requests.Response:
    """POST /api/dev/seed/all -- seed test data."""
    return requests.post(
        f"{INVENTORY_SERVICE_URL}/api/dev/seed/all",
        timeout=30,
    )


def health_check_inventory() -> bool:
    """Check if inventory-service is healthy."""
    try:
        resp = requests.get(
            f"{INVENTORY_SERVICE_URL}/actuator/health",
            timeout=5,
        )
        return resp.status_code == 200
    except requests.ConnectionError:
        return False


def health_check_forecasting() -> bool:
    """Check if forecasting-service is healthy."""
    try:
        resp = requests.get(
            f"{FORECASTING_SERVICE_URL}/health",
            timeout=5,
        )
        return resp.status_code == 200
    except requests.ConnectionError:
        return False
