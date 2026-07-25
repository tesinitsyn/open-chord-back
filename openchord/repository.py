import uuid
from datetime import datetime
from typing import cast

from sqlalchemy import Select, desc, func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import joinedload, selectinload
from sqlalchemy.orm.interfaces import ORMOption

from openchord.models import Album, Artist, PlaybackEvent, Track


def album_graph() -> tuple[ORMOption, ...]:
    return (
        joinedload(Album.artist),
        selectinload(Album.tracks).selectinload(Track.lyrics),
    )


class CatalogRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def albums(self, search: str | None, limit: int, offset: int) -> list[Album]:
        query: Select[tuple[Album]] = select(Album).options(*album_graph())
        if search:
            pattern = f"%{search.strip()}%"
            query = query.join(Album.artist).where(
                or_(Album.title.ilike(pattern), Artist.name.ilike(pattern))
            )
        result = await self.session.scalars(
            query.order_by(desc(Album.release_year), Album.title).offset(offset).limit(limit)
        )
        return list(result.unique())

    async def album(self, album_id: uuid.UUID) -> Album | None:
        return cast(
            Album | None,
            await self.session.scalar(
                select(Album).options(*album_graph()).where(Album.id == album_id)
            ),
        )

    async def track(self, track_id: uuid.UUID) -> Track | None:
        return cast(
            Track | None,
            await self.session.scalar(
                select(Track)
                .options(
                    joinedload(Track.album).joinedload(Album.artist), selectinload(Track.lyrics)
                )
                .where(Track.id == track_id)
            ),
        )

    async def recently_played(self, limit: int) -> list[Album]:
        latest = (
            select(
                Track.album_id.label("album_id"), func.max(PlaybackEvent.played_at).label("latest")
            )
            .join(PlaybackEvent, PlaybackEvent.track_id == Track.id)
            .group_by(Track.album_id)
            .subquery()
        )
        result = await self.session.scalars(
            select(Album)
            .join(latest, latest.c.album_id == Album.id)
            .options(*album_graph())
            .order_by(desc(latest.c.latest))
            .limit(limit)
        )
        return list(result.unique())

    async def record_playback(
        self, track: Track, played_at: datetime, position_ms: int, completed: bool
    ) -> PlaybackEvent:
        event = PlaybackEvent(
            track_id=track.id,
            played_at=played_at,
            position_ms=min(position_ms, track.duration_ms),
            completed=completed,
        )
        self.session.add(event)
        await self.session.commit()
        await self.session.refresh(event)
        return event
