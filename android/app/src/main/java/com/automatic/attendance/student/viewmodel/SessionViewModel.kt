package com.automatic.attendance.student.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.automatic.attendance.student.repository.SessionRepository
import com.automatic.attendance.student.network.ActiveSession
import retrofit2.Response

sealed class SessionsUiState {
    object Idle : SessionsUiState()
    object Loading : SessionsUiState()
    data class Success(val sessions: List<ActiveSession>) : SessionsUiState()
    data class Error(val message: String) : SessionsUiState()
}

sealed class JoinUiState {
    object Idle : JoinUiState()
    object Joining : JoinUiState()
    object Joined : JoinUiState()
    data class JoinError(val message: String) : JoinUiState()
}

class SessionViewModel(private val repo: SessionRepository) : ViewModel() {
    private val _state = MutableStateFlow<SessionsUiState>(SessionsUiState.Idle)
    val state: StateFlow<SessionsUiState> = _state

    private val _joinState = MutableStateFlow<JoinUiState>(JoinUiState.Idle)
    val joinState: StateFlow<JoinUiState> = _joinState

    fun fetchActiveSessions() {
        _state.value = SessionsUiState.Loading
        viewModelScope.launch {
            try {
                val resp: Response<Map<String, List<ActiveSession>>> = repo.listActive()
                if (resp.isSuccessful) {
                    val map = resp.body()
                    val sessions = map?.get("sessions") ?: emptyList()
                    if (sessions.isEmpty()) _state.value = SessionsUiState.Success(emptyList()) else _state.value = SessionsUiState.Success(sessions)
                } else {
                    _state.value = SessionsUiState.Error("Failed to load sessions: ${resp.code()}")
                }
            } catch (e: Exception) {
                _state.value = SessionsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun joinSession(sessionId: String, studentId: String) {
        _joinState.value = JoinUiState.Joining
        viewModelScope.launch {
           try {
    val resp = repo.joinSession(sessionId, studentId)

    if (resp.isSuccessful && resp.body() != null) {
        val result = resp.body()!!

        _joinState.value = JoinUiState.Joined

        println(
            "Joined session ${result.sessionId} " +
            "status=${result.attendanceStatus}"
        )
    } else {
        _joinState.value =
            JoinUiState.JoinError("Join failed: ${resp.code()}")
    }
} catch (e: Exception) {
    _joinState.value =
        JoinUiState.JoinError(e.message ?: "Unknown error")
}
        }
    }
}
