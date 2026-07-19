package com.smartattendance.teacher.ui.details

enum class AttendanceStatus {

    PRESENT,

    ABSENT,

    LATE,

    LOW_CONFIDENCE

}

data class StudentDetailsUiState(

    val studentId: String = "",

    val studentName: String = "Alice Johnson",

    val usn: String = "PES2UG22CS001",

    val attendanceStatus: AttendanceStatus = AttendanceStatus.PRESENT,

    val confidence: Int = 98,

    val averageRssi: Int = -56,

    val packetsReceived: Int = 145,

    val firstSeen: String = "09:01:14",

    val lastSeen: String = "09:47:52",

    val rollingToken: String = "**************",

    val deviceId: String = "ANDROID-AB12CD34",

    val manualOverride: Boolean = false,

    val overrideReason: String = "",

    val isLoading: Boolean = false

)