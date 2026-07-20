from __future__ import annotations

import logging
from dataclasses import dataclass
from uuid import UUID

from ...models import BleAttendance, BleObservation
from ...repositories.ble_repositories import BleAttendanceRepository, BleObservationRepository
from .contracts import BleAttendanceDraft


logger = logging.getLogger(__name__)


def _truncate_token(value: str) -> str:
    token = value.strip().upper()
    if len(token) <= 16:
        return token
    return f"{token[:16]}..."


@dataclass(slots=True)
class AttendancePersistence:
    attendance_repository: BleAttendanceRepository
    observation_repository: BleObservationRepository

    async def store_observations(self, observations: list[BleObservation]) -> list[BleObservation]:
        if not observations:
            return []
        logger.info("Database INSERT observations count=%s", len(observations))
        persisted = await self.observation_repository.create_many(observations)
        for observation in persisted:
            logger.info(
                "Observation stored observation_id=%s session=%s student=%s rssi=%s anonymous_id=<resolved_pre_storage> rolling_token=%s timestamp=%s",
                observation.id,
                observation.session_id,
                observation.student_id,
                observation.rssi,
                _truncate_token(observation.rolling_token),
                observation.timestamp,
            )
        return persisted

    async def store_attendance(
        self,
        drafts: list[BleAttendanceDraft],
        session_id: UUID | None = None,
    ) -> list[BleAttendance]:
        effective_session_id = session_id or (drafts[0].session_id if drafts else None)
        if effective_session_id is None:
            return []

        current_rows = await self.attendance_repository.list_by_session(effective_session_id)
        desired_student_ids = [draft.student_id for draft in drafts]
        if current_rows:
            deleted_count = await self.attendance_repository.delete_by_session_and_student_ids(
                effective_session_id,
                desired_student_ids,
            )
            if deleted_count > 0:
                logger.info(
                    "Deleted stale attendance rows session=%s deleted=%s retained=%s",
                    effective_session_id,
                    deleted_count,
                    len(desired_student_ids),
                )

        persisted: list[BleAttendance] = []
        for draft in drafts:
            attendance = BleAttendance(
                session_id=draft.session_id,
                student_id=draft.student_id,
                status=draft.status,
                first_seen=draft.first_seen,
                last_seen=draft.last_seen,
            )
            stored = await self.upsert_attendance(attendance)
            logger.info(
                "Attendance Updated session=%s student=%s status=%s first_seen=%s last_seen=%s",
                stored.session_id,
                stored.student_id,
                stored.status,
                stored.first_seen,
                stored.last_seen,
            )
            persisted.append(stored)
        return persisted

    async def create_attendance(self, attendance: BleAttendance) -> BleAttendance:
        return await self.attendance_repository.create(attendance)

    async def update_attendance(self, attendance: BleAttendance) -> BleAttendance:
        return await self.attendance_repository.update(attendance)

    async def upsert_attendance(self, attendance: BleAttendance) -> BleAttendance:
        logger.info(
            "Database INSERT/UPDATE attendance session=%s student=%s status=%s",
            attendance.session_id,
            attendance.student_id,
            attendance.status,
        )
        return await self.attendance_repository.upsert(attendance)

    async def read_attendance(self, session_id: UUID) -> list[BleAttendance]:
        return await self.attendance_repository.list_by_session(session_id)

    async def get_attendance(self, session_id: UUID, student_id: UUID) -> BleAttendance | None:
        return await self.attendance_repository.get_by_session_and_student(session_id, student_id)

    async def retrieve_attendance(self, session_id: UUID) -> list[BleAttendance]:
        return await self.read_attendance(session_id)
