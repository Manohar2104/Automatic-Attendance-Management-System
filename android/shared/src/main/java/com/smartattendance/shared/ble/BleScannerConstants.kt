package com.smartattendance.shared.ble

object BleScannerConstants {
    const val ACTION_START_SCANNING = "com.smartattendance.app.action.START_BLE_SCANNING"
    const val ACTION_STOP_SCANNING = "com.smartattendance.app.action.STOP_BLE_SCANNING"

    const val EXTRA_SESSION_ID = "extra_session_id"
    const val EXTRA_TEACHER_ID = "extra_teacher_id"
    const val EXTRA_AUTH_TOKEN = "extra_auth_token"
    const val EXTRA_REGISTERED_DEVICES_JSON = "extra_registered_devices_json"

    const val BLE_SCAN_NOTIFICATION_ID = 2_101
    const val SCAN_BATCH_UPLOAD_INTERVAL_MS = 2_500L
    const val SCAN_FAILURE_RETRY_MS = 5_000L
    const val MAX_PENDING_OBSERVATIONS = 50
    const val MAX_REGISTRATION_JSON_LENGTH = 65_536
}
