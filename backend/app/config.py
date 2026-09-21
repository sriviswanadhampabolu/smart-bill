from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional


class Settings(BaseSettings):
    model_config = SettingsConfigDict(case_sensitive=True, env_file=".env", extra="ignore")

    PROJECT_NAME: str = "Kirana Billing API"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    
    # Default to SQLite for zero-setup local dev; easily overridden by postgresql://...
    DATABASE_URL: str = "sqlite:///./grocery_backend.db"

    @property
    def sync_database_url(self) -> str:
        url = self.DATABASE_URL
        if url.startswith("postgres://"):
            url = url.replace("postgres://", "postgresql://", 1)
        return url
    
    # JWT security
    SECRET_KEY: str = "kirana-super-secret-counter-billing-key-change-in-production"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 30  # 30-day counter persistence


settings = Settings()
