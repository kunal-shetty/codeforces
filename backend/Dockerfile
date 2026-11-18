# Build from the repo root so the shared lesson content is included:
#   docker build -f backend/Dockerfile -t upsolve-api .
FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /srv/backend

COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY backend/ .
COPY content/ /srv/content/
ENV CONTENT_DIR=/srv/content

RUN useradd --create-home api
USER api
EXPOSE 8000

# Apply migrations, then serve. Neon is reached over TLS; nothing else is needed in the image.
CMD ["sh", "-c", "alembic upgrade head && uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000} --proxy-headers"]
