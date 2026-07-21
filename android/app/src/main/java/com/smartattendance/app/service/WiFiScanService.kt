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

        val find3Url = if (serverUrl.endsWith("/")) "${serverUrl}find3-data" else "$serverUrl/find3-data"

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
        private const val SCAN_INTERVAL_MS = 10_000L
    }
}
