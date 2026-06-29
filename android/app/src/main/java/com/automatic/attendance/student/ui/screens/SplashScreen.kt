package com.automatic.attendance.student.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.automatic.attendance.student.network.AuthApi
import com.automatic.attendance.student.network.RetrofitClient
import com.automatic.attendance.student.repository.AuthRepository
import com.automatic.attendance.student.storage.SecureTokenStorage
import com.automatic.attendance.student.storage.SecureTokenStorageImpl
import com.automatic.attendance.student.viewmodel.AuthViewModel

@Composable
fun SplashScreen(navController: NavController) {

    val context = LocalContext.current

    val retrofit = RetrofitClient.create(
        "http://192.168.137.1:3000/"
    )

    val api = retrofit.create(AuthApi::class.java)

    val storage: SecureTokenStorage =
        SecureTokenStorageImpl(context)

    val repo = AuthRepository(
        api,
        storage
    )

    val vm: AuthViewModel = remember {
        AuthViewModel(repo)
    }

    Surface(
        color = MaterialTheme.colors.background
    ) {

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = "Smart Attendance Registry",
                    style = MaterialTheme.typography.h5
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "Secure Attendance Using WiFi Fingerprinting",
                    style = MaterialTheme.typography.body2
                )

                Spacer(
                    modifier = Modifier.height(32.dp)
                )

                CircularProgressIndicator()

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text("Loading...")
            }
        }
    }

    LaunchedEffect(Unit) {

        vm.restoreSession { ok ->

            if (ok) {

                navController.navigate("dashboard") {
                    popUpTo("splash") {
                        inclusive = true
                    }
                }

            } else {

                navController.navigate("login") {
                    popUpTo("splash") {
                        inclusive = true
                    }
                }
            }
        }
    }
}