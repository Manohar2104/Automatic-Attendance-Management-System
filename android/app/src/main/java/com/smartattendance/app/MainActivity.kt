package com.smartattendance.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

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
            var connectionStatus by remember { mutableStateOf("Checking...") }
            var lastSyncTime by remember { mutableStateOf(0L) }

            LaunchedEffect(Unit) {
                try {
                    isRegistered = prefs.isRegistered.first()
                    deviceId = prefs.deviceId.first()
                    serverUrl = prefs.serverUrl.first()
                    sessionId = prefs.sessionId.first()
                    lastSyncTime = prefs.lastSyncTime.first()
                    val savedScanning = prefs.isScanning.first()
                    if (isRegistered && savedScanning) {
                        if (hasLocationPermission()) {
                            scanning = true
                            startWiFiScanService()
                            PresenceSubmissionWorker.schedule(this@MainActivity)
                        } else {
                            prefs.setScanning(false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "LaunchedEffect: failed to load prefs", e)
                }
            }

            // Periodic connection check
            LaunchedEffect(serverUrl) {
                if (serverUrl.isNotBlank()) {
                    while (true) {
                        val connected = checkServerConnection(serverUrl)
                        connectionStatus = if (connected) "Connected" else "Disconnected"
                        delay(10_000)
                    }
                }
            }

            if (!isRegistered) {
                RegistrationScreen(
                    onRegister = { email, password ->
                        loading = true
                        error = null
                        lifecycleScope.launch {
                            try {
                                val hash = withContext(Dispatchers.IO) {
                                    deviceManager.getAndroidId()
                                }
                                if (hash.isBlank()) {
                                    error = "Could not get device ID. Check permissions."
                                    return@launch
                                }

                                val client = withContext(Dispatchers.IO) {
                                    ApiClient(serverUrl)
                                }
                                val token = try {
                                    withContext(Dispatchers.IO) {
                                        client.api.register(RegisterRequest(email, password))
                                    }.access_token
                                } catch (_: Exception) {
                                    withContext(Dispatchers.IO) {
                                        client.api.login(RegisterRequest(email, password))
                                    }.access_token
                                }

                                withContext(Dispatchers.IO) {
                                    client.api.registerDevice(
                                        "Bearer $token",
                                        DeviceRegisterRequest(hash)
                                    )
                                }
                                prefs.saveDeviceId(hash)
                                prefs.setRegistered(true)
                                deviceId = hash
                                isRegistered = true

                                if (hasLocationPermission()) {
                                    scanning = true
                                    prefs.setScanning(true)
                                    startWiFiScanService()
                                    PresenceSubmissionWorker.schedule(this@MainActivity)
                                }
                            } catch (e: java.net.ConnectException) {
                                Log.e(TAG, "Register: ConnectException", e)
                                error = "Cannot reach server at $serverUrl\nCheck that server is running and phone is on same WiFi"
                            } catch (e: java.net.SocketTimeoutException) {
                                Log.e(TAG, "Register: timeout", e)
                                error = "Connection timed out. Check server URL and WiFi."
                            } catch (e: java.net.UnknownHostException) {
                                Log.e(TAG, "Register: unknown host", e)
                                error = "Cannot find server. Check the URL."
                            } catch (e: Exception) {
                                Log.e(TAG, "Register: unexpected", e)
                                error = e.message ?: "Registration failed"
                            } catch (e: Throwable) {
                                Log.e(TAG, "Register: fatal", e)
                                error = "Unexpected error: ${e.message}"
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
                    lastSyncTime = lastSyncTime,
                    connectionStatus = connectionStatus,
                    onToggleScanning = { start ->
                        if (start) {
                            if (hasLocationPermission()) {
                                scanning = true
                                lifecycleScope.launch { prefs.setScanning(true) }
                                startWiFiScanService()
                                PresenceSubmissionWorker.schedule(this@MainActivity)
                            } else {
                                requestPermissions()
                            }
                        } else {
                            scanning = false
                            lifecycleScope.launch { prefs.setScanning(false) }
                            stopWiFiScanService()
                            WorkManager.getInstance(this@MainActivity).cancelUniqueWork("presence_submission")
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

    private suspend fun checkServerConnection(url: String): Boolean {
        return try {
            val client = ApiClient(url)
            val response = client.api.getHealth()
            response.status == "ok"
        } catch (e: Exception) {
            Log.d(TAG, "Connection check failed: ${e.message}")
            false
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
            Manifest.permission.INTERNET,
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

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "SmartAttendance"
    }
}
