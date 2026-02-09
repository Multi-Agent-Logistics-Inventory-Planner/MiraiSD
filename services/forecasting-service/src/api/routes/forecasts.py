from __future__ import annotations

import logging
from fastapi import APIRouter, HTTPException

# Configure logging
logger = logging.getLogger(__name__)

router = APIRouter()


@router.post("/run")
def run_forecast_job(
    from_ts: str | None = None,
    to_ts: str | None = None,
    method: str = "ma14",
    target_days: int | None = None,
) -> dict:
    """
    Run the batch forecast job and return the output path.

    Security: This endpoint implements structured error handling to prevent
    information leakage. Detailed errors are logged server-side, while clients
    receive generic error messages.

    Args:
        from_ts: Start timestamp for forecast period (optional)
        to_ts: End timestamp for forecast period (optional)
        method: Forecasting method to use (default: "ma14")
        target_days: Number of days to forecast (optional)

    Returns:
        dict: Contains "out_path" with the forecast output file path

    Raises:
        HTTPException: 400 for validation errors, 500 for internal errors
    """
    try:
        # Import inside handler to avoid heavy imports at API startup time.
        from ...forecast_job import run_batch

        out_path = run_batch(
            from_ts=from_ts,
            to_ts=to_ts,
            method=method,
            target_days=target_days
        )
        return {"out_path": str(out_path)}

    except ValueError as e:
        # Value errors are typically input validation issues - safe to show user
        logger.warning(f"Validation error in forecast job: {str(e)}")
        raise HTTPException(
            status_code=400,
            detail={
                "error": "Invalid input parameters",
                "code": "VALIDATION_ERROR"
            }
        )

    except (ConnectionError, OSError) as e:
        # Database/network connection errors - log details, return generic message
        logger.error(f"Database error in forecast job: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail={
                "error": "Forecast calculation failed",
                "code": "DATABASE_ERROR"
            }
        )

    except (ImportError, ModuleNotFoundError) as e:
        # Import errors - log details, return generic message
        logger.error(f"Module import error in forecast job: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail={
                "error": "An unexpected error occurred",
                "code": "INTERNAL_ERROR"
            }
        )

    except FileNotFoundError as e:
        # File system errors - log details, return generic message
        logger.error(f"File not found in forecast job: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail={
                "error": "Forecast calculation failed",
                "code": "FILE_ERROR"
            }
        )

    except Exception as e:
        # Catch-all for unexpected errors - log details, return generic message
        logger.error(f"Unexpected error in forecast job: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail={
                "error": "An unexpected error occurred",
                "code": "INTERNAL_ERROR"
            }
        )

