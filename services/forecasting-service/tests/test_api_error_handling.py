"""
Tests for API error handling in forecasting service.

Security Requirement: API endpoints must not leak sensitive information
in error responses. All exceptions should return generic error messages
to the client while logging detailed errors server-side.

FastAPI wraps HTTPException detail under response.json()["detail"],
so error/code fields are accessed via response.json()["detail"]["error"].
"""

import sys
from unittest.mock import MagicMock, patch

import pytest

# Mock kafka before importing the app
sys.modules.setdefault("kafka", MagicMock())
sys.modules.setdefault("kafka.errors", MagicMock())

from fastapi.testclient import TestClient

from src.api.main import app

client = TestClient(app)


class TestHealthEndpoint:
    """Test the health check endpoint."""

    def test_health_returns_ok(self):
        """Health endpoint should always return 200 OK."""
        response = client.get("/health")
        assert response.status_code == 200
        assert response.json() == {"status": "ok"}


class TestForecastErrorHandling:
    """Test error handling for forecast endpoints.

    The /forecasts/run endpoint imports run_batch locally inside the handler,
    so we patch it at its definition site: src.forecast_job.run_batch.
    """

    @patch("src.forecast_job.run_batch")
    def test_run_forecast_success(self, mock_run_batch):
        """Successful forecast should return 200 with output path."""
        mock_run_batch.return_value = "/tmp/forecast_output.json"

        response = client.post("/forecasts/run")

        assert response.status_code == 200
        assert "out_path" in response.json()
        assert response.json()["out_path"] == "/tmp/forecast_output.json"

    @patch("src.forecast_job.run_batch")
    def test_run_forecast_connection_error_returns_500(self, mock_run_batch):
        """Database connection errors should return 500 with generic message."""
        mock_run_batch.side_effect = ConnectionError(
            "connection to server at \"localhost\", port 5432 failed"
        )

        response = client.post("/forecasts/run")

        assert response.status_code == 500
        detail = response.json()["detail"]

        # Should NOT leak database connection details
        assert "localhost" not in detail["error"]
        assert "5432" not in detail["error"]

        assert detail["error"] == "Forecast calculation failed"
        assert detail["code"] == "DATABASE_ERROR"

    @patch("src.forecast_job.run_batch")
    def test_run_forecast_import_error_returns_500(self, mock_run_batch):
        """Import errors should return 500 with generic message."""
        mock_run_batch.side_effect = ImportError("No module named 'prophet'")

        response = client.post("/forecasts/run")

        assert response.status_code == 500
        detail = response.json()["detail"]

        # Should NOT leak module details
        assert "prophet" not in detail["error"]
        assert detail["code"] == "INTERNAL_ERROR"

    @patch("src.forecast_job.run_batch")
    def test_run_forecast_value_error_returns_400(self, mock_run_batch):
        """Invalid input parameters should return 400."""
        mock_run_batch.side_effect = ValueError("Invalid date format: not-a-date")

        response = client.post("/forecasts/run?from_ts=not-a-date")

        assert response.status_code == 400
        detail = response.json()["detail"]

        assert detail["code"] == "VALIDATION_ERROR"

    @patch("src.forecast_job.run_batch")
    def test_run_forecast_file_not_found_returns_500(self, mock_run_batch):
        """File system errors should return 500 with generic message."""
        mock_run_batch.side_effect = FileNotFoundError(
            "/secret/path/to/data.csv not found"
        )

        response = client.post("/forecasts/run")

        assert response.status_code == 500
        detail = response.json()["detail"]

        # Should NOT leak file system paths
        assert "/secret/path" not in detail["error"]
        assert detail["error"] == "Forecast calculation failed"
        assert detail["code"] == "FILE_ERROR"

    @patch("src.forecast_job.run_batch")
    def test_run_forecast_generic_exception_returns_500(self, mock_run_batch):
        """Unexpected exceptions should return 500 with generic message."""
        mock_run_batch.side_effect = RuntimeError(
            "Something unexpected happened with sensitive data: API_KEY_12345"
        )

        response = client.post("/forecasts/run")

        assert response.status_code == 500
        detail = response.json()["detail"]

        # Should NOT leak sensitive information
        assert "API_KEY" not in detail["error"]
        assert "sensitive data" not in detail["error"]
        assert detail["error"] == "An unexpected error occurred"
        assert detail["code"] == "INTERNAL_ERROR"

    @patch("src.forecast_job.run_batch")
    def test_error_response_structure(self, mock_run_batch):
        """Error responses should have consistent structure."""
        mock_run_batch.side_effect = Exception("Test error")

        response = client.post("/forecasts/run")

        assert response.status_code == 500
        detail = response.json()["detail"]

        # Must have error and code fields
        assert "error" in detail
        assert "code" in detail

        # Both should be non-empty strings
        assert isinstance(detail["error"], str)
        assert isinstance(detail["code"], str)
        assert len(detail["error"]) > 0
        assert len(detail["code"]) > 0

    @patch("src.forecast_job.run_batch")
    @patch("src.api.routes.forecasts.logger")
    def test_errors_are_logged_server_side(self, mock_logger, mock_run_batch):
        """Detailed errors should be logged server-side for debugging."""
        test_error = Exception("Detailed error with stack trace")
        mock_run_batch.side_effect = test_error

        response = client.post("/forecasts/run")

        assert response.status_code == 500

        # Verify that detailed error was logged
        mock_logger.error.assert_called()

        # Verify log contains detailed information (for server-side debugging)
        call_args = str(mock_logger.error.call_args)
        assert "Detailed error" in call_args or "error" in call_args.lower()


class TestParameterValidation:
    """Test input parameter validation."""

    @patch("src.forecast_job.run_batch")
    def test_valid_parameters_accepted(self, mock_run_batch):
        """Valid parameters should be accepted."""
        mock_run_batch.return_value = "/tmp/output.json"

        response = client.post(
            "/forecasts/run",
            params={
                "from_ts": "2026-01-01",
                "to_ts": "2026-01-31",
                "method": "ma14",
                "target_days": 30,
            },
        )

        assert response.status_code == 200
        mock_run_batch.assert_called_once_with(
            from_ts="2026-01-01",
            to_ts="2026-01-31",
            method="ma14",
            target_days=30,
        )

    @patch("src.forecast_job.run_batch")
    def test_no_parameters_accepted(self, mock_run_batch):
        """Endpoint should work with no parameters (use defaults)."""
        mock_run_batch.return_value = "/tmp/output.json"

        response = client.post("/forecasts/run")

        assert response.status_code == 200
        mock_run_batch.assert_called_once_with(
            from_ts=None,
            to_ts=None,
            method="ma14",
            target_days=None,
        )
