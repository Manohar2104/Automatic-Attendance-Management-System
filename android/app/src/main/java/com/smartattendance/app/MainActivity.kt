package com.smartattendance.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.smartattendance.app.data.DeviceManager
import com.smartattendance.app.data.BiometricKeyManager
import com.smartattendance.app.data.PreferencesManager
import com.smartattendance.app.network.ApiClient
import com.smartattendance.app.network.DeviceRegisterRequest
import com.smartattendance.app.network.RegisterRequest
import com.smartattendance.app.network.SessionInfo
import com.smartattendance.app.network.AttendanceResult
import com.smartattendance.app.service.PresenceSubmissionWorker
import com.smartattendance.app.service.WiFiScanService
import com.smartattendance.app.ui.MainScreen
import com.smartattendance.app.ui.RegistrationScreen
import com.smartattendance.app.ui.FacultyScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

class MainActivity : FragmentActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var deviceManager: DeviceManager

    private val PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferencesManager(this)
        deviceManager = DeviceManager(this)

        requestAppPermissions()
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
            var totalScans by remember { mutableStateOf(0) }
            var validScans by remember { mutableStateOf(0) }

            var liveWifiScans by remember { mutableStateOf<List<String>>(emptyList()) }
            var liveBleScans by remember { mutableStateOf<List<String>>(emptyList()) }

            val context = androidx.compose.ui.platform.LocalContext.current
            DisposableEffect(scanning) {
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(c: android.content.Context?, intent: Intent?) {
                        intent?.let {
                            val wifi = it.getStringArrayExtra("bssids")?.toList() ?: emptyList()
                            val ble = it.getStringArrayExtra("ble_beacons")?.toList() ?: emptyList()
                            liveWifiScans = wifi
                            liveBleScans = ble
                        }
                    }
                }
                val filter = android.content.IntentFilter(WiFiScanService.SCAN_RESULT_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
                onDispose {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (e: Exception) {}
                }
            }

            // Faculty state variables
            var userRole by remember { mutableStateOf("STUDENT") }
            var accessToken by remember { mutableStateOf("") }
            var userEmail by remember { mutableStateOf("") }
            var sessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
            var loadingSessions by remember { mutableStateOf(false) }
            var selectedSessionId by remember { mutableStateOf<String?>(null) }
            var attendanceResults by remember { mutableStateOf<List<AttendanceResult>?>(null) }
            var loadingAttendance by remember { mutableStateOf(false) }

            // Helper function to fetch sessions for Faculty
            val fetchSessionsForFaculty = {
                loadingSessions = true
                selectedSessionId = null
                attendanceResults = null
                lifecycleScope.launch {
                    try {
                        val client = withContext(Dispatchers.IO) { ApiClient(serverUrl) }
                        val list = withContext(Dispatchers.IO) {
                            client.api.getSessions("Bearer $accessToken")
                        }
                        sessions = list
                    } catch (e: Exception) {
                        Log.e(TAG, "fetchSessions failed", e)
                    } finally {
                        loadingSessions = false
                    }
                }
            }

            // Helper function to fetch attendance summary
            val fetchAttendanceForSession: (String, String) -> Unit = { id, loc ->
                selectedSessionId = id
                loadingAttendance = true
                attendanceResults = null
                lifecycleScope.launch {
                    try {
                        val client = withContext(Dispatchers.IO) { ApiClient(serverUrl) }
                        val res = withContext(Dispatchers.IO) {
                            client.api.computeAttendance(id, loc)
                        }
                        attendanceResults = res.results
                    } catch (e: Exception) {
                        Log.e(TAG, "fetchAttendance failed", e)
                    } finally {
                        loadingAttendance = false
                    }
                }
            }

            // Helper function to logout
            val handleLogout = {
                BiometricKeyManager.deleteKey()
                stopWiFiScanService()
                WorkManager.getInstance(this@MainActivity).cancelUniqueWork("presence_submission")
                lifecycleScope.launch {
                    prefs.setRegistered(false)
                    prefs.saveAccessToken("")
                    prefs.saveUserRole("STUDENT")
                    prefs.setScanning(false)
                    isRegistered = false
                    accessToken = ""
                    userRole = "STUDENT"
                    userEmail = ""
                    selectedSessionId = null
                    sessions = emptyList()
                    attendanceResults = null
                }
            }

            // Auto-start scanning and presence worker for faculty
            LaunchedEffect(userRole, isRegistered) {
                if (isRegistered && userRole == "FACULTY") {
                    if (hasLocationPermission()) {
                        startWiFiScanService()
                        PresenceSubmissionWorker.schedule(this@MainActivity)
                    }
                }
            }

            LaunchedEffect(Unit) {
                lifecycleScope.launch {
                    prefs.totalScans.collect { totalScans = it }
                }
                lifecycleScope.launch {
                    prefs.validScans.collect { validScans = it }
                }
            }

            LaunchedEffect(Unit) {
                try {
                    isRegistered = prefs.isRegistered.first()
                    deviceId = prefs.deviceId.first()
                    serverUrl = prefs.serverUrl.first()
                    sessionId = prefs.sessionId.first()
                    lastSyncTime = prefs.lastSyncTime.first()
                    userRole = prefs.userRole.first()
                    accessToken = prefs.accessToken.first()

                    if (isRegistered && accessToken.isNotBlank()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val client = ApiClient(serverUrl)
                                val profile = client.api.getProfile("Bearer $accessToken")
                                userEmail = profile.email
                                userRole = profile.role
                                prefs.saveUserRole(profile.role)
                                prefs.saveUserId(profile.id)

                                if (profile.role == "FACULTY") {
                                    loadingSessions = true
                                    val list = client.api.getSessions("Bearer $accessToken")
                                    sessions = list
                                    loadingSessions = false
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Profile startup fetch failed", e)
                            }
                        }
                    }

                    scanning = false
                    prefs.setScanning(false)
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
                    onRegister = { email, password, role ->
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
                                        client.api.register(RegisterRequest(email, password, role))
                                    }.access_token
                                } catch (_: Exception) {
                                    withContext(Dispatchers.IO) {
                                        client.api.login(RegisterRequest(email, password, role))
                                    }.access_token
                                }

                                val profile = withContext(Dispatchers.IO) {
                                    client.api.getProfile("Bearer $token")
                                }

                                withContext(Dispatchers.IO) {
                                    client.api.registerDevice(
                                        "Bearer $token",
                                        DeviceRegisterRequest(hash)
                                    )
                                }

                                prefs.saveDeviceId(hash)
                                prefs.saveAccessToken(token)
                                prefs.saveUserRole(profile.role)
                                prefs.saveUserId(profile.id)
                                prefs.setRegistered(true)

                                deviceId = hash
                                accessToken = token
                                userRole = profile.role
                                userEmail = profile.email
                                isRegistered = true

                                scanning = false
                                prefs.setScanning(false)
                                
                                if (profile.role == "FACULTY") {
                                    // Fetch sessions immediately for Faculty
                                    loadingSessions = true
                                    val list = withContext(Dispatchers.IO) {
                                        client.api.getSessions("Bearer $token")
                                    }
                                    sessions = list
                                    loadingSessions = false
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
                            } finally {
                                loading = false
                            }
                        }
                    },
                    loading = loading,
                    error = error
                )
            } else {
                if (userRole == "FACULTY") {
                    FacultyScreen(
                        email = userEmail,
                        sessions = sessions,
                        loadingSessions = loadingSessions,
                        selectedSessionId = selectedSessionId,
                        attendanceResults = attendanceResults,
                        loadingAttendance = loadingAttendance,
                        onSelectSession = { id, loc -> fetchAttendanceForSession(id, loc) },
                        onRefreshSessions = { fetchSessionsForFaculty() },
                        onLogout = { handleLogout() },
                        onOverrideAttendance = { studentId, status, justification ->
                            val sId = selectedSessionId
                            if (sId != null) {
                                lifecycleScope.launch {
                                    try {
                                        val client = withContext(Dispatchers.IO) { ApiClient(serverUrl) }
                                        withContext(Dispatchers.IO) {
                                            client.api.overrideAttendance(
                                                "Bearer $accessToken",
                                                sId,
                                                studentId,
                                                status,
                                                justification
                                            )
                                        }
                                        val activeLoc = sessions.find { it.id == sId }?.location ?: ""
                                        fetchAttendanceForSession(sId, activeLoc)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Override attendance failed", e)
                                    }
                                }
                            }
                        }
                    )
                } else {
                    MainScreen(
                        deviceId = deviceId,
                        serverUrl = serverUrl,
                        sessionId = sessionId,
                        scanning = scanning,
                        lastSyncTime = lastSyncTime,
                        totalScans = totalScans,
                        validScans = validScans,
                        onResetScans = {
                            lifecycleScope.launch { prefs.resetScanCounts() }
                        },
                        connectionStatus = connectionStatus,
                        onToggleScanning = { start ->
                            if (start) {
                                if (hasLocationPermission()) {
                                    showBiometricPrompt {
                                        scanning = true
                                        lifecycleScope.launch { prefs.setScanning(true) }
                                        startWiFiScanService()
                                        PresenceSubmissionWorker.schedule(this@MainActivity)
                                    }
                                } else {
                                    requestAppPermissions()
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
                            if (scanning) {
                                scanning = false
                                lifecycleScope.launch { prefs.setScanning(false) }
                                stopWiFiScanService()
                                WorkManager.getInstance(this@MainActivity).cancelUniqueWork("presence_submission")
                            }
                        },
                        onLogout = { handleLogout() },
                        liveWifiScans = liveWifiScans,
                        liveBleScans = liveBleScans
                    )
                }
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

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
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

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val biometricManager = androidx.biometric.BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
        )
        if (canAuthenticate == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            val cipher = BiometricKeyManager.getCipher()
            if (cipher == null) {
                // Key was permanently invalidated because a new fingerprint was registered!
                BiometricKeyManager.deleteKey()
                android.app.AlertDialog.Builder(this)
                    .setTitle("Security Alert")
                    .setMessage("A new fingerprint was detected on this device. For security, you have been logged out. Please log in again to register your biometrics.")
                    .setPositiveButton("OK") { _: android.content.DialogInterface, _: Int ->
                        lifecycleScope.launch {
                            prefs.setRegistered(false)
                            prefs.saveAccessToken("")
                            prefs.saveUserRole("STUDENT")
                            prefs.setScanning(false)
                            recreate()
                        }
                    }
                    .setCancelable(false)
                    .show()
                return
            }

            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = androidx.biometric.BiometricPrompt(
                this,
                executor,
                object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }
                }
            )

            val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("Attendance Verification")
                .setSubtitle("Authenticate to start attendance tracking")
                .setNegativeButtonText("Cancel")
                .build()

            biometricPrompt.authenticate(promptInfo, androidx.biometric.BiometricPrompt.CryptoObject(cipher))
        } else {
            onSuccess()
        }
    }

    companion object {
        private const val TAG = "SmartAttendance"
    }
}
