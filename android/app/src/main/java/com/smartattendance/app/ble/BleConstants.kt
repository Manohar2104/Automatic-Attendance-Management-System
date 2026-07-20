package com.smartattendance.app.ble

import android.os.ParcelUuid
import java.util.UUID

object BleConstants {
    const val ADVERTISEMENT_UUID_STRING = "6f9c1e53-7caa-4f2f-a54d-0b3f1b7c1a11"
    val ADVERTISEMENT_UUID: UUID = UUID.fromString(ADVERTISEMENT_UUID_STRING)
    val ADVERTISEMENT_PARCEL_UUID: ParcelUuid = ParcelUuid.fromString(ADVERTISEMENT_UUID_STRING)

    const val MANUFACTURER_ID = 0x5341

    const val ANONYMOUS_DEVICE_ID_BYTES = 16
    const val ROLLING_TOKEN_BYTES = 8
    const val ADVERTISEMENT_PAYLOAD_BYTES = ANONYMOUS_DEVICE_ID_BYTES + ROLLING_TOKEN_BYTES
    const val LEGACY_PRIMARY_ADVERTISEMENT_BYTES = 31
    const val TIMESTAMP_BYTES = 8
    const val HMAC_BYTES = 32

    const val ADVERTISEMENT_INTERVAL_MS = 1_200L
    const val ROLLING_TOKEN_INTERVAL_MS = 30_000L
    const val ADVERTISING_FAILURE_RETRY_MS = 5_000L

    const val BLE_NOTIFICATION_CHANNEL_ID = "ble_advertising"
    const val BLE_NOTIFICATION_ID = 2_001

    const val ACTION_START_ADVERTISING = "com.smartattendance.app.action.START_BLE_ADVERTISING"
    const val ACTION_STOP_ADVERTISING = "com.smartattendance.app.action.STOP_BLE_ADVERTISING"
    const val EXTRA_SESSION_ID = "extra_session_id"
}
