import re
import uuid
from pathlib import Path

from fastapi import APIRouter, HTTPException, Request, status
from fastapi.responses import FileResponse, Response

from openchord.models import Album, Track

router = APIRouter(prefix="/media", tags=["media"])
RANGE_PATTERN = re.compile(r"bytes=(\d*)-(\d*)$")


def resolve_media_path(media_root: Path, stored_path: str) -> Path:
    root = media_root.resolve()
    candidate = (root / stored_path).resolve()
    if not candidate.is_relative_to(root):
        raise HTTPException(status.HTTP_404_NOT_FOUND)
    return candidate


@router.get("/tracks/{track_id}")
async def stream_track(track_id: uuid.UUID, request: Request) -> Response:
    async with request.app.state.database.session() as session:
        track = await session.get(Track, track_id)
    if track is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Track not found")

    path = resolve_media_path(request.app.state.settings.media_root, track.audio_path)
    if not path.is_file():
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Audio file unavailable")
    return ranged_file_response(path, track.content_type, request.headers.get("range"))


@router.get("/artwork/{album_id}", response_class=FileResponse)
async def album_artwork(album_id: uuid.UUID, request: Request) -> FileResponse:
    async with request.app.state.database.session() as session:
        album = await session.get(Album, album_id)
    if album is None or album.artwork_path is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Artwork not found")
    path = resolve_media_path(request.app.state.settings.media_root, album.artwork_path)
    if not path.is_file():
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Artwork file unavailable")
    return FileResponse(path, headers={"Cache-Control": "public, max-age=86400"})


def ranged_file_response(path: Path, content_type: str, range_header: str | None) -> Response:
    size = path.stat().st_size
    common_headers = {
        "Accept-Ranges": "bytes",
        "Cache-Control": "private, max-age=3600",
    }
    if range_header is None:
        return FileResponse(path, media_type=content_type, headers=common_headers)

    match = RANGE_PATTERN.fullmatch(range_header.strip())
    if match is None:
        return Response(
            status_code=status.HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE,
            headers={"Content-Range": f"bytes */{size}"},
        )

    start_text, end_text = match.groups()
    if not start_text and not end_text:
        return Response(
            status_code=status.HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE,
            headers={"Content-Range": f"bytes */{size}"},
        )
    if start_text:
        start = int(start_text)
        end = min(int(end_text), size - 1) if end_text else size - 1
    else:
        suffix_length = min(int(end_text), size)
        start, end = size - suffix_length, size - 1
    if start >= size or start > end:
        return Response(
            status_code=status.HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE,
            headers={"Content-Range": f"bytes */{size}"},
        )

    length = end - start + 1
    with path.open("rb") as media:
        media.seek(start)
        body = media.read(length)
    return Response(
        body,
        status_code=status.HTTP_206_PARTIAL_CONTENT,
        media_type=content_type,
        headers={
            **common_headers,
            "Content-Range": f"bytes {start}-{end}/{size}",
            "Content-Length": str(length),
        },
    )
