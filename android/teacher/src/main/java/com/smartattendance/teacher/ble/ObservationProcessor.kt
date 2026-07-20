package com.smartattendance.teacher.ble

import android.util.Log
import com.smartattendance.shared.ble.BleConstants
import java.time.Instant

class ObservationProcessor {
    fun prepareObservation(
        parsedAdvertisement: ParsedBleAdvertisement,
        timestampMillis: Long,
        studentId: String,
        sessionId: String? = null,
    ): ParsedObservation? {
        val anonymousBleDeviceId = parsedAdvertisement.anonymousDeviceIdHex.trim()
        val rollingTokenHex = parsedAdvertisement.rollingTokenHex.trim()
        val resolvedStudentId = studentId.trim()
        if (anonymousBleDeviceId.isBlank()) {
            Log.d(TAG, "Observation ignored reason=Invalid Advertisement detail=Anonymous ID blank timestamp=${Instant.ofEpochMilli(timestampMillis)}")
            return null
        }
        if (resolvedStudentId.isBlank()) {
            Log.d(TAG, "Observation ignored reason=Device not registered detail=Student ID blank timestamp=${Instant.ofEpochMilli(timestampMillis)}")
            return null
        }
        if (rollingTokenHex.length != BleConstants.ROLLING_TOKEN_BYTES * 2) {
            Log.d(
                TAG,
                "Observation ignored reason=Rolling Token mismatch detail=Invalid rolling token length actual=${rollingTokenHex.length} expected=${BleConstants.ROLLING_TOKEN_BYTES * 2} timestamp=${Instant.ofEpochMilli(timestampMillis)}",
            )
            return null
        }

        return ParsedObservation(
            studentId = resolvedStudentId,
            anonymousBleDeviceId = anonymousBleDeviceId,
            rollingToken = rollingTokenHex,
            rssi = parsedAdvertisement.rssi,
            timestampMillis = timestampMillis,
            sessionId = sessionId,
        )
    }
}

data class ParsedObservation(
    val studentId: String,
    val anonymousBleDeviceId: String,
    val rollingToken: String,
    val rssi: Int,
    val timestampMillis: Long,
    val sessionId: String? = null,
)

private const val TAG = "ObservationProcessor"
