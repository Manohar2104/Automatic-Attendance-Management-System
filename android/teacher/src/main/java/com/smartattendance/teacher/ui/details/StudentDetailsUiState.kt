package com.smartattendance.teacher.ui.details

enum class AttendanceStatus {

    PRESENT,

    ABSENT,

    LATE,

    LOW_CONFIDENCE

}

data class StudentDetailsUiState(

    val studentId: String = "",

    val studentName: String = "",

    val usn: String = "",

    val attendanceStatus: AttendanceStatus = AttendanceStatus.ABSENT,

    val confidence: Int = 0,

    val averageRssi: Int = 0,

    val packetsReceived: Int = 0,

    val firstSeen: String = "--",

    val lastSeen: String = "--",

    val rollingToken: String = "--",

    val deviceId: String = "--",

    val manualOverride: Boolean = false,

    val overrideReason: String = "",

    val isLoading: Boolean = false

)