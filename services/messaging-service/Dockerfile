FROM python:3.11-slim

WORKDIR /app

# System deps
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd --system --gid 1001 appgroup && \
    useradd --system --uid 1001 --gid appgroup appuser

COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY --chown=appuser:appgroup . /app

# Switch to non-root user
USER appuser

# Expose port for optional API mode
EXPOSE 5001

# Default: run worker for real-time event processing
# Override with: ["uvicorn", "src.api.main:app", "--host", "0.0.0.0", "--port", "5001"]
CMD ["python", "-m", "src.worker"]

