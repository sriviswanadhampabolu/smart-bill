from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        case_sensitive=True,
        env_file=(".env", "../.env.local", ".env.local"),
        extra="ignore"
    )

    PROJECT_NAME: str = "Kirana Billing API"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    
    # Neon Online Cloud PostgreSQL Connection (Endpoint: ep-old-grass-b4k9t8zp)
    DATABASE_URL: str = (
        "postgresql://neondb_owner:npg_YT86SNxVpLFE@ep-old-grass-b4k9t8zp.c-6.us-east-2.aws.neon.tech/neondb?sslmode=require"
    )
    DATABASE_URL_POOLED: str = (
        "postgresql://neondb_owner:npg_YT86SNxVpLFE@ep-old-grass-b4k9t8zp-pooler.c-6.us-east-2.aws.neon.tech/neondb?sslmode=require"
    )
    NEON_DATA_API_URL: str = (
        "https://ep-old-grass-b4k9t8zp.apirest.c-6.us-east-2.aws.neon.tech/neondb/rest/v1"
    )

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
