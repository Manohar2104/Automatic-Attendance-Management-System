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
