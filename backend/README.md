# SmartBill Cloud & Sync Backend

High-performance, lightweight FastAPI backend providing authentication, offline-to-cloud bidirectional synchronization, multi-store profile management, and transaction ledger storage for **SmartBill**.

---

## 🛠️ Tech Stack

- **Framework**: [FastAPI](https://fastapi.tiangolo.com/) (Python 3.10+)
- **ORM & DB**: [SQLAlchemy 2.0](https://www.sqlalchemy.org/) (SQLite for zero-setup local dev / PostgreSQL & Neon for production)
- **Security**: JWT tokens (HMAC-SHA256) & Bcrypt password hashing
- **Testing**: Pytest & HTTPX test client

---

## 📂 Architecture

```
backend/
├── app/
│   ├── api/v1/          # Endpoints (auth, shop, items, billing, sync)
│   ├── core/            # Security, JWT tokens, password hashing
│   ├── db/              # SQLAlchemy session, engine, declarative base
│   ├── models/          # Database models (Shop, Item, Bill, Khata, SyncQueue)
│   ├── schemas/         # Pydantic validation models
│   ├── config.py        # Environment settings (SQLite/Postgres toggle)
│   └── main.py          # FastAPI application entrypoint & CORS
├── tests/               # Automated unit & integration tests
├── .env.example         # Template environment file
└── requirements.txt     # Python dependencies
```

---

## 🚀 Getting Started

### 1. Install Dependencies

```bash
# Create virtual environment (optional but recommended)
python -m venv venv
# Activate on Windows:
venv\Scripts\activate
# Activate on macOS/Linux:
source venv/bin/activate

# Install requirements
pip install -r requirements.txt
```

### 2. Configure Environment

Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```

To run with zero configuration, the backend defaults to SQLite (`grocery_backend.db`). To connect to Neon Cloud PostgreSQL, set `DATABASE_URL` in `.env`:
```env
DATABASE_URL=postgresql://user:password@ep-xyz.us-east-2.aws.neon.tech/neondb?sslmode=require
```

### 3. Run Development Server

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Interactive Swagger API documentation will be available at:
`http://localhost:8000/docs`

---

## 🧪 Running Tests

```bash
pytest
```
