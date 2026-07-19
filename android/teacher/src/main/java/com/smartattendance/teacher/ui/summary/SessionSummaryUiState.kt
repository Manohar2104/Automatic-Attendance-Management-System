package com.smartattendance.teacher.ui.summary

data class AttendanceRecordUi(

    val studentId: String,

    val studentName: String,

    val usn: String,

    val status: AttendanceStatus,

    val confidence: Int,

    val manuallyModified: Boolean = false

)

enum class AttendanceStatus {

    PRESENT,

    ABSENT,

    LATE

}

data class SessionSummaryUiState(

    val sessionId: String = "",

    val course: String = "UE23CS351",

    val room: String = "C-401",

    val faculty: String = "Dr. XYZ",

    val duration: String = "00:00:00",

    val registeredDevices: Int = 0,

    val studentsSeen: Int = 0,

    val attendancePercentage: Float = 0f,

    val totalPackets: Int = 0,

    val totalObservations: Int = 0,

    val startTime: String = "--",

    val endTime: String = "--",

    val present: Int = 0,

    val missing: Int = 0,

    val isLoading: Boolean = false,

    val errorMessage: String? = null,

    val exportInProgress: Boolean = false,

    val attendanceRecords: List<AttendanceRecordUi> = emptyList()

)