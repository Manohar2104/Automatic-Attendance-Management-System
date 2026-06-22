package com.automatic.attendance.student.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.automatic.attendance.student.repository.AuthRepository

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(username: String, password: String) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val resp = repo.login(username, password)
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!
                    repo.saveTokens(body.accessToken, body.refreshToken)
                    _uiState.value = AuthUiState.Success
                } else {
                    _uiState.value = AuthUiState.Error("Login failed: ${resp.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun restoreSession(onResult: (Boolean) -> Unit) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val access = repo.getAccessToken()
                if (access.isNullOrEmpty()) {
                    _uiState.value = AuthUiState.Idle
                    onResult(false)
                    return@launch
                }

                val resp = repo.me()
                if (resp.isSuccessful) {
                    _uiState.value = AuthUiState.Success
                    onResult(true)
                } else {
                    _uiState.value = AuthUiState.Idle
                    onResult(false)
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Idle
                onResult(false)
            }
        }
    }
}
