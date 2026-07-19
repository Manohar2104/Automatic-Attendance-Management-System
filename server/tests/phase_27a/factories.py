from __future__ import annotations

from datetime import datetime, timedelta, timezone
from uuid import UUID, uuid4

from app.attendance.ble.contracts import (
    BleAttendanceDraft,
    BleAttendanceEvaluationResult,
    BleObservationPayload,
    BleObservationProcessingResult,
    BlePresenceDisposition,
    BlePresenceState,
    BleSessionConfiguration,
    BleSessionContext,
    BleValidatedObservation,
    BleValidationIssue,
    BleValidationResult,
    BleValidationState,
)
from app.ble_schemas import BleObservationItem, BleObservationUploadRequest
from app.models import (
    BleAttendance,
    BleAttendanceMode,
    BleAttendanceStatus,
    BleObservation,
    BleRegisteredDevice,
    BleSession,
    BleSessionStatus,
    RoleEnum,
    User,
)


def utcnow(offset_seconds: int = 0) -> datetime:
    return datetime.now(timezone.utc) + timedelta(seconds=offset_seconds)


def make_user(
    *,
    email: str = "student@example.com",
    role: RoleEnum = RoleEnum.STUDENT,
    user_id: UUID | None = None,
) -> User:
    return User(
        id=user_id or uuid4(),
        email=email,
        password_hash="hashed-password",
        role=role,
    )


def make_ble_session(
    *,
    session_id: UUID | None = None,
    course_id: str = "BLE-101",
    teacher_id: UUID | None = None,
    attendance_mode: BleAttendanceMode = BleAttendanceMode.BLE,
    status: BleSessionStatus = BleSessionStatus.ACTIVE,
    start_time: datetime | None = None,
    end_time: datetime | None = None,
) -> BleSession:
    now = start_time or utcnow()
    return BleSession(
        id=session_id or uuid4(),
        course_id=course_id,
        teacher_id=teacher_id or uuid4(),
        attendance_mode=attendance_mode,
        status=status,
        start_time=now,
        end_time=end_time,
    )


def make_registered_device(
    *,
    student_id: UUID | None = None,
    anonymous_ble_id: str = "a" * 32,
    public_identifier: str = "student-device",
    device_hash: str = "device-hash-1",
) -> BleRegisteredDevice:
    return BleRegisteredDevice(
        id=uuid4(),
        student_id=student_id or uuid4(),
        anonymous_ble_id=anonymous_ble_id,
        public_identifier=public_identifier,
        device_hash=device_hash,
    )


def make_ble_observation(
    *,
    session_id: UUID | None = None,
    student_id: UUID | None = None,
    rssi: int = -58,
    rolling_token: str = "b" * 16,
    timestamp: datetime | None = None,
    hmac: str = "c" * 64,
    last_seen: datetime | None = None,
) -> BleObservation:
    seen_at = last_seen or utcnow()
    return BleObservation(
        id=uuid4(),
        session_id=session_id or uuid4(),
        student_id=student_id or uuid4(),
        rssi=rssi,
        rolling_token=rolling_token,
        timestamp=timestamp or seen_at,
        hmac=hmac,
        last_seen=seen_at,
    )


def make_ble_attendance(
    *,
    session_id: UUID | None = None,
    student_id: UUID | None = None,
    status: BleAttendanceStatus = BleAttendanceStatus.PRESENT,
    first_seen: datetime | None = None,
    last_seen: datetime | None = None,
) -> BleAttendance:
    start = first_seen or utcnow(-60)
    end = last_seen or utcnow()
    return BleAttendance(
        id=uuid4(),
        session_id=session_id or uuid4(),
        student_id=student_id or uuid4(),
        status=status,
        first_seen=start,
        last_seen=end,
    )


def make_observation_item(
    *,
    student_id: UUID | None = None,
    rssi: int = -60,
    last_seen: datetime | None = None,
    rolling_token: str = "b" * 16,
    timestamp: datetime | None = None,
    hmac: str = "c" * 64,
) -> BleObservationItem:
    seen_at = last_seen or utcnow()
    return BleObservationItem(
        student_id=student_id or uuid4(),
        rssi=rssi,
        last_seen=seen_at,
        rolling_token=rolling_token,
        timestamp=timestamp or seen_at,
        hmac=hmac,
    )


def make_upload_request(
    *,
    session_id: UUID | None = None,
    teacher_id: UUID | None = None,
    observed_at: datetime | None = None,
    observations: list[BleObservationItem] | None = None,
) -> BleObservationUploadRequest:
    return BleObservationUploadRequest(
        session_id=session_id or uuid4(),
        teacher_id=teacher_id or uuid4(),
        observed_at=observed_at or utcnow(),
        observations=observations or [],
    )


def make_observation_payload(
    *,
    student_id: UUID | None = None,
    rssi: int = -62,
    last_seen: datetime | None = None,
    rolling_token: str = "b" * 16,
    timestamp: datetime | None = None,
    hmac: str = "c" * 64,
) -> BleObservationPayload:
    seen_at = last_seen or utcnow()
    return BleObservationPayload(
        student_id=student_id or uuid4(),
        rssi=rssi,
        last_seen=seen_at,
        rolling_token=rolling_token,
        timestamp=timestamp or seen_at,
        hmac=hmac,
    )


def make_validated_observation(
    *,
    student_id: UUID | None = None,
    rssi: int = -58,
    last_seen: datetime | None = None,
    rolling_token: str = "b" * 16,
    timestamp: datetime | None = None,
    hmac: str = "c" * 64,
    anonymous_ble_id: str = "a" * 32,
    device_hash: str = "device-hash-1",
) -> BleValidatedObservation:
    seen_at = last_seen or utcnow()
    return BleValidatedObservation(
        student_id=student_id or uuid4(),
        rssi=rssi,
        last_seen=seen_at,
        rolling_token=rolling_token,
        timestamp=timestamp or seen_at,
        hmac=hmac,
        anonymous_ble_id=anonymous_ble_id,
        device_hash=device_hash,
    )


def make_presence_state(
    *,
    student_id: UUID | None = None,
    first_seen: datetime | None = None,
    last_seen: datetime | None = None,
    disposition: BlePresenceDisposition = BlePresenceDisposition.PRESENT,
    observation_count: int = 1,
) -> BlePresenceState:
    seen_at = last_seen or utcnow()
    return BlePresenceState(
        student_id=student_id or uuid4(),
        first_seen=first_seen,
        last_seen=seen_at,
        disposition=disposition,
        observation_count=observation_count,
    )


def make_evaluation_result(
    *,
    presence_states: list[BlePresenceState] | None = None,
    present_count: int = 0,
    absent_count: int = 0,
    missing_count: int = 0,
    late_count: int = 0,
) -> BleAttendanceEvaluationResult:
    return BleAttendanceEvaluationResult(
        presence_states=presence_states or [],
        present_count=present_count,
        absent_count=absent_count,
        missing_count=missing_count,
        late_count=late_count,
    )


def make_session_context(
    *,
    session: BleSession | None = None,
    missing_timeout_seconds: int = 45,
    scan_upload_interval_seconds: int = 15,
    clock_skew_seconds: int = 15,
) -> BleSessionContext:
    return BleSessionContext(
        session=session or make_ble_session(),
        configuration=BleSessionConfiguration(
            missing_timeout_seconds=missing_timeout_seconds,
            scan_upload_interval_seconds=scan_upload_interval_seconds,
            clock_skew_seconds=clock_skew_seconds,
        ),
    )


def make_validation_result(
    *,
    state: BleValidationState = BleValidationState.VALID,
    issues: list[BleValidationIssue] | None = None,
) -> BleValidationResult:
    return BleValidationResult(state=state, issues=issues or [])