from pydantic import BaseModel, EmailStr
from typing import Optional, List
from datetime import datetime


class UserCreate(BaseModel):
    email: EmailStr
    password: str
    role: Optional[str] = "STUDENT"


class DeviceRegister(BaseModel):
    device_fingerprint: str


class DeviceInfo(BaseModel):
    id: str
    device_fingerprint: str
    status: str
    last_seen_at: Optional[datetime]


class PresenceEvent(BaseModel):
    device_fingerprint: str
    session_id: Optional[str] = None
    location: Optional[str] = None
    event_type: str = "ENTER"


class Token(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class HealthCheck(BaseModel):
    status: str
    now: datetime


class TimetableEntry(BaseModel):
    course_id: str
    room_id: str
    location: str
    scheduled_start: str  # ISO format
    scheduled_end: str  # ISO format
    faculty_email: Optional[str] = None


class SessionInfo(BaseModel):
    id: str
    course_id: str
    room_id: str
    location: str
    scheduled_start: datetime
    scheduled_end: datetime
    actual_start: Optional[datetime]
    actual_end: Optional[datetime]
    status: str
    faculty_email: Optional[str] = None


class AttendanceResult(BaseModel):
    user_id: str
    email: str
    score: float
    status: str
    enter_count: int
    location_match: bool
    wifi_scans: int = 0
    ble_scans: int = 0
    total_scans_required: int = 1
    ble_verified: bool = True


class ComputeAttendanceResponse(BaseModel):
    session_id: str
    duration_seconds: int
    max_possible_submissions: int
    bound_devices: int
    crowd_ok: bool
    location_validated: bool
    results: List[AttendanceResult]
