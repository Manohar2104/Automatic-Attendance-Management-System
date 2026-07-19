package com.smartattendance.teacher.ui.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextOverflow

private data class MetricItem(
    val label: String,
    val value: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveAttendanceScreen(

    viewModel: LiveAttendanceViewModel,

    onBack: () -> Unit = {},

    onStudentDetails: (StudentAttendanceUi) -> Unit = {},

    onFinishSession: () -> Unit = {}

) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val metrics = remember(uiState) {
        listOf(
            MetricItem("Registered Devices", uiState.registeredDevices.toString()),
            MetricItem("Students Seen", uiState.studentsSeen.toString()),
            MetricItem("Packets Received", uiState.packetsReceived.toString()),
            MetricItem("Packets Accepted", uiState.packetsAccepted.toString()),
            MetricItem("Cooldown", "${uiState.cooldownIntervalMinutes} min"),
            MetricItem("Last Packet", uiState.lastPacketReceivedAt),
        )
    }

    Scaffold(

        topBar = {

            CenterAlignedTopAppBar(

                title = {

                    Text("Live Attendance")

                },

                navigationIcon = {

                    IconButton(

                        onClick = onBack

                    ) {

                        Icon(

                            Icons.Default.ArrowBack,

                            contentDescription = null

                        )

                    }

                },

                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()

            )

        }

    ) { padding ->

        if (uiState.isLoading) {

            Box(

                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),

                contentAlignment = Alignment.Center

            ) {

                CircularProgressIndicator()

            }

        } else {

            LazyColumn(

                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),

                contentPadding = PaddingValues(16.dp),

                verticalArrangement = Arrangement.spacedBy(16.dp)

            ) {

                uiState.errorMessage?.let { error ->

                    item {

                        Card(

                            colors = CardDefaults.cardColors(

                                containerColor =
                                    MaterialTheme.colorScheme.errorContainer

                            )

                        ) {

                            Text(

                                text = error,

                                modifier = Modifier.padding(16.dp),

                                color =
                                    MaterialTheme.colorScheme.onErrorContainer

                            )

                        }

                    }

                }

                item {

                    SessionHeader(uiState)

                }

                item {

                    SessionMetrics(uiState, metrics)

                }

                item {

                    AttendanceSearchBar(

                        query = uiState.searchQuery,

                        onQueryChange = viewModel::updateSearch

                    )

                }

                item {

                    AttendanceFilterChips(

                        selected = uiState.selectedFilter,

                        onSelected = viewModel::updateFilter

                    )

                }

                items(

                    uiState.students

                ) { student ->

                    StudentRow(

                        student = student,

                        onDetails = onStudentDetails,

                        onToggleAttendance = {

                            if (student.status == AttendanceStatus.PRESENT) {

                                viewModel.markAbsent(

                                    student.studentId

                                )

                            } else {

                                viewModel.markPresent(

                                    student.studentId

                                )

                            }

                        }

                    )

                }

                if (uiState.students.isEmpty() && uiState.errorMessage == null) {
                    item {
                        Card {
                            Text(
                                text = "No observations yet",
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                item {

                    Spacer(

                        Modifier.height(12.dp)

                    )

                    Button(

                        modifier = Modifier.fillMaxWidth(),

                        onClick = onFinishSession

                    ) {

                        Icon(

                            Icons.Default.Done,

                            contentDescription = null

                        )

                        Spacer(

                            Modifier.width(8.dp)

                        )

                        Text(

                            "Finish Session"

                        )

                    }

                }
                            }

        }

    }

}

@Composable
private fun SessionHeader(

    uiState: LiveAttendanceUiState

) {

    Card {

        Column(

            modifier = Modifier.padding(16.dp)

        ) {

            Text(

                text = uiState.course,

                style = MaterialTheme.typography.titleLarge,

                fontWeight = FontWeight.Bold

            )

            Spacer(

                Modifier.height(8.dp)

            )

            Text(

                text = "Teacher: ${uiState.faculty}",

                maxLines = 1,

                overflow = TextOverflow.Ellipsis

            )

            Text(

                "Room: ${uiState.room}"

            )

            Text(

                "Session: ${uiState.sessionStatus}"

            )

            Text(

                "Started: ${uiState.sessionStartedAt}"

            )

            Text(

                "Duration: ${uiState.sessionDuration}"

            )

        }

    }

}

@Composable
private fun AttendanceSummary(

    uiState: LiveAttendanceUiState

) {

    Card {

        Column(

            modifier = Modifier.padding(16.dp)

        ) {

            Text(

                "Attendance Summary",

                style = MaterialTheme.typography.titleMedium,

                fontWeight = FontWeight.Bold

            )

            Spacer(

                Modifier.height(12.dp)

            )

            SummaryRow(

                "Present",

                uiState.presentCount.toString()

            )

            SummaryRow(

                "Absent",

                uiState.absentCount.toString()

            )

            SummaryRow(

                "Detected",

                uiState.detectedCount.toString()

            )

            SummaryRow(

                "Active Session",

                if (uiState.sessionActive) "YES" else "NO"

            )

        }

    }

}

@Composable
private fun SessionMetrics(
    uiState: LiveAttendanceUiState,
    metrics: List<MetricItem>,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Session Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            metrics.forEach { metric ->
                SummaryRow(metric.label, metric.value)
            }
            Text(
                text = "Summary: ${uiState.presentCount} present, ${uiState.absentCount} absent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryRow(

    label: String,

    value: String

) {

    androidx.compose.foundation.layout.Row(

        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement = Arrangement.SpaceBetween

    ) {

        Text(

            label,

            fontWeight = FontWeight.SemiBold

        )

        Text(value)

    }

    HorizontalDivider(

        modifier = Modifier.padding(vertical = 8.dp)

    )

}