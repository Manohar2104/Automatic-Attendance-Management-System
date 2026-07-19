package com.smartattendance.teacher.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState

import com.smartattendance.teacher.ui.components.InfoRow
import com.smartattendance.teacher.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailsScreen(

    viewModel: StudentDetailsViewModel,

    onBack: () -> Unit = {}

) {

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(

        topBar = {

            CenterAlignedTopAppBar(

                title = {

                    Text("Student Details")

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

        Column(

            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),

            verticalArrangement = Arrangement.spacedBy(12.dp)

        ) {

            Text(

                text = uiState.studentName,

                style = MaterialTheme.typography.headlineSmall,

                fontWeight = FontWeight.Bold

            )

            Text(

                text = uiState.usn,

                style = MaterialTheme.typography.bodyLarge

            )

            HorizontalDivider()

            InfoRow(

                "Attendance",

                ""

            )

            StatusChip(

                uiState.attendanceStatus.name.replace('_', ' ')

            )

            Spacer(Modifier.height(8.dp))

            InfoRow(

                "Confidence",

                "${uiState.confidence}%"

            )

            InfoRow(

                "Average RSSI",

                "${uiState.averageRssi} dBm"

            )

            InfoRow(

                "Packets Received",

                uiState.packetsReceived.toString()

            )

            InfoRow(

                "First Seen",

                uiState.firstSeen

            )

            InfoRow(

                "Last Seen",

                uiState.lastSeen

            )

            InfoRow(

                "Device ID",

                uiState.deviceId

            )

            HorizontalDivider()

            Text(

                "Manual Override",

                style = MaterialTheme.typography.titleMedium,

                fontWeight = FontWeight.Bold

            )

            OutlinedTextField(

                modifier = Modifier.fillMaxWidth(),

                value = uiState.overrideReason,

                onValueChange = viewModel::updateReason,

                label = {

                    Text("Reason")

                }

            )

            Button(

                modifier = Modifier.fillMaxWidth(),

                onClick = {

                    viewModel.markPresent()

                }

            ) {

                Text("Mark Present")

            }

            OutlinedButton(

                modifier = Modifier.fillMaxWidth(),

                onClick = {

                    viewModel.markAbsent()

                }

            ) {

                Text("Mark Absent")

            }

            OutlinedButton(

                modifier = Modifier.fillMaxWidth(),

                onClick = {

                    viewModel.markLate()

                }

            ) {

                Text("Mark Late")

            }

            Spacer(

                Modifier.height(8.dp)

            )

            Button(

                modifier = Modifier.fillMaxWidth(),

                onClick = {

                    viewModel.saveOverride()

                }

            ) {

                Text("Save Override")

            }

        }

    }

}