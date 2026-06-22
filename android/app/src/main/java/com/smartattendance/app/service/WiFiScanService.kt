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
import androidx.core.app.NotificationCompat
import com.smartattendance.app.MainActivity
import com.smartattendance.app.R
import com.smartattendance.app.SmartAttendanceApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

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
        startForeground(NOTIFICATION_ID, createNotification())
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

    private suspend fun performScan() {
        wifiManager?.let { wm ->
            try {
                val success = wm.startScan()
                if (success) {
                    val scanResults = wm.scanResults
                    val strongest = scanResults.maxByOrNull { it.level }
                    val bssids = scanResults.take(5).map { "${it.SSID}(${it.level}dBm)" }

                    onScanResult(bssids, strongest?.level ?: 0)
                }
            } catch (e: Exception) {
                // Scan failed, will retry on next interval
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
