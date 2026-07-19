package com.smartattendance.teacher.ui.details

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 3.1
 *
 * UI only.
 *
 * Backend integration will be added
 * during Phase 3.2.
 */
class StudentDetailsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(

        StudentDetailsUiState()

    )

    val uiState: StateFlow<StudentDetailsUiState> =

        _uiState.asStateFlow()

    /**
     * Loads student information.
     *
     * Phase 3.2:
     * Fetch from TeacherRepository.
     */
    fun loadStudent(

        studentId: String

    ) {

        // TODO
        // repository.getStudent(studentId)

    }

    /**
     * Mark Present
     */
    fun markPresent() {

        _uiState.value =

            _uiState.value.copy(

                attendanceStatus = AttendanceStatus.PRESENT,

                manualOverride = true

            )

    }

    /**
     * Mark Absent
     */
    fun markAbsent() {

        _uiState.value =

            _uiState.value.copy(

                attendanceStatus = AttendanceStatus.ABSENT,

                manualOverride = true

            )

    }

    /**
     * Mark Late
     */
    fun markLate() {

        _uiState.value =

            _uiState.value.copy(

                attendanceStatus = AttendanceStatus.LATE,

                manualOverride = true

            )

    }

    /**
     * Update Override Reason
     */
    fun updateReason(

        reason: String

    ) {

        _uiState.value =

            _uiState.value.copy(

                overrideReason = reason

            )

    }

    /**
     * Save Manual Override
     *
     * Phase 3.2:
     *
     * TeacherRepository.overrideAttendance()
     */
    fun saveOverride() {

        // TODO

    }

}