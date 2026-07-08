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

class WiFiScanService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null
    private var wifiManager: WifiManager? = null

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
            while (isActive) {
                performScan()
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    private val httpClient = OkHttpClient()

    private suspend fun uploadScanToFind3(scanResults: List<android.net.wifi.ScanResult>) {
        val prefs = PreferencesManager(applicationContext)
        val deviceId = prefs.deviceId.first()
        val serverUrl = prefs.serverUrl.first()
        
        if (deviceId.isBlank() || serverUrl.isBlank()) return

        val host = try {
            val uri = java.net.URI(serverUrl)
            uri.host ?: "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
        val find3Url = "http://$host:8005/data"

        val wifiJson = JSONObject()
        for (res in scanResults) {
            wifiJson.put(res.BSSID, res.level)
        }

        val sensorsJson = JSONObject()
        sensorsJson.put("wifi", wifiJson)

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
                        Log.d("WiFiScanService", "Find3 upload success: ${response.body?.string()}")
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
            // Acquire wake lock with a 5s timeout to avoid any chance of leaking the lock
            wakeLock?.acquire(5000)
            
            wifiManager?.let { wm ->
                val success = wm.startScan()
                if (success) {
                    val scanResults = wm.scanResults
                    val strongest = scanResults.maxByOrNull { it.level }
                    val bssids = scanResults.take(5).map { "${it.SSID}(${it.level}dBm)" }

                    onScanResult(bssids, strongest?.level ?: 0)
                    uploadScanToFind3(scanResults)
                }
            }
        } catch (e: Exception) {
            // Scan failed, will retry on next interval
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }
    }

    private fun onScanResult(bssids: List<String>, rssi: Int) {
        val intent = Intent(SCAN_RESULT_ACTION).apply {
            putExtra("bssids", bssids.toTypedArray())
            putExtra("rssi", rssi)
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
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
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
        private const val SCAN_INTERVAL_MS = 30_000L
    }
}
