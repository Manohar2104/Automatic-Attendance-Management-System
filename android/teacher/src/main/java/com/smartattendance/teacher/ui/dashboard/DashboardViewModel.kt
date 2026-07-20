package com.smartattendance.teacher.ui.dashboard

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartattendance.shared.ble.BleScannerConstants
import com.smartattendance.teacher.repository.TeacherRepository
import com.smartattendance.teacher.service.BLEScannerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class DashboardViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = TeacherRepository(
        context = application.applicationContext,
        baseUrl = "http://192.168.137.1:8000/",
    )

    private val _uiState = MutableStateFlow(TeacherDashboardUiState())
    val uiState: StateFlow<TeacherDashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
        startAutoRefresh()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            refreshDashboardOnce()
        }
    }

    private suspend fun refreshDashboardOnce() {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null,
        )

        repository.getDashboardState()
            .onSuccess { dashboard ->
                val noActiveSession = dashboard.activeSessionId.isBlank() && dashboard.sessionStatus == "NO SESSION"
                val nextScannerStatus = if (noActiveSession) "STOPPED" else _uiState.value.scannerStatus

                _uiState.value = dashboard.copy(
                    isLoading = false,
                    scannerStatus = nextScannerStatus,
                    activeSessionId = if (noActiveSession) "" else dashboard.activeSessionId,
                )
                Log.d(
                    TAG,
                    "Teacher metrics update sessionId=${_uiState.value.activeSessionId.ifBlank { "<none>" }} packetsReceived=${_uiState.value.packetsReceived} studentsSeen=${_uiState.value.studentsSeen} registered=${_uiState.value.registeredDevices} scannerStatus=${_uiState.value.scannerStatus}",
                )

                if (noActiveSession && _uiState.value.scannerStatus == "RUNNING") {
                    stopScanning()
                }
            }
            .onFailure { exception ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    serverConnected = false,
                    errorMessage = exception.message,
                )
            }
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(10_000L)
                refreshDashboardOnce()
            }
        }
    }

    fun startScanning() {
        viewModelScope.launch {
            if (_uiState.value.scannerStatus == "STARTING" || _uiState.value.scannerStatus == "RUNNING") {
                Log.d(TAG, "Ignoring duplicate startScanning() request scannerStatus=${_uiState.value.scannerStatus}")
                return@launch
            }

            repository.getDashboardState()
                .onSuccess { dashboard ->
                    _uiState.value = _uiState.value.copy(
                        activeSessionId = dashboard.activeSessionId.ifBlank { _uiState.value.activeSessionId },
                        teacherId = dashboard.teacherId.ifBlank { _uiState.value.teacherId },
                    )
                }

            if (_uiState.value.activeSessionId.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    scannerStatus = "STOPPED",
                    errorMessage = "Active session is not loaded yet",
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                scannerStatus = "STARTING",
                errorMessage = null,
            )

            val intent = Intent(appContext, BLEScannerService::class.java).apply {
                action = BleScannerConstants.ACTION_START_SCANNING
            }

            runCatching {
                ContextCompat.startForegroundService(appContext, intent)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(scannerStatus = "RUNNING")
            }.onFailure { exception ->
                _uiState.value = _uiState.value.copy(
                    scannerStatus = "STOPPED",
                    errorMessage = exception.message,
                )
            }
        }
    }

    fun stopScanning() {
        viewModelScope.launch {
            val intent = Intent(appContext, BLEScannerService::class.java).apply {
                action = BleScannerConstants.ACTION_STOP_SCANNING
            }

            runCatching {
                appContext.startService(intent)
            }

            _uiState.value = _uiState.value.copy(scannerStatus = "STOPPED")
        }
    }

    fun logout() {
        viewModelScope.launch {
            stopScanning()
            repository.clearTeacherSession()
        }
    }

    fun loadSession() {
        loadDashboard()
    }

    fun refreshDashboard() {
        loadDashboard()
    }

    companion object {
        private const val TAG = "DashboardViewModel"
    }
}
