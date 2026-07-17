package com.smartattendance.app.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.smartattendance.app.data.PreferencesManager
import com.smartattendance.app.network.ApiClient
import com.smartattendance.app.network.PresenceRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class PresenceSubmissionWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        val isRegistered = prefs.isRegistered.first()
        if (!isRegistered) return Result.success()

        val deviceId = prefs.deviceId.first()
        val serverUrl = prefs.serverUrl.first()
        val sessionId = prefs.sessionId.first()
        // Read the last detected location from prefs (set by WiFiScanService via find3)
        val location = prefs.lastLocation.first().ifBlank { "unknown" }

        if (deviceId.isBlank()) return Result.success()

        return try {
            val client = ApiClient(serverUrl)
            val response = client.api.sendPresence(
                PresenceRequest(
                    device_fingerprint = deviceId,
                    // Send null session_id so backend resolves it from location + active sessions
                    session_id = sessionId.ifBlank { null },
                    event_type = "ENTER",
                    location = location
                )
            )
            prefs.updateLastSyncTime()
            Log.d(TAG, "Presence sent (location=$location): ${response.id}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Presence failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "PresenceWorker"
        private const val WORK_NAME = "presence_submission"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<PresenceSubmissionWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}
