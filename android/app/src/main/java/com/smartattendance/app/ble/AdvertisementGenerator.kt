package com.smartattendance.app.ble

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdvertisementGenerator(
    private val registrationManager: BleRegistrationManager,
    private val rollingTokenManager: RollingTokenManager,
) {
    suspend fun generatePayload(nowMillis: Long = System.currentTimeMillis()): ByteArray? {
        val anonymousDeviceId = registrationManager.getAnonymousDeviceId() ?: return null
        val rollingToken = rollingTokenManager.currentToken(nowMillis)
        val payload = ByteBuffer.allocate(BleConstants.ADVERTISEMENT_PAYLOAD_BYTES)
            .order(ByteOrder.BIG_ENDIAN)

        payload.put(normalizeToLength(anonymousDeviceId, BleConstants.ANONYMOUS_DEVICE_ID_BYTES))
        payload.put(rollingToken)

        val anonymousIdHex = toHex(normalizeToLength(anonymousDeviceId, BleConstants.ANONYMOUS_DEVICE_ID_BYTES))
        val rollingTokenHex = toHex(rollingToken)
        Log.d(TAG, "Advertising started")
        Log.d(TAG, "Service UUID=${BleConstants.ADVERTISEMENT_UUID_STRING}")
        Log.d(TAG, "Manufacturer ID=${BleConstants.MANUFACTURER_ID}")
        Log.d(TAG, "Anonymous ID=$anonymousIdHex")
        Log.d(TAG, "Rolling Token=$rollingTokenHex")
        Log.d(TAG, "Session ID=<not_in_ble_payload>")
        Log.d(TAG, "Packet Version=<not_in_ble_payload>")
        Log.d(TAG, "Timestamp=$nowMillis")
        Log.d(TAG, "Advertising interval=${BleConstants.ADVERTISEMENT_INTERVAL_MS}ms")
        Log.d(TAG, "Manufacturer Data=${toHex(payload.array())}")

        return payload.array()
    }

    private fun normalizeToLength(input: ByteArray, expectedLength: Int): ByteArray {
        return when {
            input.size == expectedLength -> input
            input.size > expectedLength -> input.copyOf(expectedLength)
            else -> ByteArray(expectedLength).also { output ->
                input.copyInto(output)
            }
        }
    }

    private fun toHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val output = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            output.append(hexChars[value ushr 4])
            output.append(hexChars[value and 0x0F])
        }
        return output.toString()
    }

    companion object {
        private const val TAG = "AdvertisementGenerator"
    }
}
