package com.smartattendance.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    onSessionIdChange: (String) -> Unit
) {
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
            Text("Smart Attendance", style = MaterialTheme.typography.headlineMedium)
            Icon(
                imageVector = if (connectionStatus == "Connected") Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = "Connection status",
                tint = if (connectionStatus == "Connected") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(8.dp))

        // Connection status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Status",
                tint = if (connectionStatus == "Connected") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Server: $connectionStatus",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(4.dp))

        // Last sync
        if (lastSyncTime != null && lastSyncTime > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Last sync",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Last sync: ${formatTime(lastSyncTime)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        // Device Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Device Info", style = MaterialTheme.typography.titleMedium)
                Divider(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Device Fingerprint (SHA-256)", style = MaterialTheme.typography.labelMedium)
                    Text(
                        deviceId.take(16) + "...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Status", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (scanning) "Active" else "Inactive",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (scanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Server URL
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = connectionStatus != "Connected"
        )
        Spacer(Modifier.height(8.dp))

        // Session ID
        OutlinedTextField(
            value = sessionId,
            onValueChange = onSessionIdChange,
            label = { Text("Session ID (Optional for Auto-Tracking)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("e.g. CS101-2026-06-16") },
            isError = false
        )
        Spacer(Modifier.height(16.dp))

        // Scanning Toggle
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
                        Text("WiFi Scanning", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (scanning) "Actively scanning for WiFi networks" else "Scanning paused",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = scanning,
                        onCheckedChange = onToggleScanning,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Status Card when scanning
        if (scanning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = "Scanning",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Scanning Active", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("• WiFi scanning runs every 30 seconds in foreground", style = MaterialTheme.typography.bodySmall)
                    Text("• Presence submitted every 15 minutes", style = MaterialTheme.typography.bodySmall)
                    Text("• Tap switch above to pause", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
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
