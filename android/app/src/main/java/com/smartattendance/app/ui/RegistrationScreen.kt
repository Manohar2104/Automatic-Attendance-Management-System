package com.smartattendance.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.smartattendance.app.R

@Composable
fun RegistrationScreen(
    onRegister: (email: String, password: String, role: String) -> Unit,
    loading: Boolean,
    error: String?
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("STUDENT") }
    var showPassword by remember { mutableStateOf(false) }

    val isValidEmail = email.contains("@") && email.contains(".")
    val isValidInput = email.isNotBlank() && password.length >= 6 && isValidEmail

    val pesNavy = Color(0xFF1A237E)
    val pesGold = Color(0xFFC9A84C)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(pesNavy, Color(0xFF283593))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // PES Logo
            Image(
                painter = painterResource(id = R.drawable.pes_logo),
                contentDescription = "PES University",
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .border(2.dp, pesGold, CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))

            Text(
                "PES University",
                style = MaterialTheme.typography.headlineSmall,
                color = pesGold,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Smart Attendance System",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(32.dp))

            // Login Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Sign In",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = pesNavy
                    )
                    Text(
                        "Use your university credentials",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(24.dp))

                    // Role toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF0F0F0))
                            .padding(4.dp)
                    ) {
                        listOf("STUDENT", "FACULTY").forEach { role ->
                            val isSelected = selectedRole == role
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) pesNavy else Color.Transparent)
                                    .clickable { selectedRole = role }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (role == "STUDENT") Icons.Filled.School else Icons.Filled.Person,
                                        contentDescription = role,
                                        tint = if (isSelected) Color.White else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        role.lowercase().replaceFirstChar { it.uppercase() },
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text("University Email") },
                        leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = pesNavy) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = email.isNotBlank() && !isValidEmail,
                        shape = RoundedCornerShape(12.dp),
                        placeholder = {
                            Text(
                                if (selectedRole == "STUDENT") "srn@stu.pes.edu" else "faculty@pes.edu",
                                color = Color.LightGray
                            )
                        }
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = pesNavy) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle password",
                                    tint = Color.Gray
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = password.isNotBlank() && password.length < 6,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Inline error messages
                    AnimatedVisibility(visible = email.isNotBlank() && !isValidEmail) {
                        Text("Enter a valid email address", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    AnimatedVisibility(visible = password.isNotBlank() && password.length < 6) {
                        Text("Password must be at least 6 characters", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    AnimatedVisibility(visible = error != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { onRegister(email, password, selectedRole) },
                        enabled = isValidInput && !loading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = pesNavy)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(12.dp))
                            Text("Signing in...", color = Color.White, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Filled.Login, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign In", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Attendance is recorded automatically\nonce you sign in and enable scanning",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}
