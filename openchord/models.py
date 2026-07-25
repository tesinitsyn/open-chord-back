import uuid
from datetime import datetime

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class Artist(Base):
    __tablename__ = "artists"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    name: Mapped[str] = mapped_column(String(255), index=True)
    albums: Mapped[list["Album"]] = relationship(
        back_populates="artist", cascade="all, delete-orphan"
    )


class Album(Base):
    __tablename__ = "albums"
    __table_args__ = (CheckConstraint("release_year >= 1000 AND release_year <= 9999"),)

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    title: Mapped[str] = mapped_column(String(255), index=True)
    release_year: Mapped[int] = mapped_column(Integer)
    artwork_path: Mapped[str | None] = mapped_column(String(1024))
    artist_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("artists.id", ondelete="CASCADE"))
    artist: Mapped[Artist] = relationship(back_populates="albums")
    tracks: Mapped[list["Track"]] = relationship(
        back_populates="album",
        cascade="all, delete-orphan",
        order_by="Track.disc_number, Track.number",
    )


class Track(Base):
    __tablename__ = "tracks"
    __table_args__ = (
        CheckConstraint("duration_ms > 0"),
        CheckConstraint("disc_number > 0"),
        CheckConstraint("number > 0"),
        UniqueConstraint("album_id", "disc_number", "number"),
    )

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    title: Mapped[str] = mapped_column(String(255), index=True)
    duration_ms: Mapped[int] = mapped_column(Integer)
    disc_number: Mapped[int] = mapped_column(Integer, default=1)
    number: Mapped[int] = mapped_column(Integer)
    audio_path: Mapped[str] = mapped_column(String(1024), unique=True)
    content_type: Mapped[str] = mapped_column(String(127))
    album_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("albums.id", ondelete="CASCADE"))
    album: Mapped[Album] = relationship(back_populates="tracks")
    lyrics: Mapped[list["LyricLine"]] = relationship(
        back_populates="track", cascade="all, delete-orphan", order_by="LyricLine.start_ms"
    )


class LyricLine(Base):
    __tablename__ = "lyric_lines"
    __table_args__ = (
        CheckConstraint("start_ms >= 0"),
        CheckConstraint("end_ms > start_ms"),
        UniqueConstraint("track_id", "start_ms"),
    )

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    text: Mapped[str] = mapped_column(Text)
    start_ms: Mapped[int] = mapped_column(Integer)
    end_ms: Mapped[int] = mapped_column(Integer)
    track_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("tracks.id", ondelete="CASCADE"))
    track: Mapped[Track] = relationship(back_populates="lyrics")


class PlaybackEvent(Base):
    __tablename__ = "playback_events"
    __table_args__ = (
        CheckConstraint("position_ms >= 0"),
        Index("ix_playback_events_played_at", "played_at"),
    )

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    track_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("tracks.id", ondelete="CASCADE"), index=True
    )
    played_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    position_ms: Mapped[int] = mapped_column(Integer)
    completed: Mapped[bool] = mapped_column(default=False)
