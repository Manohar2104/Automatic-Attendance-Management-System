import pytest
import asyncio
import uuid
import datetime
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import sessionmaker
from sqlalchemy import select

from app.main import app
from app.db import Base, get_db
from app.models import User, RoleEnum, TimetableEntry, Session, SessionStatus, DeviceBinding, Event, Attendance
from app.session_scheduler import check_and_process_sessions, auto_start_timetable_session


@pytest.fixture
async def client(test_db):
    """Create async test client with database dependency override."""
    async def override_get_db():
        async with test_db() as session:
            yield session
            
    app.dependency_overrides[get_db] = override_get_db
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.pop(get_db, None)


async def get_auth_headers(client: AsyncClient, email: str, role: str = "STUDENT"):
    # Register and login helper
    await client.post(
        "/register",
        json={"email": email, "password": "password123", "role": role}
    )
    resp = await client.post(
        "/login",
        json={"email": email, "password": "password123"}
    )
    token = resp.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


class TestTimetable:
    """Test suite for Timetable features."""

    async def test_timetable_crud(self, client):
        teacher_headers = await get_auth_headers(client, "teacher@test.com", "FACULTY")
        student_headers = await get_auth_headers(client, "student@test.com", "STUDENT")

        # 1. Create Timetable Entry (FACULTY)
        resp = await client.post(
            "/timetable",
            headers=teacher_headers,
            json={
                "course_id": "CS101",
                "room_id": "101",
                "location": "Room 101",
                "day_of_week": "Monday",
                "start_time": "09:00",
                "end_time": "10:30"
            }
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["course_id"] == "CS101"
        assert data["day_of_week"] == "Monday"
        entry_id = data["id"]

        # 2. STUDENT trying to create timetable -> 403
        resp_stud = await client.post(
            "/timetable",
            headers=student_headers,
            json={
                "course_id": "CS101",
                "room_id": "101",
                "location": "Room 101",
                "day_of_week": "Monday",
                "start_time": "09:00",
                "end_time": "10:30"
            }
        )
        assert resp_stud.status_code == 403

        # 3. List Timetable Entries
        resp_list = await client.get("/timetable", headers=teacher_headers)
        assert resp_list.status_code == 200
        assert len(resp_list.json()) == 1
        assert resp_list.json()[0]["id"] == entry_id

        # 4. Delete Timetable Entry
        resp_del = await client.delete(f"/timetable/{entry_id}", headers=teacher_headers)
        assert resp_del.status_code == 200
        assert resp_del.json() == {"status": "deleted"}

        # 5. List after delete should be empty
        resp_list2 = await client.get("/timetable", headers=teacher_headers)
        assert len(resp_list2.json()) == 0

    async def test_timetable_bulk_upload(self, client):
        teacher_headers = await get_auth_headers(client, "teacher_bulk@test.com", "FACULTY")
        
        resp = await client.post(
            "/timetable/bulk",
            headers=teacher_headers,
            json=[
                {
                    "course_id": "CS201",
                    "room_id": "201",
                    "location": "Room 201",
                    "day_of_week": "Tuesday",
                    "start_time": "10:00",
                    "end_time": "11:30"
                },
                {
                    "course_id": "CS202",
                    "room_id": "202",
                    "location": "Room 202",
                    "day_of_week": "Wednesday",
                    "start_time": "14:00",
                    "end_time": "15:30"
                }
            ]
        )
        assert resp.status_code == 200
        assert resp.json() == {"created": 2}

        # Verify listed count
        resp_list = await client.get("/timetable", headers=teacher_headers)
        assert len(resp_list.json()) == 2

    async def test_teacher_auto_start_on_presence(self, client, test_db):
        # 1. Create a teacher and bind a device
        teacher_headers = await get_auth_headers(client, "teacher_presence@test.com", "FACULTY")
        
        # Get user info
        me_resp = await client.get("/users/me", headers=teacher_headers)
        teacher_id = me_resp.json()["id"]

        # Bind teacher device
        device_fingerprint = "teacher_dev_1"
        await client.post(
            "/register-device",
            headers=teacher_headers,
            json={"device_fingerprint": device_fingerprint}
        )

        # 2. Add a timetable entry: e.g. Monday 09:00 - 10:30 in Room 101
        # To make time logic consistent in tests, we determine what local day and time we will simulate
        # Let's target a Monday at 09:05:00 local time.
        # Construct Monday 09:05:00 in system timezone, convert to UTC
        local_monday = datetime.datetime.now()
        while local_monday.strftime("%A") != "Monday":
            local_monday += datetime.timedelta(days=1)
        
        simulated_time_local = datetime.datetime.combine(local_monday.date(), datetime.time(9, 5)).astimezone()
        simulated_time_utc = simulated_time_local.astimezone(datetime.timezone.utc)

        await client.post(
            "/timetable",
            headers=teacher_headers,
            json={
                "course_id": "CS301",
                "room_id": "101",
                "location": "Room 101",
                "day_of_week": "Monday",
                "start_time": "09:00",
                "end_time": "10:30"
            }
        )

        # 3. Simulate teacher ENTER event at Room 101 with simulated_time_utc
        # We call /presence (since we resolved device mapping, device_fingerprint maps to teacher_id)
        resp_pres = await client.post(
            "/presence",
            json={
                "device_fingerprint": device_fingerprint,
                "location": "Room 101",
                "event_type": "ENTER",
                "timestamp": simulated_time_utc.isoformat()
            }
        )
        assert resp_pres.status_code == 200

        # 4. Verify that an ACTIVE Session has been started in DB
        async with test_db() as session:
            stmt = select(Session).where(Session.course_id == "CS301")
            res = await session.execute(stmt)
            sess = res.scalars().first()
            assert sess is not None
            assert sess.status == SessionStatus.ACTIVE
            assert sess.location == "Room 101"
            assert sess.teacher_id == uuid.UUID(teacher_id)

    async def test_teacher_auto_start_periodic_scheduler(self, client, test_db):
        # 1. Register teacher and add timetable
        teacher_headers = await get_auth_headers(client, "teacher_scheduler@test.com", "FACULTY")
        me_resp = await client.get("/users/me", headers=teacher_headers)
        teacher_id = me_resp.json()["id"]

        # Bind teacher device
        device_fingerprint = "teacher_dev_2"
        await client.post(
            "/register-device",
            headers=teacher_headers,
            json={"device_fingerprint": device_fingerprint}
        )

        # Get local next Monday at 09:05
        local_monday = datetime.datetime.now()
        while local_monday.strftime("%A") != "Monday":
            local_monday += datetime.timedelta(days=1)
        
        simulated_time_local = datetime.datetime.combine(local_monday.date(), datetime.time(9, 5)).astimezone()
        simulated_time_utc = simulated_time_local.astimezone(datetime.timezone.utc)

        # Add timetable
        await client.post(
            "/timetable",
            headers=teacher_headers,
            json={
                "course_id": "CS302",
                "room_id": "102",
                "location": "Room 102",
                "day_of_week": "Monday",
                "start_time": "09:00",
                "end_time": "10:30"
            }
        )

        # Log an ENTER event slightly before/at start time in DB directly (past 2 minutes)
        # So teacher is "recently present"
        async with test_db() as db_sess:
            event_ts = simulated_time_utc - datetime.timedelta(minutes=2)
            ev = Event(
                user_id=uuid.UUID(teacher_id),
                location="Room 102",
                type="ENTER",
                timestamp=event_ts
            )
            db_sess.add(ev)
            await db_sess.commit()

        # Run periodic scheduler with override db session using simulated_time_utc
        # We need to temporarily mock/override datetime.now in the scheduler, or run it
        # Since scheduler uses datetime.now(timezone.utc), let's mock it
        # However, to be simple, let's test check_and_process_sessions directly.
        # To test the helper _auto_start_timetable_sessions, we run check_and_process_sessions
        # wait, let's see. If the local system time isn't Monday 9am right now, datetime.now() inside
        # _auto_start_timetable_sessions will not match.
        # But wait! We can write a unit test specifically calling the helper with a custom now,
        # or we can test the auto_start_timetable_session function directly, which is clean and covers the logic!
        async with test_db() as db_sess:
            started = await auto_start_timetable_session(
                db_sess, uuid.UUID(teacher_id), "Room 102", simulated_time_utc
            )
            assert started is not None
            assert started.status == SessionStatus.ACTIVE
            assert started.course_id == "CS302"
            assert started.teacher_id == uuid.UUID(teacher_id)

    async def test_teacher_attendance_override_permissions(self, client, test_db):
        # 1. Create Teacher A, Teacher B, and a Student
        tA_headers = await get_auth_headers(client, "teacherA@test.com", "FACULTY")
        tB_headers = await get_auth_headers(client, "teacherB@test.com", "FACULTY")
        stud_headers = await get_auth_headers(client, "student_override@test.com", "STUDENT")

        me_resp = await client.get("/users/me", headers=stud_headers)
        student_id = me_resp.json()["id"]

        # Create session assigned to Teacher A
        admin_headers = await get_auth_headers(client, "admin_override@test.com", "ADMIN")
        me_ta = await client.get("/users/me", headers=tA_headers)
        teacherA_id = me_ta.json()["id"]

        start_time = datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(minutes=30)
        end_time = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=1)

        resp_sess = await client.post(
            "/sessions/create",
            headers=admin_headers,
            params={
                "course_id": "CS401",
                "room_id": "401",
                "location": "Room 401",
                "scheduled_start": start_time.isoformat(),
                "scheduled_end": end_time.isoformat(),
                "teacher_id": teacherA_id
            }
        )
        assert resp_sess.status_code == 200
        session_id = resp_sess.json()["id"]

        # 2. Teacher A (assigned teacher) overrides student attendance -> 200
        resp_ov = await client.post(
            f"/sessions/{session_id}/override-attendance",
            headers=tA_headers,
            params={
                "student_id": student_id,
                "override_status": "PRESENT",
                "justification": "Checked manually"
            }
        )
        assert resp_ov.status_code == 200
        assert resp_ov.json()["new_status"] == "PRESENT"

        # 3. Teacher B (unassigned teacher) overrides student attendance -> 403 Forbidden
        resp_ov_bad = await client.post(
            f"/sessions/{session_id}/override-attendance",
            headers=tB_headers,
            params={
                "student_id": student_id,
                "override_status": "ABSENT",
                "justification": "Teacher B override"
            }
        )
        assert resp_ov_bad.status_code == 403

        # 4. Student overrides student attendance -> 403 Forbidden
        resp_ov_stud = await client.post(
            f"/sessions/{session_id}/override-attendance",
            headers=stud_headers,
            params={
                "student_id": student_id,
                "override_status": "ABSENT",
                "justification": "Self-override"
            }
        )
        assert resp_ov_stud.status_code == 403

    async def test_student_attendance_dashboard(self, client, test_db):
        student_headers = await get_auth_headers(client, "student_dashboard@test.com", "STUDENT")
        me_resp = await client.get("/users/me", headers=student_headers)
        student_id = me_resp.json()["id"]

        admin_headers = await get_auth_headers(client, "admin_dashboard@test.com", "ADMIN")

        # Create scheduled session today
        today_date = datetime.datetime.now().date()
        start_time = datetime.datetime.combine(today_date, datetime.time(10, 0)).astimezone(datetime.timezone.utc)
        end_time = datetime.datetime.combine(today_date, datetime.time(11, 0)).astimezone(datetime.timezone.utc)

        resp_sess = await client.post(
            "/sessions/create",
            headers=admin_headers,
            params={
                "course_id": "CS501",
                "room_id": "501",
                "location": "Room 501",
                "scheduled_start": start_time.isoformat(),
                "scheduled_end": end_time.isoformat()
            }
        )
        assert resp_sess.status_code == 200
        session_id = resp_sess.json()["id"]

        # Create attendance record
        async with test_db() as db_sess:
            att = Attendance(
                student_id=uuid.UUID(student_id),
                session_id=session_id,
                score=100.0,
                status="PRESENT"
            )
            db_sess.add(att)
            await db_sess.commit()

        # Query Student Daily Attendance
        resp_dash = await client.get(
            f"/student/attendance?date={today_date.isoformat()}",
            headers=student_headers
        )
        assert resp_dash.status_code == 200
        data = resp_dash.json()
        assert len(data) == 1
        assert data[0]["course_id"] == "CS501"
        assert data[0]["status"] == "PRESENT"
