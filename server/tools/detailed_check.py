import asyncio
import sys
sys.path.append("/home/abhijith/projects/isfcr/Automatic-Attendance-Management-System/server")
from app.db import get_sessionmaker
from app.models import Session, User
from sqlalchemy import select

async def run():
    SessionLocal = get_sessionmaker()
    async with SessionLocal() as db:
        print("=== USERS ===")
        res_users = await db.execute(select(User))
        for u in res_users.scalars().all():
            print(f"User: {u.email} | Role: {u.role.value if hasattr(u.role, 'value') else u.role}")

        print("\n=== SESSIONS ===")
        res_sess = await db.execute(select(Session))
        for s in res_sess.scalars().all():
            fac_email = "None"
            if s.faculty_id:
                q_fac = await db.execute(select(User).where(User.id == s.faculty_id))
                fac = q_fac.scalars().first()
                fac_email = fac.email if fac else "Not Found"
            print(f"Session: {s.course_id} @ Room {s.location} | Status: {s.status.value} | Faculty: {fac_email}")

if __name__ == "__main__":
    asyncio.run(run())
