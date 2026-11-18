"""Lesson content: the JSON files in content/learn, shared with the Android app.

Run `python -m app.content` to check every unit before opening a pull request.
"""

import json
import sys
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path

from .config import settings

# Keep in sync with the app (data/LearnDb.java): a lesson is worth 10 XP, +5 with no mistakes.
LESSON_XP = 10
PERFECT_BONUS = 5
MAX_LESSON_XP = LESSON_XP + PERFECT_BONUS
# No honest day of lessons comes near this; anything above is clamped.
MAX_DAILY_XP = 2000

STEP_TYPES = {"card", "choice", "blank", "order", "trace", "match", "type"}
# Steps that only show something; every other type is a question.
SHOW_TYPES = {"card", "trace"}
TRACE_KEYS = {"array", "range", "note"}
MAX_TRACE_CELLS = 10
COLORS = {"mint", "sky", "lilac", "peach", "butter", "rose"}


@dataclass
class Catalog:
    version: int
    tracks: list[dict]
    units: dict[str, dict]
    lessons: dict[str, dict] = field(default_factory=dict)
    errors: list[str] = field(default_factory=list)

    def summary(self) -> dict:
        """Tracks with their units and lesson titles, without the steps."""
        out = []
        for track in self.tracks:
            units = []
            for uid in track["units"]:
                u = self.units.get(uid)
                if u is None:
                    continue
                units.append({
                    "id": u["id"], "title": u["title"], "summary": u.get("summary", ""),
                    "icon": u.get("icon", ""), "color": u.get("color", "mint"),
                    "lessons": [{"id": l["id"], "title": l["title"], "steps": len(l["steps"])} for l in u["lessons"]],
                })
            out.append({"id": track["id"], "title": track["title"], "units": units})
        return {"version": self.version, "tracks": out}


def _check_step(where: str, step: dict, errors: list[str]) -> None:
    kind = step.get("type")
    if kind not in STEP_TYPES:
        errors.append(f"{where}: unknown type {kind!r}")
        return
    if kind == "card":
        if not step.get("title") or not step.get("body"):
            errors.append(f"{where}: a card needs a title and a body")
        return
    if kind == "trace":
        _check_trace(where, step, errors)
        return
    if not step.get("prompt"):
        errors.append(f"{where}: missing prompt")
    if not step.get("why"):
        errors.append(f"{where}: missing why (shown after answering)")
    if kind in ("choice", "blank"):
        options = step.get("options", [])
        if len(options) < 2 or len(set(options)) != len(options):
            errors.append(f"{where}: needs at least two different options")
        answer = step.get("answer")
        if not isinstance(answer, int) or not 0 <= answer < len(options):
            errors.append(f"{where}: answer must index into options")
        if kind == "blank" and "___" not in step.get("code", ""):
            errors.append(f"{where}: a blank needs ___ in its code")
    if kind == "order":
        lines = step.get("lines", [])
        if len(lines) < 3 or len(set(l.strip() for l in lines)) != len(lines):
            errors.append(f"{where}: needs at least three different lines")
    if kind == "match":
        pairs = step.get("pairs", [])
        if len(pairs) < 3 or any(len(p) != 2 for p in pairs):
            errors.append(f"{where}: needs at least three [left, right] pairs")
        elif len({p[0] for p in pairs}) != len(pairs) or len({p[1] for p in pairs}) != len(pairs):
            errors.append(f"{where}: every left and every right must be different")
    if kind == "type":
        if not str(step.get("answer", "")).strip():
            errors.append(f"{where}: missing answer")


def _check_trace(where: str, step: dict, errors: list[str]) -> None:
    """A trace is an array plus frames; any frame key except array/range/note is a pointer index."""
    if not step.get("title"):
        errors.append(f"{where}: a trace needs a title")
    cells = step.get("array", [])
    if not 1 <= len(cells) <= MAX_TRACE_CELLS:
        errors.append(f"{where}: array needs 1 to {MAX_TRACE_CELLS} cells")
        return
    frames = step.get("frames", [])
    if len(frames) < 2:
        errors.append(f"{where}: needs at least two frames")
    for i, frame in enumerate(frames):
        at = f"{where} frame {i + 1}"
        if not frame.get("note"):
            errors.append(f"{at}: missing note")
        if "array" in frame and len(frame["array"]) != len(cells):
            errors.append(f"{at}: array must keep {len(cells)} cells")
        if "range" in frame:
            r = frame["range"]
            if len(r) != 2 or not 0 <= r[0] <= r[1] < len(cells):
                errors.append(f"{at}: range must be [from, to] inside the array")
        for key, value in frame.items():
            if key not in TRACE_KEYS and not (isinstance(value, int) and 0 <= value < len(cells)):
                errors.append(f"{at}: pointer {key!r} must be an index into the array")


def load(root: Path) -> Catalog:
    learn = root / "learn"
    index = json.loads((learn / "index.json").read_text(encoding="utf-8"))
    catalog = Catalog(version=index.get("version", 1), tracks=index["tracks"], units={})
    errors = catalog.errors
    for track in index["tracks"]:
        for uid in track["units"]:
            path = learn / "units" / f"{uid}.json"
            if not path.exists():
                errors.append(f"{uid}: listed in index.json but {path.name} is missing")
                continue
            try:
                unit = json.loads(path.read_text(encoding="utf-8"))
            except json.JSONDecodeError as e:
                errors.append(f"{path.name}: invalid JSON ({e})")
                continue
            if unit.get("id") != uid:
                errors.append(f"{path.name}: id must be {uid!r}")
            if unit.get("color") not in COLORS:
                errors.append(f"{uid}: color must be one of {sorted(COLORS)}")
            if not unit.get("lessons"):
                errors.append(f"{uid}: has no lessons")
            catalog.units[uid] = unit
            for lesson in unit.get("lessons", []):
                lid = lesson.get("id", "")
                if not lid.startswith(uid + "."):
                    errors.append(f"{uid}: lesson id {lid!r} must start with '{uid}.'")
                if lid in catalog.lessons:
                    errors.append(f"{lid}: duplicate lesson id")
                catalog.lessons[lid] = lesson
                steps = lesson.get("steps", [])
                if not any(s.get("type") not in SHOW_TYPES for s in steps):
                    errors.append(f"{lid}: needs at least one question")
                for i, step in enumerate(steps):
                    _check_step(f"{lid} step {i + 1}", step, errors)
    return catalog


@lru_cache
def catalog() -> Catalog:
    return load(settings.content_dir)


if __name__ == "__main__":
    c = load(settings.content_dir)
    for e in c.errors:
        print("✗", e)
    print(f"{len(c.units)} units, {len(c.lessons)} lessons, {len(c.errors)} problems")
    sys.exit(1 if c.errors else 0)
