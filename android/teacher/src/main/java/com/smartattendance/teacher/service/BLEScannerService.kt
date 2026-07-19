package com.smartattendance.teacher.service

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
import com.smartattendance.teacher.BuildConfig
import com.smartattendance.teacher.MainActivity
import com.smartattendance.teacher.ble.BleLiveMetricsStore
import com.smartattendance.teacher.ble.ObservationProcessor
import com.smartattendance.teacher.ble.ObservationUploader
import com.smartattendance.teacher.ble.RegisteredDeviceFilter
import com.smartattendance.teacher.ble.ScanParser
import com.smartattendance.teacher.repository.TeacherRepository
import com.smartattendance.shared.data.PreferencesManager
import com.smartattendance.shared.ble.BleConstants
import com.smartattendance.shared.ble.BlePermissions
import com.smartattendance.shared.ble.BleScannerConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.Locale

class BLEScannerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var shouldRun = false
    private lateinit var preferencesManager: PreferencesManager

    private val scanParser = ScanParser()
    private val registeredDeviceFilter = RegisteredDeviceFilter()
    private val observationProcessor = ObservationProcessor()
    private lateinit var observationUploader: ObservationUploader
    private lateinit var teacherRepository: TeacherRepository
    private var registeredDevicesRefreshJob: Job? = null
    private var scanHealthJob: Job? = null
    private var startScanJob: Job? = null
    private var lastScanResultAtMillis: Long = 0L
    private var packetsReceivedCount: Long = 0L
    private var packetsAcceptedCount: Long = 0L
    private var rssiTotal: Long = 0L
    private var rssiSamples: Long = 0L
    private val studentsSeen = mutableSetOf<String>()

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    if (shouldRun) {
                        scheduleStartScan()
                    }
                }

                BluetoothAdapter.STATE_OFF -> {
                    stopScan("Bluetooth is off")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        teacherRepository = TeacherRepository(
            context = applicationContext,
            baseUrl = "http://192.168.137.1:8000/",
        )
        preferencesManager = PreferencesManager(applicationContext)
        observationUploader = ObservationUploader(
            scope = serviceScope,
            teacherRepository = teacherRepository,
            preferencesManager = preferencesManager,
        )
        createNotificationChannel()
        registerBluetoothReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action} flags=$flags startId=$startId shouldRun=$shouldRun")
        when (intent?.action) {
            BleScannerConstants.ACTION_STOP_SCANNING -> {
                stopScan()
                return START_NOT_STICKY
            }

            BleScannerConstants.ACTION_START_SCANNING, null -> {
                if (!shouldRun) {
                    BleLiveMetricsStore.reset()
                }
                shouldRun = true
                observationUploader.start()
                startAsForeground("BLE scanning starting")
                scheduleStartScan()
            }
        }

        return START_STICKY
    }

    private suspend fun startScan() {
        if (!shouldRun) {
            shouldRun = true
        }

        Log.d(TAG, "startScan() invoked timestamp=${nowIso()} shouldRun=$shouldRun")

        primeUploadContext()

        if (!BlePermissions.hasScannerPermissions(this)) {
            Log.w(TAG, "Missing BLE scan permissions; stopping scanner\n${BlePermissions.scannerPermissionReport(this)}")
            stopScan("Missing BLE scan permissions")
            return
        }

        Log.d(TAG, "startScan() permission_status=GRANTED timestamp=${nowIso()}\n${BlePermissions.scannerPermissionReport(this)}")

        if (!BlePermissions.isBluetoothEnabled(this)) {
            Log.w(TAG, "Bluetooth disabled; stopping scanner")
            stopScan("Bluetooth is off")
            return
        }

        Log.d(TAG, "startScan() bluetooth_enabled=true timestamp=${nowIso()}")

        refreshRegisteredDevices()

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: bluetoothLeScanner
        if (scanner == null) {
            updateNotification("BLE scanner unavailable")
            return
        }

        if (scanCallback != null) return

        lastScanResultAtMillis = System.currentTimeMillis()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = emptyList<ScanFilter>()

        Log.d(TAG, "Scanner permissions granted\n${BlePermissions.scannerPermissionReport(this)}")
        Log.d(
            TAG,
            "Scanner started preflight timestamp=${nowIso()} mode=${scanModeName(settings.scanMode)} callbackType=${scanCallbackTypeName(settings.callbackType)} reportDelayMs=${settings.reportDelayMillis} bluetoothEnabled=${BlePermissions.isBluetoothEnabled(this)}",
        )
        Log.d(
            TAG,
            "Scan filter disabled so advertisements can reach parser; serviceUuid=${BleConstants.ADVERTISEMENT_UUID_STRING} manufacturerId=${BleConstants.MANUFACTURER_ID}",
        )
        Log.d(TAG, "Filters used count=${filters.size} values=${describeFilters(filters)}")

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult?) {
                Log.i("BLEScannerService", "onScanResult() callback reached")
                if (result == null) {
                    Log.d(TAG, "onScanResult() timestamp=${nowIso()} result=null")
                    return
                }
                lastScanResultAtMillis = System.currentTimeMillis()
                packetsReceivedCount += 1
                val callbackRecord = result.scanRecord
                val callbackServiceUuids = callbackRecord?.serviceUuids?.joinToString { it.uuid.toString() } ?: "none"
                val callbackManufacturerData = callbackRecord?.getManufacturerSpecificData(com.smartattendance.shared.ble.BleConstants.MANUFACTURER_ID)
                val callbackManufacturerLength = callbackManufacturerData?.size ?: -1
                Log.d(
                    TAG,
                    "onScanResult() timestamp=${nowIso()} callbackType=$callbackType(${scanCallbackTypeName(callbackType)}) mac=${result.device?.address ?: "<null>"} deviceHash=${result.device?.hashCode() ?: -1} rssi=${result.rssi} txPower=${result.txPower} serviceUuid=$callbackServiceUuids manufacturerLength=$callbackManufacturerLength",
                )
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<android.bluetooth.le.ScanResult>) {
                lastScanResultAtMillis = System.currentTimeMillis()
                packetsReceivedCount += results.size
                Log.d(TAG, "onBatchScanResults() timestamp=${nowIso()} count=${results.size}")
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "onScanFailed() timestamp=${nowIso()} code=$errorCode reason=${scanFailureReason(errorCode)}")
                updateNotification("BLE scan failed")
                Log.w(TAG, "Keeping service alive after scan failure for lifecycle probe")
            }
        }

        try {
            Log.d(TAG, "Calling BluetoothLeScanner.startScan() timestamp=${nowIso()}")
            scanner.startScan(filters, settings, scanCallback)
            updateNotification("BLE scanning active")
            Log.i(TAG, "Scanner started successfully timestamp=${nowIso()} mode=${scanModeName(settings.scanMode)}")
            scheduleRegisteredDevicesRefresh()
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Missing permission to start BLE scanning", securityException)
            stopScan("Missing permission to start BLE scanning")
        } catch (exception: Exception) {
            Log.e(TAG, "Unexpected BLE scanner start failure", exception)
            stopScan("Unexpected BLE scanner start failure")
        }
    }

    private fun scheduleStartScan() {
        if (startScanJob?.isActive == true) {
            Log.d(TAG, "Skipping startScan scheduling because a start job is already active")
            return
        }

        if (scanCallback != null) {
            Log.d(TAG, "Skipping startScan scheduling because a scan callback is already registered")
            return
        }

        startScanJob = serviceScope.launch {
            try {
                delay(5_000)
                startScan()
            } finally {
                startScanJob = null
            }
        }
    }

    private fun handleScanResult(result: android.bluetooth.le.ScanResult) {
        serviceScope.launch {
            val record = result.scanRecord
            val timestampMillis = System.currentTimeMillis()
            BleLiveMetricsStore.recordPacketReceived(
                timestampMillis = timestampMillis,
                macAddress = result.device?.address,
                rssi = result.rssi,
            )
            val advertisementFlags = record?.advertiseFlags
            val serviceUuids = record?.serviceUuids?.joinToString { it.uuid.toString() } ?: "none"
            val manufacturerData = record?.getManufacturerSpecificData(com.smartattendance.shared.ble.BleConstants.MANUFACTURER_ID)
            val serviceData = record?.serviceData?.entries?.joinToString { (uuid, value) -> "${uuid.uuid}:${value.joinToString(separator = "") { byte -> "%02x".format(byte) }}" } ?: "none"
            val advertisementBytes = record?.bytes?.joinToString(separator = "") { byte -> "%02x".format(byte) } ?: "<none>"
            val rawLength = record?.bytes?.size ?: -1
            val scanRecordDebug = record?.toString() ?: "<null>"
            Log.d(
                TAG,
                "Advertisement received timestamp=${nowIso()} mac=${result.device?.address ?: "<null>"} deviceHash=${result.device?.hashCode() ?: -1} rssi=${result.rssi} txPower=${result.txPower} manufacturerId=${com.smartattendance.shared.ble.BleConstants.MANUFACTURER_ID} manufacturerData=${manufacturerData?.joinToString(separator = "") { byte -> "%02x".format(byte) } ?: "<null>"} serviceUuids=$serviceUuids serviceData=$serviceData advertiseFlags=${advertisementFlags ?: -1} rawAdvertisementLength=$rawLength scanRecord=$scanRecordDebug advertisementBytes=$advertisementBytes",
            )
            Log.d(
                TAG,
                "Raw scan probe mac=${result.device?.address ?: "<null>"} deviceHash=${result.device?.hashCode() ?: -1} rssi=${result.rssi} txPower=${result.txPower} manufacturerData=${manufacturerData?.joinToString(separator = "") { byte -> "%02x".format(byte) } ?: "<null>"} serviceUuids=$serviceUuids serviceData=$serviceData advertiseFlags=${advertisementFlags ?: -1} rawAdvertisementLength=$rawLength scanRecord=$scanRecordDebug advertisementBytes=$advertisementBytes",
            )

            val parsedAdvertisement = scanParser.parse(result) ?: return@launch
            Log.d(
                TAG,
                "Stage=ScanParser accepted mac=${result.device?.address ?: "<null>"} anonymousId=${parsedAdvertisement.anonymousDeviceIdHex} rollingToken=${parsedAdvertisement.rollingTokenHex}",
            )
            val activeSessionId = preferencesManager.activeSessionId.first().trim()
            val studentId = registeredDeviceFilter.resolveStudentId(
                anonymousDeviceIdHex = parsedAdvertisement.anonymousDeviceIdHex,
                rollingTokenHex = parsedAdvertisement.rollingTokenHex,
                deviceAddress = result.device?.address,
                rssi = result.rssi,
                sessionId = activeSessionId.ifBlank { null },
                serviceUuids = serviceUuids,
                manufacturerId = com.smartattendance.shared.ble.BleConstants.MANUFACTURER_ID,
            ) ?: return@launch
            Log.d(
                TAG,
                "Stage=RegisteredDeviceFilter accepted mac=${result.device?.address ?: "<null>"} studentId=$studentId anonymousId=${parsedAdvertisement.anonymousDeviceIdHex} rollingToken=${parsedAdvertisement.rollingTokenHex}",
            )

            val observation = observationProcessor.prepareObservation(
                parsedAdvertisement = parsedAdvertisement,
                timestampMillis = timestampMillis,
                studentId = studentId,
                sessionId = activeSessionId.ifBlank { null },
            ) ?: return@launch
            Log.d(
                TAG,
                "Stage=ObservationProcessor accepted mac=${result.device?.address ?: "<null>"} studentId=$studentId anonymousId=${parsedAdvertisement.anonymousDeviceIdHex} rollingToken=${parsedAdvertisement.rollingTokenHex}",
            )

            BleLiveMetricsStore.recordPacketAccepted(
                timestampMillis = timestampMillis,
                studentId = studentId,
                macAddress = result.device?.address,
                rssi = result.rssi,
            )
            packetsAcceptedCount += 1
            studentsSeen.add(studentId)
            logLiveMetrics("accepted")
            Log.d(
                TAG,
                "Stage=ObservationUploader queued mac=${result.device?.address ?: "<null>"} studentId=$studentId anonymousId=${parsedAdvertisement.anonymousDeviceIdHex} rollingToken=${parsedAdvertisement.rollingTokenHex}",
            )
            observationUploader.enqueue(observation)
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

    private fun stopScan(reason: String? = null) {
        Log.i(TAG, "stopScan() timestamp=${nowIso()} reason=${reason ?: "none"} shouldRun=$shouldRun")
        shouldRun = false
        BleLiveMetricsStore.reset()
        stopCurrentScan()
        registeredDevicesRefreshJob?.cancel()
        registeredDevicesRefreshJob = null
        scanHealthJob?.cancel()
        scanHealthJob = null
        startScanJob?.cancel()
        startScanJob = null
        serviceScope.launch {
            observationUploader.stop()
        }
        reason?.let { updateNotification(it) }
        Log.i(TAG, "BLE scanner stopped reason=${reason ?: "none"}")
        logLiveMetrics("stopped")
        stopForegroundCompat()
        stopSelf()
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
                BLE_CHANNEL_ID,
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
        Log.i(TAG, "startForeground() notificationText=$contentText")
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

        return NotificationCompat.Builder(this, BLE_CHANNEL_ID)
            .setContentTitle("BLE Scanning")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        Log.i(TAG, "updateNotification text=$contentText")
        manager.notify(BleScannerConstants.BLE_SCAN_NOTIFICATION_ID, createNotification(contentText))
    }

    private suspend fun refreshRegisteredDevices() {
        val result = teacherRepository.getRegisteredDevices()
        result.onSuccess { devices ->
            registeredDeviceFilter.replaceAll(devices)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Loaded ${devices.size} registered devices from backend")
            }
        }.onFailure { exception ->
            Log.w(TAG, "Failed to load registered devices from backend", exception)
        }
    }

    private suspend fun primeUploadContext() {
        teacherRepository.getDashboardState()
            .onSuccess { dashboard ->
                Log.i(
                    TAG,
                    "Upload context primed activeSessionId=${dashboard.activeSessionId.ifBlank { "<blank>" }} teacherId=${dashboard.teacherId.ifBlank { "<blank>" }}",
                )
            }
            .onFailure { exception ->
                Log.w(TAG, "Failed to prime upload context from dashboard", exception)
            }
    }

    private fun scheduleRegisteredDevicesRefresh() {
        if (registeredDevicesRefreshJob?.isActive == true) return
        registeredDevicesRefreshJob = serviceScope.launch {
            while (isActive && shouldRun) {
                delay(30_000L)
                if (!shouldRun) break
                refreshRegisteredDevices()
            }
        }
    }

    private fun scheduleScanHealthCheck() {
        if (scanHealthJob?.isActive == true) return

        scanHealthJob = serviceScope.launch {
            while (isActive && shouldRun) {
                delay(15_000L)
                val lastSeen = lastScanResultAtMillis
                if (lastSeen == 0L || scanCallback == null) {
                    continue
                }

                val staleFor = System.currentTimeMillis() - lastSeen
                if (staleFor >= 25_000L) {
                    Log.w(TAG, "No BLE scan callbacks for ${staleFor}ms, restarting scanner")
                    stopCurrentScan()
                    delay(1_000L)
                    Log.w(TAG, "Advertising restart attempt due to stale scan callbacks")
                    startScan()
                }
            }
        }
    }

    private fun stopForegroundCompat() {
        Log.i(TAG, "stopForeground()")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        stopCurrentScan()
        runCatching { unregisterReceiver(bluetoothStateReceiver) }
        runCatching { observationUploader.cancel() }
        registeredDevicesRefreshJob?.cancel()
        scanHealthJob?.cancel()
        startScanJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BLEScannerService"
        private const val BLE_CHANNEL_ID = "ble_channel"
    }

    private fun describeFilters(filters: List<ScanFilter>): String {
        if (filters.isEmpty()) return "<none>"
        return filters.joinToString(separator = " | ") { filter ->
            "deviceAddress=${filter.deviceAddress ?: "<null>"},deviceName=${filter.deviceName ?: "<null>"},manufacturerId=${filter.manufacturerId},serviceUuid=${filter.serviceUuid?.uuid?.toString() ?: "<null>"}"
        }
    }

    private fun scanModeName(mode: Int): String = when (mode) {
        ScanSettings.SCAN_MODE_OPPORTUNISTIC -> "OPPORTUNISTIC"
        ScanSettings.SCAN_MODE_LOW_POWER -> "LOW_POWER"
        ScanSettings.SCAN_MODE_BALANCED -> "BALANCED"
        ScanSettings.SCAN_MODE_LOW_LATENCY -> "LOW_LATENCY"
        else -> "UNKNOWN($mode)"
    }

    private fun scanCallbackTypeName(type: Int): String = when (type) {
        ScanSettings.CALLBACK_TYPE_ALL_MATCHES -> "ALL_MATCHES"
        ScanSettings.CALLBACK_TYPE_FIRST_MATCH -> "FIRST_MATCH"
        ScanSettings.CALLBACK_TYPE_MATCH_LOST -> "MATCH_LOST"
        else -> "UNKNOWN($type)"
    }

    private fun scanFailureReason(code: Int): String = when (code) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APPLICATION_REGISTRATION_FAILED"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
        ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "OUT_OF_HARDWARE_RESOURCES"
        ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "SCANNING_TOO_FREQUENTLY"
        else -> "UNKNOWN($code)"
    }

    private fun nowIso(): String = Instant.now().toString()

    private fun logLiveMetrics(stage: String) {
        val liveMetrics = BleLiveMetricsStore.metrics.value
        val averageRssi = if (rssiSamples == 0L) null else (rssiTotal.toDouble() / rssiSamples.toDouble())
        Log.d(
            TAG,
            "Live metrics stage=$stage packetsReceived=${liveMetrics.packetsReceived} packetsAccepted=${liveMetrics.packetsAccepted} studentsSeen=${liveMetrics.studentsSeenCount} presentCount=${liveMetrics.studentsSeenCount} missingCount=<unknown_until_presence_api> averageRssi=${averageRssi?.let { String.format(Locale.US, "%.2f", it) } ?: "<none>"} lastPacketTime=${if (liveMetrics.lastPacketReceivedAtMillis == 0L) "<none>" else Instant.ofEpochMilli(liveMetrics.lastPacketReceivedAtMillis)} lastAcceptedPacketTime=${if (liveMetrics.lastAcceptedPacketAtMillis == 0L) "<none>" else Instant.ofEpochMilli(liveMetrics.lastAcceptedPacketAtMillis)}",
        )
    }
}
