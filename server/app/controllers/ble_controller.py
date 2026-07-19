from uuid import UUID

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
from ..services.ble_service import BleService


class BleController:
    @staticmethod
    async def start_session(
        payload: BleSessionStartRequest,
        ble_service: BleService,
        current_user,
    ) -> BleSessionResponse:
        return await ble_service.start_session(payload, current_user)

    @staticmethod
    async def end_session(
        payload: BleSessionEndRequest,
        ble_service: BleService,
        current_user,
    ) -> BleSessionResponse:
        return await ble_service.end_session(payload, current_user)

    @staticmethod
    async def get_session(
        session_id: UUID,
        ble_service: BleService,
        current_user,
    ) -> BleSessionResponse:
        return await ble_service.get_session(session_id, current_user)

    @staticmethod
    async def upload_observations(
        payload: BleObservationUploadRequest,
        ble_service: BleService,
        current_user,
    ) -> BleObservationUploadResponse:
        return await ble_service.upload_observations(payload, current_user)

    @staticmethod
    async def get_dashboard(
        ble_service: BleService,
        current_user,
    ) -> BleDashboardResponse:
        return await ble_service.get_dashboard(current_user)

    @staticmethod
    async def list_registered_devices(
        ble_service: BleService,
        current_user,
    ) -> BleRegisteredDevicesResponse:
        return await ble_service.list_registered_devices(current_user)

    @staticmethod
    async def register_device(
        payload: BleDeviceRegistrationRequest,
        ble_service: BleService,
        current_user,
    ) -> BleDeviceRegistrationResponse:
        return await ble_service.register_device(payload, current_user)

    @staticmethod
    async def get_attendance(
        session_id: UUID,
        ble_service: BleService,
        current_user,
    ) -> BleAttendanceResponse:
        return await ble_service.get_attendance(session_id, current_user)

    @staticmethod
    async def list_sessions(
        ble_service: BleService,
        current_user,
    ) -> BleSessionsListResponse:
        return await ble_service.list_sessions(current_user)

    @staticmethod
    async def get_session_summary(
        session_id: UUID,
        ble_service: BleService,
        current_user,
    ) -> BleSessionSummaryResponse:
        return await ble_service.get_session_summary(session_id, current_user)

    @staticmethod
    async def get_session_observations(
        session_id: UUID,
        ble_service: BleService,
        current_user,
    ) -> BleObservationsResponse:
        return await ble_service.get_session_observations(session_id, current_user)

    @staticmethod
    async def get_session_presence(
        session_id: UUID,
        ble_service: BleService,
        current_user,
    ) -> BlePresenceResponse:
        return await ble_service.get_session_presence(session_id, current_user)
