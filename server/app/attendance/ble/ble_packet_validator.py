from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Protocol

from ...models import BleRegisteredDevice
from .contracts import BleObservationPayload, BleSessionContext, BleValidationIssue, BleValidationResult, BleValidationState


class BleHmacVerifier(Protocol):
    """Interface for future HMAC verification backends.

    TODO: wire in a real verifier once cryptographic key provisioning exists.
    The public validation interface stays stable so this implementation can be
    swapped in without changing callers.
    """

    async def verify(
        self,
        payload: BleObservationPayload,
        registered_device: BleRegisteredDevice,
        session_context: BleSessionContext,
    ) -> BleValidationResult:
        ...


@dataclass(slots=True)
class BlePacketValidator:
    hmac_verifier: BleHmacVerifier | None = None

    expected_anonymous_ble_id_bytes: int = 16
    expected_rolling_token_bytes: int = 8
    expected_timestamp_bytes: int = 8
    expected_hmac_bytes: int = 32

    def validate_structure(
        self,
        payload: BleObservationPayload,
        registered_device: BleRegisteredDevice,
    ) -> BleValidationResult:
        issues: list[BleValidationIssue] = []

        if not self._is_hex_string(registered_device.anonymous_ble_id, self.expected_anonymous_ble_id_bytes):
            issues.append(
                BleValidationIssue(
                    code="INVALID_ANONYMOUS_BLE_ID",
                    message="Registered anonymous BLE ID must be a fixed 16-byte hex payload.",
                )
            )

        if not self._is_hex_string(payload.rolling_token, self.expected_rolling_token_bytes):
            issues.append(
                BleValidationIssue(
                    code="INVALID_ROLLING_TOKEN",
                    message="Rolling token must be a fixed 8-byte hex payload.",
                )
            )

        if not self._is_hex_string(payload.hmac, self.expected_hmac_bytes):
            issues.append(
                BleValidationIssue(
                    code="INVALID_HMAC_LAYOUT",
                    message="HMAC must be a fixed 32-byte hex payload.",
                )
            )

        timestamp_issue = self._validate_timestamp_format(payload.timestamp)
        if timestamp_issue is not None:
            issues.append(timestamp_issue)

        if not self._validate_logical_packet_size(registered_device, payload):
            issues.append(
                BleValidationIssue(
                    code="INVALID_PACKET_LAYOUT",
                    message="BLE observation payload does not match the expected upload metadata layout.",
                )
            )

        if issues:
            return BleValidationResult(state=BleValidationState.INVALID, issues=issues)

        return BleValidationResult(state=BleValidationState.VALID)

    async def validate_hmac(
        self,
        payload: BleObservationPayload,
        registered_device: BleRegisteredDevice,
        session_context: BleSessionContext,
    ) -> BleValidationResult:
        """Validate packet authenticity.

        TODO: implement real HMAC verification after backend and device-side
        cryptographic key provisioning is introduced.
        """
        if self.hmac_verifier is None:
            return BleValidationResult(
                state=BleValidationState.NOT_YET_IMPLEMENTED,
                issues=[
                    BleValidationIssue(
                        code="HMAC_NOT_YET_IMPLEMENTED",
                        message=(
                            "Not Yet Implemented: HMAC verification depends on future cryptographic key provisioning."
                        ),
                    )
                ],
            )

        return await self.hmac_verifier.verify(payload, registered_device, session_context)

    async def validate(
        self,
        payload: BleObservationPayload,
        registered_device: BleRegisteredDevice,
        session_context: BleSessionContext,
    ) -> BleValidationResult:
        structure_result = self.validate_structure(payload, registered_device)
        if not structure_result.is_accepted:
            return structure_result

        hmac_result = await self.validate_hmac(payload, registered_device, session_context)
        if hmac_result.state == BleValidationState.INVALID:
            return hmac_result

        return BleValidationResult(state=BleValidationState.VALID, issues=structure_result.issues + hmac_result.issues)

    def _is_hex_string(self, value: str, expected_byte_length: int) -> bool:
        if not isinstance(value, str):
            return False
        normalized = value.strip().lower()
        if len(normalized) != expected_byte_length * 2:
            return False
        if len(normalized) == 0 or len(normalized) % 2 != 0:
            return False
        return all(character in "0123456789abcdef" for character in normalized)

    def _validate_timestamp_format(self, timestamp: datetime) -> BleValidationIssue | None:
        if not isinstance(timestamp, datetime):
            return BleValidationIssue(
                code="INVALID_TIMESTAMP_TYPE",
                message="Timestamp must be a datetime value.",
            )

        if timestamp.tzinfo is None or timestamp.utcoffset() is None:
            return BleValidationIssue(
                code="INVALID_TIMESTAMP_TZ",
                message="Timestamp must be timezone-aware.",
            )

        if timestamp.tzinfo != timezone.utc:
            try:
                timestamp.astimezone(timezone.utc)
            except Exception as exc:
                return BleValidationIssue(
                    code="INVALID_TIMESTAMP_FORMAT",
                    message=f"Timestamp cannot be normalized to UTC: {exc}",
                )

        return None

    def _validate_logical_packet_size(
        self,
        registered_device: BleRegisteredDevice,
        payload: BleObservationPayload,
    ) -> bool:
        return all(
            [
                self._is_hex_string(registered_device.anonymous_ble_id, self.expected_anonymous_ble_id_bytes),
                self._is_hex_string(payload.rolling_token, self.expected_rolling_token_bytes),
                self._is_hex_string(payload.hmac, self.expected_hmac_bytes),
                isinstance(payload.timestamp, datetime),
            ]
        )
