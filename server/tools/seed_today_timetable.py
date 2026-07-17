import sys
import os
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

async def clear_and_seed():
    SessionLocal = get_sessionmaker()
    async with SessionLocal() as db:
        print("=== 1. Cleaning Database (Clean Slate) ===")
        # Delete dependent attendance records first
        await db.execute(delete(AttendanceOverride))
        await db.execute(delete(Attendance))
        await db.execute(delete(Event))
        await db.execute(delete(Session))
        await db.commit()
        print("Deleted all old sessions, events, attendances, and overrides successfully.")

        print("\n=== 2. Resolving Faculty Account ===")
        # Get or create faculty user
        q = await db.execute(select(User).where(User.email == "professor@pes.edu"))
        fac_user = q.scalars().first()
        if not fac_user:
            fac_user = User(
                email="professor@pes.edu",
                password_hash=get_password_hash("123456"),
                role=RoleEnum.FACULTY
            )
            db.add(fac_user)
            await db.flush()
            print(f"Created Faculty: {fac_user.email}")
        else:
            print(f"Faculty already exists: {fac_user.email}")

        print("\n=== 3. Seeding Today's Timetable (July 16, 2026) ===")
        now = datetime.now(timezone.utc)
        
        # Session 1: Started 10 minutes ago, ends in 50 minutes (Location 211)
        sess1 = Session(
            course_id="CS301-Theory",
            room_id="211",
            location="211",
            scheduled_start=now - timedelta(minutes=10),
            scheduled_end=now + timedelta(minutes=50),
            status=SessionStatus.SCHEDULED,
            faculty_id=fac_user.id
        )
        
        # Session 2: Scheduled to start in 1 hour (Location 212)
        sess2 = Session(
            course_id="CS302-Lab",
            room_id="212",
            location="212",
            scheduled_start=now + timedelta(hours=1),
            scheduled_end=now + timedelta(hours=2, minutes=30),
            status=SessionStatus.SCHEDULED,
            faculty_id=fac_user.id
        )

        # Session 3: Scheduled to start in 3 hours (Location 211)
        sess3 = Session(
            course_id="CS303-Seminar",
            room_id="211",
            location="211",
            scheduled_start=now + timedelta(hours=3),
            scheduled_end=now + timedelta(hours=4),
            status=SessionStatus.SCHEDULED,
            faculty_id=fac_user.id
        )

        db.add_all([sess1, sess2, sess3])
        await db.commit()
        
        # Fetch back to show details
        print("\nSuccessfully scheduled today's timetable sessions (Assigned to professor@pes.edu):")
        print("-" * 75)
        for s in [sess1, sess2, sess3]:
            start_str = s.scheduled_start.astimezone().strftime("%I:%M %p")
            end_str = s.scheduled_end.astimezone().strftime("%I:%M %p")
            print(f"Session ID: {s.id}\n  Course: {s.course_id} | Location: {s.location} | Status: {s.status.value}\n  Time: {start_str} - {end_str}")
            print("-" * 75)

if __name__ == '__main__':
    asyncio.run(clear_and_seed())
