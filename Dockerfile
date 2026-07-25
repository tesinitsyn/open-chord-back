FROM python:3.13-slim AS builder

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /build
COPY pyproject.toml README.md ./
COPY openchord ./openchord
RUN python -m venv /opt/venv \
    && /opt/venv/bin/pip install --upgrade pip \
    && /opt/venv/bin/pip install .

FROM python:3.13-slim

ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    DATABASE_URL="sqlite+aiosqlite:////tmp/openchord.db" \
    MEDIA_ROOT="/media"

RUN groupadd --system --gid 10001 openchord \
    && useradd --system --uid 10001 --gid openchord --home-dir /app openchord
WORKDIR /app
COPY --from=builder /opt/venv /opt/venv
COPY migrations ./migrations
RUN mkdir -p /media && chown openchord:openchord /media
USER openchord

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health/live')"]
CMD ["uvicorn", "openchord.app:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers"]
