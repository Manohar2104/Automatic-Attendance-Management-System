import asyncio
import os
import sys
from datetime import datetime

# Add parent directory to path so we can import 'app'
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from app.db import get_sessionmaker
from app.models import Event, Attendance, AttendanceOverride, User
from sqlalchemy import select, desc

async def dump_events(db):
    print("\n[NEONDB] LATEST 10 PRESENCE EVENTS")
    print("=" * 100)
    q = await db.execute(
        select(Event, User.email)
        .outerjoin(User, Event.user_id == User.id)
        .order_by(desc(Event.timestamp))
        .limit(10)
    )
    results = q.all()
    if not results:
        print("No events recorded in database yet.")
    else:
        print(f"{'Timestamp (UTC)':<25} | {'Student Email':<25} | {'Session ID':<36} | {'Type':<6} | {'Predicted Room':<15}")
        print("-" * 100)
        for ev, email in results:
            email_str = email if email else "Anonymous"
            session_str = str(ev.session_id)[:8] + "..." if ev.session_id else "None"
            print(f"{ev.timestamp.isoformat():<25} | {email_str:<25} | {session_str:<36} | {ev.type.name:<6} | {ev.location:<15}")

async def dump_attendances(db):
    print("\n[NEONDB] LATEST 10 ATTENDANCE COMPUTATIONS")
    print("=" * 100)
    q = await db.execute(
        select(Attendance, User.email)
        .join(User, Attendance.student_id == User.id)
        .order_by(desc(Attendance.created_at))
        .limit(10)
    )
    results = q.all()
    if not results:
        print("No attendance records computed in database yet.")
    else:
        print(f"{'Computed At (UTC)':<25} | {'Student Email':<25} | {'Session ID':<36} | {'Score':<6} | {'Status':<10}")
        print("-" * 100)
        for att, email in results:
            session_str = str(att.session_id)[:8] + "..." if att.session_id else "None"
            print(f"{att.created_at.isoformat():<25} | {email:<25} | {session_str:<36} | {att.score:<6.1f} | {att.status.name:<10}")

async def dump_overrides(db):
    print("\n[NEONDB] MANUAL ATTENDANCE OVERRIDES (AUDIT TRAIL)")
    print("=" * 100)
    q = await db.execute(
        select(AttendanceOverride, Attendance.session_id, User.email)
        .join(Attendance, AttendanceOverride.attendance_id == Attendance.id)
        .join(User, Attendance.student_id == User.id)
        .order_by(desc(AttendanceOverride.created_at))
    )
    results = q.all()
    if not results:
        print("No manual overrides registered in database yet.")
    else:
        print(f"{'Override At (UTC)':<25} | {'Student Email':<25} | {'Session ID':<10} | {'Old Status':<10} | {'New Status':<10} | {'Justification'}")
        print("-" * 120)
        for ov, session_id, email in results:
            session_str = str(session_id)[:8] + "..." if session_id else "None"
            print(f"{ov.created_at.isoformat():<25} | {email:<25} | {session_str:<10} | {ov.original_status.name:<10} | {ov.override_status.name:<10} | {ov.justification}")

async def main():
    SessionLocal = get_sessionmaker()
    async with SessionLocal() as db:
        await dump_events(db)
        await dump_attendances(db)
        await dump_overrides(db)

if __name__ == "__main__":
    asyncio.run(main())
