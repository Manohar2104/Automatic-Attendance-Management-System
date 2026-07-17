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

import retrofit2.http.Path
import retrofit2.http.Query

data class RegisterRequest(
    val email: String,
    val password: String,
    val role: String? = null
)

data class TokenResponse(
    val access_token: String,
    val token_type: String
)

data class UserProfileResponse(
    val id: String,
    val email: String,
    val role: String
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
    val session_id: String?,
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

data class AttendanceResult(
    val user_id: String,
    val email: String,
    val score: Double,
    val status: String,
    val enter_count: Int,
    val location_match: Boolean
)

data class ComputeAttendanceResponse(
    val session_id: String,
    val duration_seconds: Int,
    val max_possible_submissions: Int,
    val bound_devices: Int,
    val crowd_ok: Boolean,
    val location_validated: Boolean,
    val results: List<AttendanceResult>
)

data class HealthResponse(
    val status: String
)

interface AttendanceApi {

    @GET("health")
    suspend fun getHealth(): HealthResponse

    @POST("register")
    suspend fun register(@Body request: RegisterRequest): TokenResponse

    @POST("login")
    suspend fun login(@Body request: RegisterRequest): TokenResponse

    @GET("users/me")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): UserProfileResponse

    @POST("register-device")
    suspend fun registerDevice(
        @Header("Authorization") token: String,
        @Body request: DeviceRegisterRequest
    ): DeviceResponse

    @POST("presence")
    suspend fun sendPresence(@Body request: PresenceRequest): PresenceResponse

    @GET("sessions")
    suspend fun getSessions(
        @Header("Authorization") token: String
    ): List<SessionInfo>

    @POST("compute-attendance/{session_id}")
    suspend fun computeAttendance(
        @Path("session_id") sessionId: String,
        @Query("location_filter") locationFilter: String?
    ): ComputeAttendanceResponse

    @POST("sessions/{session_id}/override-attendance")
    suspend fun overrideAttendance(
        @Header("Authorization") token: String,
        @Path("session_id") sessionId: String,
        @Query("student_id") studentId: String,
        @Query("override_status") overrideStatus: String,
        @Query("justification") justification: String
    ): okhttp3.ResponseBody
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
