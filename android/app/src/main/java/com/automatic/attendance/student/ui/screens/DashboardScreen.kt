package com.automatic.attendance.student.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.compose.material.Button

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.ui.platform.LocalContext
import com.automatic.attendance.student.network.RetrofitClient
import com.automatic.attendance.student.network.AuthApi
import com.automatic.attendance.student.storage.SecureTokenStorageImpl
import com.automatic.attendance.student.repository.AuthRepository
import com.automatic.attendance.student.viewmodel.DashboardViewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch


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
    val vm: DashboardViewModel = remember { DashboardViewModel(repo) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.loadProfile() }

    val state = vm.state.collectAsState().value

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            is com.automatic.attendance.student.viewmodel.DashboardState.Loading -> CircularProgressIndicator()
            is com.automatic.attendance.student.viewmodel.DashboardState.Error -> Text((state as com.automatic.attendance.student.viewmodel.DashboardState.Error).message)
            is com.automatic.attendance.student.viewmodel.DashboardState.Ready -> {
                val user = (state as com.automatic.attendance.student.viewmodel.DashboardState.Ready).user
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Welcome, ${user.name ?: user.id}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { navController.navigate("active_sessions") }) { Text("View Active Sessions") }
                    Spacer(modifier = Modifier.height(8.dp))
                   Button(
    onClick = {
        vm.logout {
            navController.navigate("login") {
                popUpTo(0) {
                    inclusive = true
                }
            }
        }
    }
) {
    Text("Logout")
}
                }
            }
            else -> Text("Dashboard")
        }
    }
}
