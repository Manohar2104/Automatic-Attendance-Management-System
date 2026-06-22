package com.automatic.attendance.student.repository

import com.automatic.attendance.student.network.ActiveSession
import com.automatic.attendance.student.network.JoinRequest
import com.automatic.attendance.student.network.JoinResponse
import com.automatic.attendance.student.network.SessionApi
import retrofit2.Response

class SessionRepository(private val api: SessionApi) {

    suspend fun listActive(): Response<Map<String, List<ActiveSession>>> {
        return api.listActive()
    }

    suspend fun joinSession(
        sessionId: String,
        studentId: String
    ): Response<JoinResponse> {

        return api.joinSession(
            sessionId,
            JoinRequest(studentId)
        )
    }
}