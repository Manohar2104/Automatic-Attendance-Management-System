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
    timestamp: Optional[datetime] = None


class Token(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class HealthCheck(BaseModel):
    status: str
    now: datetime


class TimetableEntryCreate(BaseModel):
    course_id: str
    room_id: str
    location: str
    day_of_week: str
    start_time: str
    end_time: str


class TimetableEntryResponse(BaseModel):
    id: str
    teacher_id: str
    course_id: str
    room_id: str
    location: str
    day_of_week: str
    start_time: str
    end_time: str


class UserMeResponse(BaseModel):
    id: str
    email: str
    role: str


class TimetableEntry(BaseModel):
    course_id: str
    room_id: str
    location: str
    scheduled_start: str  # ISO format
    scheduled_end: str  # ISO format


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


class AttendanceResult(BaseModel):
    user_id: str
    score: float
    status: str
    enter_count: int
    location_match: bool


class ComputeAttendanceResponse(BaseModel):
    session_id: str
    duration_seconds: int
    max_possible_submissions: int
    bound_devices: int
    crowd_ok: bool
    location_validated: bool
    results: List[AttendanceResult]
