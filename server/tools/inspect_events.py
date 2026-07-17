import asyncio
import sys
sys.path.append("/home/abhijith/projects/isfcr/Automatic-Attendance-Management-System/server")
from app.db import get_sessionmaker
from app.models import Event, User
from sqlalchemy import select

async def run():
    SessionLocal = get_sessionmaker()
    async with SessionLocal() as db:
        print("=== RECENT EVENTS ===")
        stmt = select(Event).order_by(Event.timestamp.desc()).limit(20)
        res = await db.execute(stmt)
        for e in res.scalars().all():
            email = "Unknown"
            if e.user_id:
                q_u = await db.execute(select(User).where(User.id == e.user_id))
                u = q_u.scalars().first()
                email = u.email if u else "Not Found"
            print(f"Event: {e.type.value if hasattr(e.type, 'value') else e.type} | User: {email} | Location: {e.location} | Session: {e.session_id} | TS: {e.timestamp}")

if __name__ == "__main__":
    asyncio.run(run())
