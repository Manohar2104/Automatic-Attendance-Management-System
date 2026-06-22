package com.automatic.attendance.student.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.automatic.attendance.student.network.RetrofitClient
import com.automatic.attendance.student.network.SessionApi
import com.automatic.attendance.student.repository.AttendanceRepository
import com.automatic.attendance.student.viewmodel.AttendanceViewModel
import com.automatic.attendance.student.network.AuthApi
import com.automatic.attendance.student.storage.SecureTokenStorageImpl
import com.automatic.attendance.student.repository.AuthRepository

@Composable
fun CurrentAttendanceScreen(navController: NavController, sessionId: String) {
    val context = LocalContext.current
    val retrofit = RetrofitClient.create("http://192.168.137.1:3000/")
    val sessionApi = retrofit.create(SessionApi::class.java)
    val authApi = retrofit.create(AuthApi::class.java)
    val storage = SecureTokenStorageImpl(context)
    val authRepo = AuthRepository(authApi, storage)
    val attendanceRepo = AttendanceRepository(sessionApi)
    val vm = remember { AttendanceViewModel(attendanceRepo) }

    var studentId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val resp = authRepo.me()
            if (resp.isSuccessful && resp.body() != null) {
                studentId = resp.body()!!["id"]?.toString() ?: ""
                vm.loadCurrentAttendance(sessionId, studentId)
            }
        } catch (_: Exception) {}
    }

    val state = vm.currentState.collectAsState().value

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Current Attendance", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is com.automatic.attendance.student.viewmodel.CurrentAttendanceState.Loading -> {
                CircularProgressIndicator()
            }
            is com.automatic.attendance.student.viewmodel.CurrentAttendanceState.Error -> {
                Text("Error: ${(state as com.automatic.attendance.student.viewmodel.CurrentAttendanceState.Error).message}", color = MaterialTheme.colors.error)
            }
            is com.automatic.attendance.student.viewmodel.CurrentAttendanceState.Ready -> {
                val data = (state as com.automatic.attendance.student.viewmodel.CurrentAttendanceState.Ready).data
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Session: $sessionId")
                        Text("Running Presence Score: ${data["runningPresenceScore"] ?: "N/A"}")
                        Text("Confidence Score: ${data["confidenceScore"] ?: "N/A"}")
                        Text("Heartbeat Status: ${data["heartbeatStatus"] ?: "Not started"}")
                        Text("Last Heartbeat: ${data["lastHeartbeat"] ?: "N/A"}")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text("Back")
                }
            }
            else -> Text("Idle")
        }
    }
}
