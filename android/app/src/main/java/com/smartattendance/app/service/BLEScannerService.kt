package com.smartattendance.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartattendance.app.MainActivity
import com.smartattendance.app.SmartAttendanceApp
import com.smartattendance.app.ble.BleConstants
import com.smartattendance.app.ble.BlePermissions
import com.smartattendance.app.ble.BleScannerConstants
import com.smartattendance.app.ble.ObservationProcessor
import com.smartattendance.app.ble.RegisteredDeviceEntry
import com.smartattendance.app.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray

class BLEScannerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var observationProcessor: ObservationProcessor? = null
    private var scanCallback: ScanCallback? = null
    private var scanRetryJob: Job? = null
    private var shouldRun = false
    private var scannerInitialized = false
    private var currentSessionId: String? = null

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    if (shouldRun) {
                        serviceScope.launch { startScanningIfReady() }
                    }
                }

                BluetoothAdapter.STATE_OFF -> {
                    stopCurrentScan()
                    updateNotification("Bluetooth is off")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        createNotificationChannel()
        registerBluetoothReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BleScannerConstants.ACTION_STOP_SCANNING -> {
                stopScanningSession()
                return START_NOT_STICKY
            }

            BleScannerConstants.ACTION_START_SCANNING, null -> {
                shouldRun = true
                startAsForeground("BLE scanning starting")
                serviceScope.launch {
                    initializeSession(intent)
                    startScanningIfReady()
                }
            }
        }

        return START_STICKY
    }

    private suspend fun initializeSession(intent: Intent?) {
        if (scannerInitialized) return

        val sessionId = intent?.getStringExtra(BleScannerConstants.EXTRA_SESSION_ID)
        val teacherId = intent?.getStringExtra(BleScannerConstants.EXTRA_TEACHER_ID)
        val authToken = intent?.getStringExtra(BleScannerConstants.EXTRA_AUTH_TOKEN)
        currentSessionId = sessionId

        val serverUrlOverride = intent?.getStringExtra("server_url")
        val preferencesManager = PreferencesManager(applicationContext)
        val serverUrl = serverUrlOverride ?: preferencesManager.serverUrl.first()

        if (sessionId.isNullOrBlank() || teacherId.isNullOrBlank() || authToken.isNullOrBlank()) {
            Log.w(TAG, "Missing BLE scanner session context; stopping service")
            stopScanningSession()
            return
        }

        observationProcessor = ObservationProcessor(
            serverUrl = serverUrl,
            authToken = authToken,
            teacherId = teacherId,
            sessionId = sessionId,
        )

        val registeredDevicesJson = intent?.getStringExtra(BleScannerConstants.EXTRA_REGISTERED_DEVICES_JSON)
        if (!registeredDevicesJson.isNullOrBlank()) {
            observationProcessor?.replaceRegisteredDevices(parseRegisteredDevices(registeredDevicesJson))
        }

        observationProcessor?.start()
        scannerInitialized = true
    }

    private suspend fun startScanningIfReady() {
        if (!shouldRun) return

        if (!scannerInitialized) {
            updateNotification("BLE scanner waiting for session context")
            return
        }

        if (!BlePermissions.hasScannerPermissions(this)) {
            Log.w(TAG, "Missing BLE scan permissions; stopping scanner")
            stopScanningSession()
            return
        }

        Log.d(TAG, "Scanner permissions granted\n${BlePermissions.scannerPermissionReport(this)}")

        if (!BlePermissions.isBluetoothEnabled(this)) {
            stopCurrentScan()
            updateNotification("Bluetooth is off")
            return
        }

        Log.d(TAG, "Bluetooth enabled=true")

        startForegroundScan()
    }

    private fun startForegroundScan() {
        val scanner = bluetoothLeScanner
        if (scanner == null) {
            updateNotification("BLE scanner unavailable")
            scheduleScanRetry()
            return
        }

        stopCurrentScan()

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(BleConstants.ADVERTISEMENT_PARCEL_UUID)
                .build(),
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        Log.d(TAG, "Calling startForeground() with type=connectedDevice")

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult?) {
                if (result == null) return
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<android.bluetooth.le.ScanResult>) {
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed with code=$errorCode")
                updateNotification("BLE scan failed")
                scheduleScanRetry()
            }
        }

        try {
            Log.d(TAG, "Calling BluetoothLeScanner.startScan()")
            scanner.startScan(filters, settings, scanCallback)
            updateNotification("BLE scanning active")
            Log.i(TAG, "Scanner started successfully session=${currentSessionId ?: "unknown"}")
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Missing permission to start BLE scanning", securityException)
            stopScanningSession()
        } catch (exception: Exception) {
            Log.e(TAG, "Unexpected BLE scanner start failure", exception)
            scheduleScanRetry()
        }
    }

    private fun handleScanResult(result: android.bluetooth.le.ScanResult) {
        serviceScope.launch {
            observationProcessor?.processScanResult(result)
        }
    }

    private fun scheduleScanRetry() {
        scanRetryJob?.cancel()
        scanRetryJob = serviceScope.launch {
            delay(BleScannerConstants.SCAN_FAILURE_RETRY_MS)
            if (shouldRun) {
                startScanningIfReady()
            }
        }
    }

    private fun stopCurrentScan() {
        try {
            scanCallback?.let { callback ->
                bluetoothLeScanner?.stopScan(callback)
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to stop BLE scan cleanly", exception)
        } finally {
            scanCallback = null
        }
    }

    private fun stopScanningSession() {
        shouldRun = false
        scannerInitialized = false
        scanRetryJob?.cancel()
        scanRetryJob = null
        stopCurrentScan()
        val processor = observationProcessor
        serviceScope.launch { processor?.stop() }
        observationProcessor = null
        stopForegroundCompat()
        stopSelf()
    }

    private fun parseRegisteredDevices(json: String): List<RegisteredDeviceEntry> {
        return try {
            if (json.length > BleScannerConstants.MAX_REGISTRATION_JSON_LENGTH) {
                emptyList()
            } else {
                val array = JSONArray(json)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val studentId = item.optString("studentId").takeIf { it.isNotBlank() } ?: continue
                        val anonymousDeviceIdHex = item.optString("anonymousDeviceIdHex").takeIf { it.isNotBlank() } ?: continue
                        val publicIdentifier = item.optString("publicIdentifier").takeIf { it.isNotBlank() }
                        add(
                            RegisteredDeviceEntry(
                                studentId = studentId,
                                anonymousDeviceIdHex = anonymousDeviceIdHex,
                                publicIdentifier = publicIdentifier,
                            )
                        )
                    }
                }
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to parse registered BLE devices", exception)
            emptyList()
        }
    }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(bluetoothStateReceiver, filter)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SmartAttendanceApp.BLE_NOTIFICATION_CHANNEL_ID,
                "BLE Attendance",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when BLE scanning is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startAsForeground(contentText: String) {
        val notification = createNotification(contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                BleScannerConstants.BLE_SCAN_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(BleScannerConstants.BLE_SCAN_NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, SmartAttendanceApp.BLE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("BLE Scanning")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(BleScannerConstants.BLE_SCAN_NOTIFICATION_ID, createNotification(contentText))
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        stopCurrentScan()
        scanRetryJob?.cancel()
        scanRetryJob = null
        runCatching { unregisterReceiver(bluetoothStateReceiver) }
        val processor = observationProcessor
        serviceScope.launch { processor?.stop() }
        observationProcessor = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BLEScannerService"
    }
}
