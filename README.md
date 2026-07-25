# OpenChord server

Java 21 / Spring Boot backend for the self-hosted OpenChord music client. It owns
the catalog, synchronized lyrics, playback history and AVPlayer-compatible
media delivery.

## API

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

For development against a local PostgreSQL:

```sh
./mvnw spring-boot:run
```

Flyway applies the versioned migrations under
`src/main/resources/db/migration`. Audio and artwork paths stored in the
database are relative to `MEDIA_ROOT`; paths escaping that root are rejected.

## Quality gates

```sh
./mvnw spotless:check
./mvnw -DskipTests compile
./mvnw test
docker build -t openchord-back:local .
```

GitHub Actions reports formatting, static analysis, tests and container build
as separate checks. The runtime image is unprivileged and Compose mounts media
read-only.

Authentication and catalog ingestion are explicit follow-up capabilities; this
initial service establishes the client-facing catalog/playback contract and
self-hosting runtime without claiming those features are complete.
