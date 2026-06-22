package com.automatic.attendance.student.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext

import com.automatic.attendance.student.network.RetrofitClient
import com.automatic.attendance.student.network.SessionApi
import com.automatic.attendance.student.network.AuthApi
import com.automatic.attendance.student.repository.SessionRepository
import com.automatic.attendance.student.repository.AuthRepository
import com.automatic.attendance.student.storage.SecureTokenStorageImpl
import com.automatic.attendance.student.viewmodel.SessionViewModel
import com.automatic.attendance.student.viewmodel.HeartbeatViewModel
import com.automatic.attendance.student.viewmodel.JoinUiState
import com.automatic.attendance.student.ui.navigation.Routes

@Composable
fun SessionDetailScreen(
    navController: NavController,
    sessionId: String
) {
    val context = LocalContext.current

    val retrofit = RetrofitClient.create("http://192.168.137.1:3000/")

    val api = retrofit.create(SessionApi::class.java)
    val repo = SessionRepository(api)
    val vm: SessionViewModel = remember { SessionViewModel(repo) }

    val heartbeatVm: HeartbeatViewModel =
        remember { HeartbeatViewModel(context) }

    val authApi = retrofit.create(AuthApi::class.java)

    val storage = SecureTokenStorageImpl(context)
    val authRepo = AuthRepository(authApi, storage)

    var studentId by remember {
        mutableStateOf("41cc35f4-df15-4564-bb8a-8d94c3542f74")
    }

    var message by remember {
        mutableStateOf<String?>(null)
    }

    var isJoined by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        try {
            val resp = authRepo.me()

            if (resp.isSuccessful && resp.body() != null) {
                studentId = resp.body()!!["id"]?.toString() ?: ""
            }
        } catch (_: Exception) {
            // Ignore profile loading failures
        }
    }

    val joinState = vm.joinState.collectAsState().value

    /*
     * Start heartbeats once the join succeeds.
     * Moved outside the when block to avoid issues caused by recomposition.
     */
    LaunchedEffect(joinState) {
        if (joinState is JoinUiState.Joined) {
            heartbeatVm.startHeartbeats(sessionId, studentId)
        }
    }

    Column(
        modifier = Modifier.padding(16.dp)
    ) {

        Text("Session: $sessionId")

        Text("Student ID: $studentId")

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                vm.joinSession(sessionId, studentId)
            }
        ) {
            Text("Join Session")
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (joinState) {

            is JoinUiState.Joining -> {
                CircularProgressIndicator()
            }

            is JoinUiState.Joined -> {
    Text("Join successful")
    isJoined = true

    
}

            is JoinUiState.JoinError -> {
                Text("Join failed: ${joinState.message}")
            }

            else -> {}
        }

        if (isJoined) {
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    navController.navigate(
                        Routes.CURRENT_ATTENDANCE.replace(
                            "{sessionId}",
                            sessionId
                        )
                    )
                }
            ) {
                Text("View Current Attendance")
            }
        }

        message?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it)
        }
    }
}