package com.smartattendance.teacher.ui.attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartattendance.teacher.repository.TeacherRepository
import com.smartattendance.teacher.ui.dashboard.TeacherDashboardUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.math.max
import kotlin.math.min

class LiveAttendanceViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = TeacherRepository(
        context = application.applicationContext,
        baseUrl = "http://192.168.137.1:8000/",
    )

    private val _uiState = MutableStateFlow(LiveAttendanceUiState())

    val uiState: StateFlow<LiveAttendanceUiState> = _uiState.asStateFlow()

    init {
        loadAttendance()
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(5_000L)
                if (!_uiState.value.isLoading) {
                    loadAttendance()
                }
            }
        }
    }

    fun loadAttendance() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val dashboardState = repository.getDashboardState().getOrNull()
            val sessionId = _uiState.value.activeSessionId.ifBlank {
                dashboardState?.activeSessionId.orEmpty().ifBlank {
                    repository.currentSessionIdForLogging()
                }
            }

            if (sessionId.isBlank()) {
                _uiState.value = buildIdleState(dashboardState)
                return@launch
            }

            val summaryResult = repository.getSummary(sessionId)
            val presenceResult = repository.getPresence(sessionId)
            val observationsResult = repository.getObservations(sessionId)

            val summary = summaryResult.getOrNull()
            val presence = presenceResult.getOrNull()
            val observations = observationsResult.getOrNull()

            val registeredEmailMap = repository.getRegisteredDevices()
                .getOrNull()
                .orEmpty()
                .associate { entry ->
                    entry.studentId to (entry.publicIdentifier ?: "--")
                }

            val students = buildStudents(presence, observations, registeredEmailMap)
            val filteredStudents = applyFilters(
                students = students,
                query = _uiState.value.searchQuery,
                filter = _uiState.value.selectedFilter,
            )
            val sessionStartTime = summary?.session?.start_time ?: "--"
            val sessionStatus = summary?.session?.status
                ?: dashboardState?.sessionStatus
                ?: "ACTIVE"

            _uiState.value = _uiState.value.copy(
                activeSessionId = sessionId,
                course = summary?.session?.course_id
                    ?: dashboardState?.course
                    ?: "--",
                faculty = dashboardState?.faculty ?: "--",
                room = dashboardState?.room ?: "--",
                sessionStatus = sessionStatus,
                sessionStartedAt = sessionStartTime,
                sessionDuration = calculateDuration(sessionStartTime),
                registeredDevices = dashboardState?.registeredDevices ?: 0,
                studentsSeen = summary?.detected_students ?: students.size,
                packetsReceived = summary?.packets_received ?: (observations?.observations?.size ?: 0),
                packetsAccepted = observations?.observations?.size ?: 0,
                cooldownIntervalMinutes = 2,
                lastPacketReceivedAt = summary?.latest_observation
                    ?: observations?.observations?.maxByOrNull { it.observation_timestamp }?.observation_timestamp
                    ?: presence?.records?.maxByOrNull { it.last_seen }?.last_seen
                    ?: "--",
                presentCount = summary?.present
                    ?: presence?.summary?.present
                    ?: 0,
                absentCount = summary?.missing
                    ?: presence?.summary?.missing
                    ?: 0,
                detectedCount = summary?.detected_students ?: students.size,
                sessionActive = sessionStatus.equals("ACTIVE", ignoreCase = true),
                students = filteredStudents,
                allStudents = students,
                isLoading = false,
            )
            Log.d(
                TAG,
                "Live metrics update sessionId=$sessionId packetsReceived=${_uiState.value.packetsReceived} packetsAccepted=${_uiState.value.packetsAccepted} studentsSeen=${_uiState.value.studentsSeen} present=${_uiState.value.presentCount} missing=${_uiState.value.absentCount} averageRssi=${students.map { it.averageRssi }.takeIf { it.isNotEmpty() }?.average() ?: Double.NaN} lastPacketTime=${_uiState.value.lastPacketReceivedAt}",
            )
        }
    }

    fun refreshAttendance() {
        loadAttendance()
    }

    fun updateSearch(query: String) {
        val current = _uiState.value
        val filteredStudents = applyFilters(
            students = current.allStudents,
            query = query,
            filter = current.selectedFilter,
        )

        _uiState.value = current.copy(
            searchQuery = query,
            students = filteredStudents,
        )
    }

    fun updateFilter(filter: AttendanceFilter) {
        val current = _uiState.value
        val filteredStudents = applyFilters(
            students = current.allStudents,
            query = current.searchQuery,
            filter = filter,
        )

        _uiState.value = current.copy(
            selectedFilter = filter,
            students = filteredStudents,
        )
    }

    fun markPresent(studentId: String) {
        updateStudent(studentId) { student ->
            student.copy(
                status = AttendanceStatus.PRESENT,
                confidence = 100,
            )
        }
    }

    fun markAbsent(studentId: String) {
        updateStudent(studentId) { student ->
            student.copy(status = AttendanceStatus.ABSENT)
        }
    }

    private fun updateStudent(
        studentId: String,
        transform: (StudentAttendanceUi) -> StudentAttendanceUi,
    ) {
        val current = _uiState.value
        val updatedStudents = current.allStudents.map { student ->
            if (student.studentId == studentId) transform(student) else student
        }
        val visibleStudents = applyFilters(
            students = updatedStudents,
            query = current.searchQuery,
            filter = current.selectedFilter,
        )

        _uiState.value = current.copy(
            allStudents = updatedStudents,
            students = visibleStudents,
        )
    }

    private fun buildStudents(
        presence: com.smartattendance.shared.network.BlePresenceResponse?,
        observations: com.smartattendance.shared.network.BleObservationsResponse?,
        registeredEmailMap: Map<String, String>,
    ): List<StudentAttendanceUi> {
        val presenceByStudent = presence?.records.orEmpty()
            .associateBy { it.student_id }

        val latestObservationByStudent = observations?.observations.orEmpty()
            .groupBy { it.student_id }
            .mapValues { (_, items) ->
                items.maxByOrNull { it.observation_timestamp }
            }

        val studentIds = linkedSetOf<String>()
        studentIds.addAll(presenceByStudent.keys)
        studentIds.addAll(latestObservationByStudent.keys)

        return studentIds.map { studentId ->
            val presenceRecord = presenceByStudent[studentId]
            val latestObservation = latestObservationByStudent[studentId]
            val rssi = latestObservation?.rssi ?: 0
            val backendLastSeen = presenceRecord?.last_seen ?: latestObservation?.last_seen ?: "--"
            val lastSeen = backendLastSeen
            val lastAcceptedPacketTime = latestObservation?.observation_timestamp ?: backendLastSeen
            val anonymousCode = latestObservation?.anonymous_ble_id ?: "--"
            val packetsReceived = latestObservation?.advertisement_count ?: 0
            val confidence = when {
                presenceRecord?.status.equals("PRESENT", ignoreCase = true) ->
                    maxOf(0, minOf(100, 100 + rssi + 55))
                presenceRecord?.status.equals("PARTIAL", ignoreCase = true) -> 60
                presenceRecord?.status.equals("MISSING", ignoreCase = true) -> 0
                latestObservation != null -> maxOf(0, minOf(100, 100 + rssi + 55))
                else -> 0
            }

            StudentAttendanceUi(
                studentId = studentId,
                usn = studentId,
                studentName = presenceRecord?.student_name ?: studentId,
                studentEmail = registeredEmailMap[studentId] ?: "--",
                anonymousCode = anonymousCode ?: "--",
                status = mapStatus(presenceStatus = presenceRecord?.status),
                confidence = confidence,
                averageRssi = rssi,
                packetsReceived = packetsReceived,
                lastSeen = lastSeen,
                lastAcceptedPacketTime = lastAcceptedPacketTime,
            )
        }.sortedBy { it.studentName.lowercase() }
    }

    private fun applyFilters(
        students: List<StudentAttendanceUi>,
        query: String,
        filter: AttendanceFilter,
    ): List<StudentAttendanceUi> {
        val normalizedQuery = query.trim().lowercase()
        return students.filter { student ->
            val matchesQuery = normalizedQuery.isBlank() ||
                student.studentName.lowercase().contains(normalizedQuery) ||
                student.usn.lowercase().contains(normalizedQuery) ||
                student.studentId.lowercase().contains(normalizedQuery)

            val matchesFilter = when (filter) {
                AttendanceFilter.ALL -> true
                AttendanceFilter.PRESENT -> student.status == AttendanceStatus.PRESENT
                AttendanceFilter.ABSENT -> student.status == AttendanceStatus.ABSENT
                AttendanceFilter.LATE -> student.status == AttendanceStatus.LATE
                AttendanceFilter.LOW_CONFIDENCE -> student.status == AttendanceStatus.LOW_CONFIDENCE
            }

            matchesQuery && matchesFilter
        }
    }

    private fun mapStatus(
        presenceStatus: String?,
    ): AttendanceStatus {
        return when (presenceStatus?.uppercase()) {
            "PRESENT" -> AttendanceStatus.PRESENT
            "ABSENT" -> AttendanceStatus.ABSENT
            "LATE" -> AttendanceStatus.LATE
            "MISSING" -> AttendanceStatus.ABSENT
            "PARTIAL" -> AttendanceStatus.LOW_CONFIDENCE
            else -> {
                AttendanceStatus.ABSENT
            }
        }
    }

    private fun calculateDuration(startTime: String): String {
        return try {
            val duration = Duration.between(Instant.parse(startTime), Instant.now())
            val hours = duration.toHours()
            val minutes = duration.toMinutes() % 60
            val seconds = duration.seconds % 60
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        } catch (_: DateTimeParseException) {
            "00:00:00"
        } catch (_: Exception) {
            "00:00:00"
        }
    }

    private fun buildIdleState(dashboardState: TeacherDashboardUiState?): LiveAttendanceUiState {
        val teacherName = dashboardState?.faculty ?: "--"
        return LiveAttendanceUiState(
            faculty = teacherName,
            course = dashboardState?.course ?: "--",
            room = dashboardState?.room ?: "--",
            sessionStatus = dashboardState?.sessionStatus ?: "NO SESSION",
            registeredDevices = dashboardState?.registeredDevices ?: 0,
            studentsSeen = dashboardState?.studentsSeen ?: 0,
            packetsReceived = dashboardState?.packetsReceived ?: 0,
            sessionActive = false,
            students = emptyList(),
            allStudents = emptyList(),
            errorMessage = null,
            isLoading = false,
        )
    }

    companion object {
        private const val TAG = "LiveAttendanceViewModel"
    }
}