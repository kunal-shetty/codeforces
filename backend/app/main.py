from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text

from .config import settings
from .deps import DbSession
from .routers import auth, google, learn, progress

app = FastAPI(title="Upsolve API", version="0.1.0")

if settings.cors_origins:
    app.add_middleware(CORSMiddleware, allow_origins=settings.cors_origins, allow_methods=["*"], allow_headers=["*"])

app.include_router(auth.router)
app.include_router(google.router)
app.include_router(learn.router)
app.include_router(progress.router)


@app.get("/health", tags=["meta"])
def health(db: DbSession):
    db.execute(text("SELECT 1"))
    return {"ok": True, "google": settings.google_enabled}
