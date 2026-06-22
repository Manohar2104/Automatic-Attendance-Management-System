from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker, declarative_base
from .config import settings

_engine = None
_SessionLocal = None

Base = declarative_base()


def get_engine():
    """Lazily create and return the async engine."""
    global _engine, _SessionLocal
    if _engine is None:
        DATABASE_URL = settings.database_url
        _engine = create_async_engine(DATABASE_URL, future=True, echo=False)
        _SessionLocal = sessionmaker(
            bind=_engine, class_=AsyncSession, expire_on_commit=False
        )
    return _engine


def get_sessionmaker():
    global _SessionLocal
    if _SessionLocal is None:
        get_engine()
    return _SessionLocal


async def get_db():
    SessionLocal = get_sessionmaker()
    async with SessionLocal() as session:
        yield session
