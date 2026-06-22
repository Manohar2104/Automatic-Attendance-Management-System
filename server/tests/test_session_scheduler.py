"""Test session auto-start/auto-end scheduler."""
import pytest
from datetime import datetime, timedelta, timezone
from uuid import uuid4
from sqlalchemy import select
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker

from app.db import Base
from app.models import User, Session, SessionStatus, Event, EventType, RoleEnum
from app.auth import get_password_hash
from app.session_scheduler import check_and_process_sessions


@pytest.fixture
async def test_db():
    """Create test database with all tables."""
    engine = create_async_engine("sqlite+aiosqlite:///:memory:", echo=False)
    SessionLocal = sessionmaker(bind=engine, class_=AsyncSession, expire_on_commit=False)
    
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    
    yield SessionLocal
    await engine.dispose()


@pytest.mark.asyncio
async def test_auto_start_session_with_faculty_present(test_db):
    """Test that sessions auto-start when faculty is present."""
    faculty_id = uuid4()
    session_id = None
    
    async with test_db() as session:
        # Create faculty user
        faculty = User(
            id=faculty_id,
            email="faculty@test.com",
            password_hash=get_password_hash("password"),
            role=RoleEnum.FACULTY
        )
        session.add(faculty)
        
        # Create session (scheduled to start 1 minute ago, end in 1 hour)
        now = datetime.now(timezone.utc)
        sess = Session(
            course_id="CS101",
            room_id="room-1",
            location="Room 101",
            scheduled_start=now - timedelta(minutes=1),
            scheduled_end=now + timedelta(hours=1),
            status=SessionStatus.SCHEDULED
        )
        session.add(sess)
        
        # Add faculty ENTER event in the location (recent)
        event = Event(
            user_id=faculty_id,
            session_id=str(sess.id),
            type=EventType.ENTER,
            location="Room 101",
            timestamp=now - timedelta(seconds=30)
        )
        session.add(event)
        
        await session.commit()
        session_id = sess.id
        
        # Run scheduler with test session
        await check_and_process_sessions(session)
        
        # After scheduler - session should be auto-started
        result = await session.execute(select(Session).where(Session.id == session_id))
        sess = result.scalars().first()
        assert sess.status == SessionStatus.ACTIVE
        assert sess.actual_start is not None


@pytest.mark.asyncio
async def test_auto_end_session_past_end_time(test_db):
    """Test that sessions auto-end when end time passes."""
    async with test_db() as session:
        # Create active session that ended 1 minute ago
        now = datetime.now(timezone.utc)
        sess = Session(
            course_id="CS101",
            room_id="room-1",
            location="Room 101",
            scheduled_start=now - timedelta(hours=1),
            scheduled_end=now - timedelta(minutes=1),  # Ended 1 minute ago
            status=SessionStatus.ACTIVE,
            actual_start=now - timedelta(hours=1)
        )
        session.add(sess)
        await session.commit()
        session_id = sess.id
        
        # Run scheduler with test session
        await check_and_process_sessions(session)
        
        # After scheduler - session should be completed
        result = await session.execute(select(Session).where(Session.id == session_id))
        sess = result.scalars().first()
        assert sess.status == SessionStatus.COMPLETED
        assert sess.actual_end is not None


@pytest.mark.asyncio
async def test_no_auto_start_without_faculty(test_db):
    """Test that sessions don't auto-start without faculty present."""
    async with test_db() as session:
        # Create session with no faculty present
        now = datetime.now(timezone.utc)
        sess = Session(
            course_id="CS101",
            room_id="room-1",
            location="Room 101",
            scheduled_start=now - timedelta(minutes=1),
            scheduled_end=now + timedelta(hours=1),
            status=SessionStatus.SCHEDULED
        )
        session.add(sess)
        await session.commit()
        session_id = sess.id
        
        # Run scheduler with test session
        await check_and_process_sessions(session)
        
        # Session should still be SCHEDULED
        result = await session.execute(select(Session).where(Session.id == session_id))
        sess = result.scalars().first()
        assert sess.status == SessionStatus.SCHEDULED
        assert sess.actual_start is None
