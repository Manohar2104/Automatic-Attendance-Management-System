"""
Seed today's timetable (July 17, 2026).
- Clears ALL existing sessions, events, attendance, overrides.
- Creates test faculty account (professor@pes.edu / 123456).
- Seeds 5 realistic PES class sessions spread across today.
"""
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


# ─────────────────────────────────────────────
#  Timetable definition  (IST = UTC+5:30)
#  Format: (course_id, room_id/location, start_IST, end_IST)
# ─────────────────────────────────────────────
TODAY_IST = datetime(2026, 7, 17, tzinfo=timezone(timedelta(hours=5, minutes=30)))

def ist(hour: int, minute: int) -> datetime:
    """Return a UTC datetime for today at the given IST hour:minute."""
    return TODAY_IST.replace(hour=hour, minute=minute, second=0, microsecond=0).astimezone(timezone.utc)

TIMETABLE = [
    # (course_id,            room, start_IST,   end_IST,  faculty_email)
    ("CS601-Machine Learning", "301", ist(8, 30),  ist(9, 30),  "professor@pes.edu"),
    ("CS602-Cloud Computing",  "212", ist(9, 30),  ist(10, 30), "professor@pes.edu"),
    ("CS603-Deep Learning",    "301", ist(10, 30), ist(11, 30), "professor@pes.edu"),
    ("CS604-Big Data",         "Lab1",ist(11, 30), ist(13, 0),  "professor@pes.edu"),
    ("CS605-Soft Computing",   "212", ist(14, 0),  ist(15, 0),  "professor@pes.edu"),
]


async def clear_and_seed():
    SessionLocal = get_sessionmaker()
    async with SessionLocal() as db:

        # ── Step 1: Clean all dependent tables ──────────────────────────
        print("=== 1. Clearing Database ===")
        await db.execute(delete(AttendanceOverride))
        await db.execute(delete(Attendance))
        await db.execute(delete(Event))
        await db.execute(delete(Session))
        await db.commit()
        print("✓ Cleared sessions, events, attendance and overrides.")

        # ── Step 2: Ensure faculty account exists ────────────────────────
        print("\n=== 2. Resolving Faculty Account ===")
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
            print(f"✓ Created faculty: {fac.email}  (password: 123456)")
        else:
            print(f"✓ Faculty exists: {fac.email}")

        # ── Step 3: Seed timetable ────────────────────────────────────────
        now_utc = datetime.now(timezone.utc)
        print("\n=== 3. Seeding Today's Timetable (July 17, 2026) ===")
        print("-" * 70)

        sessions = []
        for course_id, room, start_utc, end_utc, fac_email in TIMETABLE:
            # Determine status based on current time
            if now_utc > end_utc:
                status = SessionStatus.COMPLETED
            elif now_utc >= start_utc:
                status = SessionStatus.ACTIVE
            else:
                status = SessionStatus.SCHEDULED

            sess = Session(
                course_id=course_id,
                room_id=room,
                location=room,
                scheduled_start=start_utc,
                scheduled_end=end_utc,
                status=status,
                faculty_id=fac.id,
            )
            db.add(sess)
            sessions.append((sess, start_utc, end_utc, status))

        await db.commit()

        # ── Print summary ─────────────────────────────────────────────────
        IST_OFFSET = timedelta(hours=5, minutes=30)
        for sess, start_utc, end_utc, status in sessions:
            start_ist = (start_utc + IST_OFFSET).strftime("%I:%M %p")
            end_ist   = (end_utc + IST_OFFSET).strftime("%I:%M %p")
            marker = "🟢" if status == SessionStatus.ACTIVE else ("✅" if status == SessionStatus.COMPLETED else "🔵")
            print(f"{marker}  {sess.course_id:<30}  Room {sess.location:<6}  {start_ist} – {end_ist}  [{status.value}]")

        print("-" * 70)
        print(f"\n✅ Seeded {len(sessions)} sessions for today.")
        print("   Faculty login → professor@pes.edu / 123456")


if __name__ == '__main__':
    asyncio.run(clear_and_seed())
