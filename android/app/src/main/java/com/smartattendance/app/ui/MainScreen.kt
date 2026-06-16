package com.smartattendance.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    deviceId: String,
    serverUrl: String,
    sessionId: String,
    scanning: Boolean,
    onToggleScanning: (Boolean) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onSessionIdChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Smart Attendance", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Device Info", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text("ANDROID ID (SHA-256):", style = MaterialTheme.typography.labelSmall)
                Text(
                    deviceId.take(16) + "...",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(12.dp))

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
            label = { Text("Session ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("e.g. CS101-2026-06-16") }
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (scanning) "Scanning active" else "Scanning paused",
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = scanning,
                onCheckedChange = onToggleScanning
            )
        }

        Spacer(Modifier.height(16.dp))

        if (scanning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("WiFi scanning is active in the background.")
                    Text("Presence will be sent every 15 minutes.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
