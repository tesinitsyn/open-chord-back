# OpenChord server

Self-hosted backend for the OpenChord Apple client. It owns the music catalog,
synchronized lyrics, playback history and byte-range media delivery.

## API

- `POST /graphql` — catalog queries and playback mutations.
- `GET /media/tracks/{id}` — audio with standard single-range HTTP requests.
- `GET /media/artwork/{id}` — album artwork.
- `GET /health/live` — process liveness.
- `GET /health/ready` — database readiness.

GraphQL introspection remains enabled for tooling, while the browser GraphiQL UI
is disabled. The schema exposes `albums`, `album`, `recentlyPlayed` and
`recordPlayback`. Client-facing durations and lyric timestamps use integer
milliseconds.

Example:

```graphql
query Library {
  albums(limit: 50) {
    id
    title
    year
    artworkUrl
    artist { id name }
    tracks {
      id
      title
      durationMs
      streamUrl
      lyrics { id text startMs endMs }
    }
  }
}
```

## Local development

Python 3.11 or newer is required.

```sh
python3 -m venv .venv
.venv/bin/pip install -e '.[dev]'
AUTO_MIGRATE=true .venv/bin/uvicorn openchord.app:app --reload
```

`AUTO_MIGRATE` is intended for local development and tests. Production uses the
versioned SQL migrations in `migrations/`; the Compose PostgreSQL container
applies them when its data volume is first created.

Run the quality gates:

```sh
.venv/bin/ruff format --check .
.venv/bin/ruff check .
.venv/bin/mypy
.venv/bin/pytest
```

## Self-hosting

1. Copy audio under `media/` and insert catalog records using paths relative to
   that directory.
2. Set `PUBLIC_BASE_URL` to the externally reachable HTTPS origin.
3. Run `docker compose up --build`.

The API container is unprivileged and the media mount is read-only. Put the
service behind a TLS reverse proxy before exposing it outside a trusted network.
Authentication and catalog ingestion are intentionally not represented as
finished features in this initial contract.

## Data model

Artists own albums; albums own ordered tracks; tracks own timestamped lyric
lines. Playback events are append-only and drive the recent-albums query.
Deleting an owner cascades to its dependent catalog records.

