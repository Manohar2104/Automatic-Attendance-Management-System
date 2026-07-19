package com.smartattendance.teacher.repository

import android.content.Context
import android.util.Log
import com.smartattendance.shared.data.PreferencesManager
import com.smartattendance.shared.network.ApiClient
import com.smartattendance.shared.network.AttendanceApi
import com.smartattendance.shared.network.BleAttendanceResponse
import com.smartattendance.shared.network.BleObservationUploadRequest
import com.smartattendance.shared.network.BleObservationUploadResponse
import com.smartattendance.shared.network.BleObservationsResponse
import com.smartattendance.shared.network.BlePresenceResponse
import com.smartattendance.shared.network.BleSessionEndRequest
import com.smartattendance.shared.network.BleSessionResponse
import com.smartattendance.shared.network.BleSessionStartRequest
import com.smartattendance.shared.network.BleSessionSummaryResponse
import com.smartattendance.shared.network.RegisterRequest
import com.smartattendance.teacher.ble.RegisteredDeviceEntry
import com.smartattendance.teacher.ui.dashboard.TeacherDashboardUiState
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException

class TeacherRepository(
    private val context: Context,
    baseUrl: String,
) {
    private val api: AttendanceApi = ApiClient(baseUrl).api
    private val preferencesManager = PreferencesManager(context)
    private var cachedAccessToken: String = ""

    companion object {
        private const val TAG = "AuthFlow"
    }

    suspend fun getHealth(): Result<Boolean> {
        return try {
            val response = api.getHealth()
            Result.success(response.status == "ok" || response.status == "healthy")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(
        email: String,
        password: String,
    ): Result<Unit> {
        return try {
            val response = api.login(
                RegisterRequest(
                    email = email,
                    password = password,
                )
            )

            cachedAccessToken = response.access_token
            Log.d(TAG, "LOGIN TOKEN: ${response.access_token.take(30)}")

            preferencesManager.saveAccessToken(response.access_token)
            preferencesManager.saveRefreshToken(response.refresh_token)
            preferencesManager.saveTeacherEmail(email)

            Log.d(TAG, "SAVED TOKEN: ${response.access_token.take(30)}")

            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearTeacherSession(): Result<Unit> {
        return try {
            preferencesManager.clearTeacherSession()
            cachedAccessToken = ""
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadObservation(
        request: BleObservationUploadRequest,
    ): Result<BleObservationUploadResponse> {
        Log.d(TAG, "Upload started -> POST /api/ble/observations")
        Log.d(
            TAG,
            "Uploading BLE observations -> session=${request.session_id} teacher=${request.teacher_id} observed_at=${request.observed_at} count=${request.observations.size}",
        )
        request.observations.forEach { observation ->
            Log.d(
                TAG,
                "Upload body item -> student=${observation.student_id} rssi=${observation.rssi} rolling_token=${observation.rolling_token} last_seen=${observation.last_seen} timestamp=${observation.timestamp}",
            )
        }

        return withAuthRetry {
            Log.d(
                TAG,
                "Upload headers -> Authorization=${if (it.startsWith("Bearer ")) "Bearer <present>" else "<missing>"} jwtAvailable=${it.removePrefix("Bearer ").isNotBlank()} Content-Type=application/json",
            )
            val response = api.uploadBleObservations(it, request)
            Log.d(
                TAG,
                "Upload response <- session=${response.session_id} received=${response.received} accepted=${response.accepted} rejected=${response.rejected} processed_at=${response.processed_at}",
            )
            response
        }
    }

    suspend fun currentSessionIdForLogging(): String {
        return preferencesManager.activeSessionId.first().trim()
    }

    suspend fun getDashboardState(): Result<TeacherDashboardUiState> {
        return withAuthRetry {
            Log.d(TAG, "Dashboard request -> GET /api/ble/dashboard authorization=${if (it.startsWith("Bearer ")) "Bearer <present>" else "<missing>"}")
            val dashboard = api.getBleDashboard(it)
            Log.d(
                TAG,
                "Dashboard response <- session=${dashboard.session?.session_id ?: "none"} registered=${dashboard.registered_devices} seen=${dashboard.students_seen} packets=${dashboard.packets_received}",
            )

            val activeSession = dashboard.session
            if (activeSession == null) {
                preferencesManager.saveActiveSessionId("")
                val teacherEmail = preferencesManager.teacherEmail.first().ifBlank { "Unknown" }

                TeacherDashboardUiState(
                    serverConnected = true,
                    faculty = teacherEmail,
                    sessionStatus = "NO SESSION",
                    registeredDevices = dashboard.registered_devices,
                    studentsSeen = dashboard.students_seen,
                    packetsReceived = dashboard.packets_received,
                    scannerStatus = "READY",
                )
            } else {
                preferencesManager.saveActiveSessionId(activeSession.session_id)
                preferencesManager.saveTeacherId(activeSession.teacher_id)

                val teacherEmail = preferencesManager.teacherEmail.first().ifBlank { "Unknown" }

                TeacherDashboardUiState(
                    serverConnected = true,
                    activeSessionId = activeSession.session_id,
                    teacherId = activeSession.teacher_id,
                    course = activeSession.course_id,
                    faculty = dashboard.faculty ?: teacherEmail,
                    room = dashboard.room ?: "Unknown",
                    sessionStatus = activeSession.status,
                    registeredDevices = dashboard.registered_devices,
                    studentsSeen = dashboard.students_seen,
                    packetsReceived = dashboard.packets_received,
                    scannerStatus = "READY",
                )
            }
        }
    }

    suspend fun getRegisteredDevices(): Result<List<RegisteredDeviceEntry>> {
        return withAuthRetry {
            Log.d(TAG, "Registered devices request -> GET /api/ble/registered-devices")
            val response = api.getRegisteredDevices(it)
            Log.d(TAG, "Registered devices response <- count=${response.registered_devices.size}")

            response.registered_devices.map { device ->
                RegisteredDeviceEntry(
                    studentId = device.student_id,
                    anonymousDeviceIdHex = device.anonymous_ble_id,
                    publicIdentifier = device.public_identifier,
                )
            }
        }
    }

    suspend fun startSession(
        request: BleSessionStartRequest,
    ): Result<BleSessionResponse> {
        return withAuthRetry {
            val response = api.startBleSession(it, request)
            preferencesManager.saveActiveSessionId(response.session_id)
            preferencesManager.saveTeacherId(response.teacher_id)
            response
        }
    }

    suspend fun endSession(
        request: BleSessionEndRequest,
    ): Result<BleSessionResponse> {
        return withAuthRetry {
            val response = api.endBleSession(it, request)
            preferencesManager.saveActiveSessionId("")
            response
        }
    }

    suspend fun getAttendance(
        sessionId: String,
    ): Result<BleAttendanceResponse> {
        return withAuthRetry {
            api.getAttendance(it, sessionId)
        }
    }

    suspend fun getPresence(
        sessionId: String,
    ): Result<BlePresenceResponse> {
        return withAuthRetry {
            Log.d(TAG, "Presence request -> GET /api/ble/session/$sessionId/presence")
            api.getPresence(it, sessionId)
        }
    }

    suspend fun getObservations(
        sessionId: String,
    ): Result<BleObservationsResponse> {
        return withAuthRetry {
            Log.d(TAG, "Observations request -> GET /api/ble/session/$sessionId/observations")
            api.getObservations(it, sessionId)
        }
    }

    suspend fun getSummary(
        sessionId: String,
    ): Result<BleSessionSummaryResponse> {
        return withAuthRetry {
            Log.d(TAG, "Summary request -> GET /api/ble/session/$sessionId/summary")
            api.getSessionSummary(it, sessionId)
        }
    }

    private suspend fun <T> withAuthRetry(call: suspend (String) -> T): Result<T> {
        val authorizationHeader = authorizedHeader()
            ?: return Result.failure(IllegalStateException("Missing access token"))

        return try {
            Result.success(call(authorizationHeader))
        } catch (e: HttpException) {
            if (e.code() == 401 && refreshAccessToken()) {
                val refreshedHeader = authorizedHeader()
                    ?: return Result.failure(IllegalStateException("Missing access token"))
                try {
                    Result.success(call(refreshedHeader))
                } catch (retry: HttpException) {
                    if (retry.code() == 401) {
                        clearTeacherSession()
                    }
                    Result.failure(retry)
                }
            } else {
                if (e.code() == 401) {
                    clearTeacherSession()
                }
                Result.failure(e)
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun refreshAccessToken(): Boolean {
        val refreshToken = preferencesManager.refreshToken.first().trim()
        if (refreshToken.isBlank()) {
            return false
        }

        return try {
            val response = api.refresh("Bearer $refreshToken")
            cachedAccessToken = response.access_token
            preferencesManager.saveAccessToken(response.access_token)
            preferencesManager.saveRefreshToken(response.refresh_token)
            Log.d(TAG, "Refreshed access token successfully")
            true
        } catch (e: Exception) {
            Log.d(TAG, "Token refresh failed", e)
            clearTeacherSession()
            false
        }
    }

    private suspend fun authorizedHeader(): String? {
        val storedToken = preferencesManager.accessToken.first().trim()
        Log.d(TAG, "LOADED TOKEN: ${storedToken.take(30)}")

        val accessToken = cachedAccessToken.ifBlank { storedToken }
        if (accessToken.isBlank()) {
            return null
        }

        val authorizationHeader = "Bearer $accessToken"
        Log.d(TAG, "HEADER TOKEN: ${accessToken.take(30)}")
        return authorizationHeader
    }
}
