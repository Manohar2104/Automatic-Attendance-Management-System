from __future__ import annotations

from dataclasses import dataclass
from uuid import UUID

from fastapi import HTTPException

from ...config import settings
from ...models import BleAttendanceMode, BleSession, BleSessionStatus
from ...repositories.ble_repositories import BleSessionRepository
from .contracts import BleSessionConfiguration, BleSessionContext


@dataclass(slots=True)
class BleSessionManager:
    session_repository: BleSessionRepository

    def load_configuration(self) -> BleSessionConfiguration:
        missing_timeout = getattr(settings, "ble_missing_timeout_seconds", 45)
        scan_interval = getattr(settings, "ble_scan_upload_interval_seconds", 15)
        clock_skew = getattr(settings, "ble_clock_skew_seconds", 15)
        late_threshold = getattr(settings, "ble_late_threshold_seconds", 15)
        return BleSessionConfiguration(
            missing_timeout_seconds=missing_timeout,
            scan_upload_interval_seconds=scan_interval,
            clock_skew_seconds=clock_skew,
            late_threshold_seconds=late_threshold,
        )

    async def load_session(self, session_id: UUID) -> BleSession:
        session = await self.session_repository.get_by_id(session_id)
        if session is None:
            raise HTTPException(status_code=404, detail="Session not found")
        if session.attendance_mode != BleAttendanceMode.BLE:
            raise HTTPException(status_code=409, detail="Session is not configured for BLE attendance")
        return session

    async def load_active_session_context(self, session_id: UUID) -> BleSessionContext:
        session = await self.load_session(session_id)
        if session.status != BleSessionStatus.ACTIVE:
            raise HTTPException(status_code=409, detail="Session is not active")
        return BleSessionContext(session=session, configuration=self.load_configuration())

    async def load_session_context(self, session_id: UUID) -> BleSessionContext:
        session = await self.load_session(session_id)
        return BleSessionContext(session=session, configuration=self.load_configuration())
