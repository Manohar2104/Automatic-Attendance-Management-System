package com.smartattendance.app.ble

import android.util.Log
import com.smartattendance.app.network.ApiClient
import com.smartattendance.app.network.BleObservationItemRequest
import com.smartattendance.app.network.BleObservationUploadRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque

private data class PendingObservation(
    val studentId: String,
    val rssi: Int,
    val lastSeenMillis: Long,
    val rollingTokenHex: String,
    val timestampMillis: Long,
    val hmacHex: String,
)

class ObservationUploader(
    private val scope: CoroutineScope,
    private val serverUrl: String,
    private val authToken: String,
    private val teacherId: String,
    private val sessionId: String,
) {
    private val queue = ArrayDeque<PendingObservation>()
    private val mutex = Mutex()
    private val api = ApiClient(serverUrl).api
    private var uploadJob: Job? = null

    fun start() {
        if (uploadJob != null) return
        uploadJob = scope.launch {
            while (isActive) {
                delay(BleScannerConstants.SCAN_BATCH_UPLOAD_INTERVAL_MS)
                flushPendingObservations()
            }
        }
    }

    suspend fun enqueueObservation(
        studentId: String,
        rssi: Int,
        lastSeenMillis: Long,
        rollingTokenHex: String,
        timestampMillis: Long,
        hmacHex: String,
    ) {
        mutex.withLock {
            queue.addLast(
                PendingObservation(
                    studentId = studentId,
                    rssi = rssi,
                    lastSeenMillis = lastSeenMillis,
                    rollingTokenHex = rollingTokenHex,
                    timestampMillis = timestampMillis,
                    hmacHex = hmacHex,
                )
            )
        }

        if (pendingCount() >= BleScannerConstants.MAX_PENDING_OBSERVATIONS) {
            flushPendingObservations()
        }
    }

    suspend fun flushPendingObservations() {
        val pendingBatch = mutex.withLock {
            if (queue.isEmpty()) {
                return
            }

            val drained = mutableListOf<PendingObservation>()
            while (queue.isNotEmpty()) {
                drained.add(queue.removeFirst())
            }
            drained
        }

        if (pendingBatch.isEmpty()) return

        val request = BleObservationUploadRequest(
            session_id = sessionId,
            teacher_id = teacherId,
            observed_at = currentIsoTimestamp(),
            observations = pendingBatch.map { observation ->
                BleObservationItemRequest(
                    student_id = observation.studentId,
                    rssi = observation.rssi,
                    last_seen = isoFromMillis(observation.lastSeenMillis),
                    rolling_token = observation.rollingTokenHex,
                    timestamp = isoFromMillis(observation.timestampMillis),
                    hmac = observation.hmacHex,
                )
            }
        )

        try {
            api.uploadBleObservations("Bearer $authToken", request)
            Log.d(TAG, "Uploaded ${pendingBatch.size} BLE observations for session=$sessionId")
        } catch (exception: Exception) {
            Log.e(TAG, "BLE observation upload failed; re-queueing batch", exception)
            mutex.withLock {
                pendingBatch.forEach { queue.addLast(it) }
            }
        }
    }

    suspend fun stop() {
        uploadJob?.cancel()
        uploadJob = null
        flushPendingObservations()
    }

    private suspend fun pendingCount(): Int {
        return mutex.withLock { queue.size }
    }

    private fun currentIsoTimestamp(): String = isoFromMillis(System.currentTimeMillis())

    private fun isoFromMillis(value: Long): String = java.time.Instant.ofEpochMilli(value).toString()

    companion object {
        private const val TAG = "ObservationUploader"
    }
}
