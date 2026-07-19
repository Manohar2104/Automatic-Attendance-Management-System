import datetime
import logging
import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from .auth import get_password_hash
from .models import (
    BleAttendanceMode,
    BleSession,
    BleSessionStatus,
    RoleEnum,
    Session as LegacySession,
    SessionStatus as LegacySessionStatus,
    User,
)

logger = logging.getLogger(__name__)


async def seed_demo_data(db: AsyncSession) -> None:
    """Ensure the local demo accounts and session exist without touching production rows."""
    now = datetime.datetime.now(datetime.timezone.utc)
    created_items: list[str] = []

    faculty, faculty_created = await _ensure_user(
        db,
        email="faculty@example.com",
        password="faculty123",
        role=RoleEnum.FACULTY,
    )
    if faculty_created:
        created_items.append("faculty user")

    student, student_created = await _ensure_user(
        db,
        email="student@example.com",
        password="student123",
        role=RoleEnum.STUDENT,
    )
    if student_created:
        created_items.append("student user")

    active_legacy_result = await db.execute(
        select(LegacySession)
        .where(LegacySession.status == LegacySessionStatus.ACTIVE)
        .order_by(LegacySession.scheduled_start.desc())
    )
    active_ble_result = await db.execute(
        select(BleSession)
        .where(BleSession.status == BleSessionStatus.ACTIVE)
        .order_by(BleSession.start_time.desc())
    )

    active_legacy_session = active_legacy_result.scalars().first()
    active_ble_session = active_ble_result.scalars().first()

    if active_legacy_session is None and active_ble_session is None and faculty is not None:
        session_id = uuid.uuid4()
        scheduled_start = now - datetime.timedelta(minutes=5)
        scheduled_end = now + datetime.timedelta(minutes=55)

        db.add(
            LegacySession(
                id=session_id,
                course_id="CS101",
                room_id="Room101",
                location="Room101",
                scheduled_start=scheduled_start,
                scheduled_end=scheduled_end,
                actual_start=scheduled_start,
                actual_end=None,
                status=LegacySessionStatus.ACTIVE,
            )
        )
        db.add(
            BleSession(
                id=session_id,
                course_id="CS101",
                teacher_id=faculty.id,
                attendance_mode=BleAttendanceMode.BLE,
                status=BleSessionStatus.ACTIVE,
                start_time=scheduled_start,
                end_time=scheduled_end,
            )
        )
        created_items.append("active demo session")
    elif active_legacy_session is None and active_ble_session is not None:
        scheduled_start = active_ble_session.start_time
        scheduled_end = active_ble_session.end_time or (scheduled_start + datetime.timedelta(hours=1))
        minimum_future_end = now + datetime.timedelta(minutes=55)
        if scheduled_end <= minimum_future_end:
            scheduled_end = minimum_future_end
        existing_legacy_result = await db.execute(
            select(LegacySession).where(LegacySession.id == active_ble_session.id)
        )
        existing_legacy_session = existing_legacy_result.scalars().first()
        if existing_legacy_session is None:
            db.add(
                LegacySession(
                    id=active_ble_session.id,
                    course_id=active_ble_session.course_id,
                    room_id="Room101",
                    location="Room101",
                    scheduled_start=scheduled_start,
                    scheduled_end=scheduled_end,
                    actual_start=scheduled_start,
                    actual_end=None,
                    status=LegacySessionStatus.ACTIVE,
                )
            )
        else:
            existing_legacy_session.course_id = active_ble_session.course_id
            existing_legacy_session.room_id = "Room101"
            existing_legacy_session.location = "Room101"
            existing_legacy_session.scheduled_start = scheduled_start
            existing_legacy_session.scheduled_end = scheduled_end
            existing_legacy_session.actual_start = scheduled_start
            existing_legacy_session.actual_end = None
            existing_legacy_session.status = LegacySessionStatus.ACTIVE
        created_items.append("legacy session backfill")
    elif active_legacy_session is not None and active_ble_session is None and faculty is not None:
        db.add(
            BleSession(
                id=active_legacy_session.id,
                course_id=active_legacy_session.course_id,
                teacher_id=faculty.id,
                attendance_mode=BleAttendanceMode.BLE,
                status=BleSessionStatus.ACTIVE,
                start_time=active_legacy_session.actual_start or active_legacy_session.scheduled_start,
                end_time=active_legacy_session.actual_end or active_legacy_session.scheduled_end,
            )
        )
        created_items.append("ble session backfill")

    if created_items:
        await db.commit()
        logger.info("Seeded demo data: %s", ", ".join(created_items))
    else:
        logger.info("Demo data already present; seed skipped")


async def _ensure_user(
    db: AsyncSession,
    *,
    email: str,
    password: str,
    role: RoleEnum,
) -> tuple[User, bool]:
    result = await db.execute(select(User).where(User.email == email))
    existing = result.scalars().first()
    if existing is not None:
        return existing, False

    user = User(
        email=email,
        password_hash=get_password_hash(password),
        role=role,
    )
    db.add(user)
    await db.flush()
    return user, True