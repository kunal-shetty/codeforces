from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    email: str
    name: str
    avatar_url: str | None = None


class TokenOut(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int
    user: UserOut


class RegisterIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    name: str = Field(default="", max_length=120)


class LoginIn(BaseModel):
    email: EmailStr
    password: str = Field(max_length=128)


class RefreshIn(BaseModel):
    refresh_token: str = Field(max_length=200)


class GoogleExchangeIn(BaseModel):
    code: str = Field(max_length=200)
    verifier: str = Field(min_length=43, max_length=128)


class LessonIn(BaseModel):
    lesson_id: str = Field(max_length=80)
    stars: int = Field(ge=1, le=3)
    xp: int = Field(ge=0)
    completions: int = Field(default=1, ge=1)
    completed_at: datetime


class DayIn(BaseModel):
    day: date
    xp: int = Field(ge=0)


class SyncIn(BaseModel):
    lessons: list[LessonIn] = Field(default_factory=list, max_length=2000)
    days: list[DayIn] = Field(default_factory=list, max_length=1000)


class LessonOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    lesson_id: str
    stars: int
    xp: int
    completions: int
    first_completed_at: datetime


class DayOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    day: date
    xp: int


class ProgressOut(BaseModel):
    lessons: list[LessonOut]
    days: list[DayOut]
    total_xp: int
    ignored: list[str] = Field(default_factory=list)
