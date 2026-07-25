from pathlib import Path

from fastapi import FastAPI

from openchord.models import Album, Artist, LyricLine, Track


async def seed_catalog(app: FastAPI) -> tuple[Album, Track]:
    audio_path = Path(app.state.settings.media_root) / "tracks" / "night-drive.m4a"
    audio_path.parent.mkdir(parents=True)
    audio_path.write_bytes(b"0123456789")
    artist = Artist(name="Aurora Lines")
    album = Album(title="Afterglow", release_year=2026, artist=artist)
    track = Track(
        title="Night Drive",
        duration_ms=96_000,
        disc_number=1,
        number=1,
        audio_path="tracks/night-drive.m4a",
        content_type="audio/mp4",
        album=album,
    )
    track.lyrics = [LyricLine(text="Streetlights drawing silver lines", start_ms=0, end_ms=8000)]
    async with app.state.database.session() as session:
        session.add(album)
        await session.commit()
        await session.refresh(album)
        await session.refresh(track)
    return album, track
