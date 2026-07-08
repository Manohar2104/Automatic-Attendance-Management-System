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
        connect_args = {}
        if "asyncpg" in DATABASE_URL:
            from urllib.parse import urlparse, urlunparse, parse_qsl, urlencode
            parsed = urlparse(DATABASE_URL)
            query_params = dict(parse_qsl(parsed.query))
            
            sslmode = query_params.pop("sslmode", None)
            query_params.pop("channel_binding", None)
            
            if sslmode in ("require", "verify-ca", "verify-full"):
                connect_args["ssl"] = True
                
            new_query = urlencode(query_params)
            parsed = parsed._replace(query=new_query)
            DATABASE_URL = urlunparse(parsed)
            
        _engine = create_async_engine(
            DATABASE_URL, connect_args=connect_args, future=True, echo=False
        )
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
