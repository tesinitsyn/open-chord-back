import uuid
from datetime import UTC, datetime

import strawberry
from fastapi import Request
from graphql import GraphQLError
from strawberry.fastapi import BaseContext, GraphQLRouter

from openchord.config import Settings
from openchord.database import Database
from openchord.models import Album, LyricLine, PlaybackEvent, Track
from openchord.repository import CatalogRepository


@strawberry.type
class ArtistType:
    id: strawberry.ID
    name: str


@strawberry.type
class LyricLineType:
    id: strawberry.ID
    text: str
    start_ms: int
    end_ms: int

    @classmethod
    def from_model(cls, line: LyricLine) -> "LyricLineType":
        return cls(
            id=strawberry.ID(str(line.id)),
            text=line.text,
            start_ms=line.start_ms,
            end_ms=line.end_ms,
        )


@strawberry.type
class TrackType:
    id: strawberry.ID
    title: str
    duration_ms: int
    disc_number: int
    number: int
    artist_name: str
    album_title: str
    stream_url: str
    lyrics: list[LyricLineType]

    @classmethod
    def from_model(
        cls, track: Track, settings: Settings, artist_name: str, album_title: str
    ) -> "TrackType":
        return cls(
            id=strawberry.ID(str(track.id)),
            title=track.title,
            duration_ms=track.duration_ms,
            disc_number=track.disc_number,
            number=track.number,
            artist_name=artist_name,
            album_title=album_title,
            stream_url=f"{settings.public_base_url}/media/tracks/{track.id}",
            lyrics=[LyricLineType.from_model(line) for line in track.lyrics],
        )


@strawberry.type
class AlbumType:
    id: strawberry.ID
    title: str
    year: int
    artwork_url: str | None
    artist: ArtistType
    tracks: list[TrackType]

    @classmethod
    def from_model(cls, album: Album, settings: Settings) -> "AlbumType":
        artwork_url = (
            f"{settings.public_base_url}/media/artwork/{album.id}" if album.artwork_path else None
        )
        return cls(
            id=strawberry.ID(str(album.id)),
            title=album.title,
            year=album.release_year,
            artwork_url=artwork_url,
            artist=ArtistType(id=strawberry.ID(str(album.artist.id)), name=album.artist.name),
            tracks=[
                TrackType.from_model(track, settings, album.artist.name, album.title)
                for track in album.tracks
            ],
        )


@strawberry.type
class PlaybackEventType:
    id: strawberry.ID
    track_id: strawberry.ID
    played_at: datetime
    position_ms: int
    completed: bool

    @classmethod
    def from_model(cls, event: PlaybackEvent) -> "PlaybackEventType":
        return cls(
            id=strawberry.ID(str(event.id)),
            track_id=strawberry.ID(str(event.track_id)),
            played_at=event.played_at,
            position_ms=event.position_ms,
            completed=event.completed,
        )


@strawberry.input
class PlaybackEventInput:
    track_id: strawberry.ID
    position_ms: int
    completed: bool = False
    played_at: datetime | None = None


@strawberry.type
class Query:
    @strawberry.field
    async def albums(
        self,
        info: strawberry.Info["Context", None],
        search: str | None = None,
        limit: int = 50,
        offset: int = 0,
    ) -> list[AlbumType]:
        limit = max(1, min(limit, 100))
        offset = max(0, offset)
        async with info.context.database.session() as session:
            albums = await CatalogRepository(session).albums(search, limit, offset)
        return [AlbumType.from_model(album, info.context.settings) for album in albums]

    @strawberry.field
    async def album(
        self, info: strawberry.Info["Context", None], id: strawberry.ID
    ) -> AlbumType | None:
        album_id = parse_id(id)
        async with info.context.database.session() as session:
            album = await CatalogRepository(session).album(album_id)
        return AlbumType.from_model(album, info.context.settings) if album else None

    @strawberry.field
    async def recently_played(
        self, info: strawberry.Info["Context", None], limit: int = 10
    ) -> list[AlbumType]:
        async with info.context.database.session() as session:
            albums = await CatalogRepository(session).recently_played(max(1, min(limit, 50)))
        return [AlbumType.from_model(album, info.context.settings) for album in albums]


@strawberry.type
class Mutation:
    @strawberry.mutation
    async def record_playback(
        self, info: strawberry.Info["Context", None], input: PlaybackEventInput
    ) -> PlaybackEventType:
        if input.position_ms < 0:
            raise GraphQLError(
                "positionMs must be non-negative", extensions={"code": "BAD_USER_INPUT"}
            )
        track_id = parse_id(input.track_id)
        async with info.context.database.session() as session:
            repository = CatalogRepository(session)
            track = await repository.track(track_id)
            if track is None:
                raise GraphQLError("Track not found", extensions={"code": "NOT_FOUND"})
            event = await repository.record_playback(
                track, input.played_at or datetime.now(UTC), input.position_ms, input.completed
            )
        return PlaybackEventType.from_model(event)


class Context(BaseContext):
    def __init__(self, database: Database, settings: Settings) -> None:
        self.database = database
        self.settings = settings


async def context_getter(request: Request) -> Context:
    return Context(request.app.state.database, request.app.state.settings)


def parse_id(value: strawberry.ID) -> uuid.UUID:
    try:
        return uuid.UUID(str(value))
    except ValueError as error:
        raise GraphQLError("Invalid ID", extensions={"code": "BAD_USER_INPUT"}) from error


schema = strawberry.Schema(query=Query, mutation=Mutation)
graphql_router: GraphQLRouter[Context, None] = GraphQLRouter(
    schema,
    context_getter=context_getter,  # type: ignore[arg-type]
    graphql_ide=None,
)
