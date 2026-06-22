package com.automatic.attendance.student.network

import com.automatic.attendance.student.repository.HeartbeatPayload
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface HeartbeatApi {

    @POST("/heartbeats")
    suspend fun sendHeartbeat(
        @Body payload: HeartbeatPayload
    ): Response<Map<String, Any>>
}