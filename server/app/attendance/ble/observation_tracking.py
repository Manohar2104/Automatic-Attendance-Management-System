from __future__ import annotations

import asyncio
import math
from dataclasses import dataclass
from datetime import datetime, timezone
from uuid import UUID


def _to_utc(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


@dataclass(slots=True, frozen=True)
class BleCooldownDecision:
    allowed: bool
    remaining_seconds: int = 0
    last_processed_timestamp: datetime | None = None


class BleObservationCooldownTracker:
    def __init__(self, cooldown_seconds: int = 120) -> None:
        self.cooldown_seconds = cooldown_seconds
        self._lock = asyncio.Lock()
        self._last_processed_by_session_and_student: dict[tuple[UUID, UUID], datetime] = {}

    async def check_and_mark(
        self,
        *,
        session_id: UUID,
        student_id: UUID,
        observed_at: datetime,
    ) -> BleCooldownDecision:
        observed_at = _to_utc(observed_at)
        key = (session_id, student_id)

        async with self._lock:
            last_processed = self._last_processed_by_session_and_student.get(key)
            if last_processed is not None:
                elapsed_seconds = max(0.0, (observed_at - last_processed).total_seconds())
                if elapsed_seconds < self.cooldown_seconds:
                    remaining_seconds = max(1, math.ceil(self.cooldown_seconds - elapsed_seconds))
                    return BleCooldownDecision(
                        allowed=False,
                        remaining_seconds=remaining_seconds,
                        last_processed_timestamp=last_processed,
                    )

            self._last_processed_by_session_and_student[key] = observed_at
            return BleCooldownDecision(
                allowed=True,
                remaining_seconds=0,
                last_processed_timestamp=observed_at,
            )

    async def reset(self) -> None:
        async with self._lock:
            self._last_processed_by_session_and_student.clear()

    async def reset_session(self, session_id: UUID) -> None:
        async with self._lock:
            keys_to_remove = [
                key for key in self._last_processed_by_session_and_student.keys() if key[0] == session_id
            ]
            for key in keys_to_remove:
                self._last_processed_by_session_and_student.pop(key, None)


cooldown_tracker = BleObservationCooldownTracker(cooldown_seconds=120)
