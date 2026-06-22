package com.automatic.attendance.student.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.automatic.attendance.student.storage.SecureTokenStorageImpl
import com.automatic.attendance.student.ui.navigation.AppNavHost
import com.automatic.attendance.student.ui.theme.AttendanceTheme
import com.automatic.attendance.student.ui.navigation.Routes

class MainActivity : ComponentActivity() {

    private val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled, app can continue
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestLocationPermissionsIfNeeded()

        val storage = SecureTokenStorageImpl(this)
        val isLoggedIn = storage.getAccessToken() != null

        setContent {
            AttendanceTheme {
                AppNavHost(
                    start = if (isLoggedIn) "dashboard" else "login"
                )
            }
        }
    }

    private fun requestLocationPermissionsIfNeeded() {
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasNearbyWifiPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!hasLocationPermission || !hasNearbyWifiPermission) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        }
    }
}