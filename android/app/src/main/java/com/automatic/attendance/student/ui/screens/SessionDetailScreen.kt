package com.automatic.attendance.student.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.automatic.attendance.student.network.AuthApi
import com.automatic.attendance.student.network.RetrofitClient
import com.automatic.attendance.student.network.SessionApi
import com.automatic.attendance.student.repository.AuthRepository
import com.automatic.attendance.student.repository.SessionRepository
import com.automatic.attendance.student.storage.SecureTokenStorageImpl
import com.automatic.attendance.student.viewmodel.HeartbeatViewModel
import com.automatic.attendance.student.viewmodel.JoinUiState
import com.automatic.attendance.student.viewmodel.SessionViewModel

@Composable
fun SessionDetailScreen(
    navController: NavController,
    sessionId: String
) {

    val context = LocalContext.current

    val retrofit =
        RetrofitClient.create("http://192.168.137.1:3000/")

    val api = retrofit.create(SessionApi::class.java)

    val repo = SessionRepository(api)

    val vm: SessionViewModel = remember {
        SessionViewModel(repo)
    }

    val heartbeatVm: HeartbeatViewModel = remember {
        HeartbeatViewModel(context)
    }

    val authApi = retrofit.create(AuthApi::class.java)

    val storage = SecureTokenStorageImpl(context)

    val authRepo = AuthRepository(
        authApi,
        storage
    )

    var studentId by remember {
        mutableStateOf(
            "41cc35f4-df15-4564-bb8a-8d94c3542f74"
        )
    }

    var message by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {

        try {

            val resp = authRepo.me()

            if (
                resp.isSuccessful &&
                resp.body() != null
            ) {

                studentId =
                    resp.body()!!["id"]?.toString() ?: ""
            }

        } catch (_: Exception) {
        }
    }

    val joinState =
        vm.joinState.collectAsState().value

    LaunchedEffect(joinState) {

        if (joinState is JoinUiState.Joined) {

            heartbeatVm.startHeartbeats(
                sessionId,
                studentId
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Text(
            text = "Attendance Verification",
            style = MaterialTheme.typography.h5
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Text(
                    text = "Verification Status",
                    style = MaterialTheme.typography.h6
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text("🟢 Session Available")
                Text("🟢 Authentication Verified")
                Text("🟢 Device Ready")
                Text("🟢 Ready To Join")
            }
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = {
                vm.joinSession(
                    sessionId,
                    studentId
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("Join Session")
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        when (joinState) {

            is JoinUiState.Joining -> {

                CircularProgressIndicator()

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    "Verifying Attendance..."
                )
            }

            is JoinUiState.Joined -> {

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {

                        Text(
                            text = "✅ Attendance Verified",
                            style = MaterialTheme.typography.h6
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            "Heartbeat Monitoring Active"
                        )

                        Text(
                            "Attendance Tracking Started"
                        )
                    }
                }
            }

            is JoinUiState.JoinError -> {

                Text(
                    text = "❌ ${joinState.message}",
                    color = MaterialTheme.colors.error
                )
            }

            else -> {}
        }

        message?.let {

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(it)
        }
    }
}