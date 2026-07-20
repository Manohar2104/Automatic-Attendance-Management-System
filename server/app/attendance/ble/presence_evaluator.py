from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from ...models import BleRegisteredDevice
from .contracts import (
    BleAttendanceEvaluationResult,
    BlePresenceDisposition,
    BlePresenceState,
    BleValidatedObservation,
)


logger = logging.getLogger(__name__)


@dataclass(slots=True)
class PresenceEvaluator:
    presence_timeout_seconds: int = 300
    recovery_window_seconds: int = 300
    partial_threshold_seconds: int = 600

    def _to_utc(self, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            return value.replace(tzinfo=timezone.utc)
        return value.astimezone(timezone.utc)

    def evaluate(
        self,
        session_start_time: datetime,
        evaluated_at: datetime,
        observations: list[BleValidatedObservation],
        registered_devices: list[BleRegisteredDevice],
    ) -> BleAttendanceEvaluationResult:
        evaluated_at = self._to_utc(evaluated_at)
        session_start_time = self._to_utc(session_start_time)

        observations_by_student: dict = {}
        for observation in observations:
            observations_by_student.setdefault(observation.student_id, []).append(observation)

        student_ids = {device.student_id for device in registered_devices}

        presence_states: list[BlePresenceState] = []
        presence_timeout_delta = timedelta(seconds=self.presence_timeout_seconds)
        partial_threshold_delta = timedelta(seconds=self.partial_threshold_seconds)
        present_count = 0
        absent_count = 0
        missing_count = 0
        late_count = 0

        for student_id in student_ids:
            student_observations = sorted(
                observations_by_student.get(student_id, []),
                key=lambda observation: self._to_utc(observation.last_seen),
            )

            if not student_observations:
                absent_count += 1
                presence_states.append(
                    BlePresenceState(
                        student_id=student_id,
                        first_seen=None,
                        last_seen=None,
                        disposition=BlePresenceDisposition.ABSENT,
                        observation_count=0,
                    )
                )
                logger.info(
                    "[BLE][EVALUATE] student=%s last_seen=%s now=%s elapsed=%s status=%s",
                    student_id,
                    None,
                    evaluated_at,
                    None,
                    BlePresenceDisposition.ABSENT.value,
                )
                continue

            first_seen = self._to_utc(student_observations[0].last_seen)
            last_seen = self._to_utc(student_observations[-1].last_seen)
            age = evaluated_at - last_seen
            observation_gaps = [
                current - previous
                for previous, current in zip(
                    [self._to_utc(item.last_seen) for item in student_observations[:-1]],
                    [self._to_utc(item.last_seen) for item in student_observations[1:]],
                )
            ]
            longest_gap = max(observation_gaps, default=timedelta(0))

            if age > presence_timeout_delta:
                disposition = BlePresenceDisposition.MISSING
                missing_count += 1
            elif longest_gap >= partial_threshold_delta:
                disposition = BlePresenceDisposition.PARTIAL
            else:
                disposition = BlePresenceDisposition.PRESENT
                present_count += 1

            logger.info(
                "[BLE][EVALUATE] student=%s last_seen=%s now=%s elapsed=%s status=%s",
                student_id,
                last_seen,
                evaluated_at,
                int(age.total_seconds()),
                disposition.value,
            )

            presence_states.append(
                BlePresenceState(
                    student_id=student_id,
                    first_seen=first_seen,
                    last_seen=last_seen,
                    disposition=disposition,
                    observation_count=len(student_observations),
                )
            )

        return BleAttendanceEvaluationResult(
            presence_states=presence_states,
            present_count=present_count,
            absent_count=absent_count,
            missing_count=missing_count,
            late_count=late_count,
        )
