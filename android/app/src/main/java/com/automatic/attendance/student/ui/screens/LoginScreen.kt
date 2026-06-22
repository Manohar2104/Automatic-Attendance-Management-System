package com.automatic.attendance.student.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.MaterialTheme
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.automatic.attendance.student.repository.AuthRepository
import com.automatic.attendance.student.storage.SecureTokenStorageImpl
import com.automatic.attendance.student.network.RetrofitClient
import com.automatic.attendance.student.network.AuthApi
import com.automatic.attendance.student.viewmodel.AuthViewModel
import androidx.compose.ui.platform.LocalContext

@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val storage = SecureTokenStorageImpl(context)
    val retrofit = RetrofitClient.create(
        "http://192.168.137.1:3000/",
        storage
    )
    val api = retrofit.create(AuthApi::class.java)
    
    val repo = AuthRepository(api, storage)
    val vm = remember { AuthViewModel(repo) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val state by vm.uiState.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Login", style = MaterialTheme.typography.h5)
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

        when (state) {
            is com.automatic.attendance.student.viewmodel.AuthUiState.Loading -> CircularProgressIndicator()
            is com.automatic.attendance.student.viewmodel.AuthUiState.Error -> Text((state as com.automatic.attendance.student.viewmodel.AuthUiState.Error).message, color = MaterialTheme.colors.error)
            else -> {}
        }

        Button(onClick = { vm.login(username, password) }, modifier = Modifier.padding(top = 16.dp)) {
            Text("Sign In")
        }
    }

    // Navigate on success
    LaunchedEffect(vm.uiState) {
        vm.uiState.collect { s ->
            if (s is com.automatic.attendance.student.viewmodel.AuthUiState.Success) {
                navController.navigate("dashboard") {
                    popUpTo("login") { inclusive = true }
                }
            }
        }
    }
}
