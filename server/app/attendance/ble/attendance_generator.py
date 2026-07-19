from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from uuid import UUID

from ...models import BleAttendanceStatus
from .contracts import (
    BleAttendanceDraft,
    BleAttendanceEvaluationResult,
    BlePresenceDisposition,
)


@dataclass(slots=True)
class AttendanceGenerator:
    def generate(
        self,
        session_id: UUID,
        session_start_time: datetime,
        evaluation_result: BleAttendanceEvaluationResult,
    ) -> list[BleAttendanceDraft]:
        drafts: list[BleAttendanceDraft] = []
        for presence_state in evaluation_result.presence_states:
            first_seen = presence_state.first_seen or session_start_time
            last_seen = presence_state.last_seen or session_start_time
            drafts.append(
                BleAttendanceDraft(
                    session_id=session_id,
                    student_id=presence_state.student_id,
                    disposition=presence_state.disposition,
                    status=self._map_status(presence_state.disposition),
                    first_seen=first_seen,
                    last_seen=last_seen,
                )
            )
        return drafts

    def _map_status(self, disposition: BlePresenceDisposition) -> BleAttendanceStatus:
        if disposition == BlePresenceDisposition.PRESENT:
            return BleAttendanceStatus.PRESENT
        return BleAttendanceStatus.MISSING
