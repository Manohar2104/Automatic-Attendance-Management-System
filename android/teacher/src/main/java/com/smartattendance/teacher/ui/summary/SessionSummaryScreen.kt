package com.smartattendance.teacher.ui.summary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.smartattendance.teacher.ui.components.InfoRow
import androidx.compose.foundation.lazy.items
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSummaryScreen(

    viewModel: SessionSummaryViewModel,

    onBack: () -> Unit = {},

    onFinish: () -> Unit = {}

) {

   val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSummary()
        viewModel.startAutoRefresh()
    }

    Scaffold(

        topBar = {

            CenterAlignedTopAppBar(

                title = {

                    Text("Session Summary")

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

                }

            )

        }

    ) { padding ->

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(

            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),

            contentPadding = PaddingValues(16.dp),

            verticalArrangement = Arrangement.spacedBy(16.dp)

        ) {

            uiState.errorMessage?.let { message ->
                item {
                    Card {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item {

                SessionInfoCard(uiState)

            }

            item {

                SummaryCard(uiState)

            }

            item {

                AttendancePercentageCard(uiState)

            }

            item {

    Text(

        text = "Attendance Records",

        style = MaterialTheme.typography.titleMedium,

        fontWeight = FontWeight.Bold

    )

}

items(

    uiState.attendanceRecords

) { record ->

    AttendanceRecordCard(record)

}

            if (uiState.attendanceRecords.isEmpty()) {
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

    Row(

        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement = Arrangement.spacedBy(12.dp)

    ) {

        Button(

            modifier = Modifier.weight(1f),

            onClick = {

                viewModel.exportPdf()

            }

        ) {

            Text("Export PDF")

        }

        Button(

            modifier = Modifier.weight(1f),

            onClick = {

                viewModel.exportCsv()

            }

        ) {

            Text("Export CSV")

        }

    }

}
item {

    Spacer(

        Modifier.height(8.dp)

    )

    Button(

        modifier = Modifier.fillMaxWidth(),

        onClick = {

            viewModel.finishSession()

            onFinish()

        }

    ) {

        Text("Finish Session")

    }

}
        }

    }

}

@Composable
private fun SessionInfoCard(

    uiState: SessionSummaryUiState

) {

    Card {

        Column(

            modifier = Modifier.padding(16.dp)

        ) {

            Text(

                text = "Session Information",

                style = MaterialTheme.typography.titleMedium,

                fontWeight = FontWeight.Bold

            )

            Spacer(Modifier.height(12.dp))

            InfoRow(

                "Course",

                uiState.course

            )

            InfoRow(

                "Faculty",

                uiState.faculty

            )

            InfoRow(

                "Room",

                uiState.room

            )

            InfoRow(

                "Duration",

                uiState.duration

            )

        }

    }

}

@Composable
private fun SummaryCard(

    uiState: SessionSummaryUiState

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

            Spacer(Modifier.height(12.dp))

            InfoRow(

                "Registered Devices",

                uiState.registeredDevices.toString()

            )

            InfoRow(

                "Present",

                uiState.present.toString()

            )

            InfoRow(

                "Students Seen",

                uiState.studentsSeen.toString()

            )

            InfoRow(

                "Missing",

                uiState.missing.toString()

            )

            InfoRow(

                "Total Packets",

                uiState.totalPackets.toString()

            )

            InfoRow(

                "Total Observations",

                uiState.totalObservations.toString()

            )

            InfoRow(

                "Start Time",

                uiState.startTime

            )

            InfoRow(

                "End Time",

                uiState.endTime

            )

        }

    }

}

@Composable
private fun AttendancePercentageCard(

    uiState: SessionSummaryUiState

) {

    Card {

        Column(

            modifier = Modifier.padding(16.dp)

        ) {

            Text(

                "Attendance Percentage",

                style = MaterialTheme.typography.titleMedium,

                fontWeight = FontWeight.Bold

            )

            Spacer(

                Modifier.height(12.dp)

            )

            LinearProgressIndicator(

                progress = {

                    uiState.attendancePercentage / 100f

                },

                modifier = Modifier.fillMaxWidth()

            )

            Spacer(

                Modifier.height(12.dp)

            )

            Text(

                "${uiState.attendancePercentage}%",

                style = MaterialTheme.typography.headlineSmall,

                fontWeight = FontWeight.Bold

            )

        }

    }

}

@Composable
private fun AttendanceRecordCard(

    record: AttendanceRecordUi

) {

    Card(

        modifier = Modifier.fillMaxWidth()

    ) {

        Column(

            modifier = Modifier.padding(16.dp)

        ) {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween

            ) {

                Column(

                    modifier = Modifier.weight(1f)

                ) {

                    Text(

                        record.studentName,

                        style = MaterialTheme.typography.titleMedium,

                        fontWeight = FontWeight.Bold

                    )

                    Text(

                        record.usn

                    )

                }

                AttendanceStatusChip(

                    record.status

                )

            }

            Spacer(

                Modifier.height(10.dp)

            )

            InfoRow(

                "Confidence",

                "${record.confidence}%"

            )

            if (record.manuallyModified) {

                Spacer(

                    Modifier.height(8.dp)

                )

                Text(

                    text = "Manual Override",

                    color = MaterialTheme.colorScheme.primary,

                    style = MaterialTheme.typography.labelMedium

                )

            }

        }

    }

}

@Composable
private fun AttendanceStatusChip(

    status: AttendanceStatus

) {

    AssistChip(

        onClick = {},

        enabled = false,

        label = {

            Text(

                status.name.replace('_', ' ')

            )

        }

    )

}