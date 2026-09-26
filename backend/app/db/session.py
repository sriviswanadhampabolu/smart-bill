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
    "pool_pre_ping": True,
}

if not db_url.startswith("sqlite"):
    engine_kwargs.update({
        "pool_recycle": 300,
        "pool_size": 10,
        "max_overflow": 20,
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
