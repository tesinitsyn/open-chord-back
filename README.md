# OpenChord server

Java 21 / Spring Boot backend for the self-hosted OpenChord music client. It owns
the catalog, synchronized lyrics, playback history and AVPlayer-compatible
media delivery.

## API

- `GET /admin/` — separate React admin for uploading tracks, artwork and synchronized lyrics.
- `POST /api/admin/imports/analyze` — stage an album folder and inspect embedded metadata.
- `POST /api/admin/imports/{id}/commit` — import the reviewed album; FLAC/WAV/AIFF sources become ALAC.
- `POST /graphql` — catalog queries and playback mutations.
- `GET /media/tracks/{id}` — audio with single-range HTTP support.
- `GET /media/artwork/{id}` — album artwork.
- `GET /actuator/health/liveness` — process liveness.
- `GET /actuator/health/readiness` — database readiness.

Durations and lyric timestamps are integer milliseconds. The GraphQL schema is
kept in `src/main/resources/graphql/schema.graphqls`.

## Run locally

Java 21 is required.

```sh
docker compose up --build
```

For an iPhone on the same network, the helper detects the host's LAN address,
configures media URLs and prints the address to enter in the app:

```sh
./scripts/lan-up.sh
```

If automatic detection is unavailable, provide the address explicitly:

```sh
OPENCHORD_LAN_IP=192.168.1.20 ./scripts/lan-up.sh
```

Compose starts both the API and PostgreSQL 17, waits for the database health
check, persists database state in a named volume and mounts `./media` for catalog uploads.
The database is also exposed on `${POSTGRES_PORT:-5432}` for local tooling.
The first two Flyway migrations create the schema and install a small demo
catalog whose audio is stored under `media/demo`.

For development with the application running directly on the host:

```sh
docker compose up -d database
./mvnw spring-boot:run
```

Flyway applies the versioned migrations under
`src/main/resources/db/migration`. Audio and artwork paths stored in the
database are relative to `MEDIA_ROOT`; paths escaping that root are rejected.

## Quality gates

```sh
./mvnw spotless:check
./mvnw -DskipTests compile
./mvnw test # starts an isolated PostgreSQL 17 container
docker build -t openchord-back:local .
```

GitHub Actions reports formatting, static analysis, tests and container build
as separate checks. Integration tests use Testcontainers against real
PostgreSQL rather than an in-memory database emulation. The runtime image is
unprivileged. Compose mounts `./media` read-write because the administration
workflows stage uploads and create managed media there.

The admin interface is intended for a trusted private network. Put the server behind
authenticated access before exposing it to the public internet.

## Smart album import

The admin accepts a folder containing audio and optional cover artwork. FFprobe reads
embedded artist, album, year, disc, track, title and duration metadata before anything
is added to the catalog. The review screen exposes inconsistencies and allows
corrections. On commit, lossless FLAC/WAV/AIFF sources are converted to ALAC in an M4A
container; already compatible compressed sources are kept as-is.

The database changes made by commit are transactional. Media copies and FFmpeg output
are filesystem operations and therefore are not rolled back with the database
transaction. Failed imports may leave unreferenced files under `MEDIA_ROOT`; see
[`docs/architecture.md`](docs/architecture.md) for the complete consistency and
security boundaries.

## Developer documentation

- [`docs/architecture.md`](docs/architecture.md) — module responsibilities, domain
  invariants, request flows, transaction boundaries and operational caveats.
- [`src/main/resources/graphql/schema.graphqls`](src/main/resources/graphql/schema.graphqls)
  — authoritative public GraphQL contract.
- [`src/main/resources/db/migration`](src/main/resources/db/migration) — authoritative
  database schema and seed history.
