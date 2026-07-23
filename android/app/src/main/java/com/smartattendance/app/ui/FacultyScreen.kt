package com.smartattendance.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.smartattendance.app.R
import com.smartattendance.app.network.AttendanceResult
import com.smartattendance.app.network.SessionInfo

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val FacNavy = Color(0xFF1A237E)
private val FacGold = Color(0xFFC9A84C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacultyScreen(
    email: String,
    sessions: List<SessionInfo>,
    loadingSessions: Boolean,
    selectedSessionId: String?,
    attendanceResults: List<AttendanceResult>?,
    loadingAttendance: Boolean,
    onSelectSession: (String, String) -> Unit,
    onRefreshSessions: () -> Unit,
    onLogout: () -> Unit,
    onOverrideAttendance: (studentId: String, status: String, justification: String) -> Unit
) {
    var activeOverrideStudent by remember { mutableStateOf<AttendanceResult?>(null) }
    var selectedStatus by remember { mutableStateOf("PRESENT") }
    var justification by remember { mutableStateOf("") }

    // Auto-refresh live attendance every 5 seconds when viewing a session
    LaunchedEffect(selectedSessionId) {
        if (selectedSessionId != null) {
            while (isActive) {
                val sessionObj = sessions.find { it.id == selectedSessionId }
                onSelectSession(selectedSessionId, sessionObj?.location ?: "")
                delay(5000L)
            }
        } else {
            while (isActive) {
                onRefreshSessions()
                delay(10000L)
            }
        }
    }

    // Override Dialog
    if (activeOverrideStudent != null) {
        AlertDialog(
            onDismissRequest = { activeOverrideStudent = null },
            icon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = FacNavy) },
            title = { Text("Override Attendance", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val stu = activeOverrideStudent!!
                    val display = if (stu.email.contains("@stu.pes.edu")) stu.email.substringBefore("@").uppercase() else stu.email
                    Text("Student: $display", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    listOf("PRESENT" to Color(0xFF2E7D32), "PARTIAL" to Color(0xFFE65100), "ABSENT" to MaterialTheme.colorScheme.error).forEach { (status, color) ->
                        Surface(
                            color = if (selectedStatus == status) color.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().clickable { selectedStatus = status }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                RadioButton(selected = selectedStatus == status, onClick = { selectedStatus = status }, colors = RadioButtonDefaults.colors(selectedColor = color))
                                Spacer(Modifier.width(4.dp))
                                Text(status, style = MaterialTheme.typography.bodyMedium, color = if (selectedStatus == status) color else MaterialTheme.colorScheme.onSurface, fontWeight = if (selectedStatus == status) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = justification,
                        onValueChange = { justification = it },
                        label = { Text("Reason (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        placeholder = { Text("e.g. Medical leave") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onOverrideAttendance(activeOverrideStudent!!.user_id, selectedStatus, justification.ifBlank { "Faculty Override" })
                        activeOverrideStudent = null
                        justification = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FacNavy),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { activeOverrideStudent = null; justification = "" }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (selectedSessionId != null) {
                        IconButton(onClick = onRefreshSessions) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.pes_logo),
                            contentDescription = "PES University",
                            modifier = Modifier.size(36.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                if (selectedSessionId != null) sessions.find { it.id == selectedSessionId }?.course_id ?: "Session" else "Faculty Console",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (selectedSessionId != null) {
                            val sessionObj = sessions.find { it.id == selectedSessionId }
                            onSelectSession(selectedSessionId, sessionObj?.location ?: "")
                        } else {
                            onRefreshSessions()
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        if (selectedSessionId == null) {
            // ---- Sessions List ----
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // Summary header
                Text(
                    "Today's Classes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FacNavy
                )
                Text(
                    "${sessions.count { it.status == "ACTIVE" }} active · ${sessions.count { it.status == "SCHEDULED" }} scheduled · ${sessions.count { it.status == "COMPLETED" }} completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                if (loadingSessions) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FacNavy)
                    }
                } else if (sessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(12.dp))
                            Text("No sessions scheduled", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Pull down to refresh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(sessions) { s ->
                            FacultySessionCard(session = s, onClick = { onSelectSession(s.id, s.location) })
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        } else {
            // ---- Attendance Detail ----
            val currentSession = sessions.find { it.id == selectedSessionId }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                currentSession?.let { sess ->
                    // Session info card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = FacNavy)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(sess.course_id, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = FacGold, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Room ${sess.location}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                            StatusChipFaculty(status = sess.status)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (loadingAttendance) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FacNavy)
                    }
                } else if (attendanceResults.isNullOrEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.GroupOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(12.dp))
                            Text("No attendance data yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Data appears when students start scanning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                } else {
                    val totalPresent = attendanceResults.count { it.status == "PRESENT" }
                    val totalPartial = attendanceResults.count { it.status == "PARTIAL" }
                    val totalAbsent = attendanceResults.count { it.status == "ABSENT" }
                    val total = attendanceResults.size

                    // Stats row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FacStatCard("Present", totalPresent, total, Color(0xFF2E7D32), Modifier.weight(1f))
                        FacStatCard("Partial", totalPartial, total, Color(0xFFE65100), Modifier.weight(1f))
                        FacStatCard("Absent", totalAbsent, total, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Group, contentDescription = null, tint = FacNavy, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Student List", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = FacNavy)
                        Spacer(Modifier.width(6.dp))
                        Surface(color = FacNavy.copy(alpha = 0.1f), shape = CircleShape) {
                            Text("$total", style = MaterialTheme.typography.labelSmall, color = FacNavy, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(attendanceResults) { res ->
                            FacultyStudentRow(result = res, onEditClick = {
                                activeOverrideStudent = res
                                selectedStatus = res.status
                                justification = ""
                            })
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FacultySessionCard(session: SessionInfo, onClick: () -> Unit) {
    val isActive = session.status == "ACTIVE"
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) FacNavy.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isActive) BorderStroke(1.5.dp, FacNavy.copy(alpha = 0.3f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Icon background
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) FacNavy else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isActive) Icons.Filled.PlayArrow else Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(session.course_id, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Room ${session.location}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusChipFaculty(status = session.status)
        }
    }
}

@Composable
private fun StatusChipFaculty(status: String) {
    val (color, text) = when (status) {
        "ACTIVE" -> Pair(Color(0xFF2E7D32), "Active")
        "COMPLETED" -> Pair(Color(0xFF37474F), "Done")
        else -> Pair(Color(0xFF1565C0), "Scheduled")
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FacStatCard(label: String, count: Int, total: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count/$total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun FacultyStudentRow(result: AttendanceResult, onEditClick: () -> Unit) {
    val isStu = result.email.contains("@stu.pes.edu", ignoreCase = true)
    val rollNo = if (isStu) result.email.substringBefore("@").uppercase() else null
    val statusColor = when (result.status) {
        "PRESENT" -> Color(0xFF2E7D32)
        "PARTIAL" -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (rollNo?.take(2) ?: result.email.take(2)).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(rollNo ?: result.email, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val wifiText = "WiFi: ${result.wifi_scans}/${result.total_scans_required}"
                    val bleText = "BLE: ${result.ble_scans}/${result.total_scans_required}"
                    Text("$wifiText · $bleText · ${result.score.toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!result.location_match) {
                    Icon(Icons.Filled.Warning, contentDescription = "Wrong room", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Surface(color = statusColor.copy(alpha = 0.12f), shape = RoundedCornerShape(20.dp)) {
                    Text(result.status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Override", tint = FacNavy, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
