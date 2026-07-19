package com.smartattendance.teacher.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ActionButtons(

    onStartScanning: () -> Unit,

    onStopScanning: () -> Unit,

    onViewAttendance: () -> Unit,

    onSessionSummary: () -> Unit

) {

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onStartScanning
        ) {

            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text("Start Scanning")

        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onStopScanning
        ) {

            Icon(
                Icons.Default.Stop,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text("Stop Scanning")

        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onViewAttendance
        ) {

            Icon(
                Icons.Default.Visibility,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text("Live Attendance")

        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSessionSummary
        ) {

            Icon(
                Icons.Default.Assignment,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text("Session Summary")

        }

    }

}