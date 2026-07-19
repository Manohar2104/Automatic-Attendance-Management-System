package com.smartattendance.teacher.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartattendance.teacher.ui.dashboard.TeacherDashboardUiState

@Composable
fun SessionCard(
    uiState: TeacherDashboardUiState
) {

    Card {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = "Current Session",

                style = MaterialTheme.typography.titleMedium,

                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            InfoRow(
                "Course",
                uiState.course
            )

            Spacer(modifier = Modifier.height(8.dp))

            InfoRow(
                "Faculty",
                uiState.faculty
            )

            Spacer(modifier = Modifier.height(8.dp))

            InfoRow(
                "Room",
                uiState.room
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    "Status",
                    fontWeight = FontWeight.SemiBold
                )

                StatusChip(
                    uiState.sessionStatus
                )

            }

        }

    }

}