import logging
import uuid
from datetime import datetime, timezone, timedelta
from uuid import UUID

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..attendance.ble import BleAttendanceEngine
from ..attendance.ble.observation_tracking import cooldown_tracker
from ..attendance.ble.contracts import BlePresenceDisposition
from ..ble_schemas import (
    BleDashboardResponse,
    BleDeviceRegistrationRequest,
    BleDeviceRegistrationResponse,
    BleAttendanceRecord,
    BleAttendanceResponse,
    BleAttendanceSummary,
    BleObservationViewItem,
    BlePresenceResponse,
    BlePresenceSummary,
    BlePresenceViewItem,
    BleObservationUploadRequest,
    BleObservationUploadResponse,
    BleObservationsResponse,
    BleRegisteredDeviceItem,
    BleRegisteredDevicesResponse,
    BleSessionEndRequest,
    BleSessionResponse,
    BleSessionStartRequest,
    BleSessionSummaryResponse,
    BleSessionsListResponse,
)
from ..models import BleAttendanceMode, BleSession, BleSessionStatus, RoleEnum, User
from ..models import Session as LegacySession, SessionStatus as LegacySessionStatus
from ..repositories.ble_repositories import (
    BleAttendanceRepository,
    BleObservationRepository,
    BleRegisteredDeviceRepository,
    BleSessionRepository,
)

logger = logging.getLogger(__name__)


class BleService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.session_repository = BleSessionRepository(db)
        self.registered_device_repository = BleRegisteredDeviceRepository(db)
        self.observation_repository = BleObservationRepository(db)
        self.attendance_repository = BleAttendanceRepository(db)
        self.attendance_engine = BleAttendanceEngine(
            session_repository=self.session_repository,
            registered_device_repository=self.registered_device_repository,
            observation_repository=self.observation_repository,
            attendance_repository=self.attendance_repository,
        )

    def _require_teacher_access(self, current_user) -> None:
        if current_user.role not in (RoleEnum.FACULTY, RoleEnum.ADMIN):
            raise HTTPException(status_code=403, detail="Only teachers can access BLE sessions")

    def _require_student_access(self, current_user) -> None:
        if current_user.role != RoleEnum.STUDENT:
            raise HTTPException(status_code=403, detail="Only students can register BLE devices")

    def _require_session_access(self, session: BleSession, current_user) -> None:
        if current_user.role == RoleEnum.ADMIN:
            return
        if session.teacher_id != current_user.id:
            raise HTTPException(status_code=403, detail="Forbidden")

    def _to_utc_datetime(self, value: datetime | None) -> datetime | None:
        if value is None:
            return None
        if value.tzinfo is None or value.utcoffset() is None:
            return value.replace(tzinfo=timezone.utc)
        return value.astimezone(timezone.utc)

    def _truncate_token(self, value: str) -> str:
        token = value.strip().upper()
        if len(token) <= 16:
            return token
        return f"{token[:16]}..."

    def _session_window_start(self, session: BleSession, legacy_session: LegacySession | None = None) -> datetime:
        legacy_start = None
        if legacy_session is not None:
            legacy_start = legacy_session.actual_start or legacy_session.scheduled_start
        session_start = session.start_time

        if legacy_start is None:
            return self._to_utc_datetime(session_start) or session_start

        normalized_legacy_start = self._to_utc_datetime(legacy_start) or legacy_start
        normalized_session_start = self._to_utc_datetime(session_start) or session_start
        return max(normalized_legacy_start, normalized_session_start)

    def _filter_session_window_observations(
        self,
        observations,
        session_start: datetime,
        registered_student_ids: set[UUID],
    ):
        return [
            observation
            for observation in observations
            if observation.student_id in registered_student_ids
            and self._to_utc_datetime(observation.timestamp) >= session_start
        ]

    def _filter_session_window_attendance(
        self,
        attendance,
        session_start: datetime,
        registered_student_ids: set[UUID],
    ):
        return [
            record
            for record in attendance
            if record.student_id in registered_student_ids
            and self._to_utc_datetime(record.last_seen) >= session_start
        ]

    async def _current_registered_student_ids(self) -> set[UUID]:
        registered_devices = await self.registered_device_repository.list_all()
        return {device.student_id for device in registered_devices}

    def _filter_registered_observations(self, observations, registered_student_ids: set[UUID]):
        return [observation for observation in observations if observation.student_id in registered_student_ids]

    def _filter_registered_attendance(self, attendance, registered_student_ids: set[UUID]):
        return [record for record in attendance if record.student_id in registered_student_ids]

    async def start_session(
        self, payload: BleSessionStartRequest, current_user
    ) -> BleSessionResponse:
        self._require_teacher_access(current_user)

        if payload.teacher_id != current_user.id:
            raise HTTPException(
                status_code=403,
                detail="Teacher identity does not match the authenticated user",
            )

        try:
            session_id = uuid.uuid4()
            legacy_session = LegacySession(
                id=session_id,
                course_id=payload.course_id,
                room_id="Room101",
                location="Room101",
                scheduled_start=payload.start_time,
                scheduled_end=payload.start_time + timedelta(hours=1),
                actual_start=payload.start_time,
                actual_end=None,
                status=LegacySessionStatus.ACTIVE,
            )
            session = BleSession(
                id=session_id,
                course_id=payload.course_id,
                teacher_id=current_user.id,
                attendance_mode=BleAttendanceMode.BLE,
                status=BleSessionStatus.ACTIVE,
                start_time=payload.start_time,
                end_time=payload.start_time + timedelta(hours=1),
                packets_received=0,
            )
            self.db.add(legacy_session)
            session = await self.session_repository.create(session)
            await cooldown_tracker.reset_session(session.id)
            logger.info(
                "BLE session created status=%s teacher_id=%s session_id=%s",
                session.status,
                session.teacher_id,
                session.id,
            )
            logger.info("SESSION STARTED course=%s faculty=%s session_id=%s", session.course_id, current_user.email, session.id)
            await self.db.commit()
        except Exception:
            await self.db.rollback()
            logger.exception("Failed to create BLE session")
            raise

        return self._to_session_response(session, legacy_session=legacy_session)

    async def end_session(
        self, payload: BleSessionEndRequest, current_user
    ) -> BleSessionResponse:
        self._require_teacher_access(current_user)

        session = await self.session_repository.get_by_id(payload.session_id)
        if not session:
            raise HTTPException(status_code=404, detail="Session not found")

        self._require_session_access(session, current_user)

        if session.status == BleSessionStatus.ENDED:
            raise HTTPException(status_code=409, detail="Session is already ended")
        if session.status == BleSessionStatus.CANCELLED:
            raise HTTPException(status_code=409, detail="Session is cancelled")

        try:
            evaluation_result = await self.attendance_engine.finalize_session(payload.session_id, payload.ended_at)
            session = await self.session_repository.update_status(
                payload.session_id,
                BleSessionStatus.ENDED,
                end_time=payload.ended_at,
            )
            await cooldown_tracker.reset_session(payload.session_id)
            await self._log_session_summary(session, evaluation_result, payload.ended_at, current_user.email)
            await self.db.commit()
        except Exception:
            await self.db.rollback()
            logger.exception("Failed to end BLE session")
            raise

        if session is None:
            raise HTTPException(status_code=404, detail="Session not found")

        legacy_session = await self._lookup_legacy_session(session.id)
        return self._to_session_response(session, legacy_session=legacy_session)

    async def get_session(
        self, session_id: UUID, current_user
    ) -> BleSessionResponse:
        session = await self.session_repository.get_by_id(session_id)
        if not session:
            raise HTTPException(status_code=404, detail="Session not found")

        self._require_session_access(session, current_user)

        legacy_session = await self._lookup_legacy_session(session.id)
        return self._to_session_response(session, legacy_session=legacy_session)

    async def upload_observations(
        self, payload: BleObservationUploadRequest, current_user
    ) -> BleObservationUploadResponse:
        self._require_teacher_access(current_user)

        logger.info(
            "BLE upload request received session_id=%s teacher_id=%s observed_at=%s observations=%s",
            payload.session_id,
            payload.teacher_id,
            payload.observed_at,
            len(payload.observations),
        )
        logger.info(
            "BLE upload auth context jwt_user_id=%s jwt_user_email=%s jwt_user_role=%s",
            getattr(current_user, "id", None),
            getattr(current_user, "email", None),
            getattr(current_user, "role", None),
        )
        for item in payload.observations:
            logger.info(
                "BLE upload item session_id=%s student_id=%s rolling_token=%s rssi=%s last_seen=%s timestamp=%s",
                payload.session_id,
                item.student_id,
                self._truncate_token(item.rolling_token),
                item.rssi,
                item.last_seen,
                item.timestamp,
            )

        session = await self.session_repository.get_by_id(payload.session_id)
        if not session:
            logger.info("BLE upload rejected: session not found session_id=%s", payload.session_id)
            raise HTTPException(status_code=404, detail="Session not found")

        self._require_session_access(session, current_user)

        if session.status != BleSessionStatus.ACTIVE:
            logger.info(
                "BLE upload rejected: session not active session_id=%s status=%s",
                payload.session_id,
                session.status,
            )
            raise HTTPException(
                status_code=409,
                detail="Session is not accepting observation uploads",
            )

        session.packets_received = (session.packets_received or 0) + len(payload.observations)

        try:
            logger.info(
                "BLE observations upload requested session=%s teacher=%s received=%s",
                payload.session_id,
                payload.teacher_id,
                len(payload.observations),
            )
            legacy_room = await self._lookup_legacy_room(session.id)
            upload_response = await self.attendance_engine.ingest_observations_with_context(
                payload,
                teacher_email=current_user.email,
                room=legacy_room,
            )
            await self.db.commit()
        except Exception:
            await self.db.rollback()
            logger.exception("Failed to store BLE observations")
            raise

        logger.info(
            "BLE upload completed session_id=%s received=%s accepted=%s rejected=%s",
            upload_response.session_id,
            upload_response.received,
            upload_response.accepted,
            upload_response.rejected,
        )

        return upload_response

    async def get_dashboard(self, current_user) -> BleDashboardResponse:
        self._require_teacher_access(current_user)

        logger.info("BLE dashboard endpoint called by user=%s", current_user.id)

        if current_user.role == RoleEnum.ADMIN:
            legacy_query = (
                select(LegacySession)
                .where(LegacySession.status == LegacySessionStatus.ACTIVE)
                .order_by(LegacySession.scheduled_start.desc())
            )
            ble_query = (
                select(BleSession)
                .where(BleSession.status == BleSessionStatus.ACTIVE)
                .order_by(BleSession.start_time.desc())
            )
        else:
            legacy_query = (
                select(LegacySession)
                .join(BleSession, BleSession.id == LegacySession.id)
                .where(
                    LegacySession.status == LegacySessionStatus.ACTIVE,
                    BleSession.teacher_id == current_user.id,
                )
                .order_by(LegacySession.scheduled_start.desc())
            )
            ble_query = (
                select(BleSession)
                .where(
                    BleSession.status == BleSessionStatus.ACTIVE,
                    BleSession.teacher_id == current_user.id,
                )
                .order_by(BleSession.start_time.desc())
            )

        legacy_result = await self.db.execute(legacy_query)
        active_legacy_session = legacy_result.scalars().first()

        ble_session = None
        if active_legacy_session is not None:
            ble_result = await self.db.execute(
                select(BleSession).where(BleSession.id == active_legacy_session.id)
            )
            ble_session = ble_result.scalars().first()
        if ble_session is None:
            ble_result = await self.db.execute(ble_query)
            ble_session = ble_result.scalars().first()

        logger.info(
            "Dashboard lookup: legacy=%s ble=%s status=%s",
            active_legacy_session.id if active_legacy_session else None,
            ble_session.id if ble_session else None,
            ble_session.status if ble_session else None,
        )

        session = ble_session

        if session is None:
            registered_devices = await self.registered_device_repository.list_all()
            logger.info(
                "BLE dashboard active session not found registered_devices=%s",
                len(registered_devices),
            )
            logger.info(
                "Dashboard updated session=None registered_devices=%s students_seen=0 packets_received=0",
                len(registered_devices),
            )
            return BleDashboardResponse(
                session=None,
                registered_devices=len(registered_devices),
                students_seen=0,
                packets_received=0,
                faculty=current_user.email,
                room=None,
            )

        registered_devices = await self.registered_device_repository.list_all()
        registered_student_ids = {device.student_id for device in registered_devices}
        session_start = self._session_window_start(session, active_legacy_session)
        observations = self._filter_session_window_observations(
            await self.observation_repository.list_by_session(session.id),
            session_start,
            registered_student_ids,
        )
        detected_students = {observation.student_id for observation in observations}
        packets_received = len(observations)
        logger.info(
            "BLE dashboard active session selected session=%s registered_devices=%s students_seen=%s packets_received=%s",
            session.id,
            len(registered_devices),
            len(detected_students),
            packets_received,
        )
        latest_observation = max((observation.timestamp for observation in observations), default=None)
        average_rssi = None
        if observations:
            average_rssi = round(sum(observation.rssi for observation in observations) / len(observations), 2)
        logger.info(
            "Dashboard metrics session_id=%s observation_count=%s students_seen=%s packets_received=%s average_rssi=%s latest_observation=%s",
            session.id,
            len(observations),
            len(detected_students),
            packets_received,
            average_rssi,
            latest_observation,
        )
        logger.info(
            "Dashboard updated session=%s registered_devices=%s students_seen=%s packets_received=%s",
            session.id,
            len(registered_devices),
            len(detected_students),
            packets_received,
        )
        return BleDashboardResponse(
            session=self._to_session_response(session, legacy_session=active_legacy_session),
            registered_devices=len(registered_devices),
            students_seen=len(detected_students),
            packets_received=packets_received,
            faculty=current_user.email,
            room=active_legacy_session.location if active_legacy_session else None,
        )

    async def list_registered_devices(self, current_user) -> BleRegisteredDevicesResponse:
        self._require_teacher_access(current_user)
        devices = await self.registered_device_repository.list_all()
        logger.info(
            "BLE registered devices endpoint called user=%s count=%s",
            current_user.id,
            len(devices),
        )
        return BleRegisteredDevicesResponse(
            registered_devices=[
                BleRegisteredDeviceItem(
                    student_id=device.student_id,
                    anonymous_ble_id=device.anonymous_ble_id,
                    public_identifier=device.public_identifier,
                )
                for device in devices
            ]
        )

    async def register_device(
        self, payload: BleDeviceRegistrationRequest, current_user
    ) -> BleDeviceRegistrationResponse:
        self._require_student_access(current_user)

        anonymous_ble_id = payload.anonymous_ble_id.strip().lower()
        public_identifier = (payload.public_identifier or current_user.email).strip()
        device_hash = payload.device_hash.strip()

        if not anonymous_ble_id or not public_identifier or not device_hash:
            raise HTTPException(status_code=400, detail="Invalid BLE registration payload")

        anonymous_conflict = await self.registered_device_repository.get_by_anonymous_ble_id(
            anonymous_ble_id
        )
        if anonymous_conflict and anonymous_conflict.student_id != current_user.id:
            raise HTTPException(
                status_code=409,
                detail="Anonymous BLE identifier is already registered",
            )

        device_hash_conflict = await self.registered_device_repository.get_by_device_hash(
            device_hash
        )
        if device_hash_conflict and device_hash_conflict.student_id != current_user.id:
            raise HTTPException(
                status_code=409,
                detail="Device hash is already registered",
            )

        try:
            device = await self.registered_device_repository.upsert_for_student(
                student_id=current_user.id,
                anonymous_ble_id=anonymous_ble_id,
                public_identifier=public_identifier,
                device_hash=device_hash,
            )
            logger.info(
                "STUDENT REGISTERED student_id=%s anonymous_ble_id=%s public_identifier=%s",
                device.student_id,
                device.anonymous_ble_id,
                device.public_identifier,
            )
            await self.db.commit()
        except Exception:
            await self.db.rollback()
            logger.exception("Failed to register BLE device")
            raise

        return BleDeviceRegistrationResponse(
            success=True,
            student_id=device.student_id,
            anonymous_ble_id=device.anonymous_ble_id,
        )

    async def get_attendance(
        self, session_id: UUID, current_user
    ) -> BleAttendanceResponse:
        session = await self.session_repository.get_by_id(session_id)
        if not session:
            raise HTTPException(status_code=404, detail="Session not found")

        self._require_session_access(session, current_user)
        response = await self.attendance_engine.get_attendance(session_id)
        registered_student_ids = await self._current_registered_student_ids()
        filtered_records = [
            record
            for record in response.records
            if record.student_id in registered_student_ids
        ]
        return BleAttendanceResponse(
            session_id=response.session_id,
            attendance_mode=response.attendance_mode,
            summary=BleAttendanceSummary(
                present=sum(1 for record in filtered_records if record.status == "PRESENT"),
                missing=sum(1 for record in filtered_records if record.status == "MISSING"),
            ),
            records=filtered_records,
        )

    async def list_sessions(self, current_user) -> BleSessionsListResponse:
        self._require_teacher_access(current_user)

        if current_user.role == RoleEnum.ADMIN:
            result = await self.db.execute(
                select(BleSession).order_by(BleSession.start_time.desc())
            )
        else:
            result = await self.db.execute(
                select(BleSession)
                .where(BleSession.teacher_id == current_user.id)
                .order_by(BleSession.start_time.desc())
            )

        sessions = result.scalars().all()
        legacy_result = await self.db.execute(
            select(LegacySession).where(LegacySession.id.in_([session.id for session in sessions]))
        )
        legacy_sessions = {legacy_session.id: legacy_session for legacy_session in legacy_result.scalars().all()}
        return BleSessionsListResponse(
            sessions=[
                self._to_session_response(session, legacy_session=legacy_sessions.get(session.id))
                for session in sessions
            ]
        )

    async def get_session_summary(
        self, session_id: UUID, current_user
    ) -> BleSessionSummaryResponse:
        session = await self.session_repository.get_by_id(session_id)
        if not session:
            raise HTTPException(status_code=404, detail="Session not found")

        self._require_session_access(session, current_user)

        legacy_result = await self.db.execute(select(LegacySession).where(LegacySession.id == session.id))
        legacy_session = legacy_result.scalars().first()

        registered_devices = await self.registered_device_repository.list_all()
        registered_student_ids = {device.student_id for device in registered_devices}
        session_start = self._session_window_start(session, legacy_session)
        observations = self._filter_session_window_observations(
            await self.observation_repository.list_by_session(session_id),
            session_start,
            registered_student_ids,
        )
        attendance = self._filter_session_window_attendance(
            await self.attendance_repository.list_by_session(session_id),
            session_start,
            registered_student_ids,
        )

        detected_students = {observation.student_id for observation in observations}
        latest_observation = max(
            (observation.timestamp for observation in observations),
            default=None,
        )
        average_rssi = None
        if observations:
            average_rssi = round(
                sum(observation.rssi for observation in observations) / len(observations),
                2,
            )

        present = sum(
            1
            for record in attendance
            if (record.status.value if hasattr(record.status, "value") else str(record.status))
            == "PRESENT"
        )
        missing = sum(
            1
            for record in attendance
            if (record.status.value if hasattr(record.status, "value") else str(record.status))
            == "MISSING"
        )
        packets_received = len(observations)

        return BleSessionSummaryResponse(
            session=self._to_session_response(session, legacy_session=legacy_session),
            registered_students=len(registered_devices),
            detected_students=len(detected_students),
            packets_received=packets_received,
            present=present,
            missing=missing,
            absent=None,
            late=None,
            latest_observation=self._to_utc_datetime(latest_observation),
            average_rssi=average_rssi,
        )

    async def _log_session_summary(
        self,
        session: BleSession | None,
        evaluation_result,
        ended_at: datetime | None,
        faculty_email: str,
    ) -> None:
        if session is None:
            return

        registered_devices = await self.registered_device_repository.list_all()
        session_start = self._session_window_start(session)
        registered_student_ids = {device.student_id for device in registered_devices}
        observations = self._filter_session_window_observations(
            await self.observation_repository.list_by_session(session.id),
            session_start,
            registered_student_ids,
        )
        attendance = self._filter_session_window_attendance(
            await self.attendance_repository.list_by_session(session.id),
            session_start,
            registered_student_ids,
        )
        detected_students = {observation.student_id for observation in observations}
        packets_received = len(observations)
        presence_states = getattr(evaluation_result, "presence_states", []) or []
        partial_count = sum(
            1 for state in presence_states if state.disposition == BlePresenceDisposition.PARTIAL
        )
        present_count = getattr(evaluation_result, "present_count", sum(1 for state in presence_states if state.disposition == BlePresenceDisposition.PRESENT))
        missing_count = getattr(evaluation_result, "missing_count", sum(1 for state in presence_states if state.disposition == BlePresenceDisposition.MISSING))
        average_rssi = None
        if observations:
            average_rssi = round(
                sum(observation.rssi for observation in observations) / len(observations),
                2,
            )

        start_time = self._to_utc_datetime(session.start_time)
        end_time = self._to_utc_datetime(ended_at) or datetime.now(timezone.utc)
        if start_time is not None:
            duration_seconds = max(0, int((end_time - start_time).total_seconds()))
        else:
            duration_seconds = 0

        minutes = duration_seconds // 60
        logger.info(
            "[BLE][SUMMARY] Session=%s Present=%s Missing=%s Partial=%s",
            session.id,
            present_count,
            missing_count,
            partial_count,
        )
        logger.info(
            "[BLE][SUMMARY] Course=%s Faculty=%s DurationMinutes=%s RegisteredDevices=%s StudentsSeen=%s PacketsReceived=%s PacketsAccepted=%s AttendanceRecords=%s AverageRssi=%s",
            session.course_id,
            faculty_email,
            minutes,
            len(registered_devices),
            len(detected_students),
            packets_received,
            len(observations),
            len(attendance),
            average_rssi if average_rssi is not None else "--",
        )
        logger.info("SESSION ENDED session_id=%s status=%s", session.id, session.status)

    async def get_session_observations(
        self, session_id: UUID, current_user
    ) -> BleObservationsResponse:
        session = await self.session_repository.get_by_id(session_id)
        if not session:
            raise HTTPException(status_code=404, detail="Session not found")

        self._require_session_access(session, current_user)

        legacy_session = await self._lookup_legacy_session(session.id)

        registered_student_ids = await self._current_registered_student_ids()
        session_start = self._session_window_start(session, legacy_session)
        observations = self._filter_session_window_observations(
            await self.observation_repository.list_by_session(session_id),
            session_start,
            registered_student_ids,
        )
        if not observations:
            logger.info(
                "Dashboard observations metrics session_id=%s observation_count=0 students_seen=0 packets_received=0 average_rssi=None latest_observation=None",
                session_id,
            )
            return BleObservationsResponse(session_id=session_id, observations=[])

        latest_by_student: dict[UUID, object] = {}
        advertisement_count_by_student: dict[UUID, int] = {}
        for observation in observations:
            advertisement_count_by_student[observation.student_id] = (
                advertisement_count_by_student.get(observation.student_id, 0) + 1
            )
            current_latest = latest_by_student.get(observation.student_id)
            if current_latest is None or observation.timestamp > current_latest.timestamp:
                latest_by_student[observation.student_id] = observation

        student_ids = list(latest_by_student.keys())
        student_name_map = await self._get_student_name_map(student_ids)
        registered_devices = await self.registered_device_repository.list_all()
        anonymous_id_by_student = {
            device.student_id: device.anonymous_ble_id for device in registered_devices
            if device.student_id in student_ids
        }

        items = [
            BleObservationViewItem(
                student_id=student_id,
                student_name=student_name_map.get(student_id),
                anonymous_ble_id=anonymous_id_by_student.get(student_id),
                rssi=latest_by_student[student_id].rssi,
                last_seen=self._to_utc_datetime(latest_by_student[student_id].last_seen),
                advertisement_count=advertisement_count_by_student.get(student_id, 0),
                observation_timestamp=self._to_utc_datetime(latest_by_student[student_id].timestamp),
            )
            for student_id in student_ids
        ]
        items.sort(key=lambda item: item.observation_timestamp, reverse=True)

        latest_observation = max((observation.timestamp for observation in observations), default=None)
        average_rssi = round(sum(observation.rssi for observation in observations) / len(observations), 2)
        logger.info(
            "Dashboard observations metrics session_id=%s observation_count=%s students_seen=%s packets_received=%s average_rssi=%s latest_observation=%s",
            session_id,
            len(items),
            len(student_ids),
            len(observations),
            average_rssi,
            latest_observation,
        )

        return BleObservationsResponse(session_id=session_id, observations=items)

    async def _lookup_legacy_room(self, session_id: UUID) -> str | None:
        legacy_session = await self._lookup_legacy_session(session_id)
        return legacy_session.location if legacy_session else None

    async def _lookup_legacy_session(self, session_id: UUID) -> LegacySession | None:
        result = await self.db.execute(select(LegacySession).where(LegacySession.id == session_id))
        return result.scalars().first()

    async def get_session_presence(
        self, session_id: UUID, current_user
    ) -> BlePresenceResponse:
        session = await self.session_repository.get_by_id(session_id)
        if not session:
            raise HTTPException(status_code=404, detail="Session not found")

        self._require_session_access(session, current_user)

        legacy_session = await self._lookup_legacy_session(session.id)

        registered_student_ids = await self._current_registered_student_ids()
        session_start = self._session_window_start(session, legacy_session)
        records = self._filter_session_window_attendance(
            await self.attendance_repository.list_by_session(session_id),
            session_start,
            registered_student_ids,
        )
        student_ids = [record.student_id for record in records]
        student_name_map = await self._get_student_name_map(student_ids)

        view_records = [
            BlePresenceViewItem(
                student_id=record.student_id,
                student_name=student_name_map.get(record.student_id),
                status=record.status.value
                if hasattr(record.status, "value")
                else str(record.status),
                first_seen=self._to_utc_datetime(record.first_seen),
                last_seen=self._to_utc_datetime(record.last_seen),
            )
            for record in records
        ]

        present = sum(1 for record in view_records if record.status == "PRESENT")
        missing = sum(1 for record in view_records if record.status == "MISSING")

        return BlePresenceResponse(
            session_id=session_id,
            summary=BlePresenceSummary(
                present=present,
                missing=missing,
                absent=None,
                late=None,
            ),
            records=view_records,
        )

    async def _get_student_name_map(self, student_ids: list[UUID]) -> dict[UUID, str]:
        if not student_ids:
            return {}
        result = await self.db.execute(select(User).where(User.id.in_(student_ids)))
        users = result.scalars().all()
        return {user.id: user.email for user in users}

    def _to_session_response(
        self,
        session: BleSession,
        legacy_session: LegacySession | None = None,
        room: str | None = None,
    ) -> BleSessionResponse:
        display_room = room
        if display_room is None and legacy_session is not None:
            display_room = legacy_session.location

        display_start_time = session.start_time
        display_end_time = session.end_time
        if legacy_session is not None:
            display_start_time = legacy_session.actual_start or legacy_session.scheduled_start or session.start_time
            display_end_time = legacy_session.actual_end or legacy_session.scheduled_end or session.end_time

        return BleSessionResponse(
            session_id=session.id,
            course_id=session.course_id,
            teacher_id=session.teacher_id,
            attendance_mode="BLE",
            status=session.status.value if hasattr(session.status, "value") else str(session.status),
            start_time=self._to_utc_datetime(display_start_time),
            end_time=self._to_utc_datetime(display_end_time),
            room=display_room,
            created_at=self._to_utc_datetime(session.created_at),
            updated_at=self._to_utc_datetime(session.updated_at),
        )
