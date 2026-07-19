package com.smartattendance.teacher.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartattendance.teacher.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class TeacherLoginViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val repository = TeacherRepository(
        context = application.applicationContext,
        baseUrl = BASE_URL,
    )

    private val _uiState = MutableStateFlow(TeacherLoginUiState())
    val uiState: StateFlow<TeacherLoginUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(
            email = email,
            errorMessage = null,
            isLoggedIn = false,
        )
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            errorMessage = null,
            isLoggedIn = false,
        )
    }

    fun login() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Email and password are required.",
                isLoggedIn = false,
            )
            return
        }

        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                isLoggedIn = false,
            )

            repository.login(
                email = email,
                password = password,
            ).onSuccess {
                _uiState.value = _uiState.value.copy(
                    password = "",
                    isLoading = false,
                    errorMessage = null,
                    isLoggedIn = true,
                )
            }.onFailure { exception ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedIn = false,
                    errorMessage = mapError(exception),
                )
            }
        }
    }

    private fun mapError(exception: Throwable): String {
        return when (exception) {
            is HttpException -> when (exception.code()) {
                401 -> "Invalid email or password"
                else -> extractBackendMessage(exception)
                    ?: exception.message()
                    ?: "Unable to sign in"
            }

            is IOException -> "Unable to reach server"
            else -> exception.message ?: "Unable to sign in"
        }
    }

    private fun extractBackendMessage(exception: HttpException): String? {
        val errorBody = exception.response()?.errorBody()?.string()?.trim().orEmpty()
        if (errorBody.isBlank()) return null

        val detailRegex = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"")
        return detailRegex.find(errorBody)?.groupValues?.getOrNull(1) ?: errorBody
    }

    companion object {
        private const val BASE_URL = "http://192.168.137.1:8000/"
    }
}
