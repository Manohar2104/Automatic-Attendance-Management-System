package com.automatic.attendance.student.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.automatic.attendance.student.network.RetrofitClient
import com.automatic.attendance.student.network.SessionApi
import com.automatic.attendance.student.repository.SessionRepository
import com.automatic.attendance.student.viewmodel.SessionViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun formatDateTime(dateString: String?): String {

    if (dateString.isNullOrEmpty()) {
        return "Not Available"
    }

    return try {

        val instant = Instant.parse(dateString)

        val formatter = DateTimeFormatter.ofPattern(
            "dd MMM yyyy, hh:mm a"
        )

        formatter.format(
            instant.atZone(
                ZoneId.systemDefault()
            )
        )

    } catch (e: Exception) {

        dateString
    }
}

@Composable
fun ActiveSessionsScreen(navController: NavController) {

    val retrofit = RetrofitClient.create(
        "http://192.168.137.1:3000/"
    )

    val api = retrofit.create(SessionApi::class.java)

    val repo = SessionRepository(api)

    val vm: SessionViewModel = remember {
        SessionViewModel(repo)
    }

    LaunchedEffect(Unit) {
        vm.fetchActiveSessions()
    }

    val state = vm.state.collectAsState().value

    when (state) {

        is com.automatic.attendance.student.viewmodel.SessionsUiState.Loading -> {

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is com.automatic.attendance.student.viewmodel.SessionsUiState.Error -> {

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    (state as com.automatic.attendance.student.viewmodel.SessionsUiState.Error).message
                )
            }
        }

        is com.automatic.attendance.student.viewmodel.SessionsUiState.Success -> {

            val sessions =
                (state as com.automatic.attendance.student.viewmodel.SessionsUiState.Success).sessions

            if (sessions.isEmpty()) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = "No Active Attendance Sessions",
                        style = MaterialTheme.typography.h6
                    )
                }

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {

                    items(sessions) { s ->

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .clickable {
                                    navController.navigate(
                                        "session_detail/${s.sessionId}"
                                    )
                                }
                        ) {

                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {

                                Text(
                                    text = "Attendance Session",
                                    style = MaterialTheme.typography.h6
                                )

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )

                                Text(
                                    text =
                                    if (s.status.equals("ACTIVE", true))
                                        "🟢 Active"
                                    else
                                        "🔴 Closed"
                                )

                                Spacer(
                                    modifier = Modifier.height(8.dp)
                                )

                                Text(
                                    text = formatDateTime(
                                        s.startTime
                                    )
                                )

                                Spacer(
                                    modifier = Modifier.height(16.dp)
                                )

                                Button(
                                    onClick = {
                                        navController.navigate(
                                            "session_detail/${s.sessionId}"
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {

                                    Text("View Details")
                                }
                            }
                        }
                    }
                }
            }
        }

        else -> {}
    }
}