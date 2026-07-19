from __future__ import annotations
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from uuid import uuid4
from unittest.mock import AsyncMock

import pytest

from app.auth import get_password_hash
from app.attendance.ble import ble_attendance_engine as engine_module
from app.models import BleAttendanceMode, BleSession, BleSessionStatus, RoleEnum, Session, SessionStatus, User

from tests.phase_27a.factories import make_ble_observation, make_ble_session, make_registered_device


async def seed_user_and_token(ble_db_session, client, email: str, role: RoleEnum):
    user = User(id=uuid4(), email=email, password_hash=get_password_hash("password123"), role=role)
    ble_db_session.add(user)
    await ble_db_session.commit()
    response = await client.post("/login", json={"email": email, "password": "password123"})
    assert response.status_code == 200
    return user, response.json()["access_token"]


@pytest.mark.asyncio
async def test_ble_session_api_happy_path(ble_db_session, ble_app_client, monkeypatch):
    monkeypatch.setattr(
        engine_module.BleSessionManager,
        "load_configuration",
        lambda self: type(
            "Config",
            (),
            {
                "missing_timeout_seconds": 45,
                "scan_upload_interval_seconds": 15,
                "clock_skew_seconds": 15,
                "late_threshold_seconds": 15,
            },
        )(),
    )
    monkeypatch.setattr(
        engine_module.BleAttendanceEngine,
        "finalize_session",
        AsyncMock(
            return_value=type(
                "Evaluation",
                (),
                {"present_count": 1, "absent_count": 0, "missing_count": 0, "late_count": 0},
            )(),
        ),
    )

    teacher, token = await seed_user_and_token(ble_db_session, ble_app_client, "teacher@test.com", RoleEnum.FACULTY)
    student, _ = await seed_user_and_token(ble_db_session, ble_app_client, "student@test.com", RoleEnum.STUDENT)

    session = make_ble_session(teacher_id=teacher.id, start_time=datetime.now(timezone.utc).replace(microsecond=0))
    device = make_registered_device(student_id=student.id)
    observation = make_ble_observation(session_id=session.id, student_id=student.id, last_seen=session.start_time)
    ble_db_session.add_all([session, device, observation])
    await ble_db_session.commit()

    start_response = await ble_app_client.post(
        "/api/ble/session/start",
        headers={"Authorization": f"Bearer {token}"},
        json={
            "course_id": "BLE-201",
            "teacher_id": str(teacher.id),
            "attendance_mode": "BLE",
            "start_time": datetime.now().replace(microsecond=0).isoformat(),
        },
    )
    assert start_response.status_code == 200

    sessions_response = await ble_app_client.get("/api/ble/sessions", headers={"Authorization": f"Bearer {token}"})
    assert sessions_response.status_code == 200
    assert len(sessions_response.json()["sessions"]) >= 1

    summary_response = await ble_app_client.get(f"/api/ble/session/{session.id}/summary", headers={"Authorization": f"Bearer {token}"})
    assert summary_response.status_code == 200
    assert summary_response.json()["session"]["session_id"] == str(session.id)

    observations_response = await ble_app_client.get(f"/api/ble/session/{session.id}/observations", headers={"Authorization": f"Bearer {token}"})
    assert observations_response.status_code == 200

    presence_response = await ble_app_client.get(f"/api/ble/session/{session.id}/presence", headers={"Authorization": f"Bearer {token}"})
    assert presence_response.status_code == 200

    end_response = await ble_app_client.post(
        "/api/ble/session/end",
        headers={"Authorization": f"Bearer {token}"},
        json={"session_id": str(session.id), "ended_at": datetime.now().replace(microsecond=0).isoformat()},
    )
    assert end_response.status_code == 200


@pytest.mark.asyncio
async def test_ble_presence_refreshes_without_new_packets(ble_db_session, ble_app_client, monkeypatch):
    monkeypatch.setattr(
        engine_module.BleSessionManager,
        "load_configuration",
        lambda self: type(
            "Config",
            (),
            {
                "missing_timeout_seconds": 300,
                "scan_upload_interval_seconds": 15,
                "clock_skew_seconds": 15,
                "late_threshold_seconds": 15,
            },
        )(),
    )

    teacher, token = await seed_user_and_token(ble_db_session, ble_app_client, "teacher-freshness@test.com", RoleEnum.FACULTY)
    student, _ = await seed_user_and_token(ble_db_session, ble_app_client, "student-freshness@test.com", RoleEnum.STUDENT)

    session = make_ble_session(teacher_id=teacher.id, start_time=datetime.now().replace(microsecond=0))
    device = make_registered_device(student_id=student.id)
    observation = make_ble_observation(
        session_id=session.id,
        student_id=student.id,
        last_seen=datetime.now(timezone.utc).replace(microsecond=0) - timedelta(seconds=360),
    )
    ble_db_session.add_all([session, device, observation])
    await ble_db_session.commit()

    summary_response = await ble_app_client.get(f"/api/ble/session/{session.id}/summary", headers={"Authorization": f"Bearer {token}"})
    assert summary_response.status_code == 200
    assert summary_response.json()["missing"] == 1

    presence_response = await ble_app_client.get(f"/api/ble/session/{session.id}/presence", headers={"Authorization": f"Bearer {token}"})
    assert presence_response.status_code == 200
    assert presence_response.json()["summary"]["missing"] == 1


@pytest.mark.asyncio
async def test_ble_api_auth_and_authorization_errors(ble_db_session, ble_app_client, monkeypatch):
    monkeypatch.setattr(
        engine_module.BleSessionManager,
        "load_configuration",
        lambda self: type(
            "Config",
            (),
            {
                "missing_timeout_seconds": 45,
                "scan_upload_interval_seconds": 15,
                "clock_skew_seconds": 15,
                "late_threshold_seconds": 15,
            },
        )(),
    )

    teacher, teacher_token = await seed_user_and_token(ble_db_session, ble_app_client, "teacher2@test.com", RoleEnum.FACULTY)
    student, student_token = await seed_user_and_token(ble_db_session, ble_app_client, "student2@test.com", RoleEnum.STUDENT)
    session = make_ble_session(teacher_id=teacher.id)
    ble_db_session.add(session)
    await ble_db_session.commit()

    unauthorized = await ble_app_client.get("/api/ble/sessions")
    assert unauthorized.status_code == 401

    forbidden = await ble_app_client.get("/api/ble/sessions", headers={"Authorization": f"Bearer {student_token}"})
    assert forbidden.status_code == 403

    missing = await ble_app_client.get(
        "/api/ble/session/00000000-0000-0000-0000-000000000000/summary",
        headers={"Authorization": f"Bearer {teacher_token}"},
    )
    assert missing.status_code == 404

    from app.services import ble_service as module
    monkeypatch.setattr(module.BleService, "start_session", AsyncMock(side_effect=Exception("boom")))
    failing = await ble_app_client.post(
        "/api/ble/session/start",
        headers={"Authorization": f"Bearer {teacher_token}"},
        json={
            "course_id": "BLE-201",
            "teacher_id": str(teacher.id),
            "attendance_mode": "BLE",
            "start_time": datetime.now().replace(microsecond=0).isoformat(),
        },
    )
    assert failing.status_code == 500


@pytest.mark.asyncio
async def test_ble_api_handles_bad_request_and_invalid_session(ble_db_session, ble_app_client, monkeypatch):
    monkeypatch.setattr(
        engine_module.BleSessionManager,
        "load_configuration",
        lambda self: type(
            "Config",
            (),
            {
                "missing_timeout_seconds": 45,
                "scan_upload_interval_seconds": 15,
                "clock_skew_seconds": 15,
                "late_threshold_seconds": 15,
            },
        )(),
    )

    teacher, teacher_token = await seed_user_and_token(ble_db_session, ble_app_client, "teacher3@test.com", RoleEnum.FACULTY)

    from app.services import ble_service as module
    monkeypatch.setattr(module.BleService, "start_session", AsyncMock(side_effect=Exception("bad request")))
    bad_request = await ble_app_client.post(
        "/api/ble/session/start",
        headers={"Authorization": f"Bearer {teacher_token}"},
        json={
            "course_id": "BLE-201",
            "teacher_id": str(teacher.id),
            "attendance_mode": "BLE",
            "start_time": datetime.now().replace(microsecond=0).isoformat(),
        },
    )
    assert bad_request.status_code == 500

    invalid_upload = await ble_app_client.post(
        "/api/ble/observations",
        headers={"Authorization": f"Bearer {teacher_token}"},
        json={
            "session_id": str(uuid4()),
            "teacher_id": str(teacher.id),
            "observed_at": datetime.now().replace(microsecond=0).isoformat(),
            "observations": [
                {
                    "student_id": str(uuid4()),
                    "rssi": -55,
                    "last_seen": datetime.now().replace(microsecond=0).isoformat(),
                    "rolling_token": "b" * 16,
                    "timestamp": datetime.now().replace(microsecond=0).isoformat(),
                    "hmac": "c" * 64,
                }
            ],
        },
    )
    assert invalid_upload.status_code in {404, 409}


@pytest.mark.asyncio
async def test_ble_device_registration_upserts_student_device(ble_db_session, ble_app_client):
    student, token = await seed_user_and_token(ble_db_session, ble_app_client, "student-register@test.com", RoleEnum.STUDENT)

    response = await ble_app_client.post(
        "/api/ble/register",
        headers={"Authorization": f"Bearer {token}"},
        json={
            "anonymous_ble_id": "aa11bb22cc33dd44ee55ff6677889900",
            "public_identifier": "student-register@test.com",
            "device_hash": "device-hash-abc",
        },
    )

    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True
    assert data["student_id"] == str(student.id)
    assert data["anonymous_ble_id"] == "aa11bb22cc33dd44ee55ff6677889900"

    repeat = await ble_app_client.post(
        "/api/ble/register",
        headers={"Authorization": f"Bearer {token}"},
        json={
            "anonymous_ble_id": "aa11bb22cc33dd44ee55ff6677889900",
            "public_identifier": "student-register-updated@test.com",
            "device_hash": "device-hash-abc",
        },
    )
    assert repeat.status_code == 200


@pytest.mark.asyncio
async def test_ble_device_registration_requires_student_role(ble_db_session, ble_app_client):
    teacher, token = await seed_user_and_token(ble_db_session, ble_app_client, "teacher-register@test.com", RoleEnum.FACULTY)

    response = await ble_app_client.post(
        "/api/ble/register",
        headers={"Authorization": f"Bearer {token}"},
        json={
            "anonymous_ble_id": "aa11bb22cc33dd44ee55ff6677889900",
            "public_identifier": "teacher-register@test.com",
            "device_hash": "device-hash-abc",
        },
    )

    assert response.status_code == 403


@pytest.mark.asyncio
async def test_ble_dashboard_reports_active_session_metrics(ble_db_session, ble_app_client):
    teacher, token = await seed_user_and_token(ble_db_session, ble_app_client, "teacher-dashboard@test.com", RoleEnum.FACULTY)
    student, _ = await seed_user_and_token(ble_db_session, ble_app_client, "student-dashboard@test.com", RoleEnum.STUDENT)

    from tests.phase_27a.factories import make_registered_device, make_ble_observation

    legacy_start = datetime.now().replace(microsecond=0)
    legacy_end = legacy_start + timedelta(hours=1)
    ble_start = legacy_start - timedelta(days=1)
    ble_end = ble_start + timedelta(hours=2)

    session = Session(
        id=uuid4(),
        course_id="CS101",
        room_id="sim-room-101",
        location="sim-room-101",
        scheduled_start=legacy_start,
        scheduled_end=legacy_end,
        actual_start=legacy_start,
        actual_end=None,
        status=SessionStatus.ACTIVE,
    )
    ble_session = BleSession(
        id=session.id,
        course_id=session.course_id,
        teacher_id=teacher.id,
        attendance_mode=BleAttendanceMode.BLE,
        status=BleSessionStatus.ACTIVE,
        start_time=ble_start,
        end_time=ble_end,
    )
    device = make_registered_device(student_id=student.id)
    observation = make_ble_observation(session_id=session.id, student_id=student.id)
    ble_db_session.add_all([session, ble_session, device, observation])
    await ble_db_session.commit()

    response = await ble_app_client.get(
        "/api/ble/dashboard",
        headers={"Authorization": f"Bearer {token}"},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["session"]["session_id"] == str(session.id)
    assert data["session"]["start_time"] == f"{legacy_start.isoformat()}Z"
    assert data["session"]["end_time"] == f"{legacy_end.isoformat()}Z"
    assert data["session"]["room"] == "sim-room-101"
    assert data["registered_devices"] == 1
    assert data["students_seen"] == 1
    assert data["packets_received"] == 1


@pytest.mark.asyncio
async def test_ble_dashboard_counts_all_received_packets_with_cooldown(ble_db_session, ble_app_client):
    teacher, token = await seed_user_and_token(ble_db_session, ble_app_client, "teacher-count@test.com", RoleEnum.FACULTY)
    student, _ = await seed_user_and_token(ble_db_session, ble_app_client, "student-count@test.com", RoleEnum.STUDENT)

    from tests.phase_27a.factories import make_registered_device, make_ble_session

    ble_session = make_ble_session(teacher_id=teacher.id)
    device = make_registered_device(student_id=student.id)
    ble_db_session.add_all([ble_session, device])
    await ble_db_session.commit()

    upload_response = await ble_app_client.post(
        "/api/ble/observations",
        headers={"Authorization": f"Bearer {token}"},
        json={
            "session_id": str(ble_session.id),
            "teacher_id": str(teacher.id),
            "observed_at": datetime.now().replace(microsecond=0).isoformat(),
            "observations": [
                {
                    "student_id": str(student.id),
                    "rssi": -55,
                    "last_seen": datetime.now().replace(microsecond=0).isoformat(),
                    "rolling_token": "b" * 16,
                    "timestamp": datetime.now().replace(microsecond=0).isoformat(),
                    "hmac": "c" * 64,
                },
                {
                    "student_id": str(student.id),
                    "rssi": -56,
                    "last_seen": (datetime.now().replace(microsecond=0)).isoformat(),
                    "rolling_token": "d" * 16,
                    "timestamp": (datetime.now().replace(microsecond=0)).isoformat(),
                    "hmac": "e" * 64,
                },
            ],
        },
    )

    assert upload_response.status_code == 200

    dashboard_response = await ble_app_client.get("/api/ble/dashboard", headers={"Authorization": f"Bearer {token}"})
    assert dashboard_response.status_code == 200
    data = dashboard_response.json()
    assert data["students_seen"] == 1
    assert data["packets_received"] == 2


@pytest.mark.asyncio
async def test_ble_registered_devices_list_endpoint(ble_db_session, ble_app_client):
    teacher, token = await seed_user_and_token(ble_db_session, ble_app_client, "teacher-devices@test.com", RoleEnum.FACULTY)
    student, _ = await seed_user_and_token(ble_db_session, ble_app_client, "student-devices@test.com", RoleEnum.STUDENT)

    from tests.phase_27a.factories import make_registered_device

    ble_db_session.add(make_registered_device(student_id=student.id))
    await ble_db_session.commit()

    response = await ble_app_client.get(
        "/api/ble/registered-devices",
        headers={"Authorization": f"Bearer {token}"},
    )

    assert response.status_code == 200
    data = response.json()
    assert len(data["registered_devices"]) == 1