from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker
from app.config import settings

db_url = settings.sync_database_url
connect_args = (
    {"check_same_thread": False}
    if db_url.startswith("sqlite")
    else {"connect_timeout": 15}
)

engine_kwargs = {
    "pool_pre_ping": True,  # Critical for Neon serverless to detect dropped/scaled-to-zero connections
}

if not db_url.startswith("sqlite"):
    engine_kwargs.update({
        "pool_recycle": 300,   # Recycle connections every 5 minutes
        "pool_size": 5,        # Serverless-friendly pool size
        "max_overflow": 10,    # Allow burst synchronization traffic
        "pool_timeout": 30,    # Max seconds to wait for connection from pool
    })

engine = create_engine(
    db_url,
    connect_args=connect_args,
    **engine_kwargs
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
