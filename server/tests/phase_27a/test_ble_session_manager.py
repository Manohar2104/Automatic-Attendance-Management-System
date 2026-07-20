from __future__ import annotations

from types import SimpleNamespace
from uuid import uuid4
from unittest.mock import AsyncMock

import pytest

from app.attendance.ble.ble_session_manager import BleSessionManager
from app.attendance.ble.contracts import BleSessionConfiguration
from app.models import BleAttendanceMode, BleSessionStatus

from tests.phase_27a.factories import make_ble_session


@pytest.mark.asyncio
async def test_load_configuration_reads_settings(monkeypatch):
    from app.attendance.ble import ble_session_manager as module

    monkeypatch.setattr(module.settings, "ble_missing_timeout_seconds", 90, raising=False)
    monkeypatch.setattr(module.settings, "ble_scan_upload_interval_seconds", 20, raising=False)

    manager = BleSessionManager(session_repository=SimpleNamespace())
    config = manager.load_configuration()

    assert config == BleSessionConfiguration(
        missing_timeout_seconds=90,
        scan_upload_interval_seconds=20,
        clock_skew_seconds=15,
    )


@pytest.mark.asyncio
async def test_load_session_raises_404_when_missing():
    repository = SimpleNamespace(get_by_id=AsyncMock(return_value=None))
    manager = BleSessionManager(session_repository=repository)

    with pytest.raises(Exception) as exc_info:
        await manager.load_session(uuid4())

    assert getattr(exc_info.value, "status_code", None) == 404


@pytest.mark.asyncio
async def test_load_session_rejects_non_ble_session():
    repository = SimpleNamespace(get_by_id=AsyncMock(return_value=make_ble_session(attendance_mode=BleAttendanceMode.WIFI)))
    manager = BleSessionManager(session_repository=repository)

    with pytest.raises(Exception) as exc_info:
        await manager.load_session(uuid4())

    assert getattr(exc_info.value, "status_code", None) == 409


@pytest.mark.asyncio
async def test_load_active_session_context_requires_active_status():
    repository = SimpleNamespace(get_by_id=AsyncMock(return_value=make_ble_session(status=BleSessionStatus.ENDED)))
    manager = BleSessionManager(session_repository=repository)

    with pytest.raises(Exception) as exc_info:
        await manager.load_active_session_context(uuid4())

    assert getattr(exc_info.value, "status_code", None) == 409


@pytest.mark.asyncio
async def test_load_session_context_returns_context():
    from app.attendance.ble import ble_session_manager as module

    monkeypatch = pytest.MonkeyPatch()
    monkeypatch.setattr(module.settings, "ble_missing_timeout_seconds", 45, raising=False)
    session = make_ble_session()
    repository = SimpleNamespace(get_by_id=AsyncMock(return_value=session))
    manager = BleSessionManager(session_repository=repository)

    try:
        context = await manager.load_session_context(session.id)
    finally:
        monkeypatch.undo()

    assert context.session.id == session.id
    assert context.configuration.missing_timeout_seconds == 45