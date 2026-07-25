from functools import lru_cache
from pathlib import Path

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "OpenChord"
    database_url: str = "sqlite+aiosqlite:///./openchord.db"
    media_root: Path = Path("./media")
    public_base_url: str = "http://localhost:8000"
    auto_migrate: bool = False
    log_level: str = "INFO"

    @field_validator("public_base_url")
    @classmethod
    def strip_trailing_slash(cls, value: str) -> str:
        return value.rstrip("/")


@lru_cache
def get_settings() -> Settings:
    return Settings()
