import sys
import os
import json
import asyncio
from datetime import datetime, timezone, timedelta

# Add parent directory to path to enable imports
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
dotenv_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '.env')
load_dotenv(dotenv_path)

from sqlalchemy import delete, select
from app.db import get_sessionmaker
from app.models import Session, SessionStatus, Attendance, AttendanceOverride, Event, User, RoleEnum
from app.auth import get_password_hash

async def seed_sessions():
    json_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'test_sessions.json')
    with open(json_path, 'r') as f:
        data = json.load(f)

    SessionLocal = get_sessionmaker()
    async with SessionLocal() as db:
        print("=== Clearing Existing Data ===")
        await db.execute(delete(AttendanceOverride))
        await db.execute(delete(Attendance))
        await db.execute(delete(Event))
        await db.execute(delete(Session))
        await db.commit()
        print("✓ Database tables cleared.")

        print("\n=== Resolving Faculty Account ===")
        q = await db.execute(select(User).where(User.email == "professor@pes.edu"))
        fac = q.scalars().first()
        if not fac:
            fac = User(
                email="professor@pes.edu",
                password_hash=get_password_hash("123456"),
                role=RoleEnum.FACULTY
            )
            db.add(fac)
            await db.flush()
            print(f"✓ Created faculty: {fac.email}")
        else:
            print(f"✓ Faculty exists: {fac.email}")

        print("\n=== Seeding 3 Sessions (10 min each, relative to NOW) ===")
        now = datetime.now(timezone.utc)
        
        sessions_added = []
        for idx, item in enumerate(data):
            offset = item.get("offset_start_minutes", idx * 10)
            duration = item.get("duration_minutes", 10)
            
            start_utc = now + timedelta(minutes=offset)
            end_utc = start_utc + timedelta(minutes=duration)
            
            # Determine status based on current time
            if now > end_utc:
                status = SessionStatus.COMPLETED
            elif now >= start_utc:
                status = SessionStatus.ACTIVE
            else:
                status = SessionStatus.SCHEDULED

            sess = Session(
                course_id=item["course_id"],
                room_id=item["room"],
                location=item["room"],
                scheduled_start=start_utc,
                scheduled_end=end_utc,
                status=status,
                faculty_id=fac.id,
            )
            db.add(sess)
            sessions_added.append((sess, start_utc, end_utc))

        await db.commit()

        IST_OFFSET = timedelta(hours=5, minutes=30)
        print("-" * 75)
        for sess, start_utc, end_utc in sessions_added:
            start_ist = (start_utc + IST_OFFSET).strftime("%Y-%m-%d %I:%M:%S %p")
            end_ist   = (end_utc + IST_OFFSET).strftime("%Y-%m-%d %I:%M:%S %p")
            print(f"Session: {sess.course_id:<18} | Room: {sess.location:<6} | Start: {start_ist} – End: {end_ist} | [{sess.status.value}]")
        print("-" * 75)
        print("✅ Database successfully seeded with 3 test sessions.")

if __name__ == '__main__':
    asyncio.run(seed_sessions())
