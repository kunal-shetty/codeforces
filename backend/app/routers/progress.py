from datetime import date, timedelta

from fastapi import APIRouter
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..content import MAX_DAILY_XP, MAX_LESSON_XP, catalog
from ..deps import CurrentUser, DbSession
from ..models import DailyXp, LessonProgress, User, aware
from ..schemas import ProgressOut, SyncIn

router = APIRouter(prefix="/progress", tags=["progress"])


def _state(db: Session, user: User, ignored: list[str] | None = None) -> ProgressOut:
    lessons = db.scalars(select(LessonProgress).where(LessonProgress.user_id == user.id)
                         .order_by(LessonProgress.first_completed_at)).all()
    days = db.scalars(select(DailyXp).where(DailyXp.user_id == user.id).order_by(DailyXp.day)).all()
    return ProgressOut(lessons=lessons, days=days, total_xp=sum(d.xp for d in days), ignored=ignored or [])


@router.get("", response_model=ProgressOut)
def get_progress(user: CurrentUser, db: DbSession):
    return _state(db, user)


@router.post("/sync", response_model=ProgressOut)
def sync(body: SyncIn, user: CurrentUser, db: DbSession):
    """Merges what the phone has with what the server has and returns the result.

    Everything merges with max() (and min() for the first completion), so sending the same data twice,
    or from two phones, never loses or double-counts anything.
    """
    known = catalog().lessons
    ignored = []
    have = {p.lesson_id: p for p in db.scalars(select(LessonProgress).where(LessonProgress.user_id == user.id))}
    for item in body.lessons:
        if item.lesson_id not in known:
            ignored.append(item.lesson_id)
            continue
        xp = min(item.xp, MAX_LESSON_XP)
        row = have.get(item.lesson_id)
        if row is None:
            row = LessonProgress(user_id=user.id, lesson_id=item.lesson_id, stars=item.stars, xp=xp,
                                 completions=item.completions, first_completed_at=item.completed_at)
            db.add(row)
            have[item.lesson_id] = row
        else:
            row.stars = max(row.stars, item.stars)
            row.xp = max(row.xp, xp)
            row.completions = max(row.completions, item.completions)
            row.first_completed_at = min(aware(row.first_completed_at), aware(item.completed_at))

    # The phone's local date can be a day ahead of UTC.
    latest = date.today() + timedelta(days=1)
    days = {d.day: d for d in db.scalars(select(DailyXp).where(DailyXp.user_id == user.id))}
    for item in body.days:
        if item.day > latest:
            continue
        xp = min(item.xp, MAX_DAILY_XP)
        row = days.get(item.day)
        if row is None:
            row = DailyXp(user_id=user.id, day=item.day, xp=xp)
            db.add(row)
            days[item.day] = row
        else:
            row.xp = max(row.xp, xp)
    db.commit()
    return _state(db, user, ignored)
