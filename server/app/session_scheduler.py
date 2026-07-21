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

# find3 needs at least this many scans at a location before its prediction is
# reliable. Requiring this many ENTER events prevents a single noisy reading
# (e.g., a corridor scan briefly classified as the classroom) from activating
# a session prematurely.
MIN_LOCATION_CONFIRMATIONS = 3


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
        # Check if the specific assigned faculty is present in this location.
        # NOTE: We deliberately do NOT fall back to a location-agnostic check here.
        # If the teacher is in the corridor (a different zone), their ENTER event
        # must NOT activate a session for a room they haven't entered yet.
        is_faculty_present = await _is_faculty_present_in_location(
            session, sess.location, faculty_id=sess.faculty_id
        )

        if not is_faculty_present:
            # Grace-period fallback: only fires when find3 is COMPLETELY offline.
            # If find3 IS tracking the faculty (e.g. they are in the corridor or any
            # other mapped zone), that means find3 is working — the teacher just hasn't
            # entered the classroom yet. In that case we must NOT start the session.
            minutes_past_start = (now - sess.scheduled_start).total_seconds() / 60
            if minutes_past_start >= 3:
                find3_is_tracking = await _is_find3_tracking_faculty(
                    session, faculty_id=sess.faculty_id
                )
                if not find3_is_tracking:
                    logger.info(
                        f"Grace-period auto-starting session {sess.id} "
                        f"({minutes_past_start:.1f}m past start, find3 appears offline)"
                    )
                    is_faculty_present = True
                else:
                    logger.debug(
                        f"Session {sess.id}: faculty is being tracked by find3 "
                        f"but not in session location '{sess.location}' — not starting"
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
    session: AsyncSession, location: str, faculty_id = None, minutes_ago: int = 1
) -> bool:
    """
    Check if the faculty has been confirmed in this location within the last
    minute. Requires MIN_LOCATION_CONFIRMATIONS ENTER events to account for
    find3's need for 3 scans before its location prediction stabilises.

    Window is set to 1 minute because find3 produces 3 scans/minute — so
    all required confirmations must come from fresh, current-minute scans.
    Events older than 1 minute (e.g. from when the teacher was in the corridor)
    are intentionally excluded.
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

    faculty_present = len(events) >= MIN_LOCATION_CONFIRMATIONS
    logger.debug(
        f"Faculty (id: {faculty_id}) present in {location}: {faculty_present} "
        f"({len(events)}/{MIN_LOCATION_CONFIRMATIONS} required ENTER events)"
    )

    return faculty_present


async def _is_find3_tracking_faculty(
    session: AsyncSession, faculty_id, minutes_ago: int = 2
) -> bool:
    """
    Returns True if find3 has sent ANY recent event for this faculty member,
    regardless of location. Used exclusively to detect whether find3 is online.

    Window is 2 minutes (slightly wider than the 1-minute presence window) to
    absorb occasional scan delays without incorrectly treating find3 as offline.

    If this returns True but _is_faculty_present_in_location returns False, it
    means find3 is working but the teacher is in a different zone (e.g. corridor).
    In that case the session must NOT be auto-started — the teacher simply hasn't
    arrived at the classroom yet.
    """
    cutoff_time = datetime.now(timezone.utc) - timedelta(minutes=minutes_ago)

    if faculty_id:
        faculty_ids = [faculty_id]
    else:
        stmt = select(User).where(User.role == RoleEnum.FACULTY)
        result = await session.execute(stmt)
        faculty_ids = [u.id for u in result.scalars().all()]
        if not faculty_ids:
            return False

    stmt = select(Event).where(
        and_(
            Event.user_id.in_(faculty_ids),
            Event.type == EventType.ENTER,
            Event.timestamp >= cutoff_time,
        )
    )
    result = await session.execute(stmt)
    events = result.scalars().all()
    is_tracking = len(events) > 0
    logger.debug(
        f"find3 tracking faculty (id: {faculty_id}): {is_tracking} "
        f"({len(events)} events in last {minutes_ago}m, any location)"
    )
    return is_tracking
