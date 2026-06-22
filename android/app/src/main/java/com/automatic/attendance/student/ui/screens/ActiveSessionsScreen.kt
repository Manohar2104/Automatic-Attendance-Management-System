package com.automatic.attendance.student.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.automatic.attendance.student.repository.SessionRepository
import com.automatic.attendance.student.network.RetrofitClient
import com.automatic.attendance.student.network.SessionApi
import com.automatic.attendance.student.viewmodel.SessionViewModel
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column

@Composable
fun ActiveSessionsScreen(navController: NavController) {
    val retrofit = RetrofitClient.create("http://192.168.137.1:3000/")
    val api = retrofit.create(SessionApi::class.java)
    val repo = SessionRepository(api)
    val vm: SessionViewModel = remember { SessionViewModel(repo) }

    LaunchedEffect(Unit) { vm.fetchActiveSessions() }

    val state = vm.state.collectAsState().value

    when (state) {
        is com.automatic.attendance.student.viewmodel.SessionsUiState.Loading -> CircularProgressIndicator()
        is com.automatic.attendance.student.viewmodel.SessionsUiState.Error -> Text((state as com.automatic.attendance.student.viewmodel.SessionsUiState.Error).message)
        is com.automatic.attendance.student.viewmodel.SessionsUiState.Success -> {
            val sessions = (state as com.automatic.attendance.student.viewmodel.SessionsUiState.Success).sessions
            if (sessions.isEmpty()) {
                Text("No active sessions")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sessions) { s ->
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clickable { navController.navigate("session_detail/${s.sessionId}") }) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Session: ${s.sessionId}")
                                
                                Text("Status: ${s.status}")
                                Text("Start: ${s.startTime}")
                            }
                        }
                    }
                }
            }
        }
        else -> {}
    }
}
