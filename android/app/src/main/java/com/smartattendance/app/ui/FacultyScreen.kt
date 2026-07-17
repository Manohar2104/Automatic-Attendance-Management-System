package com.smartattendance.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.network.SessionInfo
import com.smartattendance.app.network.AttendanceResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacultyScreen(
    email: String,
    sessions: List<SessionInfo>,
    loadingSessions: Boolean,
    selectedSessionId: String?,
    attendanceResults: List<AttendanceResult>?,
    loadingAttendance: Boolean,
    onSelectSession: (String, String) -> Unit, // (sessionId, location)
    onRefreshSessions: () -> Unit,
    onLogout: () -> Unit,
    onOverrideAttendance: (studentId: String, status: String, justification: String) -> Unit
) {
    var activeOverrideStudent by remember { mutableStateOf<AttendanceResult?>(null) }
    var selectedStatus by remember { mutableStateOf("PRESENT") }
    var justification by remember { mutableStateOf("") }

    // Override Dialog
    if (activeOverrideStudent != null) {
        AlertDialog(
            onDismissRequest = { activeOverrideStudent = null },
            title = { Text("Override Attendance") },
            text = {
                Column {
                    Text("Select status for ${activeOverrideStudent!!.email}:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    
                    listOf("PRESENT", "PARTIAL", "ABSENT").forEach { status ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedStatus = status }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = status }
                            )
                            Text(status, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = justification,
                        onValueChange = { justification = it },
                        label = { Text("Justification / Reason") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. Present in class") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val student = activeOverrideStudent!!
                        onOverrideAttendance(
                            student.user_id,
                            selectedStatus,
                            justification.ifBlank { "Faculty Override" }
                        )
                        activeOverrideStudent = null
                        justification = ""
                    }
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    activeOverrideStudent = null
                    justification = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Faculty Console", style = MaterialTheme.typography.headlineMedium)
                Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                IconButton(onClick = onRefreshSessions) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (selectedSessionId == null) {
            // Sessions List Screen
            Text("Select an Active or Scheduled Session", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (loadingSessions) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No class sessions scheduled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sessions) { s ->
                        SessionItem(session = s, onClick = { onSelectSession(s.id, s.location) })
                    }
                }
            }
        } else {
            // Attendance Details Screen for selected session
            val currentSession = sessions.find { it.id == selectedSessionId }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onRefreshSessions() } // Go back
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                Spacer(Modifier.width(8.dp))
                Text("Back to Sessions", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(16.dp))

            currentSession?.let { sess ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(sess.course_id, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text("Location: ${sess.location}", style = MaterialTheme.typography.bodyMedium)
                        Text("Status: ${sess.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (loadingAttendance) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (attendanceResults == null || attendanceResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("No attendance logs processed yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    // Summary counts
                    val totalPresent = attendanceResults.count { it.status == "PRESENT" }
                    val totalAbsent = attendanceResults.count { it.status == "ABSENT" }
                    val totalPartial = attendanceResults.count { it.status == "PARTIAL" }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard("Present", totalPresent.toString(), MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f))
                        StatCard("Partial", totalPartial.toString(), MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f))
                        StatCard("Absent", totalAbsent.toString(), MaterialTheme.colorScheme.errorContainer, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))

                    Text("Student List", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(attendanceResults) { res ->
                            StudentAttendanceItem(
                                result = res,
                                onEditClick = {
                                    activeOverrideStudent = res
                                    selectedStatus = res.status
                                    justification = ""
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionItem(session: SessionInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(session.course_id, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Room: ${session.location}", style = MaterialTheme.typography.bodySmall)
            }
            val statusColor = when (session.status) {
                "ACTIVE" -> MaterialTheme.colorScheme.primary
                "COMPLETED" -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    session.status,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = statusColor,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun StudentAttendanceItem(result: AttendanceResult, onEditClick: () -> Unit) {
    val email = result.email
    val isStuEmail = email.contains("@stu.pes.edu", ignoreCase = true)
    val rollNo = if (isStuEmail) email.substringBefore("@").uppercase() else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                if (rollNo != null) {
                    Text(rollNo, style = MaterialTheme.typography.titleMedium)
                    Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(email, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${result.enter_count} scans | Score: ${result.score.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (result.location_match) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Match", tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Default.Warning, contentDescription = "Wrong Room", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                val badgeColor = when (result.status) {
                    "PRESENT" -> Color(0xFF4CAF50)
                    "PARTIAL" -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.error
                }
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        result.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = badgeColor,
                        fontSize = 11.sp,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Attendance",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, containerColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
