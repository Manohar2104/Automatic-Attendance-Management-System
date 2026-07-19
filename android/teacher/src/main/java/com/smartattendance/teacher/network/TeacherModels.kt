package com.smartattendance.teacher.network

data class ActiveSessionResponse(
    val sessionId: String,
    val course: String,
    val faculty: String,
    val room: String,
    val status: String,
    val registeredDevices: Int,
    val startTime: String
)

data class RegisteredDeviceResponse(
    val studentId: String,
    val anonymousDeviceIdHex: String,
    val publicIdentifier: String?
)

data class StudentAttendance(
    val studentId: String,
    val studentName: String,
    val usn: String,
    val status: String,
    val confidence: Double,
    val averageRssi: Int?,
    val packetsReceived: Int
)

data class LiveAttendanceResponse(
    val sessionId: String,
    val present: Int,
    val absent: Int,
    val students: List<StudentAttendance>
)

data class SessionSummaryResponse(
    val sessionId: String,
    val present: Int,
    val absent: Int,
    val overrides: Int,
    val attendancePercentage: Double
)

data class AttendanceOverrideRequest(
    val sessionId: String,
    val studentId: String,
    val status: String,
    val reason: String
)

data class AttendanceOverrideResponse(
    val success: Boolean,
    val message: String
)