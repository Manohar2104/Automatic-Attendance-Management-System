package com.smartattendance.teacher.ui.dashboard

data class TeacherDashboardUiState(

    val isLoading: Boolean = false,

    val errorMessage: String? = null,

    val activeSessionId: String = "",

    val teacherId: String = "",

    val serverConnected: Boolean = false,

    val course: String = "--",

    val faculty: String = "--",

    val room: String = "--",

    val sessionStatus: String = "NO SESSION",

    val registeredDevices: Int = 0,

    val studentsSeen: Int = 0,

    val packetsReceived: Int = 0,

    val scannerStatus: String = "STOPPED"

)