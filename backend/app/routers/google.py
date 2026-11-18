"""Google sign-in for an app without Google libraries.

1. The app makes a PKCE verifier, opens the browser at /auth/google/start?challenge=S256(verifier).
2. We send the browser to Google; Google sends it back to /auth/google/callback with a code.
3. We verify the user with Google, then redirect to upsolve://auth?code=ONE_TIME_CODE.
4. The app posts that code with its verifier to /auth/google/exchange and gets our tokens.

Our client secret never leaves the server, and a code caught by another app on the phone is useless
without the verifier, which never leaves our app.
"""

import re
from datetime import timedelta
from urllib.parse import urlencode

import jwt
from fastapi import APIRouter, HTTPException, Query, status
from fastapi.responses import RedirectResponse
from sqlalchemy import select

from .. import google
from ..config import settings
from ..deps import DbSession
from ..models import LoginCode, User, aware, utcnow
from ..schemas import GoogleExchangeIn, TokenOut
from ..security import decode, encode, issue_tokens, new_secret, pkce_challenge, sha256

router = APIRouter(prefix="/auth/google", tags=["auth"])

CHALLENGE = re.compile(r"^[A-Za-z0-9_-]{43,128}$")
STATE_LIFETIME = timedelta(minutes=10)
CODE_LIFETIME = timedelta(minutes=5)


def _to_app(**params) -> RedirectResponse:
    return RedirectResponse(f"{settings.app_redirect}?{urlencode(params)}", status_code=status.HTTP_302_FOUND)


@router.get("/start")
def start(challenge: str = Query(max_length=128)):
    if not settings.google_enabled:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Google sign-in is not set up on this server")
    if not CHALLENGE.match(challenge):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "Bad PKCE challenge")
    # State is signed, so the callback knows it came from us and which app challenge it belongs to.
    state = encode({"typ": "google_state", "ch": challenge, "n": new_secret()}, STATE_LIFETIME)
    query = urlencode({
        "client_id": settings.google_client_id,
        "redirect_uri": settings.google_redirect_uri,
        "response_type": "code",
        "scope": "openid email profile",
        "state": state,
        "prompt": "select_account",
    })
    return RedirectResponse(f"{google.AUTH_URL}?{query}", status_code=status.HTTP_302_FOUND)


@router.get("/callback")
def callback(db: DbSession, state: str = "", code: str = "", error: str = ""):
    if error:
        return _to_app(error=error)
    try:
        challenge = decode(state, "google_state")["ch"]
    except (jwt.InvalidTokenError, KeyError):
        return _to_app(error="expired")
    try:
        claims = google.exchange_code(code)
    except google.GoogleError:
        return _to_app(error="google")
    if not claims.get("email_verified"):
        return _to_app(error="unverified")

    sub, email = claims["sub"], claims["email"].lower()
    user = db.scalar(select(User).where(User.google_sub == sub))
    if user is None:
        # Google has verified this email, so it is safe to link it to an existing email account.
        user = db.scalar(select(User).where(User.email == email))
        if user is None:
            user = User(email=email, name=(claims.get("name") or email.split("@")[0])[:120])
            db.add(user)
        user.google_sub = sub
    if claims.get("picture"):
        user.avatar_url = claims["picture"][:500]
    db.flush()

    one_time = new_secret()
    db.add(LoginCode(user_id=user.id, code_hash=sha256(one_time), challenge=challenge,
                     expires_at=utcnow() + CODE_LIFETIME))
    db.commit()
    return _to_app(code=one_time)


@router.post("/exchange", response_model=TokenOut)
def exchange(body: GoogleExchangeIn, db: DbSession):
    row = db.scalar(select(LoginCode).where(LoginCode.code_hash == sha256(body.code)))
    bad = HTTPException(status.HTTP_400_BAD_REQUEST, "Sign-in expired, try again")
    if row is None or row.used_at is not None or aware(row.expires_at) < utcnow():
        raise bad
    if pkce_challenge(body.verifier) != row.challenge:
        raise bad
    row.used_at = utcnow()
    return issue_tokens(db, db.get(User, row.user_id))
