from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    database_url: str = "postgresql+asyncpg://postgres:postgres@localhost:5432/postgres"
    jwt_secret: str = "dev-secret"
    host: str = "127.0.0.1"
    port: int = 8000
    max_devices_per_user: int = 2
    # confidence engine settings
    submission_interval_seconds: int = 30
    present_threshold_percent: int = 85
    partial_threshold_percent: int = 60
    min_crowd_size: int = 3
    # optional find3 websocket URL (ws:// or wss://)
    find3_ws_url: str = ""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()
