package com.automatic.attendance.student.repository

import com.automatic.attendance.student.network.SessionApi
import retrofit2.Response
import com.automatic.attendance.student.network.ActiveSession

data class AttendanceRecord(
    val sessionId: String,
    val sessionName: String?,
    val courseName: String?,
    val status: String,
    val finalScore: Int?,
    val joinScore: Int?,
    val runningPresenceScore: Int?,
    val attendedAt: String?
)

class AttendanceRepository(private val api: SessionApi) {
    // In a real app, query attendance via a dedicated API
    // For now, this is a placeholder that would be expanded with actual endpoints
    suspend fun getAttendanceHistory(studentId: String): List<AttendanceRecord> {
        // TODO: Call backend GET /attendance or similar endpoint
        return emptyList()
    }

    suspend fun getCurrentAttendance(
    sessionId: String,
    studentId: String
): Response<Map<String, List<ActiveSession>>> {
    return api.listActive()
}
}
