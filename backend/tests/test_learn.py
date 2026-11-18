from datetime import date

from app.config import settings
from app.content import MAX_LESSON_XP, load
from conftest import bearer


def test_bundled_content_is_valid():
    catalog = load(settings.content_dir)
    assert catalog.errors == []
    assert len(catalog.lessons) >= 1


def test_validator_catches_bad_steps():
    from app.content import _check_step
    errors = []
    _check_step("t", {"type": "trace", "title": "x", "array": [1, 2], "frames": [{"l": 5, "note": "a"}]}, errors)
    _check_step("m", {"type": "match", "prompt": "p", "why": "w", "pairs": [["a", "b"], ["a", "c"], ["d", "e"]]}, errors)
    _check_step("y", {"type": "type", "prompt": "p", "why": "w", "answer": " "}, errors)
    assert any("two frames" in e for e in errors)
    assert any("pointer 'l'" in e for e in errors)
    assert any("must be different" in e for e in errors)
    assert any("missing answer" in e for e in errors)


def test_tracks_and_unit(client):
    tracks = client.get("/learn/tracks").json()["tracks"]
    first = tracks[0]["units"][0]
    assert first["lessons"][0]["steps"] > 0
    unit = client.get(f"/learn/units/{first['id']}").json()
    assert unit["lessons"][0]["steps"][0]["type"]
    assert client.get("/learn/units/nope").status_code == 404


def test_sync_merges_with_max(client, signup):
    h = bearer(signup())
    one = {"lesson_id": "two-pointers.1", "stars": 2, "xp": 10, "completions": 1, "completed_at": "2026-11-10T10:00:00Z"}
    client.post("/progress/sync", headers=h, json={"lessons": [one], "days": [{"day": "2026-11-10", "xp": 10}]})
    # A second phone with a better score and an earlier first completion.
    better = {**one, "stars": 3, "xp": 15, "completions": 2, "completed_at": "2026-11-09T10:00:00Z"}
    state = client.post("/progress/sync", headers=h, json={"lessons": [better], "days": [{"day": "2026-11-10", "xp": 5}]}).json()
    lesson = state["lessons"][0]
    assert (lesson["stars"], lesson["xp"], lesson["completions"]) == (3, 15, 2)
    assert lesson["first_completed_at"].startswith("2026-11-09")
    assert state["days"] == [{"day": "2026-11-10", "xp": 10}]
    assert state["total_xp"] == 10


def test_sync_clamps_and_ignores(client, signup):
    h = bearer(signup())
    state = client.post("/progress/sync", headers=h, json={
        "lessons": [
            {"lesson_id": "two-pointers.1", "stars": 3, "xp": 9999, "completed_at": "2026-11-10T10:00:00Z"},
            {"lesson_id": "made-up.1", "stars": 3, "xp": 10, "completed_at": "2026-11-10T10:00:00Z"},
        ],
        "days": [{"day": "2100-01-01", "xp": 50}],
    }).json()
    assert state["lessons"][0]["xp"] == MAX_LESSON_XP
    assert state["ignored"] == ["made-up.1"]
    assert state["days"] == []


def test_progress_is_per_user(client, signup):
    ada = bearer(signup())
    bob = bearer(signup(email="bob@example.com"))
    client.post("/progress/sync", headers=ada, json={"days": [{"day": date.today().isoformat(), "xp": 10}]})
    assert client.get("/progress", headers=bob).json()["total_xp"] == 0
    assert client.get("/progress", headers=ada).json()["total_xp"] == 10
