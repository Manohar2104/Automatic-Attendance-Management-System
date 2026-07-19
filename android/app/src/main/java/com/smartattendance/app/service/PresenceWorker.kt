package com.smartattendance.app.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.smartattendance.app.data.PreferencesManager
import com.smartattendance.app.network.ApiClient
import com.smartattendance.app.network.PresenceRequest
import retrofit2.HttpException
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

        if (deviceId.isBlank() || sessionId.isBlank()) return Result.success()

        if (!isCurrentSessionActive(serverUrl, sessionId, prefs)) {
            Log.d(TAG, "Skipping heartbeat because session is not ACTIVE")
            return Result.success()
        }

        return try {
            val client = ApiClient(serverUrl)
            val response = client.api.sendPresence(
                PresenceRequest(
                    device_fingerprint = deviceId,
                    session_id = sessionId,
                    event_type = "ENTER",
                    location = "auto-detected"
                )
            )
            prefs.updateLastSyncTime()
            Log.d(TAG, "Presence sent: ${response.id}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Presence failed: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun isCurrentSessionActive(
        serverUrl: String,
        sessionId: String,
        prefs: PreferencesManager,
    ): Boolean {
        val accessToken = prefs.accessToken.first().trim()
        if (serverUrl.isBlank() || sessionId.isBlank() || accessToken.isBlank()) {
            return false
        }

        return try {
            val sessions = ApiClient(serverUrl).api.getSessions("Bearer $accessToken")
            sessions.any { session ->
                session.id == sessionId && session.status.equals("ACTIVE", ignoreCase = true)
            }
        } catch (e: HttpException) {
            if (e.code() != 401) {
                Log.d(TAG, "Heartbeat session check failed: ${e.message()}")
                return false
            }

            val refreshed = refreshStudentToken(serverUrl, prefs)
            if (!refreshed) {
                prefs.clearStudentSession()
                return false
            }

            val refreshedToken = prefs.accessToken.first().trim()
            if (refreshedToken.isBlank()) {
                prefs.clearStudentSession()
                return false
            }

            try {
                val sessions = ApiClient(serverUrl).api.getSessions("Bearer $refreshedToken")
                sessions.any { session ->
                    session.id == sessionId && session.status.equals("ACTIVE", ignoreCase = true)
                }
            } catch (retry: Exception) {
                Log.d(TAG, "Heartbeat retry failed: ${retry.message}")
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "Heartbeat session check failed: ${e.message}")
            false
        }
    }

    private suspend fun refreshStudentToken(
        serverUrl: String,
        prefs: PreferencesManager,
    ): Boolean {
        val refreshToken = prefs.refreshToken.first().trim()
        if (refreshToken.isBlank()) {
            return false
        }

        return try {
            val response = ApiClient(serverUrl).api.refresh("Bearer $refreshToken")
            prefs.saveAccessToken(response.access_token)
            prefs.saveRefreshToken(response.refresh_token)
            true
        } catch (e: Exception) {
            Log.d(TAG, "Heartbeat refresh failed: ${e.message}")
            false
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
