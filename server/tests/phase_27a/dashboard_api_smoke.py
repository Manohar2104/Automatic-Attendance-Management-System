from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

import pytest

from app.auth import create_access_token, get_password_hash
from app.models import RoleEnum, User

from tests.phase_27a.factories import make_ble_observation, make_ble_session, make_registered_device


async def _seed_user(ble_db_session, client, *, email: str, role: RoleEnum) -> tuple[User, str]:
    user = User(id=uuid4(), email=email, password_hash=get_password_hash("password123"), role=role)
    ble_db_session.add(user)
    await ble_db_session.commit()

    return user, create_access_token(str(user.id))


def _assert_uuid(value: object) -> None:
    assert isinstance(value, str)
    UUID(value)


def _assert_datetime(value: object) -> None:
    assert isinstance(value, str)
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    assert parsed.tzinfo is not None


def _assert_session_item(item: dict[str, object]) -> None:
    for field in ["session_id", "course_id", "teacher_id", "attendance_mode", "status", "start_time", "end_time"]:
        assert field in item
    _assert_uuid(item["session_id"])
    assert isinstance(item["course_id"], str)
    _assert_uuid(item["teacher_id"])
    assert item["attendance_mode"] == "BLE"
    assert isinstance(item["status"], str)
    _assert_datetime(item["start_time"])
    if item["end_time"] is not None:
        _assert_datetime(item["end_time"])


def _assert_summary_payload(body: dict[str, object]) -> None:
    assert "session" in body
    assert "registered_students" in body
    assert "detected_students" in body
    assert "present" in body
    assert "missing" in body
    assert "absent" in body
    assert "late" in body
    assert "latest_observation" in body
    assert "average_rssi" in body
    assert isinstance(body["registered_students"], int)
    assert isinstance(body["detected_students"], int)
    assert isinstance(body["present"], int)
    assert isinstance(body["missing"], int)
    if body["absent"] is not None:
        assert isinstance(body["absent"], int)
    if body["late"] is not None:
        assert isinstance(body["late"], int)
    if body["latest_observation"] is not None:
        _assert_datetime(body["latest_observation"])
    if body["average_rssi"] is not None:
        assert isinstance(body["average_rssi"], (int, float))
    _assert_session_item(body["session"])


def _assert_observations_payload(body: dict[str, object]) -> None:
    assert "session_id" in body
    assert "observations" in body
    _assert_uuid(body["session_id"])
    assert isinstance(body["observations"], list)
    for item in body["observations"]:
        assert isinstance(item, dict)
        for field in [
            "student_id",
            "student_name",
            "anonymous_ble_id",
            "rssi",
            "last_seen",
            "advertisement_count",
            "observation_timestamp",
        ]:
            assert field in item
        _assert_uuid(item["student_id"])
        if item["student_name"] is not None:
            assert isinstance(item["student_name"], str)
        if item["anonymous_ble_id"] is not None:
            assert isinstance(item["anonymous_ble_id"], str)
        assert isinstance(item["rssi"], int)
        _assert_datetime(item["last_seen"])
        assert isinstance(item["advertisement_count"], int)
        _assert_datetime(item["observation_timestamp"])


def _assert_presence_payload(body: dict[str, object]) -> None:
    assert "session_id" in body
    assert "summary" in body
    assert "records" in body
    _assert_uuid(body["session_id"])
    summary = body["summary"]
    assert isinstance(summary, dict)
    for field in ["present", "missing", "absent", "late"]:
        assert field in summary
    assert isinstance(summary["present"], int)
    assert isinstance(summary["missing"], int)
    if summary["absent"] is not None:
        assert isinstance(summary["absent"], int)
    if summary["late"] is not None:
        assert isinstance(summary["late"], int)
    assert isinstance(body["records"], list)
    for item in body["records"]:
        assert isinstance(item, dict)
        for field in ["student_id", "student_name", "status", "first_seen", "last_seen"]:
            assert field in item
        _assert_uuid(item["student_id"])
        if item["student_name"] is not None:
            assert isinstance(item["student_name"], str)
        assert isinstance(item["status"], str)
        _assert_datetime(item["first_seen"])
        _assert_datetime(item["last_seen"])


@pytest.mark.asyncio
async def test_dashboard_ble_endpoints_smoke_and_contract(ble_db_session, ble_app_client):
    teacher, token = await _seed_user(ble_db_session, ble_app_client, email="teacher@dashboard.test", role=RoleEnum.FACULTY)
    student, student_token = await _seed_user(ble_db_session, ble_app_client, email="student@dashboard.test", role=RoleEnum.STUDENT)

    session = make_ble_session(teacher_id=teacher.id, start_time=datetime.now().astimezone())
    registered_device = make_registered_device(student_id=student.id)
    observation = make_ble_observation(session_id=session.id, student_id=student.id, last_seen=session.start_time)
    ble_db_session.add_all([session, registered_device, observation])
    await ble_db_session.commit()

    for path in [
        "/api/ble/sessions",
        f"/api/ble/session/{session.id}/summary",
        f"/api/ble/session/{session.id}/observations",
        f"/api/ble/session/{session.id}/presence",
    ]:
        unauthorized = await ble_app_client.get(path)
        assert unauthorized.status_code == 401

    forbidden = await ble_app_client.get("/api/ble/sessions", headers={"Authorization": f"Bearer {student_token}"})
    assert forbidden.status_code == 403

    sessions_response = await ble_app_client.get("/api/ble/sessions", headers={"Authorization": f"Bearer {token}"})
    assert sessions_response.status_code == 200
    sessions_body = sessions_response.json()
    assert isinstance(sessions_body, dict)
    assert "sessions" in sessions_body
    assert isinstance(sessions_body["sessions"], list)
    assert len(sessions_body["sessions"]) >= 1
    for item in sessions_body["sessions"]:
        _assert_session_item(item)

    summary_response = await ble_app_client.get(f"/api/ble/session/{session.id}/summary", headers={"Authorization": f"Bearer {token}"})
    assert summary_response.status_code == 200
    summary_body = summary_response.json()
    _assert_summary_payload(summary_body)

    observations_response = await ble_app_client.get(
        f"/api/ble/session/{session.id}/observations",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert observations_response.status_code == 200
    observations_body = observations_response.json()
    _assert_observations_payload(observations_body)

    presence_response = await ble_app_client.get(f"/api/ble/session/{session.id}/presence", headers={"Authorization": f"Bearer {token}"})
    assert presence_response.status_code == 200
    presence_body = presence_response.json()
    _assert_presence_payload(presence_body)


@pytest.mark.asyncio
async def test_dashboard_ble_endpoints_handle_empty_and_invalid_sessions(ble_db_session, ble_app_client):
    teacher, token = await _seed_user(ble_db_session, ble_app_client, email="teacher-empty@dashboard.test", role=RoleEnum.FACULTY)
    session = make_ble_session(teacher_id=teacher.id, start_time=datetime.now().astimezone())
    ble_db_session.add(session)
    await ble_db_session.commit()

    sessions_response = await ble_app_client.get("/api/ble/sessions", headers={"Authorization": f"Bearer {token}"})
    assert sessions_response.status_code == 200
    assert sessions_response.json().get("sessions")

    summary_response = await ble_app_client.get(
        f"/api/ble/session/{session.id}/summary",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert summary_response.status_code == 200
    _assert_summary_payload(summary_response.json())

    observations_response = await ble_app_client.get(
        f"/api/ble/session/{session.id}/observations",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert observations_response.status_code == 200
    observations_body = observations_response.json()
    assert observations_body["observations"] == []

    presence_response = await ble_app_client.get(
        f"/api/ble/session/{session.id}/presence",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert presence_response.status_code == 200
    presence_body = presence_response.json()
    assert presence_body["records"] == []

    invalid_id = "00000000-0000-0000-0000-000000000000"
    for path in [
        f"/api/ble/session/{invalid_id}/summary",
        f"/api/ble/session/{invalid_id}/observations",
        f"/api/ble/session/{invalid_id}/presence",
    ]:
        response = await ble_app_client.get(path, headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 404


@pytest.mark.asyncio
async def test_dashboard_ble_sessions_empty_list_is_supported(ble_db_session, ble_app_client):
    teacher, token = await _seed_user(ble_db_session, ble_app_client, email="teacher-empty-list@dashboard.test", role=RoleEnum.FACULTY)

    response = await ble_app_client.get("/api/ble/sessions", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200
    body = response.json()
    assert body == {"sessions": []}
