import logging

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from uuid import UUID

from ..auth import require_current_user
from ..ble_schemas import (
    BleDashboardResponse,
    BleDeviceRegistrationRequest,
    BleDeviceRegistrationResponse,
    BleAttendanceResponse,
    BleObservationsResponse,
    BleObservationUploadRequest,
    BleObservationUploadResponse,
    BleRegisteredDevicesResponse,
    BlePresenceResponse,
    BleSessionEndRequest,
    BleSessionResponse,
    BleSessionSummaryResponse,
    BleSessionStartRequest,
    BleSessionsListResponse,
)
from ..controllers.ble_controller import BleController
from ..db import get_db
from ..services.ble_service import BleService

router = APIRouter(prefix="/api/ble", tags=["ble"])
logger = logging.getLogger(__name__)


@router.get("/sessions", response_model=BleSessionsListResponse)
async def list_ble_sessions(
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    ble_service = BleService(db)
    return await BleController.list_sessions(ble_service, current_user)


@router.post("/session/start", response_model=BleSessionResponse)
async def start_ble_session(
    payload: BleSessionStartRequest,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    ble_service = BleService(db)
    return await BleController.start_session(payload, ble_service, current_user)


@router.post("/session/end", response_model=BleSessionResponse)
async def end_ble_session(
    payload: BleSessionEndRequest,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    ble_service = BleService(db)
    return await BleController.end_session(payload, ble_service, current_user)


@router.post("/observations", response_model=BleObservationUploadResponse)
async def upload_ble_observations(
    payload: BleObservationUploadRequest,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    logger.info(
        "BLE upload endpoint reached session_id=%s teacher_id=%s observation_count=%s",
        payload.session_id,
        payload.teacher_id,
        len(payload.observations),
    )
    logger.info(
        "Received BLE Observation jwt_user_id=%s jwt_user_email=%s jwt_user_role=%s",
        getattr(current_user, "id", None),
        getattr(current_user, "email", None),
        getattr(current_user, "role", None),
    )
    for item in payload.observations:
        logger.info(
            "Received BLE Observation item student=%s session=%s rssi=%s anonymous_id=<resolved_on_server> rolling_token=%s",
            item.student_id,
            payload.session_id,
            item.rssi,
            item.rolling_token,
        )
    logger.info("BLE upload raw payload=%s", payload.model_dump_json())
    ble_service = BleService(db)
    return await BleController.upload_observations(payload, ble_service, current_user)


@router.get("/dashboard", response_model=BleDashboardResponse)
async def get_ble_dashboard(
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    ble_service = BleService(db)
    return await BleController.get_dashboard(ble_service, current_user)


@router.get("/registered-devices", response_model=BleRegisteredDevicesResponse)
async def list_ble_registered_devices(
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    ble_service = BleService(db)
    return await BleController.list_registered_devices(ble_service, current_user)


@router.post("/register", response_model=BleDeviceRegistrationResponse)
async def register_ble_device(
    payload: BleDeviceRegistrationRequest,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    ble_service = BleService(db)
    return await BleController.register_device(payload, ble_service, current_user)


@router.get("/session/{session_id}", response_model=BleSessionResponse)
async def get_ble_session(
    session_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    ble_service = BleService(db)
    return await BleController.get_session(session_id, ble_service, current_user)


@router.get("/attendance/{session_id}", response_model=BleAttendanceResponse)
async def get_ble_attendance(
    session_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    ble_service = BleService(db)
    return await BleController.get_attendance(session_id, ble_service, current_user)


@router.get("/session/{session_id}/summary", response_model=BleSessionSummaryResponse)
async def get_ble_session_summary(
    session_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    ble_service = BleService(db)
    return await BleController.get_session_summary(session_id, ble_service, current_user)


@router.get("/session/{session_id}/observations", response_model=BleObservationsResponse)
async def get_ble_session_observations(
    session_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    ble_service = BleService(db)
    return await BleController.get_session_observations(session_id, ble_service, current_user)


@router.get("/session/{session_id}/presence", response_model=BlePresenceResponse)
async def get_ble_session_presence(
    session_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    ble_service = BleService(db)
    return await BleController.get_session_presence(session_id, ble_service, current_user)
