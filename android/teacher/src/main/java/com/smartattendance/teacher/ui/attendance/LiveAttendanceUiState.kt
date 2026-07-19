package com.smartattendance.teacher.ui.attendance

data class StudentAttendanceUi(

    val studentId: String,

    val usn: String,

    val studentName: String,

    val studentEmail: String = "--",

    val anonymousCode: String = "--",

    val status: AttendanceStatus,

    val confidence: Int,

    val averageRssi: Int,

    val packetsReceived: Int,

    val lastSeen: String = "--",

    val lastAcceptedPacketTime: String = "--"

)

enum class AttendanceStatus {

    PRESENT,

    ABSENT,

    LATE,

    LOW_CONFIDENCE

}

enum class AttendanceFilter {

    ALL,

    PRESENT,

    ABSENT,

    LATE,

    LOW_CONFIDENCE

}

data class LiveAttendanceUiState(

    val activeSessionId: String = "",

    val course: String = "UE23CS351",

    val faculty: String = "--",

    val room: String = "C-401",

    val sessionStatus: String = "NO SESSION",

    val sessionStartedAt: String = "--",

    val sessionDuration: String = "00:00:00",

    val registeredDevices: Int = 0,

    val studentsSeen: Int = 0,

    val packetsReceived: Int = 0,

    val packetsAccepted: Int = 0,

    val cooldownIntervalMinutes: Int = 2,

    val lastPacketReceivedAt: String = "--",

    val searchQuery: String = "",

    val selectedFilter: AttendanceFilter = AttendanceFilter.ALL,

    val isLoading: Boolean = false,
    val errorMessage: String? = null,

    val sessionActive: Boolean = false,

    val presentCount: Int = 0,

    val absentCount: Int = 0,

    val detectedCount: Int = 0,

    val students: List<StudentAttendanceUi> = emptyList(),

    val allStudents: List<StudentAttendanceUi> = emptyList()

)