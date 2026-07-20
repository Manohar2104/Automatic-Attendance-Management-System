from sqlalchemy import (
    String,
    Column,
    DateTime,
    Enum,
    Float,
    ForeignKey,
    UUID,
    Integer,
    CheckConstraint,
    Index,
    UniqueConstraint,
)
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
    role = Column(Enum(RoleEnum, native_enum=False), default=RoleEnum.STUDENT, nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())


class DeviceBinding(Base):
    __tablename__ = "device_bindings"
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    # user_id may be null until a device is bound to a user
    user_id = Column(
        UUID(as_uuid=True), ForeignKey("users.id"), nullable=True, index=True
    )
    device_fingerprint = Column(String(255), nullable=False, unique=True, index=True)
    status = Column(Enum(BindingStatus, native_enum=False), default=BindingStatus.ACTIVE, nullable=False)
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
    status = Column(Enum(AttendanceStatus, native_enum=False), nullable=False)
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
        Enum(SessionStatus, native_enum=False), default=SessionStatus.SCHEDULED, nullable=False
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
    type = Column(Enum(EventType, native_enum=False), nullable=False)
    location = Column(String(255), nullable=True)
    timestamp = Column(DateTime(timezone=True), server_default=func.now())


class AttendanceOverride(Base):
    __tablename__ = "attendance_overrides"
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    attendance_id = Column(
        UUID(as_uuid=True), ForeignKey("attendances.id"), nullable=False
    )
    admin_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    original_status = Column(Enum(AttendanceStatus, native_enum=False), nullable=False)
    override_status = Column(Enum(AttendanceStatus, native_enum=False), nullable=False)
    justification = Column(String(1024), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())


class BleAttendanceMode(str, enum.Enum):
    WIFI = "WIFI"
    BLE = "BLE"


class BleSessionStatus(str, enum.Enum):
    ACTIVE = "ACTIVE"
    ENDED = "ENDED"
    CANCELLED = "CANCELLED"


class BleAttendanceStatus(str, enum.Enum):
    PRESENT = "PRESENT"
    MISSING = "MISSING"


class BleSession(Base):
    __tablename__ = "ble_sessions"
    __table_args__ = (
        CheckConstraint(
            "attendance_mode IN ('WIFI', 'BLE')",
            name="ck_ble_sessions_attendance_mode",
        ),
        CheckConstraint(
            "status IN ('ACTIVE', 'ENDED', 'CANCELLED')",
            name="ck_ble_sessions_status",
        ),
        Index("ix_ble_sessions_course_id_start_time", "course_id", "start_time"),
        Index("ix_ble_sessions_teacher_id_start_time", "teacher_id", "start_time"),
        Index("ix_ble_sessions_status_start_time", "status", "start_time"),
        Index(
            "ix_ble_sessions_attendance_mode_status",
            "attendance_mode",
            "status",
        ),
    )

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    course_id = Column(String(255), nullable=False)
    teacher_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    attendance_mode = Column(
        Enum(BleAttendanceMode, native_enum=False),
        nullable=False,
        default=BleAttendanceMode.BLE,
    )
    status = Column(
        Enum(BleSessionStatus, native_enum=False),
        nullable=False,
        default=BleSessionStatus.ACTIVE,
    )
    start_time = Column(DateTime(timezone=True), nullable=False)
    end_time = Column(DateTime(timezone=True), nullable=True)
    packets_received = Column(Integer, nullable=False, default=0)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )


class BleRegisteredDevice(Base):
    __tablename__ = "ble_registered_devices"
    __table_args__ = (
        UniqueConstraint(
            "anonymous_ble_id",
            name="uq_ble_registered_devices_anonymous_ble_id",
        ),
        UniqueConstraint("device_hash", name="uq_ble_registered_devices_device_hash"),
        UniqueConstraint("student_id", name="uq_ble_registered_devices_student_id"),
        Index("ix_ble_registered_devices_student_id", "student_id"),
        Index(
            "ix_ble_registered_devices_public_identifier",
            "public_identifier",
        ),
    )

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    student_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    anonymous_ble_id = Column(String(64), nullable=False)
    public_identifier = Column(String(255), nullable=False)
    device_hash = Column(String(255), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )


class BleObservation(Base):
    __tablename__ = "ble_observations"
    __table_args__ = (
        CheckConstraint(
            "rssi BETWEEN -120 AND 0",
            name="ck_ble_observations_rssi_range",
        ),
        UniqueConstraint(
            "session_id",
            "student_id",
            "rolling_token",
            "timestamp",
            name="uq_ble_observations_session_student_token_timestamp",
        ),
        Index("ix_ble_observations_session_id_timestamp", "session_id", "timestamp"),
        Index(
            "ix_ble_observations_session_id_student_id",
            "session_id",
            "student_id",
        ),
        Index(
            "ix_ble_observations_student_id_last_seen",
            "student_id",
            "last_seen",
        ),
        Index("ix_ble_observations_rolling_token", "rolling_token"),
    )

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    session_id = Column(
        UUID(as_uuid=True), ForeignKey("ble_sessions.id"), nullable=False
    )
    student_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    rssi = Column(Integer, nullable=False)
    rolling_token = Column(String(64), nullable=False)
    timestamp = Column(DateTime(timezone=True), nullable=False)
    hmac = Column(String(128), nullable=False)
    last_seen = Column(DateTime(timezone=True), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class BleAttendance(Base):
    __tablename__ = "ble_attendance"
    __table_args__ = (
        CheckConstraint(
            "status IN ('PRESENT', 'MISSING')",
            name="ck_ble_attendance_status",
        ),
        UniqueConstraint(
            "session_id",
            "student_id",
            name="uq_ble_attendance_session_student",
        ),
        Index("ix_ble_attendance_session_id_status", "session_id", "status"),
        Index("ix_ble_attendance_student_id_last_seen", "student_id", "last_seen"),
    )

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    session_id = Column(
        UUID(as_uuid=True), ForeignKey("ble_sessions.id"), nullable=False
    )
    student_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    status = Column(
        Enum(BleAttendanceStatus, native_enum=False), nullable=False
    )
    first_seen = Column(DateTime(timezone=True), nullable=False)
    last_seen = Column(DateTime(timezone=True), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )
