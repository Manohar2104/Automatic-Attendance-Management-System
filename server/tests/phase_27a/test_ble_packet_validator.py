from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone

import pytest

from app.attendance.ble.ble_packet_validator import BlePacketValidator
from app.attendance.ble.contracts import BleValidationState

from tests.phase_27a.factories import (
    make_observation_payload,
    make_registered_device,
    make_session_context,
    make_validation_result,
)


@dataclass(slots=True)
class FakeVerifier:
    result: object

    async def verify(self, payload, registered_device, session_context):
        return self.result


def test_validate_structure_accepts_valid_packet():
    validator = BlePacketValidator()
    payload = make_observation_payload()
    registered_device = make_registered_device()

    result = validator.validate_structure(payload, registered_device)

    assert result.state == BleValidationState.VALID
    assert result.issues == []


@pytest.mark.parametrize(
    "payload_kwargs, registered_kwargs",
    [
        ({"rolling_token": "zz" * 8}, {}),
        ({"hmac": "gg" * 32}, {}),
        ({"timestamp": datetime.now()}, {}),
        ({}, {"anonymous_ble_id": "12ab"}),
    ],
)
def test_validate_structure_rejects_invalid_packet_layout(payload_kwargs, registered_kwargs):
    validator = BlePacketValidator()
    payload = make_observation_payload(**payload_kwargs)
    registered_device = make_registered_device(**registered_kwargs)

    result = validator.validate_structure(payload, registered_device)

    assert result.state == BleValidationState.INVALID
    assert result.issues


@pytest.mark.asyncio
async def test_validate_hmac_returns_not_yet_implemented_without_verifier():
    validator = BlePacketValidator()
    payload = make_observation_payload()
    registered_device = make_registered_device()
    session_context = make_session_context()

    result = await validator.validate_hmac(payload, registered_device, session_context)

    assert result.state == BleValidationState.NOT_YET_IMPLEMENTED
    assert result.issues[0].code == "HMAC_NOT_YET_IMPLEMENTED"


@pytest.mark.asyncio
async def test_validate_uses_hmac_verifier_when_available():
    expected = make_validation_result(state=BleValidationState.VALID)
    validator = BlePacketValidator(hmac_verifier=FakeVerifier(expected))
    payload = make_observation_payload()
    registered_device = make_registered_device()
    session_context = make_session_context()

    result = await validator.validate_hmac(payload, registered_device, session_context)

    assert result == expected


@pytest.mark.asyncio
async def test_validate_short_circuits_on_invalid_structure():
    validator = BlePacketValidator()
    payload = make_observation_payload(rolling_token="bad-token")
    registered_device = make_registered_device()
    session_context = make_session_context()

    result = await validator.validate(payload, registered_device, session_context)

    assert result.state == BleValidationState.INVALID
