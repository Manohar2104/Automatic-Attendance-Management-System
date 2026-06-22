package com.automatic.attendance.student.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.automatic.attendance.student.service.HeartbeatForegroundService

sealed class HeartbeatState {
    object Idle : HeartbeatState()
    object Starting : HeartbeatState()
    object Active : HeartbeatState()
    object Stopping : HeartbeatState()
    data class Error(val message: String) : HeartbeatState()
}

class HeartbeatViewModel(private val context: Context) : ViewModel() {
    private var serviceIntent: Intent? = null
    private var state: HeartbeatState = HeartbeatState.Idle

    fun startHeartbeats(sessionId: String, studentId: String) {
        if (state == HeartbeatState.Active) return

        state = HeartbeatState.Starting
        try {
            serviceIntent = Intent(context, HeartbeatForegroundService::class.java).apply {
                putExtra("sessionId", sessionId)
                putExtra("studentId", studentId)
            }
            context.startForegroundService(serviceIntent)
            state = HeartbeatState.Active
        } catch (e: Exception) {
            state = HeartbeatState.Error(e.message ?: "Unknown error")
        }
    }

    fun stopHeartbeats() {
        if (state == HeartbeatState.Idle) return

        state = HeartbeatState.Stopping
        try {
            serviceIntent?.let { context.stopService(it) }
            state = HeartbeatState.Idle
        } catch (e: Exception) {
            state = HeartbeatState.Error(e.message ?: "Unknown error")
        }
    }

    fun getState(): HeartbeatState = state
}
