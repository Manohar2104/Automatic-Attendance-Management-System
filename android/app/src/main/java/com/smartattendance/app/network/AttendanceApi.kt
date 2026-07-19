package com.smartattendance.app.network

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

data class RegisterRequest(
    val email: String,
    val password: String
)

data class BleDeviceRegistrationRequest(
    val anonymous_ble_id: String,
    val public_identifier: String? = null,
    val device_hash: String,
)

data class BleDeviceRegistrationResponse(
    val success: Boolean,
    val student_id: String,
    val anonymous_ble_id: String,
)

data class TokenResponse(
    val access_token: String,
    val refresh_token: String = "",
    val token_type: String
)

data class RefreshRequest(
    val refresh_token: String
)

data class DeviceRegisterRequest(
    val device_fingerprint: String
)

data class DeviceResponse(
    val id: String,
    val device_fingerprint: String,
    val status: String
)

data class PresenceRequest(
    val device_fingerprint: String,
    val session_id: String,
    val event_type: String,
    val location: String
)

data class PresenceResponse(
    val id: String
)

data class SessionInfo(
    val id: String,
    val course_id: String,
    val status: String,
    val location: String,
    val scheduled_start: String,
    val scheduled_end: String
)

data class HealthResponse(
    val status: String
)

data class ActiveSessionResponse(
    val active: Boolean,
    val session: SessionInfo?
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

interface AttendanceApi {

    @GET("health")
    suspend fun getHealth(): HealthResponse

    @POST("register")
    suspend fun register(@Body request: RegisterRequest): TokenResponse

    @POST("login")
    suspend fun login(@Body request: RegisterRequest): TokenResponse

    @POST("register-device")
    suspend fun registerDevice(
        @Header("Authorization") token: String,
        @Body request: DeviceRegisterRequest
    ): DeviceResponse

    @POST("api/ble/register")
    suspend fun registerBleDevice(
        @Header("Authorization") token: String,
        @Body request: BleDeviceRegistrationRequest,
    ): BleDeviceRegistrationResponse

    @POST("presence")
    suspend fun sendPresence(@Body request: PresenceRequest): PresenceResponse

    @POST("api/ble/observations")
    suspend fun uploadBleObservations(
        @Header("Authorization") token: String,
        @Body request: BleObservationUploadRequest
    ): BleObservationUploadResponse

    @GET("sessions")
    suspend fun getSessions(
        @Header("Authorization") token: String
    ): List<SessionInfo>

    @GET("api/sessions/active")
    suspend fun getActiveSession(
        @Header("Authorization") token: String
    ): ActiveSessionResponse

    @POST("refresh")
    suspend fun refresh(
        @Header("Authorization") token: String
    ): TokenResponse
}

class ApiClient(private val baseUrl: String) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: AttendanceApi = retrofit.create(AttendanceApi::class.java)
}
