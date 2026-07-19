import asyncio
import datetime

from app.config import settings
from app.db import get_engine, get_sessionmaker
from app.bootstrap import seed_demo_data

async def main():
    DATABASE_URL = settings.database_url
    print(f"Connecting to database: {DATABASE_URL}")
    engine = get_engine()
    SessionLocal = get_sessionmaker()
    
    async with engine.begin() as conn:
        from app.db import Base
        await conn.run_sync(Base.metadata.create_all)

    async with SessionLocal() as db:
        await seed_demo_data(db)
        
    await engine.dispose()

if __name__ == "__main__":
    asyncio.run(main())
