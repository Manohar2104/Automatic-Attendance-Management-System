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

from app.models import Session, SessionStatus, Event, User, RoleEnum, EventType
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
            await _auto_end_sessions(session)
            await session.commit()
    else:
        # Reuse provided session (for testing)
        await _auto_start_sessions(session)
        await _auto_end_sessions(session)
        await session.commit()


async def _auto_start_sessions(session: AsyncSession) -> None:
    """Auto-start sessions when scheduled time arrives and faculty is present (or find3 not deployed)."""
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
        # First try: check if the specific assigned faculty is present in this location
        is_faculty_present = await _is_faculty_present_in_location(
            session, sess.location, faculty_id=sess.faculty_id
        )

        if not is_faculty_present and sess.faculty_id:
            # Fallback: check if the faculty has ANY recent event (anywhere) —
            # this covers the case where find3 is deployed but location calibration
            # hasn't been done yet, so all events land with location="unknown".
            is_faculty_present = await _has_any_recent_faculty_event(
                session, faculty_id=sess.faculty_id
            )

        if not is_faculty_present:
            # Grace period fallback: if the session is more than 3 minutes past its
            # scheduled start and still SCHEDULED, auto-start it regardless.
            # This handles the case where find3 is not deployed at all.
            minutes_past_start = (now - sess.scheduled_start).total_seconds() / 60
            if minutes_past_start >= 3:
                logger.info(
                    f"Grace-period auto-starting session {sess.id} "
                    f"({minutes_past_start:.1f}m past start, find3 may be offline)"
                )
                is_faculty_present = True

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
    session: AsyncSession, location: str, faculty_id = None, minutes_ago: int = 5
) -> bool:
    """
    Check if the specific faculty member (or any faculty member, if faculty_id is None)
    has been present in the location recently. Looks at events from the past N minutes.
    """
    cutoff_time = datetime.now(timezone.utc) - timedelta(minutes=minutes_ago)

    if faculty_id:
        # Check presence only for the assigned faculty
        faculty_ids = [faculty_id]
    else:
        # Fallback to any faculty user
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
        f"Faculty (id: {faculty_id}) present in {location}: {faculty_present} "
        f"({len(events)} recent ENTER events)"
    )

    return faculty_present


async def _has_any_recent_faculty_event(
    session: AsyncSession, faculty_id, minutes_ago: int = 10
) -> bool:
    """
    Fallback check: has this faculty sent ANY presence event recently,
    regardless of location? Used when find3 location calibration is incomplete
    and events land with location='unknown'.
    """
    cutoff_time = datetime.now(timezone.utc) - timedelta(minutes=minutes_ago)
    stmt = select(Event).where(
        and_(
            Event.user_id == faculty_id,
            Event.type == EventType.ENTER,
            Event.timestamp >= cutoff_time,
        )
    )
    result = await session.execute(stmt)
    events = result.scalars().all()
    has_events = len(events) > 0
    logger.debug(
        f"Faculty (id: {faculty_id}) any-location presence: {has_events} "
        f"({len(events)} events in last {minutes_ago}m)"
    )
    return has_events
