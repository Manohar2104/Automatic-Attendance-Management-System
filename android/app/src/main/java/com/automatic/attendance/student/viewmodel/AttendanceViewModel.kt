package com.automatic.attendance.student.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.automatic.attendance.student.repository.AttendanceRepository
import com.automatic.attendance.student.repository.AttendanceRecord

sealed class AttendanceHistoryState {
    object Idle : AttendanceHistoryState()
    object Loading : AttendanceHistoryState()
    data class Success(val records: List<AttendanceRecord>) : AttendanceHistoryState()
    data class Error(val message: String) : AttendanceHistoryState()
}

sealed class CurrentAttendanceState {
    object Idle : CurrentAttendanceState()
    object Loading : CurrentAttendanceState()
    data class Ready(val data: Map<String, Any>) : CurrentAttendanceState()
    data class Error(val message: String) : CurrentAttendanceState()
}

class AttendanceViewModel(private val repo: AttendanceRepository) : ViewModel() {
    private val _historyState = MutableStateFlow<AttendanceHistoryState>(AttendanceHistoryState.Idle)
    val historyState: StateFlow<AttendanceHistoryState> = _historyState

    private val _currentState = MutableStateFlow<CurrentAttendanceState>(CurrentAttendanceState.Idle)
    val currentState: StateFlow<CurrentAttendanceState> = _currentState

    fun loadAttendanceHistory(studentId: String) {
        _historyState.value = AttendanceHistoryState.Loading
        viewModelScope.launch {
            try {
                val records = repo.getAttendanceHistory(studentId)
                _historyState.value = AttendanceHistoryState.Success(records)
            } catch (e: Exception) {
                _historyState.value = AttendanceHistoryState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadCurrentAttendance(sessionId: String, studentId: String) {
        _currentState.value = CurrentAttendanceState.Loading
        viewModelScope.launch {
            try {
                val resp = repo.getCurrentAttendance(sessionId, studentId)
                if (resp.isSuccessful && resp.body() != null) {
                    _currentState.value = CurrentAttendanceState.Ready(resp.body()!!)
                } else {
                    _currentState.value = CurrentAttendanceState.Error("Failed to load: ${resp.code()}")
                }
            } catch (e: Exception) {
                _currentState.value = CurrentAttendanceState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
