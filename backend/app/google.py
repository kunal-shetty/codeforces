"""Talking to Google: swap the authorization code for an ID token and verify it."""

import httpx
import jwt

from .config import settings

AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
TOKEN_URL = "https://oauth2.googleapis.com/token"
CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs"
ISSUERS = ("https://accounts.google.com", "accounts.google.com")

_keys = jwt.PyJWKClient(CERTS_URL, cache_keys=True)


class GoogleError(Exception):
    pass


def exchange_code(code: str) -> dict:
    """Returns the verified ID token claims (sub, email, email_verified, name, picture)."""
    try:
        r = httpx.post(TOKEN_URL, timeout=10, data={
            "code": code,
            "client_id": settings.google_client_id,
            "client_secret": settings.google_client_secret,
            "redirect_uri": settings.google_redirect_uri,
            "grant_type": "authorization_code",
        })
    except httpx.HTTPError as e:
        raise GoogleError(f"could not reach Google: {e}") from e
    if r.status_code != 200:
        raise GoogleError(f"Google refused the code ({r.status_code})")
    id_token = r.json().get("id_token")
    if not id_token:
        raise GoogleError("Google sent no ID token")
    try:
        key = _keys.get_signing_key_from_jwt(id_token).key
        claims = jwt.decode(id_token, key, algorithms=["RS256"], audience=settings.google_client_id)
    except jwt.PyJWTError as e:
        raise GoogleError(f"bad ID token: {e}") from e
    if claims.get("iss") not in ISSUERS:
        raise GoogleError("ID token from an unexpected issuer")
    return claims
