from __future__ import annotations

import logging
from datetime import datetime, timezone
from uuid import UUID

from ...ble_schemas import (
    BleAttendanceRecord,
    BleAttendanceResponse,
    BleAttendanceSummary,
    BleObservationUploadRequest,
    BleObservationUploadResponse,
)
from ...models import BleObservation
from ...repositories.ble_repositories import (
    BleAttendanceRepository,
    BleObservationRepository,
    BleRegisteredDeviceRepository,
    BleSessionRepository,
)
from .attendance_generator import AttendanceGenerator
from .attendance_persistence import AttendancePersistence
from .ble_observation_processor import BleObservationProcessor
from .ble_packet_validator import BlePacketValidator
from .ble_session_manager import BleSessionManager
from .contracts import BleAttendanceEvaluationResult, BleObservationProcessingResult, BleSessionContext, BleValidatedObservation
from .presence_evaluator import PresenceEvaluator
from ...config import settings


logger = logging.getLogger(__name__)


class BleAttendanceEngine:
    def __init__(
        self,
        session_repository: BleSessionRepository,
        registered_device_repository: BleRegisteredDeviceRepository,
        observation_repository: BleObservationRepository,
        attendance_repository: BleAttendanceRepository,
    ) -> None:
        self.session_repository = session_repository
        self.registered_device_repository = registered_device_repository
        self.observation_repository = observation_repository
        self.attendance_repository = attendance_repository
        self.session_manager = BleSessionManager(self.session_repository)
        self.packet_validator = BlePacketValidator()
        self.observation_processor = BleObservationProcessor(
            session_manager=self.session_manager,
            registered_device_repository=self.registered_device_repository,
            packet_validator=self.packet_validator,
        )
        self.presence_evaluator = PresenceEvaluator(
            presence_timeout_seconds=settings.ble_presence_timeout_seconds,
            recovery_window_seconds=settings.ble_recovery_window_seconds,
            partial_threshold_seconds=settings.ble_partial_threshold_seconds,
        )
        self.attendance_generator = AttendanceGenerator()
        self.persistence = AttendancePersistence(
            attendance_repository=self.attendance_repository,
            observation_repository=self.observation_repository,
        )

    async def ingest_observations(self, payload: BleObservationUploadRequest) -> BleObservationUploadResponse:
        session_context, processing_result = await self.observation_processor.process_batch(payload)
        observation_rows = self._to_observation_rows(session_context, processing_result)
        await self.persistence.store_observations(observation_rows)
        self._log_observed_observations(session_context.session.id, observation_rows)

        session_observations = await self.observation_repository.list_by_session(session_context.session.id)
        registered_devices = await self.registered_device_repository.list_all()
        evaluation_result = self.presence_evaluator.evaluate(
            session_start_time=session_context.session.start_time,
            evaluated_at=payload.observed_at,
            observations=self._to_validated_observations(session_observations),
            registered_devices=registered_devices,
        )
        drafts = self.attendance_generator.generate(
            session_context.session.id,
            session_context.session.start_time,
            evaluation_result,
        )
        await self.persistence.store_attendance(drafts)

        return BleObservationUploadResponse(
            session_id=payload.session_id,
            received=len(payload.observations),
            accepted=processing_result.accepted_count,
            rejected=processing_result.rejected_count,
            processed_at=datetime.now(timezone.utc),
        )

    async def ingest_observations_with_context(
        self,
        payload: BleObservationUploadRequest,
        *,
        teacher_email: str | None = None,
        room: str | None = None,
    ) -> BleObservationUploadResponse:
        session_context, processing_result = await self.observation_processor.process_batch(
            payload,
            teacher_email=teacher_email,
            room=room,
        )
        observation_rows = self._to_observation_rows(session_context, processing_result)
        await self.persistence.store_observations(observation_rows)
        self._log_observed_observations(session_context.session.id, observation_rows)

        session_observations = await self.observation_repository.list_by_session(session_context.session.id)
        registered_devices = await self.registered_device_repository.list_all()
        evaluation_result = self.presence_evaluator.evaluate(
            session_start_time=session_context.session.start_time,
            evaluated_at=payload.observed_at,
            observations=self._to_validated_observations(session_observations),
            registered_devices=registered_devices,
        )
        drafts = self.attendance_generator.generate(
            session_context.session.id,
            session_context.session.start_time,
            evaluation_result,
        )
        await self.persistence.store_attendance(drafts)

        return BleObservationUploadResponse(
            session_id=payload.session_id,
            received=len(payload.observations),
            accepted=processing_result.accepted_count,
            rejected=processing_result.rejected_count,
            processed_at=datetime.now(timezone.utc),
        )

    async def finalize_session(
        self, session_id: UUID, ended_at: datetime | None = None
    ) -> BleAttendanceEvaluationResult:
        session_context = await self.session_manager.load_session_context(session_id)
        evaluation_time = ended_at or datetime.now(timezone.utc)
        session_observations = await self.observation_repository.list_by_session(session_context.session.id)
        registered_devices = await self.registered_device_repository.list_all()
        evaluation_result = self.presence_evaluator.evaluate(
            session_start_time=session_context.session.start_time,
            evaluated_at=evaluation_time,
            observations=self._to_validated_observations(session_observations),
            registered_devices=registered_devices,
        )
        drafts = self.attendance_generator.generate(
            session_context.session.id,
            session_context.session.start_time,
            evaluation_result,
        )
        await self.persistence.store_attendance(drafts)
        return evaluation_result

    async def get_attendance(self, session_id: UUID) -> BleAttendanceResponse:
        session_context = await self.session_manager.load_session_context(session_id)
        records = await self.persistence.read_attendance(session_context.session.id)
        response_records = [
            BleAttendanceRecord(
                student_id=record.student_id,
                status=record.status.value if hasattr(record.status, "value") else str(record.status),
                first_seen=record.first_seen,
                last_seen=record.last_seen,
            )
            for record in records
        ]
        summary = BleAttendanceSummary(
            present=sum(1 for record in response_records if record.status == "PRESENT"),
            missing=sum(1 for record in response_records if record.status == "MISSING"),
        )
        return BleAttendanceResponse(
            session_id=session_id,
            attendance_mode="BLE",
            summary=summary,
            records=response_records,
        )

    def _to_observation_rows(
        self,
        session_context: BleSessionContext,
        processing_result: BleObservationProcessingResult,
    ) -> list[BleObservation]:
        rows: list[BleObservation] = []
        for observation in processing_result.accepted:
            rows.append(
                BleObservation(
                    session_id=session_context.session.id,
                    student_id=observation.student_id,
                    rssi=observation.rssi,
                    rolling_token=observation.rolling_token,
                    timestamp=observation.timestamp,
                    hmac=observation.hmac,
                    last_seen=observation.last_seen,
                )
            )
        return rows

    def _to_validated_observations(self, observations: list[BleObservation]) -> list[BleValidatedObservation]:
        return [
            BleValidatedObservation(
                student_id=observation.student_id,
                rssi=observation.rssi,
                last_seen=observation.last_seen,
                rolling_token=observation.rolling_token,
                timestamp=observation.timestamp,
                hmac=observation.hmac,
                anonymous_ble_id="",
                device_hash="",
            )
            for observation in observations
        ]

    def _log_observed_observations(self, session_id: UUID, observations: list[BleObservation]) -> None:
        for observation in observations:
            logger.info(
                "[BLE][OBSERVED] session=%s student=%s status=PRESENT last_seen=%s rssi=%s",
                session_id,
                observation.student_id,
                observation.last_seen,
                observation.rssi,
            )
