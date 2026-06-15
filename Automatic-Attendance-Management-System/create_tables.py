import asyncio
from app.db.session import engine
from app.db.base import Base
from app.models import student, device, session, presence

async def init():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

asyncio.run(init())