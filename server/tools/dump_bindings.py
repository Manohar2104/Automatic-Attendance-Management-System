import asyncio
import os
import sys

# Add the parent directory to Python path so we can import 'app'
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from app.db import get_sessionmaker
from app.models import DeviceBinding, User
from sqlalchemy import select

async def main():
    SessionLocal = get_sessionmaker()
    async with SessionLocal() as db:
        q = await db.execute(
            select(DeviceBinding, User.email)
            .join(User, DeviceBinding.user_id == User.id)
        )
        results = q.all()
        if not results:
            print("No student devices registered in the database yet.")
            return
        
        print("\nRegistered Student Device Fingerprints:")
        print("-" * 80)
        for binding, email in results:
            print(f"Student:    {email}")
            print(f"Fingerprint: {binding.device_fingerprint}")
            print(f"Status:      {binding.status}")
            print("-" * 80)

if __name__ == "__main__":
    asyncio.run(main())
