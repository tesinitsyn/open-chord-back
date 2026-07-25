import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Response, status

from openchord.config import Settings, get_settings
from openchord.database import Database
from openchord.graphql import graphql_router
from openchord.media import router as media_router


def create_app(settings: Settings | None = None) -> FastAPI:
    resolved_settings = settings or get_settings()
    logging.basicConfig(level=resolved_settings.log_level)

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        database = Database(resolved_settings)
        app.state.settings = resolved_settings
        app.state.database = database
        resolved_settings.media_root.mkdir(parents=True, exist_ok=True)
        if resolved_settings.auto_migrate:
            await database.create_schema()
        yield
        await database.close()

    app = FastAPI(
        title=resolved_settings.app_name,
        version="0.1.0",
        docs_url=None,
        redoc_url=None,
        lifespan=lifespan,
    )

    @app.get("/health/live", tags=["health"])
    async def liveness() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/health/ready", tags=["health"])
    async def readiness(response: Response) -> dict[str, str]:
        if await app.state.database.is_ready():
            return {"status": "ready"}
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return {"status": "unavailable"}

    app.include_router(graphql_router, prefix="/graphql")
    app.include_router(media_router)
    return app


app = create_app()
