from conftest import bearer


def test_register_and_me(client, signup):
    tokens = signup(email="Ada@Example.com")
    me = client.get("/me", headers=bearer(tokens)).json()
    assert me["email"] == "ada@example.com"
    assert me["name"] == "Ada"


def test_duplicate_email(client, signup):
    signup()
    r = client.post("/auth/register", json={"email": "ADA@example.com", "password": "another one", "name": ""})
    assert r.status_code == 409


def test_short_password_rejected(client):
    r = client.post("/auth/register", json={"email": "a@example.com", "password": "short"})
    assert r.status_code == 422


def test_login(client, signup):
    signup()
    assert client.post("/auth/login", json={"email": "ada@example.com", "password": "correct horse"}).status_code == 200
    assert client.post("/auth/login", json={"email": "ada@example.com", "password": "wrong horse"}).status_code == 401
    assert client.post("/auth/login", json={"email": "bob@example.com", "password": "correct horse"}).status_code == 401


def test_me_needs_token(client):
    assert client.get("/me").status_code == 401
    assert client.get("/me", headers={"Authorization": "Bearer nonsense"}).status_code == 401


def test_refresh_rotates(client, signup):
    first = signup()
    second = client.post("/auth/refresh", json={"refresh_token": first["refresh_token"]})
    assert second.status_code == 200
    assert second.json()["refresh_token"] != first["refresh_token"]
    assert client.get("/me", headers=bearer(second.json())).status_code == 200


def test_refresh_reuse_ends_all_sessions(client, signup):
    first = signup()
    second = client.post("/auth/refresh", json={"refresh_token": first["refresh_token"]}).json()
    # The old token shows up again: treat it as stolen.
    assert client.post("/auth/refresh", json={"refresh_token": first["refresh_token"]}).status_code == 401
    assert client.post("/auth/refresh", json={"refresh_token": second["refresh_token"]}).status_code == 401


def test_logout(client, signup):
    tokens = signup()
    assert client.post("/auth/logout", json={"refresh_token": tokens["refresh_token"]}).status_code == 204
    assert client.post("/auth/refresh", json={"refresh_token": tokens["refresh_token"]}).status_code == 401
