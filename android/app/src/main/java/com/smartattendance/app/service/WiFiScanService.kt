package com.smartattendance.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartattendance.app.MainActivity
import com.smartattendance.app.R
import com.smartattendance.app.SmartAttendanceApp
import com.smartattendance.app.data.PreferencesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray

class WiFiScanService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null
    private var wifiManager: WifiManager? = null

    private var bluetoothLeAdvertiser: android.bluetooth.le.BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var isAdvertising = false
    private val detectedBeacons = mutableMapOf<String, Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        startScanning()
        return START_STICKY
    }

    private fun startScanning() {
        scanJob = scope.launch {
            val prefs = PreferencesManager(applicationContext)
            val role = prefs.userRole.first()
            val userIdVal = prefs.userId.first()

            if (role == "FACULTY" && userIdVal.isNotBlank()) {
                startBleAdvertising(userIdVal)
            }

            while (isActive) {
                if (role == "STUDENT") {
                    startBleScanning()
                }
                performScan()
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    private fun startBleAdvertising(userId: String) {
        if (isAdvertising) return
        try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) return
            bluetoothLeAdvertiser = adapter.bluetoothLeAdvertiser ?: return

            val settings = android.bluetooth.le.AdvertiseSettings.Builder()
                .setAdvertiseMode(android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(android.bluetooth.le.AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(false)
                .build()

            val uuid = java.util.UUID.fromString(userId)
            val data = android.bluetooth.le.AdvertiseData.Builder()
                .addServiceUuid(android.os.ParcelUuid(uuid))
                .build()

            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
            isAdvertising = true
            Log.d("WiFiScanService", "Started BLE advertising UUID: $userId")
        } catch (e: Exception) {
            Log.e("WiFiScanService", "Failed to start BLE advertising: ${e.message}")
        }
    }

    private fun stopBleAdvertising() {
        if (!isAdvertising) return
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.d("WiFiScanService", "Stopped BLE advertising")
        } catch (e: Exception) {
            Log.e("WiFiScanService", "Failed to stop BLE advertising: ${e.message}")
        }
    }

    private val advertiseCallback = object : android.bluetooth.le.AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: android.bluetooth.le.AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.i("WiFiScanService", "BLE advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e("WiFiScanService", "BLE advertising failed with code: $errorCode")
            isAdvertising = false
        }
    }

    private fun startBleScanning() {
        try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) return
            bluetoothLeScanner = adapter.bluetoothLeScanner ?: return

            synchronized(detectedBeacons) {
                detectedBeacons.clear()
            }

            val settings = android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            bluetoothLeScanner?.startScan(null, settings, scanCallback)
            Log.d("WiFiScanService", "Started BLE scan window...")

            // Stop BLE scanning after 6 seconds to conserve battery
            scope.launch {
                delay(6000)
                stopBleScanning()
            }
        } catch (e: Exception) {
            Log.e("WiFiScanService", "Failed to start BLE scanning: ${e.message}")
        }
    }

    private fun stopBleScanning() {
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            Log.d("WiFiScanService", "Stopped BLE scan window")
        } catch (e: Exception) {
            Log.e("WiFiScanService", "Failed to stop BLE scanning: ${e.message}")
        }
    }

    private val scanCallback = object : android.bluetooth.le.ScanCallback() {
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let { res ->
                val uuids = res.scanRecord?.serviceUuids ?: return
                val rssi = res.rssi
                for (parcelUuid in uuids) {
                    val uuidString = parcelUuid.uuid.toString()
                    synchronized(detectedBeacons) {
                        detectedBeacons[uuidString] = rssi
                    }
                    Log.d("WiFiScanService", "Detected BLE beacon: $uuidString | RSSI: $rssi")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("WiFiScanService", "BLE scan failed: $errorCode")
        }
    }

    private val httpClient = OkHttpClient()

    private suspend fun uploadScanToFind3(scanResults: List<android.net.wifi.ScanResult>) {
        val prefs = PreferencesManager(applicationContext)
        val deviceId = prefs.deviceId.first()
        val serverUrl = prefs.serverUrl.first()
        
        if (deviceId.isBlank() || serverUrl.isBlank()) return

        val find3Url = if (serverUrl.endsWith("/")) "${serverUrl}find3-data" else "$serverUrl/find3-data"

        val wifiJson = JSONObject()
        for (res in scanResults) {
            wifiJson.put(res.BSSID, res.level)
        }

        val sensorsJson = JSONObject()
        sensorsJson.put("wifi", wifiJson)

        val bluetoothJson = JSONObject()
        synchronized(detectedBeacons) {
            for ((uuid, rssi) in detectedBeacons) {
                bluetoothJson.put(uuid, rssi)
            }
        }
        sensorsJson.put("bluetooth", bluetoothJson)

        val payload = JSONObject()
        payload.put("d", deviceId)
        payload.put("f", "pes") // Active family name
        payload.put("t", System.currentTimeMillis())
        payload.put("s", sensorsJson)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(find3Url)
            .post(requestBody)
            .build()

        try {
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("WiFiScanService", "Find3 upload failed: ${response.code}")
                    } else {
                        val body = response.body?.string() ?: ""
                        Log.d("WiFiScanService", "Find3 upload success: $body")
                        // Try to parse the guessed location from find3 response
                        // find3 returns: {"guesses":[{"location":"301","probability":0.9}],...}
                        try {
                            val json = JSONObject(body)
                            val guesses = json.optJSONArray("guesses")
                            if (guesses != null && guesses.length() > 0) {
                                val bestGuess = guesses.getJSONObject(0)
                                val prob = bestGuess.optDouble("probability", 0.0)
                                if (prob >= 0.70) {
                                    val detectedLocation = bestGuess.optString("location", "")
                                    val isCorridor = detectedLocation.lowercase().contains("corridor") || detectedLocation.lowercase().contains("hallway")
                                    if (detectedLocation.isNotBlank() && !isCorridor) {
                                        prefs.saveLastLocation(detectedLocation)
                                        prefs.recordScanResult(true)
                                        Log.d("WiFiScanService", "Location detected: $detectedLocation")
                                    } else {
                                        prefs.saveLastLocation(detectedLocation)
                                        prefs.recordScanResult(false)
                                        Log.d("WiFiScanService", "Find3 returned corridor/empty location")
                                    }
                                } else {
                                    prefs.saveLastLocation("unknown")
                                    prefs.recordScanResult(false)
                                    Log.d("WiFiScanService", "Discarded low-confidence location guess (probability $prob < 0.70)")
                                }
                            } else {
                                prefs.recordScanResult(false)
                                Log.d("WiFiScanService", "No guesses in find3 response yet")
                            }
                        } catch (parseEx: Exception) {
                            prefs.recordScanResult(false)
                            Log.w("WiFiScanService", "Could not parse find3 location: ${parseEx.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WiFiScanService", "Find3 upload error: ${e.message}")
        }
    }

    private suspend fun performScan() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "SmartAttendance::WiFiScanWakeLock"
        )
        try {
            wakeLock?.acquire(15000)

            // Execute 3 rapid scans in a burst at the start of every minute
            repeat(3) { burstIndex ->
                wifiManager?.let { wm ->
                    wm.startScan() // Trigger scan asynchronously (might be throttled)
                    val scanResults = wm.scanResults
                    val strongest = scanResults.maxByOrNull { it.level }
                    val bssids = scanResults.take(15).map { "${it.SSID} [${it.BSSID}] (${it.level}dBm)" }
                    val beaconsCopy = synchronized(detectedBeacons) {
                        detectedBeacons.toMap()
                    }
                    onScanResult(bssids, strongest?.level ?: 0, beaconsCopy)
                    uploadScanToFind3(scanResults)
                }
                if (burstIndex < 2) {
                    kotlinx.coroutines.delay(2000L) // 2s delay between rapid burst scans
                }
            }
        } catch (e: Exception) {
            // Scan burst failed, will retry on next interval
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }
    }

    private fun onScanResult(bssids: List<String>, rssi: Int, beacons: Map<String, Int>) {
        Log.d("WiFiScanService", "onScanResult: sending broadcast. wifi=${bssids.size}, ble=${beacons.size}")
        val intent = Intent(SCAN_RESULT_ACTION).apply {
            setPackage(packageName)
            putExtra("bssids", bssids.toTypedArray())
            putExtra("rssi", rssi)
            val bleArray = beacons.map { "${it.key} (${it.value}dBm)" }.toTypedArray()
            putExtra("ble_beacons", bleArray)
            putExtra("timestamp", System.currentTimeMillis())
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        scanJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, SmartAttendanceApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Attendance Scanning")
            .setContentText("Scanning for nearby WiFi networks...")
            .setSmallIcon(R.drawable.pes_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SmartAttendanceApp.NOTIFICATION_CHANNEL_ID,
                "WiFi Scanning",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when attendance scanning is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val SCAN_RESULT_ACTION = "com.smartattendance.SCAN_RESULT"
        const val NOTIFICATION_ID = 1001
        private const val SCAN_INTERVAL_MS = 60_000L
    }
}
