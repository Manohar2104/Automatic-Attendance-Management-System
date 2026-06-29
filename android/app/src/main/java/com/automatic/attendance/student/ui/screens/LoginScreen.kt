package com.automatic.attendance.student.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.automatic.attendance.student.network.AuthApi
import com.automatic.attendance.student.network.RetrofitClient
import com.automatic.attendance.student.repository.AuthRepository
import com.automatic.attendance.student.storage.SecureTokenStorageImpl
import com.automatic.attendance.student.viewmodel.AuthUiState
import com.automatic.attendance.student.viewmodel.AuthViewModel

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

    val vm = remember {
        AuthViewModel(repo)
    }

    var username by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    val state by vm.uiState.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "Smart Attendance",
                style = MaterialTheme.typography.h4,
                color = Color(0xFF0F172A)
            )

            Text(
                text = "Registry",
                style = MaterialTheme.typography.h4,
                color = Color(0xFF1976D2)
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = "Secure attendance powered by WiFi fingerprinting",
                style = MaterialTheme.typography.subtitle1,
                color = Color(0xFF64748B)
            )

            Spacer(
                modifier = Modifier.height(32.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = 10.dp,
                backgroundColor = Color(0xFFF8FAFC)
            ) {

                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                        },
                        label = {
                            Text(
                                "Email",
                                color = Color(0xFF455A64)
                            )
                        },
                        placeholder = {
                            Text(
                                "Enter your email",
                                color = Color.Gray
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.Black,
                            backgroundColor = Color(0xFFF5F7FA),
                            focusedBorderColor = Color(0xFF1976D2),
                            unfocusedBorderColor = Color(0xFF607D8B),
                            focusedLabelColor = Color(0xFF1976D2),
                            unfocusedLabelColor = Color(0xFF455A64),
                            cursorColor = Color(0xFF1976D2)
                        )
                    )

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                        },
                        label = {
                            Text(
                                "Password",
                                color = Color(0xFF455A64)
                            )
                        },
                        placeholder = {
                            Text(
                                "Enter your password",
                                color = Color.Gray
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.Black,
                            backgroundColor = Color(0xFFF5F7FA),
                            focusedBorderColor = Color(0xFF1976D2),
                            unfocusedBorderColor = Color(0xFF607D8B),
                            focusedLabelColor = Color(0xFF1976D2),
                            unfocusedLabelColor = Color(0xFF455A64),
                            cursorColor = Color(0xFF1976D2)
                        )
                    )

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )

                    when (state) {

                        is AuthUiState.Loading -> {

                            CircularProgressIndicator()

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Text(
                                text = "Signing In...",
                                color = Color.DarkGray
                            )
                        }

                        is AuthUiState.Error -> {

                            Text(
                                text = (state as AuthUiState.Error).message,
                                color = MaterialTheme.colors.error
                            )
                        }

                        else -> {}
                    }

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Button(
                        onClick = {
                            vm.login(username, password)
                        },
                        enabled = state !is AuthUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {

                        Text(
                            text = "Sign In",
                            color = Color.White,
                            style = MaterialTheme.typography.button
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(vm.uiState) {

        vm.uiState.collect { s ->

            if (s is AuthUiState.Success) {

                navController.navigate("dashboard") {

                    popUpTo("login") {
                        inclusive = true
                    }
                }
            }
        }
    }
}