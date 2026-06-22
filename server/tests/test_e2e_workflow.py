import pytest
import asyncio
import uuid
from httpx import AsyncClient, ASGITransport
from datetime import datetime, timedelta, timezone
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker
from sqlalchemy import select

from app.main import app
from app.db import Base, get_db
from app.models import User, RoleEnum, Session, SessionStatus, Event, EventType, Attendance, AttendanceStatus, AttendanceOverride
from app.find3_subscriber import Find3Subscriber
from app.session_scheduler import check_and_process_sessions

@pytest.fixture
async def test_db_setup():
    """Create test database with all tables and override dependency."""
    engine = create_async_engine("sqlite+aiosqlite:///:memory:", echo=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    
    TestingSessionLocal = sessionmaker(
        engine, class_=AsyncSession, expire_on_commit=False
    )
    
    async def override_get_db():
        async with TestingSessionLocal() as session:
            yield session
            
    app.dependency_overrides[get_db] = override_get_db
    
    # Override global sessionmaker in db.py for background tasks/subscribers
    from app import db as app_db
    old_session_local = app_db._SessionLocal
    app_db._SessionLocal = TestingSessionLocal
    
    yield TestingSessionLocal
    
    # Restore
    app_db._SessionLocal = old_session_local
    
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
        
    await engine.dispose()

@pytest.fixture
async def client(test_db_setup):
    """Create async test client."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac

@pytest.mark.asyncio
async def test_e2e_workflow(test_db_setup, client):
    # 1. Register users (Admin, Faculty, and 3 Students)
    users_data = [
        {"email": "admin_e2e@test.com", "password": "password123", "role": RoleEnum.ADMIN},
        {"email": "faculty_e2e@test.com", "password": "password123", "role": RoleEnum.FACULTY},
        {"email": "student1_e2e@test.com", "password": "password123", "role": RoleEnum.STUDENT},
        {"email": "student2_e2e@test.com", "password": "password123", "role": RoleEnum.STUDENT},
        {"email": "student3_e2e@test.com", "password": "password123", "role": RoleEnum.STUDENT},
    ]
    
    # Seed users directly to the DB to ensure role persistence
    from app.auth import get_password_hash
    
    user_ids = {}
    async with test_db_setup() as session:
        for u in users_data:
            user = User(
                email=u["email"],
                password_hash=get_password_hash(u["password"]),
                role=u["role"]
            )
            session.add(user)
            await session.commit()
            await session.refresh(user)
            user_ids[u["email"]] = user.id
            
    # Login to get tokens
    tokens = {}
    for email in [u["email"] for u in users_data]:
        resp = await client.post("/login", json={"email": email, "password": "password123"})
        assert resp.status_code == 200
        tokens[email] = resp.json()["access_token"]
        
    # Register device bindings for faculty and students
    bindings = {
        "faculty_e2e@test.com": "smart_attendance:faculty_fingerprint",
        "student1_e2e@test.com": "smart_attendance:student1_fingerprint",
        "student2_e2e@test.com": "smart_attendance:student2_fingerprint",
        "student3_e2e@test.com": "smart_attendance:student3_fingerprint",
    }
    
    for email, fingerprint in bindings.items():
        resp = await client.post(
            "/register-device",
            headers={"Authorization": f"Bearer {tokens[email]}"},
            json={"device_fingerprint": fingerprint}
        )
        assert resp.status_code == 200
        
    # 2. Faculty / Admin creates session (course, room, schedule)
    now = datetime.now(timezone.utc)
    start_time = now - timedelta(minutes=1)
    end_time = now + timedelta(hours=1)
    
    resp = await client.post(
        "/sessions/create",
        headers={"Authorization": f"Bearer {tokens['admin_e2e@test.com']}"},
        params={
            "course_id": "CS202",
            "room_id": "room-202",
            "location": "Room 202",
            "scheduled_start": start_time.isoformat(),
            "scheduled_end": end_time.isoformat(),
        }
    )
    assert resp.status_code == 200
    session_id = resp.json()["id"]
    
    # 3. Simulate faculty ENTER event (via find3 subscriber)
    sub = Find3Subscriber("ws://test")
    
    faculty_payload = {
        "sensors": {
            "d": "faculty_fingerprint",
            "f": "smart_attendance",
            "t": int((now - timedelta(seconds=10)).timestamp() * 1000)
        },
        "guesses": [
            {"location": "Room 202"}
        ]
    }
    await sub._handle_find3_message(faculty_payload)
    
    # 4. Verify scheduler auto-starts session (SCHEDULED -> ACTIVE)
    async with test_db_setup() as db_sess:
        await check_and_process_sessions(db_sess)
        
        q = await db_sess.execute(select(Session).where(Session.id == uuid.UUID(session_id)))
        sess = q.scalars().first()
        assert sess.status == SessionStatus.ACTIVE
        assert sess.actual_start is not None
        
    # 5. Simulate student ENTER/LEAVE events during session
    # We simulate a session window of 90 seconds (3 checks expected at 30s interval)
    base_time = now
    
    # Student 1: PRESENT (3 check-ins)
    for delta in [0, 45, 90]:
        t = int((base_time + timedelta(seconds=delta)).timestamp() * 1000)
        await sub._handle_find3_message({
            "sensors": {"d": "student1_fingerprint", "f": "smart_attendance", "t": t},
            "guesses": [{"location": "Room 202"}]
        })
        
    # Student 2: PARTIAL (2 check-ins -> 66.6% score)
    for delta in [0, 45]:
        t = int((base_time + timedelta(seconds=delta)).timestamp() * 1000)
        await sub._handle_find3_message({
            "sensors": {"d": "student2_fingerprint", "f": "smart_attendance", "t": t},
            "guesses": [{"location": "Room 202"}]
        })
        
    # Student 3: ABSENT (1 check-in -> 33.3% score)
    t = int(base_time.timestamp() * 1000)
    await sub._handle_find3_message({
        "sensors": {"d": "student3_fingerprint", "f": "smart_attendance", "t": t},
        "guesses": [{"location": "Room 202"}]
    })
    
    # 6. Simulate session end time -> scheduler auto-ends (ACTIVE -> COMPLETED)
    async with test_db_setup() as db_sess:
        q = await db_sess.execute(select(Session).where(Session.id == uuid.UUID(session_id)))
        sess = q.scalars().first()
        sess.scheduled_end = now - timedelta(seconds=1)
        await db_sess.commit()
        
        # Run scheduler to trigger end
        await check_and_process_sessions(db_sess)
        
        await db_sess.refresh(sess)
        assert sess.status == SessionStatus.COMPLETED
        assert sess.actual_end is not None
        
    # 7. Compute attendance with location filter
    resp = await client.post(
        f"/compute-attendance/{session_id}",
        params={"location_filter": "Room 202"}
    )
    assert resp.status_code == 200
    res_data = resp.json()
    
    # 8. Verify PRESENT/PARTIAL/ABSENT statuses
    results = res_data["results"]
    assert len(results) == 3
    
    results_by_user = {r["user_id"]: r for r in results}
    
    s1_id = str(user_ids["student1_e2e@test.com"])
    s2_id = str(user_ids["student2_e2e@test.com"])
    s3_id = str(user_ids["student3_e2e@test.com"])
    
    assert results_by_user[s1_id]["status"] == "PRESENT"
    assert results_by_user[s2_id]["status"] == "PARTIAL"
    assert results_by_user[s3_id]["status"] == "ABSENT"
    
    # 9. Admin override -> verify audit trail
    override_resp = await client.post(
        f"/sessions/{session_id}/override-attendance",
        headers={"Authorization": f"Bearer {tokens['admin_e2e@test.com']}"},
        params={
            "student_id": s3_id,
            "override_status": "PRESENT",
            "justification": "E2E override test justification"
        }
    )
    assert override_resp.status_code == 200
    assert override_resp.json()["new_status"] == "PRESENT"
    
    # Verify the override record exists in the DB
    async with test_db_setup() as db_sess:
        q = await db_sess.execute(select(AttendanceOverride))
        overrides = q.scalars().all()
        assert len(overrides) == 1
        ov = overrides[0]
        assert ov.admin_id == user_ids["admin_e2e@test.com"]
        assert ov.original_status == AttendanceStatus.ABSENT
        assert ov.override_status == AttendanceStatus.PRESENT
        assert ov.justification == "E2E override test justification"
