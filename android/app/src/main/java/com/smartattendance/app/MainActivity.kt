package com.smartattendance.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.smartattendance.app.data.DeviceManager
import com.smartattendance.app.data.PreferencesManager
import com.smartattendance.app.network.ApiClient
import com.smartattendance.app.network.DeviceRegisterRequest
import com.smartattendance.app.network.RegisterRequest
import com.smartattendance.app.service.PresenceSubmissionWorker
import com.smartattendance.app.service.WiFiScanService
import com.smartattendance.app.ui.MainScreen
import com.smartattendance.app.ui.RegistrationScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var deviceManager: DeviceManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferencesManager(this)
        deviceManager = DeviceManager(this)

        requestPermissions()
        checkAndStopStaleWork()

        setContent {
            var isRegistered by remember { mutableStateOf(false) }
            var loading by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            var deviceId by remember { mutableStateOf("") }
            var serverUrl by remember { mutableStateOf(PreferencesManager.DEFAULT_SERVER_URL) }
            var sessionId by remember { mutableStateOf("") }
            var scanning by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                isRegistered = prefs.isRegistered.first()
                deviceId = prefs.deviceId.first()
                serverUrl = prefs.serverUrl.first()
                sessionId = prefs.sessionId.first()
            }

            if (!isRegistered) {
                RegistrationScreen(
                    onRegister = { email, password ->
                        loading = true
                        error = null
                        lifecycleScope.launch {
                            try {
                                val client = ApiClient(serverUrl)
                                val token = try {
                                    client.api.register(RegisterRequest(email, password)).access_token
                                } catch (_: Exception) {
                                    client.api.login(RegisterRequest(email, password)).access_token
                                }

                                val hash = deviceManager.getAndroidId()
                                client.api.registerDevice(
                                    "Bearer $token",
                                    DeviceRegisterRequest(hash)
                                )
                                prefs.saveDeviceId(hash)
                                prefs.setRegistered(true)
                                deviceId = hash
                                isRegistered = true

                                PresenceSubmissionWorker.schedule(this@MainActivity)
                            } catch (e: Exception) {
                                error = e.message ?: "Registration failed"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    loading = loading,
                    error = error
                )
            } else {
                MainScreen(
                    deviceId = deviceId,
                    serverUrl = serverUrl,
                    sessionId = sessionId,
                    scanning = scanning,
                    onToggleScanning = { start ->
                        scanning = start
                        if (start) {
                            startWiFiScanService()
                        } else {
                            stopWiFiScanService()
                        }
                    },
                    onServerUrlChange = { url ->
                        serverUrl = url
                        lifecycleScope.launch { prefs.saveServerUrl(url) }
                    },
                    onSessionIdChange = { sid ->
                        sessionId = sid
                        lifecycleScope.launch { prefs.saveSessionId(sid) }
                    }
                )
            }
        }
    }

    private fun startWiFiScanService() {
        val intent = Intent(this, WiFiScanService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopWiFiScanService() {
        stopService(Intent(this, WiFiScanService::class.java))
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun checkAndStopStaleWork() {
        WorkManager.getInstance(this).cancelUniqueWork("presence_submission")
    }
}
