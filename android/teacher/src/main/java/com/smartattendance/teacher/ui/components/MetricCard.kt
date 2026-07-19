package com.smartattendance.teacher.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartattendance.teacher.ui.dashboard.TeacherDashboardUiState

@Composable
fun MetricsCard(
    uiState: TeacherDashboardUiState
) {

    Card {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = "Attendance Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            MetricRow(
                icon = {
                    Icon(
                        Icons.Default.Devices,
                        contentDescription = null
                    )
                },
                title = "Registered Devices",
                value = uiState.registeredDevices.toString()
            )

            Spacer(modifier = Modifier.height(12.dp))

            MetricRow(
                icon = {
                    Icon(
                        Icons.Default.Groups,
                        contentDescription = null
                    )
                },
                title = "Students Seen",
                value = uiState.studentsSeen.toString()
            )

            Spacer(modifier = Modifier.height(12.dp))

            MetricRow(
                icon = {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null
                    )
                },
                title = "Packets Received",
                value = uiState.packetsReceived.toString()
            )

        }

    }

}

@Composable
private fun MetricRow(
    icon: @Composable () -> Unit,
    title: String,
    value: String
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        icon()

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = title,
                fontWeight = FontWeight.SemiBold
            )

        }

        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

    }

}