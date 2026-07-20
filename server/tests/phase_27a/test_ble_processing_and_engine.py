from __future__ import annotations

from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
from uuid import uuid4
from unittest.mock import AsyncMock

import pytest

from app.attendance.ble.attendance_generator import AttendanceGenerator
from app.attendance.ble.attendance_persistence import AttendancePersistence
from app.attendance.ble.ble_attendance_engine import BleAttendanceEngine
from app.attendance.ble.ble_observation_processor import BleObservationProcessor
from app.attendance.ble.presence_evaluator import PresenceEvaluator
from app.attendance.ble.contracts import BleAttendanceDraft, BlePresenceDisposition
from app.models import BleAttendanceStatus
from app.repositories.ble_repositories import (
    BleAttendanceRepository,
    BleObservationRepository,
    BleRegisteredDeviceRepository,
    BleSessionRepository,
)

from tests.phase_27a.factories import (
    make_ble_attendance,
    make_ble_observation,
    make_ble_session,
    make_evaluation_result,
    make_observation_item,
    make_registered_device,
    make_session_context,
    make_upload_request,
    make_validated_observation,
    make_presence_state,
    utcnow,
)
from app.attendance.ble import ble_attendance_engine as engine_module


@pytest.mark.asyncio
async def test_observation_processor_rejects_duplicates_and_unregistered_devices():
    student_id = uuid4()
    context = make_session_context(session=make_ble_session())
    repository = SimpleNamespace(get_by_student_id=AsyncMock(side_effect=lambda requested_student_id: make_registered_device(student_id=requested_student_id) if requested_student_id == student_id else None))
    validator = SimpleNamespace(validate=AsyncMock(return_value=SimpleNamespace(state="VALID", is_accepted=True, issues=[])))
    processor = BleObservationProcessor(
        session_manager=SimpleNamespace(load_active_session_context=AsyncMock(return_value=context)),
        registered_device_repository=repository,
        packet_validator=validator,
    )
    duplicate_timestamp = utcnow()
    payload = make_upload_request(
        session_id=context.session.id,
        observations=[
            make_observation_item(student_id=student_id, rolling_token="b" * 16, timestamp=duplicate_timestamp, last_seen=duplicate_timestamp),
            make_observation_item(student_id=student_id, rolling_token="b" * 16, timestamp=duplicate_timestamp, last_seen=duplicate_timestamp),
            make_observation_item(student_id=uuid4(), rolling_token="d" * 16, timestamp=utcnow(), last_seen=utcnow()),
        ],
    )

    _, result = await processor.process_batch(payload)

    assert result.accepted_count == 1
    assert result.rejected_count == 2


@pytest.mark.asyncio
async def test_observation_processor_rejects_invalid_packets():
    student_id = uuid4()
    context = make_session_context(session=make_ble_session())
    processor = BleObservationProcessor(
        session_manager=SimpleNamespace(load_active_session_context=AsyncMock(return_value=context)),
        registered_device_repository=SimpleNamespace(get_by_student_id=AsyncMock(return_value=make_registered_device(student_id=student_id))),
        packet_validator=SimpleNamespace(validate=AsyncMock(return_value=SimpleNamespace(state="INVALID", is_accepted=False, issues=[SimpleNamespace(message="bad packet")]))),
    )
    payload = make_upload_request(session_id=context.session.id, observations=[make_observation_item(student_id=student_id)])

    _, result = await processor.process_batch(payload)

    assert result.accepted_count == 0
    assert result.rejected_count == 1


@pytest.mark.asyncio
async def test_observation_processor_keeps_valid_observations_flowing():
    student_id = uuid4()
    session = make_ble_session()
    context = make_session_context(session=session)
    repository = SimpleNamespace(
        get_by_student_id=AsyncMock(return_value=make_registered_device(student_id=student_id))
    )
    validator = SimpleNamespace(validate=AsyncMock(return_value=SimpleNamespace(state="VALID", is_accepted=True, issues=[])))
    processor = BleObservationProcessor(
        session_manager=SimpleNamespace(load_active_session_context=AsyncMock(return_value=context)),
        registered_device_repository=repository,
        packet_validator=validator,
    )
    first = utcnow()
    payload = make_upload_request(
        session_id=context.session.id,
        observations=[
            make_observation_item(student_id=student_id, rolling_token="b" * 16, timestamp=first, last_seen=first),
            make_observation_item(student_id=student_id, rolling_token="c" * 16, timestamp=first + timedelta(seconds=60), last_seen=first + timedelta(seconds=60)),
            make_observation_item(student_id=student_id, rolling_token="d" * 16, timestamp=first + timedelta(seconds=125), last_seen=first + timedelta(seconds=125)),
        ],
    )

    _, result = await processor.process_batch(payload, teacher_email="faculty@example.com", room="Room 101")

    assert result.accepted_count == 2
    assert result.rejected_count == 1


def test_presence_evaluator_counts_present_missing_late_and_absent():
    now = utcnow()
    session_start = utcnow(-30)
    registered_devices = [
        make_registered_device(student_id=uuid4()),
        make_registered_device(student_id=uuid4()),
        make_registered_device(student_id=uuid4()),
        make_registered_device(student_id=uuid4()),
    ]
    present = make_validated_observation(student_id=registered_devices[0].student_id, last_seen=session_start + timedelta(seconds=5))
    missing = make_validated_observation(student_id=registered_devices[1].student_id, last_seen=utcnow(-360))
    partial_student = registered_devices[2].student_id
    partial_first = make_validated_observation(student_id=partial_student, last_seen=session_start - timedelta(seconds=700))
    partial_second = make_validated_observation(student_id=partial_student, last_seen=session_start + timedelta(seconds=10))
    evaluator = PresenceEvaluator(
        presence_timeout_seconds=300,
        recovery_window_seconds=300,
        partial_threshold_seconds=600,
    )

    result = evaluator.evaluate(
        session_start_time=session_start,
        evaluated_at=now,
        observations=[present, missing, partial_first, partial_second],
        registered_devices=registered_devices,
    )

    assert result.present_count == 1
    assert result.missing_count == 1
    assert result.absent_count == 1
    assert any(state.disposition == BlePresenceDisposition.PARTIAL for state in result.presence_states)


def test_attendance_generator_maps_partial_to_present_and_absent_to_missing():
    session_id = uuid4()
    session_start = utcnow(-300)
    result = make_evaluation_result(
        presence_states=[
            make_presence_state(student_id=uuid4(), first_seen=session_start, last_seen=utcnow(), disposition=BlePresenceDisposition.PRESENT),
            make_presence_state(student_id=uuid4(), first_seen=session_start, last_seen=utcnow(), disposition=BlePresenceDisposition.PARTIAL),
            make_presence_state(student_id=uuid4(), first_seen=None, last_seen=None, disposition=BlePresenceDisposition.ABSENT),
        ],
        present_count=1,
        absent_count=1,
    )
    drafts = AttendanceGenerator().generate(session_id=session_id, session_start_time=session_start, evaluation_result=result)

    assert len(drafts) == 3
    assert drafts[0].status == BleAttendanceStatus.PRESENT
    assert drafts[1].status == BleAttendanceStatus.PRESENT
    assert drafts[2].status == BleAttendanceStatus.MISSING


@pytest.mark.asyncio
async def test_attendance_persistence_stores_observations_and_attendance():
    observation_repo = SimpleNamespace(create_many=AsyncMock(return_value=[]))
    attendance_repo = SimpleNamespace(
        upsert=AsyncMock(side_effect=lambda attendance: attendance),
        list_by_session=AsyncMock(return_value=[]),
        delete_by_session_and_student_ids=AsyncMock(return_value=0),
        get_by_session_and_student=AsyncMock(return_value=None),
        create=AsyncMock(side_effect=lambda attendance: attendance),
        update=AsyncMock(side_effect=lambda attendance: attendance),
    )
    persistence = AttendancePersistence(attendance_repository=attendance_repo, observation_repository=observation_repo)

    observation = make_ble_observation()
    attendance = make_ble_attendance(session_id=observation.session_id, student_id=observation.student_id)

    assert await persistence.store_observations([observation]) == []
    persisted = await persistence.store_attendance([BleAttendanceDraft(session_id=attendance.session_id, student_id=attendance.student_id, disposition=BlePresenceDisposition.PRESENT, status=BleAttendanceStatus.PRESENT, first_seen=attendance.first_seen, last_seen=attendance.last_seen)])

    assert len(persisted) == 1
    assert persisted[0].student_id == attendance.student_id


@pytest.mark.asyncio
async def test_attendance_persistence_prunes_stale_rows_before_upsert():
    session_id = uuid4()
    student_id = uuid4()
    stale_student_id = uuid4()
    observation_repo = SimpleNamespace(create_many=AsyncMock(return_value=[]))
    attendance_repo = SimpleNamespace(
        upsert=AsyncMock(side_effect=lambda attendance: attendance),
        list_by_session=AsyncMock(return_value=[make_ble_attendance(session_id=session_id, student_id=stale_student_id)]),
        delete_by_session_and_student_ids=AsyncMock(return_value=1),
        get_by_session_and_student=AsyncMock(return_value=None),
        create=AsyncMock(side_effect=lambda attendance: attendance),
        update=AsyncMock(side_effect=lambda attendance: attendance),
    )
    persistence = AttendancePersistence(attendance_repository=attendance_repo, observation_repository=observation_repo)

    persisted = await persistence.store_attendance(
        [
            BleAttendanceDraft(
                session_id=session_id,
                student_id=student_id,
                disposition=BlePresenceDisposition.PRESENT,
                status=BleAttendanceStatus.PRESENT,
                first_seen=utcnow(),
                last_seen=utcnow(),
            )
        ],
        session_id=session_id,
    )

    attendance_repo.delete_by_session_and_student_ids.assert_awaited_once_with(session_id, [student_id])
    assert len(persisted) == 1
    assert persisted[0].student_id == student_id


@pytest.mark.asyncio
async def test_ble_attendance_engine_ingest_finalize_and_read_attendance(ble_db_session, monkeypatch):
    monkeypatch.setattr(
        engine_module.BleSessionManager,
        "load_configuration",
        lambda self: SimpleNamespace(
            missing_timeout_seconds=300,
            scan_upload_interval_seconds=15,
            clock_skew_seconds=15,
        ),
    )

    session_repo = BleSessionRepository(ble_db_session)
    device_repo = BleRegisteredDeviceRepository(ble_db_session)
    observation_repo = BleObservationRepository(ble_db_session)
    attendance_repo = BleAttendanceRepository(ble_db_session)

    session = make_ble_session(start_time=datetime.now(timezone.utc).replace(microsecond=0))
    device = make_registered_device(student_id=uuid4())
    observation_item = make_observation_item(student_id=device.student_id, last_seen=session.start_time)

    ble_db_session.add_all([session, device])
    await ble_db_session.commit()

    engine = BleAttendanceEngine(session_repo, device_repo, observation_repo, attendance_repo)
    payload = make_upload_request(session_id=session.id, teacher_id=session.teacher_id, observed_at=datetime.now(timezone.utc).replace(microsecond=0), observations=[observation_item])

    upload = await engine.ingest_observations(payload)
    assert upload.session_id == session.id
    assert upload.accepted == 1

    final = await engine.finalize_session(session.id)
    assert final.present_count >= 0

    attendance = await engine.get_attendance(session.id)
    assert attendance.session_id == session.id
    assert attendance.attendance_mode == "BLE"