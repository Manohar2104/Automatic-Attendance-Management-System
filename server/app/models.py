from sqlalchemy import String, Column, DateTime, Enum, Float, ForeignKey, UUID
from sqlalchemy.sql import func
import enum
import uuid
from .db import Base


class RoleEnum(str, enum.Enum):
    STUDENT = "STUDENT"
    FACULTY = "FACULTY"
    ADMIN = "ADMIN"


class BindingStatus(str, enum.Enum):
    ACTIVE = "ACTIVE"
    REVOKED = "REVOKED"


class AttendanceStatus(str, enum.Enum):
    PRESENT = "PRESENT"
    PARTIAL = "PARTIAL"
    ABSENT = "ABSENT"


class User(Base):
    __tablename__ = "users"
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    email = Column(String(255), unique=True, nullable=False)
    password_hash = Column(String(255), nullable=False)
    role = Column(Enum(RoleEnum), default=RoleEnum.STUDENT, nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())


class DeviceBinding(Base):
    __tablename__ = "device_bindings"
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    # user_id may be null until a device is bound to a user
    user_id = Column(
        UUID(as_uuid=True), ForeignKey("users.id"), nullable=True, index=True
    )
    device_fingerprint = Column(String(255), nullable=False, unique=True, index=True)
    status = Column(Enum(BindingStatus), default=BindingStatus.ACTIVE, nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    last_seen_at = Column(DateTime(timezone=True), nullable=True)
    revoked_at = Column(DateTime(timezone=True), nullable=True)
    revoked_by = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)


class Attendance(Base):
    __tablename__ = "attendances"
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    student_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    session_id = Column(String(255), nullable=False)
    score = Column(Float, nullable=True)
    status = Column(Enum(AttendanceStatus), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())


class SessionStatus(str, enum.Enum):
    SCHEDULED = "SCHEDULED"
    ACTIVE = "ACTIVE"
    COMPLETED = "COMPLETED"


class Session(Base):
    __tablename__ = "sessions"
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    course_id = Column(String(255), nullable=False)
    room_id = Column(String(255), nullable=False)
    location = Column(String(255), nullable=False)  # friendly name/room number
    scheduled_start = Column(DateTime(timezone=True), nullable=False)
    scheduled_end = Column(DateTime(timezone=True), nullable=False)
    actual_start = Column(DateTime(timezone=True), nullable=True)
    actual_end = Column(DateTime(timezone=True), nullable=True)
    status = Column(
        Enum(SessionStatus), default=SessionStatus.SCHEDULED, nullable=False
    )
    created_at = Column(DateTime(timezone=True), server_default=func.now())


class EventType(str, enum.Enum):
    ENTER = "ENTER"
    LEAVE = "LEAVE"


class Event(Base):
    __tablename__ = "events"
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    # user_id may be null for anonymous/unbound devices
    user_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    session_id = Column(String(255), nullable=True)
    type = Column(Enum(EventType), nullable=False)
    location = Column(String(255), nullable=True)
    timestamp = Column(DateTime(timezone=True), server_default=func.now())


class AttendanceOverride(Base):
    __tablename__ = "attendance_overrides"
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    attendance_id = Column(
        UUID(as_uuid=True), ForeignKey("attendances.id"), nullable=False
    )
    admin_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    original_status = Column(Enum(AttendanceStatus), nullable=False)
    override_status = Column(Enum(AttendanceStatus), nullable=False)
    justification = Column(String(1024), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
