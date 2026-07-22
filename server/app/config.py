from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    database_url: str = "postgresql+asyncpg://postgres:postgres@localhost:5432/postgres"
    jwt_secret: str = "dev-secret"
    host: str = "127.0.0.1"
    port: int = 8000
    max_devices_per_user: int = 2
    # confidence engine settings
    submission_interval_seconds: int = 60
    present_threshold_percent: int = 70
    partial_threshold_percent: int = 40
    min_crowd_size: int = 3
    # optional find3 websocket URL (ws:// or wss://)
    find3_ws_url: str = ""
    # Redis configuration
    redis_url: str = "redis://localhost:6379/0"

    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", extra="ignore"
    )


settings = Settings()
