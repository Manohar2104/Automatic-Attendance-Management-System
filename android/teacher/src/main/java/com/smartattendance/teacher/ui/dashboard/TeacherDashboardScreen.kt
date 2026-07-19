package com.smartattendance.teacher.ui.dashboard

import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smartattendance.shared.ble.BlePermissions
import com.smartattendance.teacher.ui.components.ActionButtons
import com.smartattendance.teacher.ui.components.ConnectionCard
import com.smartattendance.teacher.ui.components.MetricsCard
import com.smartattendance.teacher.ui.components.RecentActivityCard
import com.smartattendance.teacher.ui.components.ScannerCard
import com.smartattendance.teacher.ui.components.SessionCard
import com.smartattendance.teacher.ui.components.TeacherTopBar

@Composable
fun TeacherDashboardScreen(

    viewModel: DashboardViewModel,

    onViewAttendance: () -> Unit = {},

    onViewSummary: () -> Unit = {},

    onLogout: () -> Unit = {}

) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantMap ->
        Log.d(
            "TeacherDashboardScreen",
            "Scanner permission result ->\n${BlePermissions.scannerPermissionReport(context)}",
        )
        val allGranted = BlePermissions.requiredScannerPermissions().all { permission ->
            grantMap[permission] == true || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            viewModel.startScanning()
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(

        topBar = {

            TeacherTopBar(

                onRefresh = {

                    viewModel.refreshDashboard()

                },

                onLogout = {

                    onLogout()

                }

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
                    .fillMaxSize()
                    .padding(16.dp),

                verticalArrangement = Arrangement.spacedBy(16.dp)

            ) {

               uiState.errorMessage?.let { error ->

    item {

        Card(

            colors = CardDefaults.cardColors(

                containerColor = MaterialTheme.colorScheme.errorContainer

            )

        ) {

            Text(

                text = error,

                modifier = Modifier.padding(16.dp),

                color = MaterialTheme.colorScheme.onErrorContainer

            )

        }

    }

}

                item {

                    ConnectionCard(

                        connected = uiState.serverConnected

                    )

                }

                item {

                    SessionCard(

                        uiState = uiState

                    )

                }
                                item {

                    MetricsCard(

                        uiState = uiState

                    )

                }

                item {

                    ScannerCard(

                        uiState = uiState

                    )

                }

                item {

                    ActionButtons(

                        onStartScanning = {
                            val missingPermissions = BlePermissions.missingScannerPermissions(context)
                            if (missingPermissions.isNotEmpty()) {
                                Log.w(
                                    "TeacherDashboardScreen",
                                    "Requesting scanner permissions ->\n${BlePermissions.scannerPermissionReport(context)}",
                                )
                                permissionLauncher.launch(missingPermissions.toTypedArray())
                            } else {
                                viewModel.startScanning()
                            }

                        },

                        onStopScanning = {

                            viewModel.stopScanning()

                        },

                        onViewAttendance = onViewAttendance,

                        onSessionSummary = onViewSummary

                    )

                }

                item {

                    RecentActivityCard(

                        activities = emptyList()

                    )

                }
                            }

        }

    }

}