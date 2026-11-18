from functools import cached_property
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

BACKEND_DIR = Path(__file__).resolve().parents[1]
DEFAULT_SECRET = "change-me"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=BACKEND_DIR / ".env", extra="ignore")

    database_url: str = f"sqlite:///{BACKEND_DIR / 'dev.db'}"
    database_url_direct: str = ""
    jwt_secret: str = DEFAULT_SECRET
    access_minutes: int = 30
    refresh_days: int = 60

    # Lessons live at the repo root so the Android app bundles the very same files.
    content_dir: Path = BACKEND_DIR.parent / "content"

    public_url: str = "http://localhost:8000"
    google_client_id: str = ""
    google_client_secret: str = ""
    # Where the browser goes after Google sign-in; the app handles this scheme.
    app_redirect: str = "upsolve://auth"
    cors_origins: list[str] = []

    @staticmethod
    def _driver(url: str) -> str:
        """Neon hands out postgresql:// URLs; SQLAlchemy needs to be told to use psycopg 3."""
        for prefix in ("postgres://", "postgresql://"):
            if url.startswith(prefix):
                return "postgresql+psycopg://" + url[len(prefix):]
        return url

    @cached_property
    def sqlalchemy_url(self) -> str:
        return self._driver(self.database_url)

    @cached_property
    def migrations_url(self) -> str:
        return self._driver(self.database_url_direct or self.database_url)

    @property
    def is_sqlite(self) -> bool:
        return self.sqlalchemy_url.startswith("sqlite")

    @property
    def google_enabled(self) -> bool:
        return bool(self.google_client_id and self.google_client_secret)

    @property
    def google_redirect_uri(self) -> str:
        return self.public_url.rstrip("/") + "/auth/google/callback"


settings = Settings()

if not settings.is_sqlite and (settings.jwt_secret == DEFAULT_SECRET or len(settings.jwt_secret) < 32):
    raise RuntimeError("Set JWT_SECRET in backend/.env (32+ random characters) before using a real database.")
