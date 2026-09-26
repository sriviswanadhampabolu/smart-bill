from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.db.session import engine, Base
from app.api.v1 import api_router

# Auto-create tables if they don't exist yet
try:
    Base.metadata.create_all(bind=engine)
except Exception as e:
    import logging
    logging.getLogger("uvicorn").warning(f"Tables initialization note: {e}")

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description="High-speed counter billing & sync API for grocery store owners."
)

# Enable CORS for mobile & web dev
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix=settings.API_V1_STR)


@app.get("/")
def root():
    return {
        "app": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "status": "online",
        "database": "neon_postgresql",
        "neon_data_api": settings.NEON_DATA_API_URL,
    }


@app.get("/health")
@app.get(f"{settings.API_V1_STR}/health")
def health_check():
    from app.db.session import SessionLocal
    from sqlalchemy import text
    try:
        db = SessionLocal()
        db.execute(text("SELECT 1"))
        shop_count = db.execute(text("SELECT count(*) FROM shops")).scalar()
        item_count = db.execute(text("SELECT count(*) FROM items")).scalar()
        bill_count = db.execute(text("SELECT count(*) FROM bills")).scalar()
        db.close()
        return {
            "status": "healthy",
            "database": "connected",
            "provider": "Neon Cloud PostgreSQL",
            "neon_data_api_url": settings.NEON_DATA_API_URL,
            "counts": {
                "shops": shop_count,
                "items": item_count,
                "bills": bill_count,
            }
        }
    except Exception as e:
        return {
            "status": "degraded",
            "database": "error",
            "error": str(e)
        }
