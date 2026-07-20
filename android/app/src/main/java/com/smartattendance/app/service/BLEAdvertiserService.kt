package com.smartattendance.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartattendance.app.MainActivity
import com.smartattendance.app.SmartAttendanceApp
import com.smartattendance.app.ble.AdvertisementGenerator
import com.smartattendance.app.ble.BleConstants
import com.smartattendance.app.ble.BlePermissions
import com.smartattendance.app.ble.BleRegistrationManager
import com.smartattendance.app.ble.RollingTokenManager
import com.smartattendance.app.data.PreferencesManager
import com.smartattendance.app.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BLEAdvertiserService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tokenManager by lazy { RollingTokenManager() }
    private val registrationManager by lazy { BleRegistrationManager(applicationContext) }
    private val preferencesManager by lazy { PreferencesManager(applicationContext) }
    private val advertisementGenerator by lazy { AdvertisementGenerator(registrationManager, tokenManager) }
    private val isAdvertising = AtomicBoolean(false)
    private val advertisingCycleMutex = Mutex()

    private val retryDelaysMs = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L)

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var advertisingLoop: Job? = null
    private var retryJob: Job? = null
    private var currentSessionId: String? = null
    private var shouldRun = false
    private var retryAttemptIndex = 0

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    Log.d(TAG, "Bluetooth state changed to ON")
                    if (shouldRun) {
                        serviceScope.launch {
                            delay(1_000L)
                            requestAdvertisingCycleRefresh()
                        }
                    }
                }

                BluetoothAdapter.STATE_OFF -> {
                    Log.w(TAG, "Bluetooth OFF")
                    stopCurrentAdvertising("Bluetooth OFF")
                    updateNotification("Bluetooth is off")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        refreshBluetoothState()
        createNotificationChannel()
        registerBluetoothReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BleConstants.ACTION_STOP_ADVERTISING -> {
                stopAdvertisingSession()
                return START_NOT_STICKY
            }

            BleConstants.ACTION_START_ADVERTISING, null -> {
                currentSessionId = intent?.getStringExtra(BleConstants.EXTRA_SESSION_ID)
                    ?: runBlockingCurrentSessionId()
                shouldRun = true
                retryAttemptIndex = 0
                startAsForeground()
                requestAdvertisingCycleRefresh()
            }
        }

        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = createNotification("BLE advertising starting")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                BleConstants.BLE_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(BleConstants.BLE_NOTIFICATION_ID, notification)
        }
    }

    private suspend fun refreshAdvertisingCycle() {
        advertisingCycleMutex.withLock {
            if (!shouldRun) return

            refreshBluetoothState()

            if (!isCurrentSessionActive()) {
                Log.i(TAG, "No active BLE session; advertising stays idle")
                stopCurrentAdvertising("No active BLE session")
                updateNotification("Waiting for active session")
                serviceScope.launch {
                    delay(30_000L)
                    if (shouldRun) {
                        requestAdvertisingCycleRefresh()
                    }
                }
                return
            }

            val payload = advertisementGenerator.generatePayload()
            runBleDiagnostics(payload)

            if (!validateAdvertiserPermissions()) {
                stopAdvertisingSession()
                return
            }

            if (!BlePermissions.isBluetoothEnabled(this)) {
                Log.w(TAG, "Reason: Bluetooth OFF")
                stopCurrentAdvertising("Bluetooth OFF")
                updateNotification("Bluetooth disabled")
                return
            }

            if (!ensureBleRegistration()) {
                updateNotification("BLE device registration failed")
                stopAdvertisingSession()
                return
            }

            if (payload == null) {
                Log.w(TAG, "Reason: Advertiser payload could not be generated")
                stopAdvertisingSession()
                return
            }

            startAdvertising(payload)
        }
    }

    private fun requestAdvertisingCycleRefresh(delayMs: Long = 0L) {
        serviceScope.launch {
            if (delayMs > 0L) {
                delay(delayMs)
            }
            refreshAdvertisingCycle()
        }
    }

    private suspend fun ensureBleRegistration(): Boolean {
        if (preferencesManager.isBleRegistered.first()) {
            return true
        }

        val serverUrl = preferencesManager.serverUrl.first().trim()
        val authToken = preferencesManager.accessToken.first().trim()
        if (serverUrl.isBlank() || authToken.isBlank()) {
            Log.w(TAG, "Reason: Missing server URL or access token for BLE registration")
            return false
        }

        return try {
            registrationManager.registerBleDevice(serverUrl, authToken)
        } catch (exception: Exception) {
            Log.e(TAG, "BLE device registration failed", exception)
            false
        }
    }

    private fun startAdvertising(payload: ByteArray) {
        val advertiser = resolveAdvertiser()
        if (advertiser == null) {
            Log.w(TAG, "Reason: Advertiser not initialized yet or unsupported")
            scheduleRetry("BluetoothLeAdvertiser is null")
            return
        }

        stopCurrentAdvertising("Restart advertising with fresh payload")

        val advertisingMode = AdvertiseSettings.ADVERTISE_MODE_BALANCED
        val txPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(advertisingMode)
            .setTxPowerLevel(txPowerLevel)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeTxPowerLevel(false)
            .setIncludeDeviceName(false)
            .addManufacturerData(BleConstants.MANUFACTURER_ID, payload)
            .build()

        val scanResponseData = AdvertiseData.Builder()
            .setIncludeTxPowerLevel(false)
            .setIncludeDeviceName(false)
            .addServiceUuid(BleConstants.ADVERTISEMENT_PARCEL_UUID)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                isAdvertising.set(true)
                retryAttemptIndex = 0
                updateNotification("Advertising attendance packets...")
                scheduleTokenRefresh()
                Log.i(TAG, "BLE Advertiser Started Mode = BALANCED TX Power = MEDIUM Interval ≈ ${BleConstants.ADVERTISEMENT_INTERVAL_MS} ms")
            }

            override fun onStartFailure(errorCode: Int) {
                val reason = advertiseFailureReason(errorCode)
                Log.e(TAG, "onStartFailure() code=$errorCode reason=$reason")
                updateNotification("BLE failed: $reason")
                Log.e(TAG, "startAdvertising() failure callback errorCode=$errorCode reason=$reason")
                scheduleRetry(reason)
            }
        }

        Log.d(
            TAG,
            "Starting advertising with manufacturerId=${BleConstants.MANUFACTURER_ID} payloadLength=${payload.size} advertisingMode=$advertisingMode txPower=$txPowerLevel",
        )
        try {
            Log.d(TAG, "startAdvertising() called payloadLength=${payload.size}")
            advertiser.startAdvertising(
                settings,
                data,
                scanResponseData,
                advertiseCallback,
            )
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Reason: Missing Bluetooth Advertise permission", securityException)
            stopAdvertisingSession()
        } catch (exception: Exception) {
            Log.e(TAG, "Unexpected BLE advertising start failure", exception)
            scheduleRetry("Exception starting advertiser: ${exception.message ?: exception::class.java.simpleName}")
        }
    }

    private suspend fun isCurrentSessionActive(): Boolean {
        val sessionId = currentSessionId?.trim().orEmpty()
        if (sessionId.isBlank()) {
            return false
        }

        val serverUrl = preferencesManager.serverUrl.first().trim()
        val accessToken = preferencesManager.accessToken.first().trim()
        if (serverUrl.isBlank() || accessToken.isBlank()) {
            return false
        }

        return try {
            val sessions = ApiClient(serverUrl).api.getSessions("Bearer $accessToken")
            sessions.any { session ->
                session.id == sessionId && session.status.equals("ACTIVE", ignoreCase = true)
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to verify active session state", exception)
            false
        }
    }

    private fun resolveAdvertiser(): BluetoothLeAdvertiser? {
        refreshBluetoothState()
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.w(TAG, "Reason: BluetoothAdapter == null")
            return null
        }

        val packageManager = applicationContext.packageManager
        val bluetoothFeaturePresent = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val bleFeaturePresent = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val bluetoothEnabled = adapter.isEnabled
        val multipleAdvertisementSupported = adapter.isMultipleAdvertisementSupported()
        val advertiser = adapter.bluetoothLeAdvertiser
        bluetoothLeAdvertiser = advertiser

        Log.d(TAG, "Manufacturer=${Build.MANUFACTURER}")
        Log.d(TAG, "Model=${Build.MODEL}")
        Log.d(TAG, "Android version=${Build.VERSION.RELEASE}")
        Log.d(TAG, "SDK=${Build.VERSION.SDK_INT}")
        Log.d(TAG, "FEATURE_BLUETOOTH present=$bluetoothFeaturePresent")
        Log.d(TAG, "FEATURE_BLUETOOTH_LE present=$bleFeaturePresent")
        Log.d(TAG, "Bluetooth LE supported=$bleFeaturePresent")
        Log.d(TAG, "Bluetooth enabled=$bluetoothEnabled")
        Log.d(TAG, "Multiple advertisement supported=$multipleAdvertisementSupported")
        Log.d(TAG, "BluetoothLeAdvertiser object=${if (advertiser == null) "null" else "non-null"}")
        Log.d(TAG, "isOffloadedFilteringSupported=${adapter.isOffloadedFilteringSupported}")
        Log.d(TAG, "isOffloadedScanBatchingSupported=${adapter.isOffloadedScanBatchingSupported}")

        if (!bluetoothFeaturePresent) {
            Log.w(TAG, "Reason: FEATURE_BLUETOOTH=false")
            return null
        }

        if (!bleFeaturePresent) {
            Log.w(TAG, "Reason: FEATURE_BLUETOOTH_LE=false")
            return null
        }

        if (!bluetoothEnabled) {
            Log.w(TAG, "Reason: Bluetooth OFF")
            return null
        }

        if (!multipleAdvertisementSupported) {
            Log.w(TAG, "Reason: Hardware does not support BLE Peripheral Mode")
            return null
        }

        if (advertiser == null) {
            Log.w(TAG, "Reason: Advertiser not initialized yet")
        }

        return advertiser
    }

    private fun validateAdvertiserPermissions(): Boolean {
        val permissions = BlePermissions.requiredBlePermissions()
        val missing = permissions.filter { permission ->
            checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }

        Log.d(TAG, "Permission report:\n${BlePermissions.permissionReport(this)}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.d(TAG, "Android 12+ permission validation:")
            Log.d(TAG, "BLUETOOTH_ADVERTISE granted=${checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED}")
            Log.d(TAG, "BLUETOOTH_CONNECT granted=${checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED}")
            Log.d(TAG, "BLUETOOTH_SCAN granted=${checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED}")
        } else {
            Log.d(TAG, "Android 6-11 permission validation:")
            Log.d(TAG, "BLUETOOTH granted=${checkSelfPermission(android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED}")
            Log.d(TAG, "BLUETOOTH_ADMIN granted=${checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED}")
            Log.d(TAG, "ACCESS_FINE_LOCATION granted=${checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED}")
        }

        if (missing.isNotEmpty()) {
            missing.forEach { permission ->
                Log.w(TAG, "Missing permission: $permission")
            }
        }

        val advertiserPermissionsOk = BlePermissions.hasAdvertiserPermissions(this)
        if (!advertiserPermissionsOk) {
            Log.w(TAG, "Reason: Missing Bluetooth Advertise permission")
        }
        return advertiserPermissionsOk
    }

    private fun runBleDiagnostics(currentPayload: ByteArray?) {
        refreshBluetoothState()

        val adapter = bluetoothAdapter
        val packageManager = applicationContext.packageManager

        Log.d(TAG, "runBleDiagnostics()")
        Log.d(TAG, "Manufacturer=${Build.MANUFACTURER}")
        Log.d(TAG, "Model=${Build.MODEL}")
        Log.d(TAG, "Android version=${Build.VERSION.RELEASE}")
        Log.d(TAG, "SDK=${Build.VERSION.SDK_INT}")
        Log.d(TAG, "BluetoothAdapter=${if (adapter == null) "null" else "non-null"}")
        Log.d(TAG, "Bluetooth enabled=${adapter?.isEnabled == true}")
        Log.d(TAG, "Bluetooth LE supported=${packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)}")
        Log.d(TAG, "FEATURE_BLUETOOTH present=${packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)}")
        Log.d(TAG, "FEATURE_BLUETOOTH_LE present=${packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)}")
        Log.d(TAG, "BluetoothLeAdvertiser object=${if (bluetoothLeAdvertiser == null) "null" else "non-null"}")
        Log.d(TAG, "Multiple advertisement supported=${adapter?.isMultipleAdvertisementSupported() == true}")
        Log.d(TAG, "isOffloadedFilteringSupported=${adapter?.isOffloadedFilteringSupported == true}")
        Log.d(TAG, "isOffloadedScanBatchingSupported=${adapter?.isOffloadedScanBatchingSupported == true}")
        Log.d(TAG, "Maximum payload=${BleConstants.ADVERTISEMENT_PAYLOAD_BYTES} bytes")
        Log.d(TAG, "Current payload size=${currentPayload?.size ?: 0} bytes")
        Log.d(TAG, "Current payload hex=${currentPayload?.let { toHex(it) } ?: "<none>"}")
        Log.d(TAG, "Permissions:\n${BlePermissions.permissionReport(this)}")
        Log.d(TAG, "Advertiser permissions:\n${BlePermissions.advertiserPermissionReport(this)}")
        Log.d(TAG, "Scanner permissions:\n${BlePermissions.scannerPermissionReport(this)}")
    }

    private fun scheduleRetry(reason: String) {
        retryJob?.cancel()
        if (retryAttemptIndex >= retryDelaysMs.size) {
            Log.e(TAG, "Reason: $reason. Advertising failed after all retries")
            updateNotification("BLE advertising failed: $reason")
            stopAdvertisingSession()
            return
        }

        val delayMs = retryDelaysMs[retryAttemptIndex]
        Log.w(TAG, "Advertising restart attempt=${retryAttemptIndex + 1} in ${delayMs}ms because: $reason")
        retryAttemptIndex += 1
        retryJob = serviceScope.launch {
            delay(delayMs)
            if (shouldRun) {
                refreshAdvertisingCycle()
            }
        }
    }

    private fun scheduleTokenRefresh() {
        advertisingLoop?.cancel()
        advertisingLoop = serviceScope.launch {
            var lastWindow = tokenManager.currentWindow()
            while (isActive && shouldRun) {
                val delayMs = tokenManager.nextRotationDelay()
                delay(delayMs)
                if (!shouldRun) break
                val currentWindow = tokenManager.currentWindow()
                if (currentWindow != lastWindow) {
                    lastWindow = currentWindow
                    Log.d(TAG, "Advertisement refresh timestamp=${Instant.now()} reason=rolling_token_rotation sessionId=${currentSessionId ?: "<null>"}")
                    refreshAdvertisingCycle()
                }
            }
        }
    }

    private fun stopCurrentAdvertising(reason: String = "unknown") {
        try {
            advertiseCallback?.let { callback ->
                bluetoothLeAdvertiser?.stopAdvertising(callback)
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to stop BLE advertising cleanly", exception)
        } finally {
            if (isAdvertising.get()) {
                Log.w(TAG, "Advertising stopped timestamp=${Instant.now()} reason=$reason")
            }
            advertiseCallback = null
            isAdvertising.set(false)
            bluetoothLeAdvertiser = null
            advertisingLoop?.cancel()
            advertisingLoop = null
        }
    }

    private fun stopAdvertisingSession() {
        shouldRun = false
        advertisingLoop?.cancel()
        retryJob?.cancel()
        stopCurrentAdvertising("stopAdvertisingSession invoked")
        stopForegroundCompat()
        stopSelf()
    }

    private fun refreshBluetoothState() {
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
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
                "BLE Advertising",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when BLE advertising is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
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
            .setContentTitle("BLE Advertising")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(BleConstants.BLE_NOTIFICATION_ID, createNotification(contentText))
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun advertiseFailureReason(errorCode: Int): String {
        return when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
            else -> "UNKNOWN($errorCode)"
        }
    }

    private fun toHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val output = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            output.append(hexChars[value ushr 4])
            output.append(hexChars[value and 0x0F])
        }
        return output.toString()
    }

    override fun onDestroy() {
        stopCurrentAdvertising("Service destroyed")
        advertisingLoop?.cancel()
        retryJob?.cancel()
        unregisterReceiverSafely()
        bluetoothLeAdvertiser = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun unregisterReceiverSafely() {
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver may already be unregistered during teardown.
        }
    }

    private fun runBlockingCurrentSessionId(): String? {
        return kotlinx.coroutines.runBlocking {
            preferencesManager.sessionId.first().trim().takeIf { it.isNotBlank() }
        }
    }

    companion object {
        private const val TAG = "BLEAdvertiserService"
    }
}