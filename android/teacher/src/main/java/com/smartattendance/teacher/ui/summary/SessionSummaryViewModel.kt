package com.smartattendance.teacher.ui.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartattendance.teacher.repository.TeacherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class SessionSummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TeacherRepository(
        context = application.applicationContext,
        baseUrl = "http://192.168.137.1:8000/",
    )

    private val _uiState =

        MutableStateFlow(

            SessionSummaryUiState()

        )

    val uiState: StateFlow<SessionSummaryUiState> =

        _uiState.asStateFlow()

    fun loadSummary(

        sessionId: String = ""

    ) {

        viewModelScope.launch {
            val resolvedSessionId = sessionId.ifBlank {
                repository.currentSessionIdForLogging()
            }

            if (resolvedSessionId.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "No active session",
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val summary = repository.getSummary(resolvedSessionId).getOrNull()
            val dashboard = repository.getDashboardState().getOrNull()
            val presence = repository.getPresence(resolvedSessionId).getOrNull()

            if (summary == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load session summary",
                )
                return@launch
            }

            val records = presence?.records.orEmpty().map { record ->
                AttendanceRecordUi(
                    studentId = record.student_id,
                    studentName = record.student_name ?: record.student_id,
                    usn = record.student_id,
                    status = when (record.status.uppercase()) {
                        "PRESENT" -> AttendanceStatus.PRESENT
                        "MISSING" -> AttendanceStatus.ABSENT
                        "LATE" -> AttendanceStatus.LATE
                        else -> AttendanceStatus.ABSENT
                    },
                    confidence = when (record.status.uppercase()) {
                        "PRESENT" -> 100
                        "MISSING" -> 0
                        "LATE" -> 50
                        else -> 0
                    },
                    manuallyModified = false,
                )
            }

            val totalAttendance = (presence?.summary?.present.orZero() + presence?.summary?.missing.orZero()).coerceAtLeast(1)
            val attendancePct = (summary.present.toFloat() / totalAttendance.toFloat()) * 100f

            _uiState.value = _uiState.value.copy(
                sessionId = resolvedSessionId,
                course = summary.session.course_id,
                room = dashboard?.room ?: "Unknown",
                faculty = dashboard?.faculty ?: "Unknown",
                duration = formatDuration(summary.session.start_time, summary.session.end_time),
                registeredDevices = dashboard?.registeredDevices ?: summary.registered_students,
                studentsSeen = summary.detected_students,
                attendancePercentage = attendancePct,
                totalPackets = summary.packets_received,
                totalObservations = summary.packets_received,
                startTime = summary.session.start_time,
                endTime = summary.session.end_time ?: "--",
                present = presence?.summary?.present ?: summary.present,
                missing = presence?.summary?.missing ?: summary.missing,
                attendanceRecords = records,
                isLoading = false,
                errorMessage = null,
            )
        }

    }

    fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(5_000L)
                val sessionId = _uiState.value.sessionId
                if (!_uiState.value.isLoading) {
                    loadSummary(sessionId)
                }
            }
        }
    }

    /**
     * Export PDF
     *
     * Phase 3.3
     */
    fun exportPdf() {

        _uiState.value =

            _uiState.value.copy(

                exportInProgress = true

            )

        // TODO

        _uiState.value =

            _uiState.value.copy(

                exportInProgress = false

            )

    }

    /**
     * Export CSV
     *
     * Phase 3.3
     */
    fun exportCsv() {

        _uiState.value =

            _uiState.value.copy(

                exportInProgress = true

            )

        // TODO

        _uiState.value =

            _uiState.value.copy(

                exportInProgress = false

            )

    }

    /**
     * Finish Session
     *
     * Phase 3.2
     */
    fun finishSession() {

        // TODO

    }

    private fun formatDuration(start: String, end: String?): String {
        return try {
            val startInstant = Instant.parse(start)
            val endInstant = end?.let { Instant.parse(it) } ?: Instant.now()
            val duration = Duration.between(startInstant, endInstant)
            val h = duration.toHours()
            val m = duration.toMinutes() % 60
            val s = duration.seconds % 60
            "%02d:%02d:%02d".format(h, m, s)
        } catch (_: Exception) {
            "00:00:00"
        }
    }

    private fun Int?.orZero(): Int = this ?: 0

}