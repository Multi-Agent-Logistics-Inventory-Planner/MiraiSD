from __future__ import annotations

import logging
from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="messaging-service",
    version="0.1.0",
    # Disable docs in production to reduce attack surface
    docs_url="/docs" if __debug__ else None,
    redoc_url="/redoc" if __debug__ else None
)


# Global exception handler for unhandled exceptions
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """
    Global exception handler to prevent information leakage.

    Security: Catches all unhandled exceptions and returns generic error
    messages to clients while logging detailed errors server-side.
    """
    # Log detailed error for server-side debugging
    logger.error(
        f"Unhandled exception in {request.method} {request.url.path}: {str(exc)}",
        exc_info=True
    )

    # Return generic error to client
    return JSONResponse(
        status_code=500,
        content={
            "error": "An internal error occurred",
            "code": "INTERNAL_ERROR"
        }
    )


# Handler for validation errors (FastAPI/Pydantic)
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """
    Handler for request validation errors.

    Security: Validation errors can be safely shown to users as they
    indicate client-side input issues, not server-side problems.
    """
    logger.warning(
        f"Validation error in {request.method} {request.url.path}: {exc.errors()}"
    )

    return JSONResponse(
        status_code=422,
        content={
            "error": "Invalid request parameters",
            "code": "VALIDATION_ERROR",
            "details": exc.errors()
        }
    )


# Handler for HTTP exceptions (raised explicitly in code)
@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    """
    Handler for HTTP exceptions raised by endpoints.

    These are already formatted correctly, so we just pass them through
    with consistent logging.
    """
    if exc.status_code >= 500:
        logger.error(
            f"HTTP {exc.status_code} in {request.method} {request.url.path}: {exc.detail}"
        )
    elif exc.status_code >= 400:
        logger.warning(
            f"HTTP {exc.status_code} in {request.method} {request.url.path}: {exc.detail}"
        )

    return JSONResponse(
        status_code=exc.status_code,
        content={"detail": exc.detail}
    )


@app.get("/health")
def health() -> dict:
    """
    Health check endpoint.

    Returns:
        dict: Status indicating service is running
    """
    return {"status": "ok"}

