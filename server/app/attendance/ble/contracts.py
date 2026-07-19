from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from uuid import UUID

from ...models import BleAttendanceStatus, BleSession


class BleValidationState(str, Enum):
    VALID = "VALID"
    INVALID = "INVALID"
    NOT_YET_IMPLEMENTED = "NOT_YET_IMPLEMENTED"


@dataclass(slots=True)
class BleValidationIssue:
    code: str
    message: str


@dataclass(slots=True)
class BleValidationResult:
    state: BleValidationState
    issues: list[BleValidationIssue] = field(default_factory=list)

    @property
    def is_accepted(self) -> bool:
        return self.state in (BleValidationState.VALID, BleValidationState.NOT_YET_IMPLEMENTED)


@dataclass(slots=True, frozen=True)
class BleSessionConfiguration:
    missing_timeout_seconds: int = 45
    scan_upload_interval_seconds: int = 15
    clock_skew_seconds: int = 15
    late_threshold_seconds: int = 15


@dataclass(slots=True)
class BleSessionContext:
    session: BleSession
    configuration: BleSessionConfiguration


class BlePresenceDisposition(str, Enum):
    PRESENT = "PRESENT"
    ABSENT = "ABSENT"
    MISSING = "MISSING"
    PARTIAL = "PARTIAL"
    LATE = "LATE"


@dataclass(slots=True, frozen=True)
class BleObservationPayload:
    student_id: UUID
    rssi: int
    last_seen: datetime
    rolling_token: str
    timestamp: datetime
    hmac: str


@dataclass(slots=True, frozen=True)
class BleValidatedObservation:
    student_id: UUID
    rssi: int
    last_seen: datetime
    rolling_token: str
    timestamp: datetime
    hmac: str
    anonymous_ble_id: str
    device_hash: str


@dataclass(slots=True, frozen=True)
class BleRejectedObservation:
    observation: BleObservationPayload
    reason: str


@dataclass(slots=True)
class BleObservationProcessingResult:
    accepted: list[BleValidatedObservation] = field(default_factory=list)
    rejected: list[BleRejectedObservation] = field(default_factory=list)

    @property
    def accepted_count(self) -> int:
        return len(self.accepted)

    @property
    def rejected_count(self) -> int:
        return len(self.rejected)


@dataclass(slots=True, frozen=True)
class BlePresenceState:
    student_id: UUID
    first_seen: datetime | None
    last_seen: datetime | None
    disposition: BlePresenceDisposition
    observation_count: int = 0


@dataclass(slots=True, frozen=True)
class BleAttendanceDraft:
    session_id: UUID
    student_id: UUID
    disposition: BlePresenceDisposition
    status: BleAttendanceStatus
    first_seen: datetime
    last_seen: datetime


@dataclass(slots=True)
class BleAttendanceEvaluationResult:
    presence_states: list[BlePresenceState] = field(default_factory=list)
    present_count: int = 0
    absent_count: int = 0
    missing_count: int = 0
    late_count: int = 0


@dataclass(slots=True)
class BleAttendanceGenerationResult:
    drafts: list[BleAttendanceDraft] = field(default_factory=list)
