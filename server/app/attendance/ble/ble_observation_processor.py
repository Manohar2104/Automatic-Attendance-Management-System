from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from uuid import UUID

from ...ble_schemas import BleObservationUploadRequest
from ...models import BleRegisteredDevice
from ...repositories.ble_repositories import BleRegisteredDeviceRepository
from .ble_packet_validator import BlePacketValidator
from .ble_session_manager import BleSessionManager
from .observation_tracking import cooldown_tracker
from .contracts import (
    BleObservationPayload,
    BleObservationProcessingResult,
    BleRejectedObservation,
    BleSessionContext,
    BleValidatedObservation,
)


logger = logging.getLogger(__name__)


@dataclass(slots=True)
class BleObservationProcessor:
    session_manager: BleSessionManager
    registered_device_repository: BleRegisteredDeviceRepository
    packet_validator: BlePacketValidator

    async def process_batch(
        self,
        payload: BleObservationUploadRequest,
        *,
        teacher_email: str | None = None,
        room: str | None = None,
    ) -> tuple[BleSessionContext, BleObservationProcessingResult]:
        session_context = await self.session_manager.load_active_session_context(payload.session_id)
        batch_result = BleObservationProcessingResult()
        seen_keys: set[tuple[UUID, str, datetime]] = set()

        for item in payload.observations:
            normalized_last_seen = self._to_utc(item.last_seen)
            normalized_timestamp = self._to_utc(item.timestamp)
            logger.info(
                "Received BLE observation session_id=%s student_id=%s rolling_token=%s rssi=%s last_seen=%s timestamp=%s",
                payload.session_id,
                item.student_id,
                item.rolling_token,
                item.rssi,
                normalized_last_seen,
                normalized_timestamp,
            )
            logger.info(
                "Observation validation stage=payload_normalized session_id=%s anonymous_id=%s rolling_token=%s",
                payload.session_id,
                item.student_id,
                item.rolling_token,
            )
            observation_payload = BleObservationPayload(
                student_id=item.student_id,
                rssi=item.rssi,
                last_seen=normalized_last_seen,
                rolling_token=item.rolling_token,
                timestamp=normalized_timestamp,
                hmac=item.hmac,
            )

            duplicate_key = (item.student_id, item.rolling_token.strip().lower(), normalized_timestamp)
            if duplicate_key in seen_keys:
                batch_result.rejected.append(
                    BleRejectedObservation(
                        observation=observation_payload,
                        reason="Duplicate observation within the upload batch",
                    )
                )
                continue
            seen_keys.add(duplicate_key)

            registered_device = await self._load_registered_device(item.student_id)
            if registered_device is None:
                logger.info(
                    "Observation rejected reason=Registration missing session_id=%s student_id=%s",
                    payload.session_id,
                    item.student_id,
                )
                batch_result.rejected.append(
                    BleRejectedObservation(
                        observation=observation_payload,
                        reason="Student device is not registered for BLE attendance",
                    )
                )
                continue

            logger.info(
                "Received BLE Observation resolved student=%s session=%s rssi=%s anonymous_id=%s rolling_token=%s jwt_user=%s",
                item.student_id,
                payload.session_id,
                item.rssi,
                self._truncate_anonymous_code(registered_device.anonymous_ble_id),
                self._truncate_token(item.rolling_token),
                teacher_email or "<unknown>",
            )

            validation_result = await self.packet_validator.validate(
                observation_payload,
                registered_device,
                session_context,
            )
            logger.info(
                "Rolling token verification session_id=%s student_id=%s result=%s issues=%s",
                payload.session_id,
                item.student_id,
                validation_result.state,
                "; ".join(issue.message for issue in validation_result.issues) if validation_result.issues else "none",
            )
            if not validation_result.is_accepted:
                reason = self._summarize_validation_issues(validation_result)
                logger.info(
                    "Observation rejected reason=%s session_id=%s student_id=%s",
                    reason,
                    payload.session_id,
                    item.student_id,
                )
                batch_result.rejected.append(
                    BleRejectedObservation(
                        observation=observation_payload,
                        reason=reason,
                    )
                )
                continue

            cooldown_decision = await cooldown_tracker.check_and_mark(
                session_id=session_context.session.id,
                student_id=item.student_id,
                observed_at=normalized_timestamp,
            )
            logger.info(
                "Cooldown decision session_id=%s student_id=%s allowed=%s remaining_seconds=%s",
                payload.session_id,
                item.student_id,
                cooldown_decision.allowed,
                cooldown_decision.remaining_seconds,
            )
            if not cooldown_decision.allowed:
                logger.info(
                    "Cooldown Triggered student_id=%s remaining_seconds=%s",
                    item.student_id,
                    cooldown_decision.remaining_seconds,
                )
                batch_result.rejected.append(
                    BleRejectedObservation(
                        observation=observation_payload,
                        reason=f"Observation cooldown active; remaining={cooldown_decision.remaining_seconds} seconds",
                    )
                )
                self._log_cooldown_packet(
                    session_context=session_context,
                    observation=observation_payload,
                    registered_device=registered_device,
                    teacher_email=teacher_email,
                    room=room,
                    remaining_seconds=cooldown_decision.remaining_seconds,
                )
                continue

            batch_result.accepted.append(
                BleValidatedObservation(
                    student_id=item.student_id,
                    rssi=item.rssi,
                    last_seen=normalized_last_seen,
                    rolling_token=item.rolling_token,
                    timestamp=normalized_timestamp,
                    hmac=item.hmac,
                    anonymous_ble_id=registered_device.anonymous_ble_id,
                    device_hash=registered_device.device_hash,
                )
            )

            logger.info(
                "Student Detected student_id=%s anonymous_code=%s rssi=%s",
                item.student_id,
                self._truncate_anonymous_code(registered_device.anonymous_ble_id),
                item.rssi,
            )
            logger.info(
                "Observation accepted session_id=%s student_id=%s anonymous_id=%s",
                payload.session_id,
                item.student_id,
                self._truncate_anonymous_code(registered_device.anonymous_ble_id),
            )
            self._log_accepted_packet(
                session_context=session_context,
                observation=observation_payload,
                registered_device=registered_device,
                teacher_email=teacher_email,
                room=room,
            )

        return session_context, batch_result

    async def _load_registered_device(self, student_id: UUID) -> BleRegisteredDevice | None:
        registered_device = await self.registered_device_repository.get_by_student_id(student_id)
        return registered_device

    def _summarize_validation_issues(self, validation_result) -> str:
        if not validation_result.issues:
            return "Observation rejected by packet validator"
        return "; ".join(issue.message for issue in validation_result.issues)

    def _log_accepted_packet(
        self,
        *,
        session_context: BleSessionContext,
        observation: BleObservationPayload,
        registered_device: BleRegisteredDevice,
        teacher_email: str | None,
        room: str | None,
    ) -> None:
        timestamp = observation.timestamp
        if timestamp.tzinfo is None or timestamp.utcoffset() is None:
            timestamp = timestamp.replace(tzinfo=timezone.utc)
        else:
            timestamp = timestamp.astimezone(timezone.utc)

        logger.info("=========================================================")
        logger.info("BLE ATTENDANCE PACKET")
        logger.info("=========================================================")
        logger.info("")
        logger.info("Time")
        logger.info("----")
        logger.info("%s", timestamp.strftime("%H:%M:%S"))
        logger.info("")
        logger.info("Student")
        logger.info("-------")
        logger.info("Name            : %s", self._display_name(registered_device.public_identifier))
        logger.info("Email           : %s", self._display_email(registered_device.public_identifier))
        logger.info("Anonymous Code  : %s", self._truncate_anonymous_code(registered_device.anonymous_ble_id))
        logger.info("Rolling Token   : %s", self._truncate_token(observation.rolling_token))
        logger.info("RSSI            : %s dBm", observation.rssi)
        logger.info("")
        logger.info("Session")
        logger.info("-------")
        logger.info("Faculty         : %s", teacher_email or "Unknown")
        logger.info("Course          : %s", session_context.session.course_id)
        logger.info("Room            : %s", room or "Unknown")
        logger.info("")
        logger.info("Security Checks")
        logger.info("---------------")
        logger.info("✓ Authentication Passed")
        logger.info("✓ Anonymous ID Verified")
        logger.info("✓ Rolling Token Verified")
        logger.info("")
        logger.info("Attendance")
        logger.info("----------")
        logger.info("Confidence      : %s%%", self._derive_confidence(observation.rssi))
        logger.info("Decision        : ACCEPTED")
        logger.info("Attendance Updated student_id=%s decision=ACCEPTED", observation.student_id)
        logger.info("=========================================================")

    def _log_cooldown_packet(
        self,
        *,
        session_context: BleSessionContext,
        observation: BleObservationPayload,
        registered_device: BleRegisteredDevice,
        teacher_email: str | None,
        room: str | None,
        remaining_seconds: int,
    ) -> None:
        timestamp = observation.timestamp
        if timestamp.tzinfo is None or timestamp.utcoffset() is None:
            timestamp = timestamp.replace(tzinfo=timezone.utc)
        else:
            timestamp = timestamp.astimezone(timezone.utc)

        logger.info("=========================================================")
        logger.info("BLE ATTENDANCE PACKET")
        logger.info("=========================================================")
        logger.info("")
        logger.info("Student")
        logger.info("-------")
        logger.info("Name            : %s", self._display_name(registered_device.public_identifier))
        logger.info("Email           : %s", self._display_email(registered_device.public_identifier))
        logger.info("Anonymous Code  : %s", self._truncate_anonymous_code(registered_device.anonymous_ble_id))
        logger.info("Rolling Token   : %s", self._truncate_token(observation.rolling_token))
        logger.info("RSSI            : %s dBm", observation.rssi)
        logger.info("")
        logger.info("Decision")
        logger.info("--------")
        logger.info("IGNORED")
        logger.info("")
        logger.info("Reason")
        logger.info("------")
        logger.info("Observation cooldown active")
        logger.info("")
        logger.info("Remaining Cooldown")
        logger.info("------------------")
        logger.info("%s seconds", remaining_seconds)
        logger.info("=========================================================")

    def _truncate_token(self, value: str) -> str:
        token = value.strip().upper()
        if len(token) <= 16:
            return token
        return f"{token[:16]}..."

    def _truncate_anonymous_code(self, value: str) -> str:
        code = value.strip().upper()
        return code[:6] if len(code) > 6 else code

    def _display_name(self, value: str) -> str:
        text = value.strip()
        if "@" in text:
            name = text.split("@", 1)[0].replace(".", " ").replace("_", " ").strip()
            if name:
                return " ".join(part.capitalize() for part in name.split())
        return text or "Unknown"

    def _display_email(self, value: str) -> str:
        text = value.strip()
        return text if "@" in text else "Unknown"

    def _to_utc(self, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            return value.replace(tzinfo=timezone.utc)
        return value.astimezone(timezone.utc)

    def _derive_confidence(self, rssi: int) -> int:
        return max(0, min(100, 100 + rssi + 55))
