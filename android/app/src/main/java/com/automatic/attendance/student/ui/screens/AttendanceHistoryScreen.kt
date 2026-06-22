package com.automatic.attendance.student.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.automatic.attendance.student.repository.AuthRepository
import com.automatic.attendance.student.storage.SecureTokenStorageImpl
import com.automatic.attendance.student.network.AuthApi

@Composable
fun AttendanceHistoryScreen(navController: NavController) {
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
                vm.loadAttendanceHistory(studentId)
            }
        } catch (_: Exception) {}
    }

    val state = vm.historyState.collectAsState().value

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Attendance History", style = MaterialTheme.typography.h5)

        when (state) {
            is com.automatic.attendance.student.viewmodel.AttendanceHistoryState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }
            is com.automatic.attendance.student.viewmodel.AttendanceHistoryState.Error -> {
                Text("Error: ${(state as com.automatic.attendance.student.viewmodel.AttendanceHistoryState.Error).message}", color = MaterialTheme.colors.error)
            }
            is com.automatic.attendance.student.viewmodel.AttendanceHistoryState.Success -> {
                val records = (state as com.automatic.attendance.student.viewmodel.AttendanceHistoryState.Success).records
                if (records.isEmpty()) {
                    Text("No attendance records")
                } else {
                    LazyColumn {
                        items(records) { record ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("${record.courseName}")
                                    Text("Status: ${record.status}")
                                    Text("Score: ${record.finalScore ?: "N/A"}")
                                    Text("Date: ${record.attendedAt}")
                                }
                            }
                        }
                    }
                }
            }
            else -> Text("Idle")
        }
    }
}
