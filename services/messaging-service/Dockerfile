FROM python:3.11.11-slim AS builder

WORKDIR /app

# System deps for building Python packages
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Create virtual environment
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Install dependencies into venv
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

FROM python:3.11.11-slim AS runner

WORKDIR /app

# Create non-root user
RUN groupadd --system --gid 1001 appgroup && \
    useradd --system --uid 1001 --gid appgroup appuser

# Copy virtual environment from builder
COPY --from=builder /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Copy application code
COPY --chown=appuser:appgroup . /app

# Switch to non-root user
USER appuser

# Expose port for optional API mode
EXPOSE 5001

# Default: run worker for real-time event processing
# Override with: ["uvicorn", "src.api.main:app", "--host", "0.0.0.0", "--port", "5001"]
CMD ["python", "-m", "src.worker"]
