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
    val scheduled_end: String
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
