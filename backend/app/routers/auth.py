from fastapi import APIRouter, HTTPException, Response, status
from sqlalchemy import select, update

from ..deps import CurrentUser, DbSession
from ..models import RefreshToken, User, aware, utcnow
from ..schemas import LoginIn, RefreshIn, RegisterIn, TokenOut, UserOut
from ..security import hash_password, issue_tokens, sha256, verify_password

router = APIRouter(tags=["auth"])


@router.post("/auth/register", response_model=TokenOut, status_code=status.HTTP_201_CREATED)
def register(body: RegisterIn, db: DbSession):
    email = body.email.lower()
    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(status.HTTP_409_CONFLICT, "That email already has an account")
    user = User(email=email, name=body.name.strip() or email.split("@")[0], password_hash=hash_password(body.password))
    db.add(user)
    db.commit()
    return issue_tokens(db, user)


@router.post("/auth/login", response_model=TokenOut)
def login(body: LoginIn, db: DbSession):
    user = db.scalar(select(User).where(User.email == body.email.lower()))
    # Same answer for unknown email, wrong password and Google-only accounts.
    if user is None or not verify_password(body.password, user.password_hash):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Wrong email or password")
    return issue_tokens(db, user)


@router.post("/auth/refresh", response_model=TokenOut)
def refresh(body: RefreshIn, db: DbSession):
    """Trades a refresh token for a new pair. Each refresh token works once."""
    row = db.scalar(select(RefreshToken).where(RefreshToken.token_hash == sha256(body.refresh_token)))
    if row is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Sign in again")
    if row.revoked_at is not None:
        # A used token came back: someone may have copied it. End every session of this user.
        db.execute(update(RefreshToken).where(RefreshToken.user_id == row.user_id, RefreshToken.revoked_at.is_(None))
                   .values(revoked_at=utcnow()))
        db.commit()
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Sign in again")
    if aware(row.expires_at) < utcnow():
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Sign in again")
    row.revoked_at = utcnow()
    user = db.get(User, row.user_id)
    return issue_tokens(db, user)


@router.post("/auth/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout(body: RefreshIn, db: DbSession):
    row = db.scalar(select(RefreshToken).where(RefreshToken.token_hash == sha256(body.refresh_token)))
    if row is not None and row.revoked_at is None:
        row.revoked_at = utcnow()
        db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/me", response_model=UserOut)
def me(user: CurrentUser):
    return user
