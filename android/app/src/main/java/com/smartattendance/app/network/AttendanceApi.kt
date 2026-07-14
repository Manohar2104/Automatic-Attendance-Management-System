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
    val password: String,
    val role: String? = "STUDENT"
)

data class TokenResponse(
    val access_token: String,
    val token_type: String
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
    val scheduled_end: String,
    val teacher_id: String?
)

data class HealthResponse(
    val status: String
)

data class UserMeResponse(
    val id: String,
    val email: String,
    val role: String
)

data class StudentAttendanceResponse(
    val session_id: String,
    val course_id: String,
    val location: String,
    val status: String,
    val score: Float,
    val scheduled_start: String,
    val scheduled_end: String,
    val actual_start: String?,
    val actual_end: String?,
    val session_status: String
)

data class AttendanceResult(
    val user_id: String,
    val score: Float,
    val status: String,
    val enter_count: Int,
    val location_match: Boolean
)

data class ComputeAttendanceResponse(
    val session_id: String,
    val results: List<AttendanceResult>
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

    @POST("presence")
    suspend fun sendPresence(@Body request: PresenceRequest): PresenceResponse

    @GET("sessions")
    suspend fun getSessions(
        @Header("Authorization") token: String
    ): List<SessionInfo>

    @GET("users/me")
    suspend fun getMe(
        @Header("Authorization") token: String
    ): UserMeResponse

    @GET("students")
    suspend fun getStudents(
        @Header("Authorization") token: String
    ): List<UserMeResponse>

    @GET("student/attendance")
    suspend fun getStudentAttendance(
        @Header("Authorization") token: String,
        @retrofit2.http.Query("date") date: String
    ): List<StudentAttendanceResponse>

    @POST("compute-attendance/{session_id}")
    suspend fun computeAttendance(
        @Header("Authorization") token: String,
        @retrofit2.http.Path("session_id") sessionId: String,
        @retrofit2.http.Query("location_filter") locationFilter: String?
    ): ComputeAttendanceResponse

    @POST("sessions/{session_id}/override-attendance")
    suspend fun overrideAttendance(
        @Header("Authorization") token: String,
        @retrofit2.http.Path("session_id") sessionId: String,
        @retrofit2.http.Query("student_id") studentId: String,
        @retrofit2.http.Query("override_status") overrideStatus: String,
        @retrofit2.http.Query("justification") justification: String
    ): Response<Unit>
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
