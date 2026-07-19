from __future__ import annotations

import pytest

from app.repositories.ble_repositories import (
    BleAttendanceRepository,
    BleObservationRepository,
    BleRegisteredDeviceRepository,
    BleSessionRepository,
)

from tests.phase_27a.factories import make_ble_attendance, make_ble_observation, make_ble_session, make_registered_device


@pytest.mark.asyncio
async def test_ble_session_repository_crud(ble_db_session):
    repo = BleSessionRepository(ble_db_session)
    session = make_ble_session()

    created = await repo.create(session)
    assert created.id == session.id
    assert await repo.get_by_id(session.id) is not None
    assert await repo.list_by_course(session.course_id)
    assert await repo.list_by_teacher(session.teacher_id)


@pytest.mark.asyncio
async def test_ble_registered_device_repository_lookup(ble_db_session):
    repo = BleRegisteredDeviceRepository(ble_db_session)
    device = make_registered_device()
    ble_db_session.add(device)
    await ble_db_session.commit()

    assert await repo.get_by_student_id(device.student_id) is not None
    assert await repo.list_all()


@pytest.mark.asyncio
async def test_ble_observation_repository_create_and_list(ble_db_session):
    repo = BleObservationRepository(ble_db_session)
    observation = make_ble_observation()

    created = await repo.create(observation)
    assert created.id == observation.id
    assert await repo.list_by_session(observation.session_id)
    assert await repo.list_by_student(observation.student_id)


@pytest.mark.asyncio
async def test_ble_attendance_repository_upsert(ble_db_session):
    repo = BleAttendanceRepository(ble_db_session)
    attendance = make_ble_attendance()

    created = await repo.upsert(attendance)
    assert created.student_id == attendance.student_id
    listed = await repo.list_by_session(attendance.session_id)
    assert listed