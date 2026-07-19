from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class BleSessionStartRequest(BaseModel):
    course_id: str = Field(min_length=1, max_length=255)
    teacher_id: UUID
    attendance_mode: Literal["BLE"] = "BLE"
    start_time: datetime


class BleSessionEndRequest(BaseModel):
    session_id: UUID
    ended_at: datetime


class BleObservationItem(BaseModel):
    student_id: UUID
    rssi: int
    last_seen: datetime
    rolling_token: str = Field(min_length=1, max_length=64)
    timestamp: datetime
    hmac: str = Field(min_length=1, max_length=128)


class BleObservationUploadRequest(BaseModel):
    session_id: UUID
    teacher_id: UUID
    observed_at: datetime
    observations: list[BleObservationItem]


class BleDeviceRegistrationRequest(BaseModel):
    anonymous_ble_id: str = Field(min_length=1, max_length=64)
    public_identifier: str | None = Field(default=None, max_length=255)
    device_hash: str = Field(min_length=1, max_length=255)


class BleDeviceRegistrationResponse(BaseModel):
    success: bool
    student_id: UUID
    anonymous_ble_id: str


class BleSessionResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    session_id: UUID
    course_id: str
    teacher_id: UUID
    attendance_mode: Literal["BLE"]
    status: str
    start_time: datetime
    end_time: datetime | None
    room: str | None = None
    created_at: datetime
    updated_at: datetime


class BleObservationUploadResponse(BaseModel):
    session_id: UUID
    received: int
    accepted: int
    rejected: int
    processed_at: datetime


class BleRegisteredDeviceItem(BaseModel):
    student_id: UUID
    anonymous_ble_id: str
    public_identifier: str


class BleRegisteredDevicesResponse(BaseModel):
    registered_devices: list[BleRegisteredDeviceItem]


class BleDashboardResponse(BaseModel):
    session: BleSessionResponse | None
    registered_devices: int
    students_seen: int
    packets_received: int
    faculty: str | None = None
    room: str | None = None


class BleAttendanceRecord(BaseModel):
    student_id: UUID
    status: str
    first_seen: datetime
    last_seen: datetime


class BleAttendanceSummary(BaseModel):
    present: int
    missing: int


class BleAttendanceResponse(BaseModel):
    session_id: UUID
    attendance_mode: Literal["BLE"]
    summary: BleAttendanceSummary
    records: list[BleAttendanceRecord]


class BleSessionsListResponse(BaseModel):
    sessions: list[BleSessionResponse]


class BleSessionSummaryResponse(BaseModel):
    session: BleSessionResponse
    registered_students: int
    detected_students: int
    packets_received: int
    present: int
    missing: int
    absent: int | None
    late: int | None
    latest_observation: datetime | None
    average_rssi: float | None


class BleObservationViewItem(BaseModel):
    student_id: UUID
    student_name: str | None
    anonymous_ble_id: str | None
    rssi: int
    last_seen: datetime
    advertisement_count: int
    observation_timestamp: datetime


class BleObservationsResponse(BaseModel):
    session_id: UUID
    observations: list[BleObservationViewItem]


class BlePresenceViewItem(BaseModel):
    student_id: UUID
    student_name: str | None
    status: str
    first_seen: datetime
    last_seen: datetime


class BlePresenceSummary(BaseModel):
    present: int
    missing: int
    absent: int | None
    late: int | None


class BlePresenceResponse(BaseModel):
    session_id: UUID
    summary: BlePresenceSummary
    records: list[BlePresenceViewItem]
