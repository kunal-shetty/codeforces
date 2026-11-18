import base64
import hashlib
import secrets
from datetime import timedelta

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError
from sqlalchemy.orm import Session

from .config import settings
from .models import RefreshToken, User, utcnow
from .schemas import UserOut

_hasher = PasswordHasher()
ALGORITHM = "HS256"


def hash_password(password: str) -> str:
    return _hasher.hash(password)


def verify_password(password: str, hashed: str | None) -> bool:
    if not hashed:
        return False
    try:
        return _hasher.verify(hashed, password)
    except (VerificationError, InvalidHashError):
        return False


def sha256(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def new_secret() -> str:
    return secrets.token_urlsafe(32)


def pkce_challenge(verifier: str) -> str:
    """RFC 7636 S256: base64url(sha256(verifier)) without padding."""
    digest = hashlib.sha256(verifier.encode("ascii")).digest()
    return base64.urlsafe_b64encode(digest).rstrip(b"=").decode("ascii")


def encode(claims: dict, lifetime: timedelta) -> str:
    now = utcnow()
    return jwt.encode({**claims, "iat": now, "exp": now + lifetime}, settings.jwt_secret, algorithm=ALGORITHM)


def decode(token: str, kind: str) -> dict:
    """Raises jwt.InvalidTokenError when the token is bad, expired or of another kind."""
    claims = jwt.decode(token, settings.jwt_secret, algorithms=[ALGORITHM])
    if claims.get("typ") != kind:
        raise jwt.InvalidTokenError("wrong token type")
    return claims


def issue_tokens(db: Session, user: User) -> dict:
    access = encode({"sub": str(user.id), "typ": "access"}, timedelta(minutes=settings.access_minutes))
    refresh = new_secret()
    db.add(RefreshToken(user_id=user.id, token_hash=sha256(refresh),
                        expires_at=utcnow() + timedelta(days=settings.refresh_days)))
    db.commit()
    return {
        "access_token": access,
        "refresh_token": refresh,
        "token_type": "bearer",
        "expires_in": settings.access_minutes * 60,
        "user": UserOut.model_validate(user),
    }
