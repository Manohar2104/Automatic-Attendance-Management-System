import asyncio
import os
import sys
from datetime import datetime, timezone

# Add parent directory to path so we can import 'app'
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from app.db import get_sessionmaker
from app.models import Session, SessionStatus
from sqlalchemy import select, update

async def main():
    SessionLocal = get_sessionmaker()
    async with SessionLocal() as db:
        # Find all SCHEDULED sessions
        q = await db.execute(
            select(Session).where(Session.status == SessionStatus.SCHEDULED)
        )
        sessions = q.scalars().all()
        
        if not sessions:
            print("No SCHEDULED sessions found to activate.")
            return
        
        print(f"Found {len(sessions)} SCHEDULED session(s). Activating now...")
        print("-" * 60)
        
        for sess in sessions:
            sess.status = SessionStatus.ACTIVE
            sess.actual_start = datetime.now(timezone.utc)
            db.add(sess)
            print(f"Activated Session: {sess.id} (Course: {sess.course_id}, Room: {sess.location})")
            
        await db.commit()
        print("-" * 60)
        print("Success! Sessions are now ACTIVE. Scans will be correctly linked.")

if __name__ == "__main__":
    asyncio.run(main())
