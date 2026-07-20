@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.smartattendance.app.R

private val PesNavy = Color(0xFF1A237E)
private val PesGold = Color(0xFFC9A84C)
private val ScanGreen = Color(0xFF2E7D32)
private val ScanGreenLight = Color(0xFFE8F5E9)

@Composable
fun MainScreen(
    deviceId: String,
    serverUrl: String,
    sessionId: String,
    scanning: Boolean,
    lastSyncTime: Long?,
    connectionStatus: String,
    onToggleScanning: (Boolean) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onSessionIdChange: (String) -> Unit,
    onLogout: () -> Unit
) {
    val isConnected = connectionStatus == "Connected"
    var showSettings by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.pes_logo),
                            contentDescription = "PES University",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Smart Attendance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("PES University", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    // Connection badge
                    Surface(
                        color = if (isConnected) Color(0xFF2E7D32).copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) ScanGreen else MaterialTheme.colorScheme.error)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (isConnected) "Live" else "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isConnected) ScanGreen else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.Logout, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // --- Big Scan Status Card ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (scanning) ScanGreen else MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (scanning) 6.dp else 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (scanning) "Attendance Active" else "Attendance Paused",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (scanning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (scanning) "WiFi scanning every 60 seconds" else "Tap the switch to start scanning",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (scanning) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        if (scanning && lastSyncTime != null && lastSyncTime > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Last sync: ${formatTime(lastSyncTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(contentAlignment = Alignment.Center) {
                        if (scanning) {
                            // Pulsing ring behind icon
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = pulseAlpha * 0.2f))
                            )
                        }
                        Switch(
                            checked = scanning,
                            onCheckedChange = onToggleScanning,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color.White.copy(alpha = 0.3f),
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }

            // --- Device Info Card ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = PesNavy, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Device Info", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = PesNavy)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(label = "Device ID", value = if (deviceId.isNotBlank()) deviceId.take(16) + "…" else "Not registered")
                    InfoRow(label = "Session", value = if (sessionId.isNotBlank()) sessionId.take(20) + "…" else "Auto-detected")
                    InfoRow(label = "Status", value = if (scanning) "Active" else "Inactive", valueColor = if (scanning) ScanGreen else MaterialTheme.colorScheme.error)
                }
            }

            // --- Settings (collapsed by default) ---
            AnimatedVisibility(visible = showSettings) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Settings, contentDescription = null, tint = PesNavy, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Configuration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = PesNavy)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = onServerUrlChange,
                            label = { Text("Server URL") },
                            leadingIcon = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = !isConnected,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = sessionId,
                            onValueChange = onSessionIdChange,
                            label = { Text("Session ID (Optional)") },
                            leadingIcon = { Icon(Icons.Outlined.Tag, contentDescription = null) },
                            placeholder = { Text("Auto-detected from location", color = Color.LightGray) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // --- Info tip card ---
            if (!scanning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F0FF))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = PesNavy, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("How it works", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = PesNavy)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "When you enable scanning, your device will automatically scan nearby WiFi networks every 60 seconds. The system determines your classroom location and records attendance silently in the background.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF37474F)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor)
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
