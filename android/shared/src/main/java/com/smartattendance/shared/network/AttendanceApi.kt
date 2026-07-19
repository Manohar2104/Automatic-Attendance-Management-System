package com.smartattendance.shared.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class RegisterRequest(
    val email: String,
    val password: String,
)

data class TokenResponse(
    val access_token: String,
    val refresh_token: String = "",
    val token_type: String = "bearer",
)

data class RefreshRequest(
    val refresh_token: String,
)

data class DeviceRegisterRequest(
    val device_fingerprint: String,
)

data class DeviceResponse(
    val id: String,
    val device_fingerprint: String,
    val status: String,
)

data class PresenceRequest(
    val device_fingerprint: String,
    val session_id: String,
    val event_type: String,
    val location: String,
)

data class PresenceResponse(
    val id: String,
)

data class SessionInfo(
    val id: String,
    val course_id: String,
    val status: String,
    val location: String,
    val scheduled_start: String,
    val scheduled_end: String,
)

data class HealthResponse(
    val status: String,
)

data class BleObservationItemRequest(
    val student_id: String,
    val rssi: Int,
    val last_seen: String,
    val rolling_token: String,
    val timestamp: String,
    val hmac: String,
)

data class BleObservationUploadRequest(
    val session_id: String,
    val teacher_id: String,
    val observed_at: String,
    val observations: List<BleObservationItemRequest>,
)

data class BleObservationUploadResponse(
    val session_id: String,
    val received: Int,
    val accepted: Int,
    val rejected: Int,
    val processed_at: String,
)

data class BleRegisteredDeviceItem(
    val student_id: String,
    val anonymous_ble_id: String,
    val public_identifier: String,
)

data class BleRegisteredDevicesResponse(
    val registered_devices: List<BleRegisteredDeviceItem>,
)

data class BleSessionStartRequest(
    val course_id: String,
    val teacher_id: String,
    val attendance_mode: String = "BLE",
    val start_time: String,
)

data class BleSessionEndRequest(
    val session_id: String,
    val ended_at: String,
)

data class BleSessionResponse(
    val session_id: String,
    val course_id: String,
    val teacher_id: String,
    val attendance_mode: String,
    val status: String,
    val start_time: String,
    val end_time: String?,
    val created_at: String,
    val updated_at: String,
)

data class BleAttendanceRecord(
    val student_id: String,
    val status: String,
    val first_seen: String,
    val last_seen: String,
)

data class BleAttendanceSummary(
    val present: Int,
    val missing: Int,
)

data class BleAttendanceResponse(
    val session_id: String,
    val attendance_mode: String,
    val summary: BleAttendanceSummary,
    val records: List<BleAttendanceRecord>,
)

data class BleSessionsListResponse(
    val sessions: List<BleSessionResponse>,
)

data class BleSessionSummaryResponse(
    val session: BleSessionResponse,
    val registered_students: Int,
    val detected_students: Int,
    val packets_received: Int,
    val present: Int,
    val missing: Int,
    val absent: Int?,
    val late: Int?,
    val latest_observation: String?,
    val average_rssi: Double?,
)

data class BleDashboardResponse(
    val session: BleSessionResponse?,
    val registered_devices: Int,
    val students_seen: Int,
    val packets_received: Int,
    val faculty: String? = null,
    val room: String? = null,
)

data class BleObservationViewItem(
    val student_id: String,
    val student_name: String?,
    val anonymous_ble_id: String?,
    val rssi: Int,
    val last_seen: String,
    val advertisement_count: Int,
    val observation_timestamp: String,
)

data class BleObservationsResponse(
    val session_id: String,
    val observations: List<BleObservationViewItem>,
)

data class BlePresenceViewItem(
    val student_id: String,
    val student_name: String?,
    val status: String,
    val first_seen: String,
    val last_seen: String,
)

data class BlePresenceSummary(
    val present: Int,
    val missing: Int,
)

data class BlePresenceResponse(
    val session_id: String,
    val summary: BlePresenceSummary,
    val records: List<BlePresenceViewItem>,
)

interface AttendanceApi {

    @GET("health")
    suspend fun getHealth(): HealthResponse

    @POST("register")
    suspend fun register(@Body request: RegisterRequest): TokenResponse

    @POST("login")
    suspend fun login(@Body request: RegisterRequest): TokenResponse

    @POST("refresh")
    suspend fun refresh(@Header("Authorization") token: String): TokenResponse

    @POST("register-device")
    suspend fun registerDevice(
        @Header("Authorization") token: String,
        @Body request: DeviceRegisterRequest,
    ): DeviceResponse

    @POST("presence")
    suspend fun sendPresence(@Body request: PresenceRequest): PresenceResponse

    @POST("api/ble/observations")
    suspend fun uploadBleObservations(
        @Header("Authorization") token: String,
        @Body request: BleObservationUploadRequest,
    ): BleObservationUploadResponse

    @GET("api/ble/dashboard")
    suspend fun getBleDashboard(
        @Header("Authorization") token: String,
    ): BleDashboardResponse

    @GET("api/ble/registered-devices")
    suspend fun getRegisteredDevices(
        @Header("Authorization") token: String,
    ): BleRegisteredDevicesResponse

    @GET("api/ble/sessions")
    suspend fun getSessions(
        @Header("Authorization") token: String,
    ): BleSessionsListResponse

    @POST("api/ble/session/start")
    suspend fun startBleSession(
        @Header("Authorization") token: String,
        @Body request: BleSessionStartRequest,
    ): BleSessionResponse

    @POST("api/ble/session/end")
    suspend fun endBleSession(
        @Header("Authorization") token: String,
        @Body request: BleSessionEndRequest,
    ): BleSessionResponse

    @GET("api/ble/session/{sessionId}")
    suspend fun getBleSession(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
    ): BleSessionResponse

    @GET("api/ble/attendance/{sessionId}")
    suspend fun getAttendance(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
    ): BleAttendanceResponse

    @GET("api/ble/session/{sessionId}/summary")
    suspend fun getSessionSummary(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
    ): BleSessionSummaryResponse

    @GET("api/ble/session/{sessionId}/observations")
    suspend fun getObservations(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
    ): BleObservationsResponse

    @GET("api/ble/session/{sessionId}/presence")
    suspend fun getPresence(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
    ): BlePresenceResponse
}

class ApiClient(private val baseUrl: String) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        )
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: AttendanceApi = retrofit.create(AttendanceApi::class.java)
}