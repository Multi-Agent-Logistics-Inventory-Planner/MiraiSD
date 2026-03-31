"""Polling utilities for async E2E test assertions."""

import time
from typing import Any, Callable, TypeVar

T = TypeVar("T")


class WaitTimeout(Exception):
    """Raised when a wait condition is not met within the timeout."""


def wait_for_condition(
    check_fn: Callable[[], Any],
    timeout_seconds: float = 30.0,
    poll_interval: float = 0.5,
    description: str = "condition",
) -> Any:
    """Poll check_fn() until it returns a truthy value or timeout is reached.

    Args:
        check_fn: Callable that returns a truthy value when the condition is met.
        timeout_seconds: Maximum time to wait.
        poll_interval: Time between polls.
        description: Human-readable description for error messages.

    Returns:
        The truthy return value from check_fn.

    Raises:
        WaitTimeout: If the condition is not met within timeout_seconds.
    """
    deadline = time.monotonic() + timeout_seconds
    last_error = None

    while time.monotonic() < deadline:
        try:
            result = check_fn()
            if result:
                return result
        except Exception as e:
            last_error = e

        time.sleep(poll_interval)

    error_msg = f"Timed out waiting for {description} after {timeout_seconds}s"
    if last_error:
        error_msg += f" (last error: {last_error})"
    raise WaitTimeout(error_msg)
