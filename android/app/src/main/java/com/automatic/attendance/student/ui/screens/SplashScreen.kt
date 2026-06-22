package com.automatic.attendance.student.ui.screens

import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.NavController

import com.automatic.attendance.student.storage.SecureTokenStorage
import com.automatic.attendance.student.viewmodel.AuthViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import retrofit2.Retrofit
import com.automatic.attendance.student.network.RetrofitClient
import com.automatic.attendance.student.network.AuthApi
import com.automatic.attendance.student.storage.SecureTokenStorageImpl
import com.automatic.attendance.student.repository.AuthRepository
import androidx.compose.runtime.remember

@Composable
fun SplashScreen(navController: NavController) {
    Surface(color = MaterialTheme.colors.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }

    val context = LocalContext.current
    val retrofit = RetrofitClient.create("http://192.168.137.1:3000/")
    val api = retrofit.create(AuthApi::class.java)
    val storage: SecureTokenStorage = SecureTokenStorageImpl(context)
    val repo = AuthRepository(api, storage)
    val vm: AuthViewModel = remember {
    AuthViewModel(repo)
}

    LaunchedEffect(Unit) {
        vm.restoreSession { ok ->
            if (ok) navController.navigate("dashboard") else navController.navigate("login")
        }
    }
}
