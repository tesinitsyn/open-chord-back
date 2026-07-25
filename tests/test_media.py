from httpx import AsyncClient

from tests.factories import seed_catalog


async def test_audio_supports_http_range_requests(client: AsyncClient) -> None:
    _, track = await seed_catalog(client._transport.app)

    response = await client.get(f"/media/tracks/{track.id}", headers={"Range": "bytes=2-5"})

    assert response.status_code == 206
    assert response.content == b"2345"
    assert response.headers["content-range"] == "bytes 2-5/10"
    assert response.headers["accept-ranges"] == "bytes"


async def test_unsatisfiable_range_returns_416(client: AsyncClient) -> None:
    _, track = await seed_catalog(client._transport.app)

    response = await client.get(f"/media/tracks/{track.id}", headers={"Range": "bytes=20-30"})

    assert response.status_code == 416
    assert response.headers["content-range"] == "bytes */10"
