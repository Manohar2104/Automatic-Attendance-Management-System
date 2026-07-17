import asyncio
import sys
from app.db import get_sessionmaker
from app.models import Session
from sqlalchemy import select

async def run():
    SessionLocal = get_sessionmaker()
    async with SessionLocal() as db:
        res = await db.execute(select(Session))
        for x in res.scalars().all():
            print(f"{x.course_id} @ {x.location} ({x.status.value})")

if __name__ == "__main__":
    asyncio.run(run())
