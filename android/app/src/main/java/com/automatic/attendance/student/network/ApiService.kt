package com.automatic.attendance.student.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class LoginRequest(
    val email: String,
    val password: String,
    val deviceName: String? = null
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)

interface AuthApi {

    @POST("/auth/login")
    suspend fun login(
        @Body req: LoginRequest
    ): Response<TokenResponse>

    @POST("/auth/refresh")
    suspend fun refresh(
        @Body body: Map<String, String>
    ): Response<TokenResponse>

    @POST("/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("/auth/me")
    suspend fun me(): Response<Map<String, Any>>
}

data class ActiveSession(
    val sessionId: String,
    val status: String,
    val startTime: String?
)

data class JoinRequest(
    val studentId: String
)

data class JoinResponse(
    val sessionId: String,
    val studentId: String,
    val joinScore: Int,
    val attendanceStatus: String,
    val joinTime: String
)

interface SessionApi {

    @GET("/sessions/active")
    suspend fun listActive(): Response<Map<String, List<ActiveSession>>>

    @POST("/sessions/{id}/join")
    suspend fun joinSession(
        @Path("id") sessionId: String,
        @Body body: JoinRequest
    ): Response<JoinResponse>
}

data class AttendanceHistoryItem(
    val courseName: String? = null,
    val sessionId: String,
    val status: String,
    val joinScore: Int,
    val attendanceTimestamp: String,
    val lastHeartbeatTimestamp: String? = null,
    val totalAcceptedHeartbeats: Int = 0,
    val lastHeartbeatSequenceNumber: Int? = null,
    val currentHeartbeatStatus: String? = null
)

data class AttendanceHistoryResponse(
    val records: List<AttendanceHistoryItem>
)

data class CurrentAttendanceResponse(
    val record: AttendanceHistoryItem
)

interface AttendanceApi {

    @GET("/attendance/history")
    suspend fun history(
        @retrofit2.http.Query("limit") limit: Int = 50
    ): Response<AttendanceHistoryResponse>

    @GET("/attendance/current")
    suspend fun current(
        @retrofit2.http.Query("sessionId") sessionId: String
    ): Response<CurrentAttendanceResponse>
}