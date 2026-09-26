import sys
from app.config import settings
from app.db.session import engine, SessionLocal, Base
from sqlalchemy import text
from app.models.entities import Shop, Item

print("=" * 60)
print("NEON ONLINE DATABASE CONNECTION VERIFICATION")
print("=" * 60)
print(f"DATABASE_URL (configured): {settings.sync_database_url.split('@')[1] if '@' in settings.sync_database_url else settings.sync_database_url}")
print(f"NEON DATA API URL: {getattr(settings, 'NEON_DATA_API_URL', 'Not set')}")

try:
    with engine.connect() as conn:
        version = conn.execute(text("SELECT version()")).scalar()
        db_name = conn.execute(text("SELECT current_database()")).scalar()
        user_name = conn.execute(text("SELECT current_user")).scalar()
        print(f"\n[OK] Successfully connected to Neon PostgreSQL!")
        print(f"     Database: {db_name}")
        print(f"     User: {user_name}")
        print(f"     PostgreSQL Version: {version.split(',')[0]}")

        tables = conn.execute(text("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name")).fetchall()
        table_names = [t[0] for t in tables]
        print(f"\n[OK] Tables found ({len(table_names)}): {', '.join(table_names)}")

        # Count records in all tables
        print("\nRecord counts:")
        for t in table_names:
            cnt = conn.execute(text(f'SELECT count(*) FROM "{t}"')).scalar()
            print(f"     - {t}: {cnt} rows")

    print("\n" + "=" * 60)
    print("ALL NEON DATABASE CHECKS PASSED SUCCESSFULLY!")
    print("=" * 60)

except Exception as e:
    print(f"\n[ERROR] Failed to connect to Neon database: {e}")
    sys.exit(1)
