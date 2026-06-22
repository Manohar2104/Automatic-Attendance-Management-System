package com.automatic.attendance.student.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.automatic.attendance.student.repository.AuthRepository

data class UserProfile(val id: String, val name: String?)

sealed class DashboardState {
    object Idle : DashboardState()
    object Loading : DashboardState()
    data class Ready(val user: UserProfile) : DashboardState()
    data class Error(val message: String) : DashboardState()
}

class DashboardViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow<DashboardState>(DashboardState.Idle)
    val state: StateFlow<DashboardState> = _state

    fun loadProfile() {
        _state.value = DashboardState.Loading
        viewModelScope.launch {
            try {
                val resp = repo.me()
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!

val user = body["user"] as? Map<*, *>

val id = user?.get("id")?.toString() ?: ""

val name =
    user?.get("full_name")?.toString()
        ?: user?.get("email")?.toString()

_state.value = DashboardState.Ready(
    UserProfile(id, name)
)
                } else {
                    _state.value = DashboardState.Error("Failed to load profile: ${resp.code()}")
                }
            } catch (e: Exception) {
                _state.value = DashboardState.Error(e.message ?: "Unknown error")
            }
        }
    }

   fun logout(onComplete: () -> Unit) {
    viewModelScope.launch {
        try {
            repo.logout()
        } catch (_: Exception) {
            // ignore backend errors
        }

        onComplete()
    }
}
}
