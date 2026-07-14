"""
Session auto-start and auto-end scheduler.
Runs periodically to:
1. Auto-start sessions when scheduled time arrives and faculty is present
2. Auto-end sessions when scheduled end time passes
"""

import logging
from datetime import datetime, timedelta, timezone

from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import Session, SessionStatus, Event, User, RoleEnum, EventType, TimetableEntry
from app.db import get_sessionmaker

logger = logging.getLogger(__name__)


async def check_and_process_sessions(session: AsyncSession = None) -> None:
    """
    Periodically check and process session state transitions:
    - Auto-start: SCHEDULED → ACTIVE (when time arrives + faculty present)
    - Auto-end: ACTIVE → COMPLETED (when end time passes)
    """
    if session is None:
        SessionLocal = get_sessionmaker()
        async with SessionLocal() as session:
            await _auto_start_sessions(session)
            await _auto_start_timetable_sessions(session)
            await _auto_end_sessions(session)
            await session.commit()
    else:
        # Reuse provided session (for testing)
        await _auto_start_sessions(session)
        await _auto_start_timetable_sessions(session)
        await _auto_end_sessions(session)
        await session.commit()


async def _auto_start_sessions(session: AsyncSession) -> None:
    """Auto-start sessions when scheduled time arrives and faculty is present."""
    now = datetime.now(timezone.utc)

    # Find sessions that should be starting (SCHEDULED status, within time window)
    stmt = select(Session).where(
        and_(
            Session.status == SessionStatus.SCHEDULED,
            Session.scheduled_start <= now,
            Session.scheduled_end >= now,
        )
    )
    result = await session.execute(stmt)
    sessions_to_start = result.scalars().all()

    for sess in sessions_to_start:
        # Check if any faculty member is present in this location
        is_faculty_present = await _is_faculty_present_in_location(
            session, sess.location
        )

        if is_faculty_present:
            logger.info(f"Auto-starting session {sess.id} (location: {sess.location})")
            sess.status = SessionStatus.ACTIVE
            sess.actual_start = now


async def _auto_end_sessions(session: AsyncSession) -> None:
    """Auto-end active sessions when scheduled end time passes."""
    now = datetime.now(timezone.utc)

    # Find active sessions past their scheduled end time
    stmt = select(Session).where(
        and_(
            Session.status == SessionStatus.ACTIVE,
            Session.scheduled_end <= now,
        )
    )
    result = await session.execute(stmt)
    sessions_to_end = result.scalars().all()

    for sess in sessions_to_end:
        logger.info(f"Auto-ending session {sess.id}")
        sess.status = SessionStatus.COMPLETED
        sess.actual_end = now


async def _is_faculty_present_in_location(
    session: AsyncSession, location: str, minutes_ago: int = 5
) -> bool:
    """
    Check if any faculty member has been present in the location recently.
    Looks at events from the past N minutes.
    """
    cutoff_time = datetime.now(timezone.utc) - timedelta(minutes=minutes_ago)

    # Find faculty users
    stmt = select(User).where(User.role == RoleEnum.FACULTY)
    result = await session.execute(stmt)
    faculty_users = result.scalars().all()

    if not faculty_users:
        logger.debug(f"No faculty members found for location {location}")
        return False

    faculty_ids = [f.id for f in faculty_users]

    # Check for recent events from faculty in this location
    stmt = select(Event).where(
        and_(
            Event.user_id.in_(faculty_ids),
            Event.location == location,
            Event.type == EventType.ENTER,
            Event.timestamp >= cutoff_time,
        )
    )
    result = await session.execute(stmt)
    events = result.scalars().all()

    faculty_present = len(events) > 0
    logger.debug(
        f"Faculty present in {location}: {faculty_present} "
        f"({len(events)} recent ENTER events)"
    )

    return faculty_present


async def _is_teacher_present_in_location(
    session: AsyncSession, teacher_id, location: str, minutes_ago: int = 5
) -> bool:
    """Check if a specific teacher has been present in the location recently."""
    cutoff_time = datetime.now(timezone.utc) - timedelta(minutes=minutes_ago)
    stmt = select(Event).where(
        and_(
            Event.user_id == teacher_id,
            Event.location == location,
            Event.type == EventType.ENTER,
            Event.timestamp >= cutoff_time,
        )
    )
    result = await session.execute(stmt)
    events = result.scalars().all()
    return len(events) > 0


async def auto_start_timetable_session(
    session: AsyncSession, teacher_id, location: str, now_dt: datetime
) -> Session:
    """
    Check timetable for the teacher at the given location and start/create the active session if matching.
    """
    # Convert now_dt to local time for day and time comparison
    local_dt = now_dt.astimezone() if now_dt.tzinfo else now_dt
    day_of_week = local_dt.strftime("%A")
    current_time_str = local_dt.strftime("%H:%M")

    # Query for timetable entries matching this teacher and location
    stmt = select(TimetableEntry).where(
        and_(
            TimetableEntry.teacher_id == teacher_id,
            TimetableEntry.location == location,
            TimetableEntry.day_of_week == day_of_week,
        )
    )
    result = await session.execute(stmt)
    entries = result.scalars().all()

    matching_entry = None
    sh, sm, eh, em = 0, 0, 0, 0
    for entry in entries:
        try:
            esh, esm = map(int, entry.start_time.split(":"))
            eeh, eem = map(int, entry.end_time.split(":"))
        except ValueError:
            continue

        start_min = esh * 60 + esm
        end_min = eeh * 60 + eem
        current_min = local_dt.hour * 60 + local_dt.minute

        # Match window: 15 minutes before start to end
        if (start_min - 15) <= current_min <= end_min:
            matching_entry = entry
            sh, sm, eh, em = esh, esm, eeh, eem
            break

    if not matching_entry:
        return None

    # We have a matching entry! Check if a session already exists for today
    today_date = local_dt.date()
    day_start = datetime.combine(today_date, datetime.min.time()).astimezone()
    day_end = datetime.combine(today_date, datetime.max.time()).astimezone()
    day_start_utc = day_start.astimezone(timezone.utc)
    day_end_utc = day_end.astimezone(timezone.utc)

    stmt_exist = select(Session).where(
        and_(
            Session.course_id == matching_entry.course_id,
            Session.location == location,
            Session.scheduled_start >= day_start_utc,
            Session.scheduled_start <= day_end_utc,
        )
    )
    res_exist = await session.execute(stmt_exist)
    existing_sessions = res_exist.scalars().all()

    for s in existing_sessions:
        if s.status == SessionStatus.ACTIVE:
            return s
        if s.status == SessionStatus.SCHEDULED:
            logger.info(f"Transitioning scheduled session {s.id} to ACTIVE via timetable trigger")
            s.status = SessionStatus.ACTIVE
            s.actual_start = now_dt
            s.teacher_id = teacher_id
            await session.commit()
            return s
        if s.status == SessionStatus.COMPLETED:
            return s

    # No existing session today, create a new ACTIVE session
    from datetime import time as dt_time
    local_start = datetime.combine(today_date, dt_time(sh, sm)).astimezone()
    local_end = datetime.combine(today_date, dt_time(eh, em)).astimezone()
    scheduled_start_utc = local_start.astimezone(timezone.utc)
    scheduled_end_utc = local_end.astimezone(timezone.utc)

    logger.info(f"Auto-creating/starting ACTIVE session for course {matching_entry.course_id} at {location} via timetable")
    new_sess = Session(
        course_id=matching_entry.course_id,
        room_id=matching_entry.room_id,
        location=location,
        scheduled_start=scheduled_start_utc,
        scheduled_end=scheduled_end_utc,
        actual_start=now_dt,
        status=SessionStatus.ACTIVE,
        teacher_id=teacher_id,
    )
    session.add(new_sess)
    await session.commit()
    await session.refresh(new_sess)
    return new_sess


async def _auto_start_timetable_sessions(session: AsyncSession) -> None:
    """Auto-start sessions based on weekly timetable when teachers arrive in their classrooms."""
    now = datetime.now(timezone.utc)
    local_dt = now.astimezone()
    day_of_week = local_dt.strftime("%A")

    # Get all timetable entries for today
    stmt = select(TimetableEntry).where(TimetableEntry.day_of_week == day_of_week)
    result = await session.execute(stmt)
    entries = result.scalars().all()

    for entry in entries:
        # Check if this teacher is present in this classroom recently
        is_present = await _is_teacher_present_in_location(
            session, entry.teacher_id, entry.location
        )
        if is_present:
            await auto_start_timetable_session(
                session, entry.teacher_id, entry.location, now
            )
