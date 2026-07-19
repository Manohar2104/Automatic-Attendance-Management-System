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

        Log.d(
            TAG,
            "Rolling token extracted anonymousBleId=$anonymousBleDeviceId rollingToken=$rollingTokenHex studentId=$resolvedStudentId sessionId=${sessionId ?: "<unknown>"} rssi=${parsedAdvertisement.rssi} timestamp=${Instant.ofEpochMilli(timestampMillis)}",
        )
        Log.d(
            TAG,
            "Observation created student=$resolvedStudentId session=${sessionId ?: "<unknown>"} anonymousId=$anonymousBleDeviceId rssi=${parsedAdvertisement.rssi} confidence=${maxOf(0, minOf(100, 100 + parsedAdvertisement.rssi + 55))} timestamp=${Instant.ofEpochMilli(timestampMillis)}",
        )
        Log.d(TAG, "Validation result=ACCEPTED anonymousBleId=$anonymousBleDeviceId studentId=$resolvedStudentId")

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
