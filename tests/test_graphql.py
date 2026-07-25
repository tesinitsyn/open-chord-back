from httpx import AsyncClient

from tests.factories import seed_catalog


async def test_catalog_and_playback_flow(client: AsyncClient) -> None:
    album, track = await seed_catalog(client._transport.app)

    catalog = await client.post(
        "/graphql",
        json={
            "query": """
                query {
                  albums(search: "Aurora") {
                    id title year artworkUrl
                    artist { name }
                    tracks { id title durationMs streamUrl lyrics { text startMs endMs } }
                  }
                }
            """
        },
    )

    assert catalog.status_code == 200
    data = catalog.json()["data"]["albums"]
    assert data[0]["id"] == str(album.id)
    assert data[0]["artist"]["name"] == "Aurora Lines"
    assert data[0]["tracks"][0]["streamUrl"] == f"http://test/media/tracks/{track.id}"
    assert data[0]["tracks"][0]["lyrics"][0]["endMs"] == 8000

    playback = await client.post(
        "/graphql",
        json={
            "query": """
                mutation Record($input: PlaybackEventInput!) {
                  recordPlayback(input: $input) { trackId positionMs completed }
                }
            """,
            "variables": {
                "input": {"trackId": str(track.id), "positionMs": 120000, "completed": True}
            },
        },
    )
    assert playback.status_code == 200
    assert playback.json()["data"]["recordPlayback"] == {
        "trackId": str(track.id),
        "positionMs": 96000,
        "completed": True,
    }

    recent = await client.post("/graphql", json={"query": "{ recentlyPlayed { id } }"})
    assert recent.json()["data"]["recentlyPlayed"] == [{"id": str(album.id)}]


async def test_invalid_id_is_a_safe_client_error(client: AsyncClient) -> None:
    response = await client.post("/graphql", json={"query": '{ album(id: "nope") { id } }'})

    error = response.json()["errors"][0]
    assert error["message"] == "Invalid ID"
    assert error["extensions"]["code"] == "BAD_USER_INPUT"
