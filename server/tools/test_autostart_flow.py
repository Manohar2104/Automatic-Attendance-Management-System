import sys
import os
import asyncio
from datetime import datetime, timezone, timedelta

# Add parent directory to path to enable imports
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
dotenv_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '.env')
load_dotenv(dotenv_path)

from sqlalchemy import select
from app.db import get_sessionmaker
from app.models import User, RoleEnum, DeviceBinding, Session, SessionStatus, Event, EventType
from app.session_scheduler import check_and_process_sessions
from app.main import compute_attendance
from app.auth import get_password_hash

async def run_test():
    SessionLocal = get_sessionmaker()
    async with SessionLocal() as db:
        print("=== 1. Setting up Test Users ===")
        # Check or create Faculty
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

        # Check or create DeviceBinding for Faculty
        q = await db.execute(select(DeviceBinding).where(DeviceBinding.user_id == fac_user.id))
        fac_binding = q.scalars().first()
        if not fac_binding:
            fac_binding = DeviceBinding(
                user_id=fac_user.id,
                device_fingerprint="fac-phone-fingerprint"
            )
            db.add(fac_binding)
            print("Bound Faculty device fingerprint: fac-phone-fingerprint")
        
        # Check or create Student
        q = await db.execute(select(User).where(User.email == "pes1202201001@stu.pes.edu"))
        stu_user = q.scalars().first()
        if not stu_user:
            stu_user = User(
                email="pes1202201001@stu.pes.edu",
                password_hash=get_password_hash("123456"),
                role=RoleEnum.STUDENT
            )
            db.add(stu_user)
            await db.flush()
            print(f"Created Student: {stu_user.email}")
        else:
            print(f"Student already exists: {stu_user.email}")

        # Check or create DeviceBinding for Student
        q = await db.execute(select(DeviceBinding).where(DeviceBinding.user_id == stu_user.id))
        stu_binding = q.scalars().first()
        if not stu_binding:
            stu_binding = DeviceBinding(
                user_id=stu_user.id,
                device_fingerprint="stu-phone-fingerprint"
            )
            db.add(stu_binding)
            print("Bound Student device fingerprint: stu-phone-fingerprint")

        print("\n=== 2. Creating Scheduled Class Session ===")
        now = datetime.now(timezone.utc)
        start_time = now - timedelta(minutes=5)
        end_time = now + timedelta(hours=1)
        
        session_obj = Session(
            course_id="CS301-AutoStart",
            room_id="211",
            location="211",
            scheduled_start=start_time,
            scheduled_end=end_time,
            status=SessionStatus.SCHEDULED,
            faculty_id=fac_user.id
        )
        db.add(session_obj)
        await db.flush()
        print(f"Created scheduled session {session_obj.id} for Room 211 (Status: SCHEDULED)")

        print("\n=== 3. Simulating Faculty Entry (ENTER Event) ===")
        # Create an ENTER event for the Faculty member
        fac_event = Event(
            user_id=fac_user.id,
            location="211",
            type=EventType.ENTER,
            timestamp=now
        )
        db.add(fac_event)
        await db.flush()
        print(f"Simulated Faculty ENTER event at location '211' (timestamp: {now.isoformat()})")

        print("\n=== 4. Running Session Scheduler ===")
        # Commit setup before scheduler run
        await db.commit()

        # Run scheduler checks
        await check_and_process_sessions(db)
        
        # Verify status change
        await db.refresh(session_obj)
        print(f"Session status after scheduler processing: {session_obj.status.value} (expected: ACTIVE)")

        print("\n=== 5. Simulating Student Background Presence Logs ===")
        # Simulate student posting 3 ENTER events in the active session room
        for i in range(3):
            stu_event = Event(
                user_id=stu_user.id,
                session_id=str(session_obj.id),
                location="211",
                type=EventType.ENTER,
                timestamp=now + timedelta(seconds=i * 30)
            )
            db.add(stu_event)
        await db.flush()
        print("Simulated 3 student presence logs in Room 211.")

        print("\n=== 6. Computing Attendance ===")
        # Run attendance calculation
        attendance_resp = await compute_attendance(
            session_id=str(session_obj.id),
            location_filter="211",
            db=db
        )
        
        print("\n=== Attendance Result ===")
        for res in attendance_resp.results:
            print(f"Student: {res.email} | Scans: {res.enter_count} | Status: {res.status} | Score: {res.score}%")

if __name__ == '__main__':
    asyncio.run(run_test())
