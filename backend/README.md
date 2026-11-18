# Upsolve API

FastAPI backend for accounts (email + Google), lessons and progress sync. Data lives in [Neon](https://neon.tech) Postgres.

## Run it locally

```bash
cd backend
python -m venv .venv
.venv/Scripts/activate            # macOS/Linux: source .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env              # then fill it in, see below
alembic upgrade head              # creates the tables in Neon
uvicorn app.main:app --reload --host 0.0.0.0
```

Open http://localhost:8000/docs for the interactive API. Without a `.env`, it falls back to a local SQLite file (`dev.db`); run `alembic upgrade head` once for that too.

### Neon

1. Create a project at console.neon.tech.
2. **Connect** → copy the pooled connection string (host contains `-pooler`) into `DATABASE_URL`.
3. Optional: copy the direct one (no `-pooler`) into `DATABASE_URL_DIRECT`, which migrations use.

`postgresql://…` URLs are switched to the psycopg 3 driver automatically. Connections are checked before use and recycled every 5 minutes, because Neon suspends idle computes.

### Google sign-in

1. Google Cloud Console → **APIs & Services → OAuth consent screen**: set it up (External, add yourself as a test user).
2. **Credentials → Create credentials → OAuth client ID → Web application**.
3. Authorised redirect URI: `PUBLIC_URL/auth/google/callback`, for example `https://api.example.com/auth/google/callback`.
4. Put the client ID and secret in `.env`.

Google only accepts `https` redirect URIs, or `http://localhost`. To test on a phone, expose the server with a tunnel (`cloudflared tunnel --url http://localhost:8000` or ngrok) and use that https URL as `PUBLIC_URL`.

How the flow works, without any Google SDK in the app:

```
app ──open browser──► /auth/google/start?challenge=S256(verifier)
                         └─► Google sign-in ─► /auth/google/callback
                                                └─► upsolve://auth?code=ONE_TIME
app ──POST /auth/google/exchange {code, verifier}──► access + refresh tokens
```

## Endpoints

| | |
|---|---|
| `POST /auth/register`, `POST /auth/login` | email + password → tokens |
| `GET /auth/google/start`, `/callback`, `POST /exchange` | Google sign-in, see above |
| `POST /auth/refresh` | rotating refresh token → new pair (a reused token ends every session) |
| `POST /auth/logout` | revokes a refresh token |
| `GET /me` | the signed-in user |
| `GET /learn/tracks`, `GET /learn/units/{id}` | lesson content from `../content/learn` |
| `GET /progress`, `POST /progress/sync` | lesson results and daily XP, merged with max() |

## Checks

```bash
pytest                    # API tests on in-memory SQLite
python -m app.content     # validates every lesson file
```

## Deploy

```bash
docker build -f backend/Dockerfile -t upsolve-api .    # from the repo root
docker run -p 8000:8000 --env-file backend/.env upsolve-api
```

The container applies migrations on start. Any host that runs a container works (Fly.io, Railway, Render, a small VPS).
