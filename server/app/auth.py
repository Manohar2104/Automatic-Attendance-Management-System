from datetime import datetime, timedelta, timezone
from jose import jwt
import bcrypt
from fastapi import HTTPException, Depends, Header
from sqlalchemy.ext.asyncio import AsyncSession
from .config import settings
from .db import get_db
from sqlalchemy import select
from .models import User
import uuid


def verify_password(plain_password: str, hashed_password: str) -> bool:
    try:
        return bcrypt.checkpw(plain_password.encode("utf-8"), hashed_password.encode("utf-8"))
    except Exception:
        return False


def get_password_hash(password: str) -> str:
    salt = bcrypt.gensalt()
    return bcrypt.hashpw(password.encode("utf-8"), salt).decode("utf-8")


def create_access_token(subject: str, expires_delta: int = 900):
    """Create short-lived access token (15 min default)."""
    to_encode = {
        "sub": subject,
        "type": "access"
    }
    expire = datetime.now(tz=timezone.utc) + timedelta(seconds=expires_delta)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, settings.jwt_secret, algorithm="HS256")


def create_refresh_token(subject: str, expires_delta: int = 604800):
    """Create long-lived refresh token (7 days default)."""
    to_encode = {
        "sub": subject,
        "type": "refresh"
    }
    expire = datetime.now(tz=timezone.utc) + timedelta(seconds=expires_delta)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, settings.jwt_secret, algorithm="HS256")


async def get_current_user(authorization: str = Header(None), db: AsyncSession = Depends(get_db)):
    if not authorization:
        raise HTTPException(status_code=401, detail="Missing authorization header")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid auth header")
    token = authorization[7:]
    
    # Check if token is blacklisted
    from .rate_limit import is_token_blacklisted
    if await is_token_blacklisted(token):
        raise HTTPException(status_code=401, detail="Token has been revoked")
    
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
        user_id = payload.get("sub")
        token_type = payload.get("type", "access")
        
        # Only accept access tokens for auth
        if token_type != "access":
            raise HTTPException(status_code=401, detail="Invalid token type. Use access token")
        
        if not user_id:
            raise HTTPException(status_code=401, detail="Invalid token payload")
        # ensure UUID type for DB comparisons
        try:
            user_id = uuid.UUID(user_id)
        except Exception:
            # if it's already a UUID object or invalid, leave as-is; DB will error accordingly
            pass
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token expired. Please refresh")
    except Exception as e:
        raise HTTPException(status_code=401, detail="Invalid token")

    q = await db.execute(select(User).where(User.id == user_id))
    user = q.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    # Inject user_id into async-local logging context
    from .logging_config import user_id_var
    user_id_var.set(str(user.id))
    
    return user


async def require_current_user(user=Depends(get_current_user)):
    return user
