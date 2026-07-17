import asyncio
import sys
sys.path.append("/home/abhijith/projects/isfcr/Automatic-Attendance-Management-System/server")
from app.db import get_sessionmaker
from app.models import DeviceBinding, User
from sqlalchemy import select

async def run():
    SessionLocal = get_sessionmaker()
    async with SessionLocal() as db:
        print("=== DEVICE BINDINGS ===")
        stmt = select(DeviceBinding)
        res = await db.execute(stmt)
        for binding in res.scalars().all():
            u_email = "Unknown"
            if binding.user_id:
                q_u = await db.execute(select(User).where(User.id == binding.user_id))
                u = q_u.scalars().first()
                u_email = u.email if u else "Not Found"
            print(f"Binding: {binding.device_fingerprint} -> User: {u_email} | Status: {binding.status}")

if __name__ == "__main__":
    asyncio.run(run())
