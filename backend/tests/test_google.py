from urllib.parse import parse_qs, urlparse

import pytest

from app import google
from app.config import settings
from app.security import pkce_challenge
from conftest import bearer

VERIFIER = "v" * 64
CHALLENGE = pkce_challenge(VERIFIER)


@pytest.fixture(autouse=True)
def google_on(monkeypatch):
    monkeypatch.setattr(settings, "google_client_id", "client-123")
    monkeypatch.setattr(settings, "google_client_secret", "secret")


def fake_google(monkeypatch, **claims):
    base = {"sub": "g-1", "email": "ada@gmail.com", "email_verified": True, "name": "Ada L", "picture": "https://x/p.png"}
    monkeypatch.setattr(google, "exchange_code", lambda code: {**base, **claims})


def sign_in(client):
    """Runs start → callback and returns the query the app receives."""
    start = client.get("/auth/google/start", params={"challenge": CHALLENGE}, follow_redirects=False)
    assert start.status_code == 302
    sent = parse_qs(urlparse(start.headers["location"]).query)
    assert sent["client_id"] == ["client-123"]
    assert sent["redirect_uri"] == [settings.google_redirect_uri]
    back = client.get("/auth/google/callback", params={"state": sent["state"][0], "code": "g-code"}, follow_redirects=False)
    assert back.status_code == 302
    location = back.headers["location"]
    assert location.startswith(settings.app_redirect + "?")
    return parse_qs(urlparse(location).query)


def test_full_flow(client, monkeypatch):
    fake_google(monkeypatch)
    code = sign_in(client)["code"][0]
    r = client.post("/auth/google/exchange", json={"code": code, "verifier": VERIFIER})
    assert r.status_code == 200
    me = client.get("/me", headers=bearer(r.json())).json()
    assert me["email"] == "ada@gmail.com"
    assert me["avatar_url"] == "https://x/p.png"


def test_code_works_once_and_needs_verifier(client, monkeypatch):
    fake_google(monkeypatch)
    code = sign_in(client)["code"][0]
    assert client.post("/auth/google/exchange", json={"code": code, "verifier": "w" * 64}).status_code == 400
    assert client.post("/auth/google/exchange", json={"code": code, "verifier": VERIFIER}).status_code == 200
    assert client.post("/auth/google/exchange", json={"code": code, "verifier": VERIFIER}).status_code == 400


def test_links_existing_email_account(client, monkeypatch, signup):
    first = signup(email="ada@gmail.com")
    fake_google(monkeypatch)
    code = sign_in(client)["code"][0]
    tokens = client.post("/auth/google/exchange", json={"code": code, "verifier": VERIFIER}).json()
    assert tokens["user"]["id"] == first["user"]["id"]


def test_unverified_email_refused(client, monkeypatch):
    fake_google(monkeypatch, email_verified=False)
    assert sign_in(client) == {"error": ["unverified"]}


def test_forged_state_refused(client, monkeypatch):
    fake_google(monkeypatch)
    r = client.get("/auth/google/callback", params={"state": "forged", "code": "x"}, follow_redirects=False)
    assert parse_qs(urlparse(r.headers["location"]).query) == {"error": ["expired"]}


def test_bad_challenge(client):
    r = client.get("/auth/google/start", params={"challenge": "short"}, follow_redirects=False)
    assert r.status_code == 400


def test_not_configured(client, monkeypatch):
    monkeypatch.setattr(settings, "google_client_id", "")
    r = client.get("/auth/google/start", params={"challenge": CHALLENGE}, follow_redirects=False)
    assert r.status_code == 503
