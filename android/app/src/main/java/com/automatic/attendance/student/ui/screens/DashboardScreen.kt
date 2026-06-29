package com.automatic.attendance.student.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.automatic.attendance.student.network.AuthApi
import com.automatic.attendance.student.network.RetrofitClient
import com.automatic.attendance.student.repository.AuthRepository
import com.automatic.attendance.student.storage.SecureTokenStorageImpl
import com.automatic.attendance.student.viewmodel.DashboardState
import com.automatic.attendance.student.viewmodel.DashboardViewModel

private val CardBlue = Color(0xFF102A5C)

@Composable
fun DashboardScreen(navController: NavController) {

    val context = LocalContext.current

    val storage = SecureTokenStorageImpl(context)

    val retrofit = RetrofitClient.create(
        "http://192.168.137.1:3000/",
        storage
    )

    val api = retrofit.create(AuthApi::class.java)

    val repo = AuthRepository(api, storage)

    val vm: DashboardViewModel = remember {
        DashboardViewModel(repo)
    }

    LaunchedEffect(Unit) {
        vm.loadProfile()
    }

    val state = vm.state.collectAsState().value

    when (state) {

        is DashboardState.Loading -> {

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is DashboardState.Error -> {

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(state.message)
            }
        }

        is DashboardState.Ready -> {

            val user = state.user

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Hello 👋",
                    style = MaterialTheme.typography.h5
                )

                Text(
                    text = user.name ?: "Student",
                    style = MaterialTheme.typography.h3
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("3", style = MaterialTheme.typography.h5)
                        Text("Sessions")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("98%", style = MaterialTheme.typography.h5)
                        Text("Attendance")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("12", style = MaterialTheme.typography.h5)
                        Text("Courses")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = 8.dp,
                    backgroundColor = CardBlue
                ) {

                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {

                        Text(
                            text = "Verification Status",
                            style = MaterialTheme.typography.h6,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "🟢 Device Registered",
                            color = Color.White
                        )

                        Text(
                            "🟢 Authentication Active",
                            color = Color.White
                        )

                        Text(
                            "🟢 Ready For Attendance",
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = 8.dp,
                    backgroundColor = CardBlue
                ) {

                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {

                        Text(
                            text = "Attendance System",
                            style = MaterialTheme.typography.h6,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Join active attendance sessions and verify your presence securely.",
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        navController.navigate("active_sessions")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("View Active Sessions")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {

                        vm.logout {

                            navController.navigate("login") {

                                popUpTo(0) {
                                    inclusive = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Logout")
                }
            }
        }

        else -> {}
    }
}