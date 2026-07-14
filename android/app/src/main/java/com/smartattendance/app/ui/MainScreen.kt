package com.smartattendance.app.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.smartattendance.app.network.ApiClient
import com.smartattendance.app.network.SessionInfo
import com.smartattendance.app.network.StudentAttendanceResponse
import com.smartattendance.app.network.AttendanceResult
import com.smartattendance.app.network.UserMeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    deviceId: String,
    serverUrl: String,
    sessionId: String,
    scanning: Boolean,
    lastSyncTime: Long?,
    connectionStatus: String,
    userRole: String,
    userEmail: String,
    authToken: String,
    onToggleScanning: (Boolean) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onSessionIdChange: (String) -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Smart Attendance", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(userEmail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Role Badge & Connection Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = userRole,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (connectionStatus == "Connected") Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = "Connection status",
                        tint = if (connectionStatus == "Connected") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = connectionStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (connectionStatus == "Connected") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (userRole == "STUDENT") {
                StudentDashboard(
                    deviceId = deviceId,
                    serverUrl = serverUrl,
                    sessionId = sessionId,
                    scanning = scanning,
                    lastSyncTime = lastSyncTime,
                    authToken = authToken,
                    onToggleScanning = onToggleScanning,
                    onServerUrlChange = onServerUrlChange,
                    onSessionIdChange = onSessionIdChange
                )
            } else {
                TeacherDashboard(
                    serverUrl = serverUrl,
                    authToken = authToken
                )
            }
        }
    }
}

@Composable
fun StudentDashboard(
    deviceId: String,
    serverUrl: String,
    sessionId: String,
    scanning: Boolean,
    lastSyncTime: Long?,
    authToken: String,
    onToggleScanning: (Boolean) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onSessionIdChange: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var attendanceLogs by remember { mutableStateOf<List<StudentAttendanceResponse>>(emptyList()) }
    var loadingLogs by remember { mutableStateOf(false) }
    var logsError by remember { mutableStateOf<String?>(null) }
    var selectedLogDetail by remember { mutableStateOf<StudentAttendanceResponse?>(null) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val displayFormat = remember { SimpleDateFormat("EEE, MMM dd", Locale.US) }

    // Fetch attendance when date changes
    LaunchedEffect(selectedDate, serverUrl) {
        loadingLogs = true
        logsError = null
        try {
            val dateStr = dateFormat.format(selectedDate.time)
            val client = ApiClient(serverUrl)
            val response = withContext(Dispatchers.IO) {
                client.api.getStudentAttendance("Bearer $authToken", dateStr)
            }
            attendanceLogs = response
        } catch (e: Exception) {
            logsError = "Failed to load logs: ${e.message}"
            attendanceLogs = emptyList()
        } finally {
            loadingLogs = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Scanner Control Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Automatic Presence Scanner", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (scanning) "Actively broadcasting location beacons" else "Scanning paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (scanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = scanning,
                            onCheckedChange = onToggleScanning
                        )
                    }
                    if (lastSyncTime != null && lastSyncTime > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Last Presence Sync: ${formatTime(lastSyncTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            // Connection Settings Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Connection Settings", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = onServerUrlChange,
                        label = { Text("Server URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sessionId,
                        onValueChange = onSessionIdChange,
                        label = { Text("Custom Session ID (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        item {
            // Daily Attendance Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val newCal = Calendar.getInstance().apply {
                        time = selectedDate.time
                        add(Calendar.DAY_OF_YEAR, -1)
                    }
                    selectedDate = newCal
                }) {
                    Icon(Icons.Default.ArrowBack, "Prev Day")
                }

                Text(
                    text = displayFormat.format(selectedDate.time),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = {
                    val newCal = Calendar.getInstance().apply {
                        time = selectedDate.time
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                    selectedDate = newCal
                }) {
                    Icon(Icons.Default.ArrowForward, "Next Day")
                }
            }
        }

        if (loadingLogs) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (logsError != null) {
            item {
                Text(
                    text = logsError ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else if (attendanceLogs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "No class sessions scheduled for this day.",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(attendanceLogs) { log ->
                val statusColor = when (log.status) {
                    "PRESENT" -> Color(0xFF2E7D32)
                    "PARTIAL" -> Color(0xFFEF6C00)
                    else -> Color(0xFFC62828)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedLogDetail = log },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.course_id,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Room: ${log.location}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Time: ${formatSessionTime(log.scheduled_start)} - ${formatSessionTime(log.scheduled_end)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                color = statusColor.copy(alpha = 0.15f),
                                contentColor = statusColor,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = log.status,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Match Rate: ${log.score.toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Attendance Log Detailed Modal Dialog
    selectedLogDetail?.let { log ->
        val dialogColor = when (log.status) {
            "PRESENT" -> Color(0xFF2E7D32)
            "PARTIAL" -> Color(0xFFEF6C00)
            else -> Color(0xFFC62828)
        }

        AlertDialog(
            onDismissRequest = { selectedLogDetail = null },
            title = { Text("${log.course_id} Detail Report") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Location:", fontWeight = FontWeight.Bold)
                        Text(log.location)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Calculated Grade:", fontWeight = FontWeight.Bold)
                        Text(log.status, color = dialogColor, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Scans Match Rate:", fontWeight = FontWeight.Bold)
                        Text("${log.score.toInt()}%")
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Session Lifecycle:", fontWeight = FontWeight.Bold)
                        Text(log.session_status)
                    }

                    Spacer(Modifier.height(16.dp))
                    Divider()
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = when (log.status) {
                            "PRESENT" -> "Successfully validated. Your device fingerprint sent enough check-ins from within the classroom."
                            "PARTIAL" -> "Partially validated. You checked in but were not present for the entire required duration of the class."
                            else -> {
                                if (log.session_status == "SCHEDULED" || log.session_status == "ACTIVE") {
                                    "This class session is currently active or scheduled. Scan is in progress."
                                } else {
                                    "Absent. No location scans were matched with this classroom. Please make sure the scanning switch is turned on and your device remains registered."
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedLogDetail = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun TeacherDashboard(
    serverUrl: String,
    authToken: String
) {
    val coroutineScope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var selectedSession by remember { mutableStateOf<SessionInfo?>(null) }
    var computedAttendance by remember { mutableStateOf<List<AttendanceResult>>(emptyList()) }
    var allStudentsList by remember { mutableStateOf<List<UserMeResponse>>(emptyList()) }

    var loadingSessions by remember { mutableStateOf(false) }
    var loadingAttendance by remember { mutableStateOf(false) }
    var sessionError by remember { mutableStateOf<String?>(null) }
    var attendanceError by remember { mutableStateOf<String?>(null) }

    // Dialog state
    var selectedOverrideStudent by remember { mutableStateOf<AttendanceResult?>(null) }
    var newStatusOverride by remember { mutableStateOf("PRESENT") }
    var justificationOverride by remember { mutableStateOf("") }
    var showManualOverrideDialog by remember { mutableStateOf(false) }

    // Manual override form state (for unlisted students)
    var selectedManualStudentId by remember { mutableStateOf("") }
    var newManualStatus by remember { mutableStateOf("PRESENT") }
    var justificationManual by remember { mutableStateOf("") }

    // Load sessions
    fun fetchSessions() {
        loadingSessions = true
        sessionError = null
        coroutineScope.launch {
            try {
                val client = ApiClient(serverUrl)
                val response = withContext(Dispatchers.IO) {
                    client.api.getSessions("Bearer $authToken")
                }
                sessions = response.sortedByDescending { it.scheduled_start }
            } catch (e: Exception) {
                sessionError = "Failed to load sessions: ${e.message}"
            } finally {
                loadingSessions = false
            }
        }
    }

    // Load student list (for manual overrides)
    fun fetchStudents() {
        coroutineScope.launch {
            try {
                val client = ApiClient(serverUrl)
                val response = withContext(Dispatchers.IO) {
                    client.api.getStudents("Bearer $authToken")
                }
                allStudentsList = response
            } catch (e: Exception) {
                Log.e("SmartAttendance", "Failed to fetch student list", e)
            }
        }
    }

    // Compute live attendance for selected session
    fun computeAttendanceForSession(session: SessionInfo) {
        loadingAttendance = true
        attendanceError = null
        coroutineScope.launch {
            try {
                val client = ApiClient(serverUrl)
                val response = withContext(Dispatchers.IO) {
                    client.api.computeAttendance(
                        "Bearer $authToken",
                        session.id,
                        session.location
                    )
                }
                computedAttendance = response.results
            } catch (e: Exception) {
                attendanceError = "Failed to compute attendance: ${e.message}"
                computedAttendance = emptyList()
            } finally {
                loadingAttendance = false
            }
        }
    }

    // Trigger load on startup
    LaunchedEffect(serverUrl) {
        fetchSessions()
        fetchStudents()
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Panel - Sessions list
        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .padding(end = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { fetchSessions() }) {
                    Icon(Icons.Default.Refresh, "Refresh sessions")
                }
            }

            Spacer(Modifier.height(8.dp))

            if (loadingSessions) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (sessionError != null) {
                Text(sessionError ?: "Error", color = MaterialTheme.colorScheme.error)
            } else if (sessions.isEmpty()) {
                Text("No sessions found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions) { s ->
                        val isSelected = selectedSession?.id == s.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSession = s
                                    computeAttendanceForSession(s)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(s.course_id, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Room: ${s.location}", fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                val badgeColor = when (s.status) {
                                    "ACTIVE" -> Color(0xFF2E7D32)
                                    "COMPLETED" -> Color(0xFF757575)
                                    else -> Color(0xFF1565C0)
                                }
                                Surface(
                                    color = badgeColor.copy(alpha = 0.15f),
                                    contentColor = badgeColor,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = s.status,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Divider
        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp).padding(horizontal = 4.dp))

        // Right Panel - Attendance details & overriding
        Column(
            modifier = Modifier
                .weight(1.8f)
                .fillMaxHeight()
                .padding(start = 8.dp)
        ) {
            val session = selectedSession
            if (session == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Select a class session from the left to manage attendance.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${session.course_id} Attendance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton(onClick = { computeAttendanceForSession(session) }) {
                            Icon(Icons.Default.Sync, "Recalculate")
                        }
                        IconButton(onClick = { showManualOverrideDialog = true }) {
                            Icon(Icons.Default.Add, "Manual Override")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (loadingAttendance) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (attendanceError != null) {
                    Text(attendanceError ?: "Error", color = MaterialTheme.colorScheme.error)
                } else if (computedAttendance.isEmpty()) {
                    Text(
                        "No student scans received for this session.",
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(computedAttendance) { r ->
                            val statusColor = when (r.status) {
                                "PRESENT" -> Color(0xFF2E7D32)
                                "PARTIAL" -> Color(0xFFEF6C00)
                                else -> Color(0xFFC62828)
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    val emailDisplay = allStudentsList.find { it.id == r.user_id }?.email ?: r.user_id.take(8) + "..."
                                    Text(
                                        text = emailDisplay,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Scans: ${r.enter_count}", fontSize = 11.sp)
                                            Text("Match: ${r.score.toInt()}%", fontSize = 11.sp)
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = statusColor.copy(alpha = 0.15f),
                                                contentColor = statusColor,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = r.status,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            IconButton(
                                                onClick = {
                                                    selectedOverrideStudent = r
                                                    newStatusOverride = r.status
                                                    justificationOverride = ""
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Attendance",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Attendance Override Dialog
    selectedOverrideStudent?.let { r ->
        AlertDialog(
            onDismissRequest = { selectedOverrideStudent = null },
            title = { Text("Override Attendance Status") },
            text = {
                Column {
                    val emailDisplay = allStudentsList.find { it.id == r.user_id }?.email ?: r.user_id
                    Text("Student: $emailDisplay", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    Text("New Status:")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("PRESENT", "PARTIAL", "ABSENT").forEach { status ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = newStatusOverride == status,
                                    onClick = { newStatusOverride = status }
                                )
                                Text(status.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = justificationOverride,
                        onValueChange = { justificationOverride = it },
                        label = { Text("Justification / Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val session = selectedSession ?: return@Button
                        coroutineScope.launch {
                            try {
                                val client = ApiClient(serverUrl)
                                withContext(Dispatchers.IO) {
                                    client.api.overrideAttendance(
                                        "Bearer $authToken",
                                        session.id,
                                        r.user_id,
                                        newStatusOverride,
                                        justificationOverride
                                    )
                                }
                                selectedOverrideStudent = null
                                computeAttendanceForSession(session)
                            } catch (e: Exception) {
                                Log.e("SmartAttendance", "Override failed", e)
                            }
                        }
                    },
                    enabled = justificationOverride.isNotBlank()
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedOverrideStudent = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Manual Override Dialog (for unlisted students)
    if (showManualOverrideDialog) {
        AlertDialog(
            onDismissRequest = { showManualOverrideDialog = false },
            title = { Text("Add Manual Attendance Override") },
            text = {
                Column {
                    Text("Select Student:")
                    Spacer(Modifier.height(8.dp))

                    var expanded by remember { mutableStateOf(false) }
                    val currentStudentEmail = allStudentsList.find { it.id == selectedManualStudentId }?.email ?: "Choose a student..."

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(currentStudentEmail)
                                Icon(Icons.Default.ArrowDropDown, "Open dropdown")
                            }
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            allStudentsList.forEach { student ->
                                DropdownMenuItem(
                                    text = { Text(student.email) },
                                    onClick = {
                                        selectedManualStudentId = student.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text("Status:")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("PRESENT", "PARTIAL", "ABSENT").forEach { status ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = newManualStatus == status,
                                    onClick = { newManualStatus = status }
                                )
                                Text(status.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = justificationManual,
                        onValueChange = { justificationManual = it },
                        label = { Text("Justification / Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val session = selectedSession ?: return@Button
                        coroutineScope.launch {
                            try {
                                val client = ApiClient(serverUrl)
                                withContext(Dispatchers.IO) {
                                    client.api.overrideAttendance(
                                        "Bearer $authToken",
                                        session.id,
                                        selectedManualStudentId,
                                        newManualStatus,
                                        justificationManual
                                    )
                                }
                                showManualOverrideDialog = false
                                selectedManualStudentId = ""
                                justificationManual = ""
                                computeAttendanceForSession(session)
                            } catch (e: Exception) {
                                Log.e("SmartAttendance", "Manual override failed", e)
                            }
                        }
                    },
                    enabled = selectedManualStudentId.isNotBlank() && justificationManual.isNotBlank()
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualOverrideDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} hours ago"
        else -> "${diff / 86_400_000} days ago"
    }
}

private fun formatSessionTime(isoTimestamp: String): String {
    return try {
        // Parse ISO timestamp (e.g. 2026-07-09T12:45:00)
        // Extract just the HH:mm portion
        val cleanIso = isoTimestamp.replace("Z", "")
        val formats = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm")
        var date: Date? = null
        for (f in formats) {
            try {
                date = SimpleDateFormat(f, Locale.US).parse(cleanIso)
                break
            } catch (_: Exception) {}
        }
        if (date != null) {
            SimpleDateFormat("hh:mm a", Locale.US).format(date)
        } else {
            isoTimestamp
        }
    } catch (e: Exception) {
        isoTimestamp
    }
}

