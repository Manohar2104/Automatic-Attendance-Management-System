package com.smartattendance.teacher.ble

import android.util.Log
import com.smartattendance.shared.ble.BleScannerConstants
import com.smartattendance.shared.data.PreferencesManager
import com.smartattendance.shared.network.BleObservationItemRequest
import com.smartattendance.shared.network.BleObservationUploadRequest
import com.smartattendance.teacher.repository.TeacherRepository
import com.smartattendance.teacher.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.ArrayDeque
import org.json.JSONArray
import org.json.JSONObject

class ObservationUploader(
    private val scope: CoroutineScope,
    private val teacherRepository: TeacherRepository,
    private val preferencesManager: PreferencesManager,
) {
    private val queue = ArrayDeque<ParsedObservation>()
    private val mutex = Mutex()
    private var batchJob: Job? = null

    fun start() {
        if (batchJob != null) return
        batchJob = scope.launch {
            while (isActive) {
                delay(BleScannerConstants.SCAN_BATCH_UPLOAD_INTERVAL_MS)
                flushPendingObservations()
            }
        }
    }

    suspend fun enqueue(observation: ParsedObservation) {
        mutex.withLock {
            queue.addLast(observation)
            Log.d(
                TAG,
                "Observation created anonymousBleId=${observation.anonymousBleDeviceId} rollingToken=${observation.rollingToken} rssi=${observation.rssi} timestamp=${observation.timestampMillis} queueSize=${queue.size}",
            )
        }

        val pendingSize = pendingCount()
        Log.d(TAG, "Upload queue size=$pendingSize")

        if (pendingSize == 1) {
            scope.launch {
                flushPendingObservations()
            }
        } else if (pendingSize >= BleScannerConstants.MAX_PENDING_OBSERVATIONS) {
            flushPendingObservations()
        }
    }

    suspend fun flushPendingObservations() {
        Log.d(TAG, "Upload requested")
        val pendingBatch = mutex.withLock {
            if (queue.isEmpty()) {
                Log.d(TAG, "No pending BLE observations to upload")
                return
            }

            val drained = mutableListOf<ParsedObservation>()
            while (queue.isNotEmpty()) {
                drained.add(queue.removeFirst())
            }
            drained
        }

        if (pendingBatch.isEmpty()) {
            Log.d(TAG, "No pending BLE observations after drain")
            return
        }

        val request = buildUploadRequest(pendingBatch)
        if (request == null) {
            Log.d(TAG, "BLE observation upload skipped because request could not be built")
            requeue(pendingBatch)
            return
        }

        logBatch(pendingBatch)
        Log.d(TAG, "Sending observation batch size=${pendingBatch.size}")
        val bearerToken = preferencesManager.accessToken.first().trim()
        Log.d(
            TAG,
            "POST /api/ble/observations headers={Authorization=${if (bearerToken.isBlank()) "<missing>" else "Bearer <present>"}, Content-Type=application/json} jwtAvailable=${bearerToken.isNotBlank()} sessionId=${request.session_id} teacherId=${request.teacher_id}",
        )
        Log.d(
            TAG,
            "POST /api/ble/observations payload=${buildJson(request)}",
        )
        val uploadStartedAt = System.currentTimeMillis()
        val uploadResult = teacherRepository.uploadObservation(request)
        val elapsedMs = System.currentTimeMillis() - uploadStartedAt
        if (uploadResult.isSuccess) {
            val response = uploadResult.getOrNull()
            Log.d(
                TAG,
                "HTTP Status=200 elapsedMs=$elapsedMs responseBody=${response?.let { JSONObject().put("session_id", it.session_id).put("received", it.received).put("accepted", it.accepted).put("rejected", it.rejected).put("processed_at", it.processed_at).toString() } ?: "<null>"}",
            )
            return
        }

        val failure = uploadResult.exceptionOrNull()
        if (isRetryable(failure)) {
            Log.d(TAG, "Upload failed; retrying once reason=${retryReason(failure)} elapsedMs=$elapsedMs", failure)
            val retryStartedAt = System.currentTimeMillis()
            val retryResult = teacherRepository.uploadObservation(request)
            val retryElapsedMs = System.currentTimeMillis() - retryStartedAt
            if (retryResult.isSuccess) {
                val response = retryResult.getOrNull()
                Log.d(
                    TAG,
                    "HTTP Status=200 elapsedMs=$retryElapsedMs responseBody=${response?.let { JSONObject().put("session_id", it.session_id).put("received", it.received).put("accepted", it.accepted).put("rejected", it.rejected).put("processed_at", it.processed_at).toString() } ?: "<null>"}",
                )
                return
            }

            logFailure(retryResult.exceptionOrNull(), "retry")
            requeue(pendingBatch)
            return
        }

        logFailure(failure, "initial")
        Log.d(TAG, "Upload not retried reason=${retryReason(failure)} elapsedMs=$elapsedMs")
        requeue(pendingBatch)
    }

    suspend fun stop() {
        batchJob?.cancel()
        batchJob = null
        flushPendingObservations()
    }

    fun cancel() {
        batchJob?.cancel()
        batchJob = null
        scope.cancel()
    }

    private suspend fun pendingCount(): Int {
        return mutex.withLock { queue.size }
    }

    private suspend fun requeue(batch: List<ParsedObservation>) {
        mutex.withLock {
            batch.asReversed().forEach { queue.addFirst(it) }
        }
    }

    private suspend fun buildUploadRequest(batch: List<ParsedObservation>): BleObservationUploadRequest? {
        val teacherId = preferencesManager.teacherId.first().trim()
        val sessionId = preferencesManager.activeSessionId.first().trim()

        if (teacherId.isBlank() || sessionId.isBlank()) {
            Log.d(
                TAG,
                "Ignored packet: Session ID missing or teacher context missing teacher_id=${teacherId.ifBlank { "<blank>" }} session_id=${sessionId.ifBlank { "<blank>" }}",
            )
            return null
        }

        val observations = batch.mapNotNull { observation ->
            val studentId = observation.studentId.trim()
            if (studentId.isBlank()) {
                Log.d(TAG, "Ignored packet: Unknown device anonymousBleId=${observation.anonymousBleDeviceId}")
                return@mapNotNull null
            }

            Log.d(
                TAG,
                "Preparing observation upload anonymousBleId=${observation.anonymousBleDeviceId} studentId=$studentId sessionId=${observation.sessionId ?: sessionId} rollingToken=${observation.rollingToken}",
            )

            BleObservationItemRequest(
                student_id = studentId,
                rssi = observation.rssi,
                last_seen = isoFromMillis(observation.timestampMillis),
                rolling_token = observation.rollingToken,
                timestamp = isoFromMillis(observation.timestampMillis),
                hmac = sha256Hex(
                    "${observation.anonymousBleDeviceId}:${observation.rollingToken}:${observation.timestampMillis}"
                ),
            )
        }

        if (observations.isEmpty()) {
            Log.d(TAG, "Ignored packet: No resolved BLE observations to upload")
            return null
        }

        Log.d(
            TAG,
            "Build BLEObservationRequest session_id=$sessionId teacher_id=$teacherId observations=${observations.size}",
        )
        observations.forEach { observation ->
            Log.d(
                TAG,
                "Request item student_id=${observation.student_id} rolling_token=${observation.rolling_token} rssi=${observation.rssi} last_seen=${observation.last_seen} timestamp=${observation.timestamp}",
            )
        }

        return BleObservationUploadRequest(
            session_id = sessionId,
            teacher_id = teacherId,
            observed_at = isoFromMillis(System.currentTimeMillis()),
            observations = observations,
        )
    }

    private fun logBatch(batch: List<ParsedObservation>) {
        batch.forEach { observation ->
            Log.d(
                TAG,
                "Uploading anonymousBleId=${observation.anonymousBleDeviceId} rollingToken=${observation.rollingToken} rssi=${observation.rssi} timestamp=${observation.timestampMillis}",
            )
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Prepared BLE observation batch count=${batch.size}",
            )
        }
    }

    private fun buildJson(request: BleObservationUploadRequest): String {
        val observations = JSONArray()
        request.observations.forEach { item ->
            observations.put(
                JSONObject()
                    .put("student_id", item.student_id)
                    .put("rssi", item.rssi)
                    .put("last_seen", item.last_seen)
                    .put("rolling_token", item.rolling_token)
                    .put("timestamp", item.timestamp)
                    .put("hmac", item.hmac)
            )
        }

        return JSONObject()
            .put("session_id", request.session_id)
            .put("teacher_id", request.teacher_id)
            .put("observed_at", request.observed_at)
            .put("observations", observations)
            .toString()
    }

    private fun logFailure(failure: Throwable?, phase: String) {
        if (failure == null) {
            Log.e(TAG, "HTTP upload failed phase=$phase reason=Unknown error")
            return
        }

        if (failure is retrofit2.HttpException) {
            val body = runCatching { failure.response()?.errorBody()?.string() }.getOrNull() ?: "<no-body>"
            Log.e(TAG, "HTTP upload failed phase=$phase code=${failure.code()} retryReason=${retryReason(failure)} responseBody=$body", failure)
        } else {
            Log.e(TAG, "HTTP upload failed phase=$phase retryReason=${retryReason(failure)}", failure)
        }
    }

    private fun retryReason(failure: Throwable?): String {
        if (failure == null) return "unknown"
        return when (failure) {
            is java.io.IOException -> "network_io"
            is retrofit2.HttpException -> "http_${failure.code()}"
            else -> failure::class.java.simpleName
        }
    }

    private fun isRetryable(failure: Throwable?): Boolean {
        if (failure == null) return false
        return when (failure) {
            is java.io.IOException -> true
            is retrofit2.HttpException -> failure.code() >= 500 || failure.code() == 429
            else -> false
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        val output = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            output.append(HEX_CHARS[value ushr 4])
            output.append(HEX_CHARS[value and 0x0F])
        }
        return output.toString()
    }

    private fun isoFromMillis(value: Long): String = java.time.Instant.ofEpochMilli(value).toString()

    companion object {
        private const val TAG = "ObservationUpload"
        private const val HEX_CHARS = "0123456789abcdef"
    }
}
