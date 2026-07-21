import asyncio
import logging
from datetime import datetime, timezone, timedelta
from typing import Optional

from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from .db import get_sessionmaker
from .models import Session, SessionStatus, Event, EventType, User, RoleEnum

logger = logging.getLogger(__name__)


async def check_and_process_sessions(session: Optional[AsyncSession] = None) -> None:
    """
    Main entry point for session lifecycle management.
    Handles auto-starting and auto-ending sessions based on scheduled times and faculty presence.
    """
    if session is None:
        SessionLocal = get_sessionmaker()
        async with SessionLocal() as db:
            await _auto_start_sessions(db)
            await _auto_end_sessions(db)
            await db.commit()
    else:
        # Reuse provided session (for testing)
        await _auto_start_sessions(session)
        await _auto_end_sessions(session)
        await session.commit()


async def _auto_start_sessions(session: AsyncSession) -> None:
    """Auto-start sessions when scheduled time arrives and faculty is present inside the classroom."""
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
        # 1. Check if teacher is in the corridor -- if so, DO NOT start session
        in_corridor = await _is_faculty_in_corridor(session, sess.faculty_id)
        if in_corridor:
            logger.info(f"Teacher is currently in the corridor for session {sess.id} (Room {sess.location}). Session remains SCHEDULED.")
            continue

        # 2. Check if the specific assigned faculty is present in this exact room location
        #    ONLY events with location matching the session's room count.
        #    Events with location="unknown", "corridor", "hallway" are all ignored.
        is_faculty_present = await _is_faculty_present_in_location(
            session, sess.location, faculty_id=sess.faculty_id
        )

        if not is_faculty_present:
            # Grace period fallback: ONLY if FIND3 is completely offline
            # (i.e., there are ZERO recent events from this faculty anywhere).
            # If FIND3 IS sending events (even with location="unknown" or "corridor"),
            # that means scanning is active and we should wait for the teacher to enter the room.
            has_any_events = await _has_any_recent_faculty_event(
                session, faculty_id=sess.faculty_id
            )
            if not has_any_events:
                # FIND3 is truly offline — no events at all from this teacher
                minutes_past_start = (now - sess.scheduled_start).total_seconds() / 60
                if minutes_past_start >= 3:
                    logger.info(
                        f"Grace-period auto-starting session {sess.id} "
                        f"({minutes_past_start:.1f}m past start, FIND3 appears offline — no faculty events found)"
                    )
                    is_faculty_present = True
            else:
                logger.info(
                    f"Session {sess.id}: Scanning is active but teacher not in room {sess.location}. Staying SCHEDULED."
                )

        if is_faculty_present:
            logger.info(f"Auto-starting session {sess.id} (location: {sess.location})")
            sess.status = SessionStatus.ACTIVE
            sess.actual_start = now


async def _auto_end_sessions(session: AsyncSession) -> None:
    """Auto-end active sessions when scheduled end time passes."""
    now = datetime.now(timezone.utc)

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


async def _is_faculty_in_corridor(
    session: AsyncSession, faculty_id, minutes_ago: int = 5
) -> bool:
    """
    Check if the faculty member's most recent presence event is in a corridor/hallway.
    Returns True if teacher is in corridor, blocking auto-start.
    """
    if not faculty_id:
        return False

    cutoff_time = datetime.now(timezone.utc) - timedelta(minutes=minutes_ago)
    stmt = (
        select(Event)
        .where(
            and_(
                Event.user_id == faculty_id,
                Event.type == EventType.ENTER,
                Event.timestamp >= cutoff_time,
            )
        )
        .order_by(Event.timestamp.desc())
        .limit(1)
    )
    result = await session.execute(stmt)
    latest_event = result.scalars().first()

    if latest_event and latest_event.location:
        loc_lower = latest_event.location.lower()
        if "corridor" in loc_lower or "hallway" in loc_lower:
            return True

    return False


async def _is_faculty_present_in_location(
    session: AsyncSession, location: str, faculty_id = None, minutes_ago: int = 3, min_scans: int = 2
) -> bool:
    """
    Check if the assigned faculty is STABLY present inside the classroom.
    Requires at least min_scans (default 2) inside the room spanning >= 10s apart.
    """
    cutoff_time = datetime.now(timezone.utc) - timedelta(minutes=minutes_ago)

    if faculty_id:
        faculty_ids = [faculty_id]
    else:
        stmt = select(User).where(User.role == RoleEnum.FACULTY)
        result = await session.execute(stmt)
        faculty_users = result.scalars().all()
        if not faculty_users:
            return False
        faculty_ids = [f.id for f in faculty_users]

    stmt = select(Event).where(
        and_(
            Event.user_id.in_(faculty_ids),
            Event.location == location,
            Event.type == EventType.ENTER,
            Event.timestamp >= cutoff_time,
        )
    ).order_by(Event.timestamp.asc())
    result = await session.execute(stmt)
    events = result.scalars().all()

    if len(events) < min_scans:
        return False

    # Check that events span at least 10 seconds apart (confirmed sustained presence)
    timestamps = [e.timestamp for e in events if e.timestamp]
    if len(timestamps) >= 2:
        time_span = (max(timestamps) - min(timestamps)).total_seconds()
        if time_span >= 10:
            return True

    return False


async def _has_unknown_location_faculty_event(
    session: AsyncSession, faculty_id, minutes_ago: int = 10
) -> bool:
    """Fallback: checks if faculty sent events with location='unknown' (uncalibrated). Excludes corridor."""
    cutoff_time = datetime.now(timezone.utc) - timedelta(minutes=minutes_ago)
    stmt = select(Event).where(
        and_(
            Event.user_id == faculty_id,
            Event.type == EventType.ENTER,
            Event.location == "unknown",
            Event.timestamp >= cutoff_time,
        )
    )
    result = await session.execute(stmt)
    events = result.scalars().all()
    return len(events) > 0


async def _has_any_recent_faculty_event(
    session: AsyncSession, faculty_id, minutes_ago: int = 10
) -> bool:
    """
    Check if this faculty has sent ANY presence event recently, regardless of location.
    Returns True if FIND3 is active and sending events for this teacher.
    Returns False if FIND3 appears to be offline (no events at all).
    """
    if not faculty_id:
        return False
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

