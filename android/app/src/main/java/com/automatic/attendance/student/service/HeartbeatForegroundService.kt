package com.automatic.attendance.student.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.automatic.attendance.student.R
import com.automatic.attendance.student.network.RetrofitClient
import com.automatic.attendance.student.repository.HeartbeatPayload
import com.automatic.attendance.student.repository.HeartbeatRepository
import com.automatic.attendance.student.storage.SecureTokenStorageImpl
import com.automatic.attendance.student.wifi.WifiScanManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class HeartbeatForegroundService : Service() {

    private val binder = HeartbeatBinder()

    private var sessionId: String? = null
    private var studentId: String? = null

    private lateinit var wifiManager: WifiScanManager
    private lateinit var repo: HeartbeatRepository

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)

    private var isRunning = false

    private val HEARTBEAT_INTERVAL_MS = 15_000L

    inner class HeartbeatBinder : Binder() {
        fun getService(): HeartbeatForegroundService =
            this@HeartbeatForegroundService
    }

    override fun onCreate() {
        super.onCreate()

        wifiManager = WifiScanManager(this)

        val storage = SecureTokenStorageImpl(this)

        val retrofit = RetrofitClient.create(
            "http://192.168.137.1:3000/",
            storage
        )

        repo = HeartbeatRepository(
            retrofit,
            storage
        )

        android.util.Log.d(
            "HeartbeatService",
            "Service initialized successfully"
        )
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        android.util.Log.d(
            "HeartbeatService",
            "onStartCommand called. sessionId=${
                intent?.getStringExtra("sessionId")
            }, studentId=${
                intent?.getStringExtra("studentId")
            }"
        )

        sessionId = intent?.getStringExtra("sessionId")
        studentId = intent?.getStringExtra("studentId")

        if (!sessionId.isNullOrEmpty() && !studentId.isNullOrEmpty()) {
            startHeartbeats()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                "heartbeat_channel",
                "Attendance Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Foreground service for attendance heartbeats"
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun startHeartbeats() {

        android.util.Log.d(
            "HeartbeatService",
            "Entered startHeartbeats()"
        )

        if (isRunning) return

        isRunning = true

        createNotificationChannel()

        val notification = NotificationCompat.Builder(
            this,
            "heartbeat_channel"
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Attendance Service")
            .setContentText("Monitoring attendance...")
            .setOngoing(true)
            .build()

        android.util.Log.d(
            "HeartbeatService",
            "Calling startForeground()"
        )

        startForeground(1, notification)

        android.util.Log.d(
            "HeartbeatService",
            "Foreground started successfully"
        )

        sendHeartbeat()
    }

    private fun sendHeartbeat() {

        if (!isRunning) return

        scope.launch {

            try {

                if (
                    !::wifiManager.isInitialized ||
                    !::repo.isInitialized
                ) {
                    android.util.Log.e(
                        "HeartbeatService",
                        "Dependencies not initialized"
                    )
                    return@launch
                }

                // Request a fresh Wi-Fi fingerprint snapshot with bounded wait handled by WifiScanManager.
                val fingerprint = wifiManager.getWifiFingerprint()

val seq =
    repo.getNextSequenceNumber()

                val payload = HeartbeatPayload(
                    sessionId = sessionId ?: "",
                    wifiFingerprint = fingerprint,
                    sequenceNumber = seq,
                    deviceFingerprint = Build.FINGERPRINT,
                    timestamp = Instant.now().toString()
                )

                val resp = repo.sendHeartbeat(payload)

                android.util.Log.d(
                    "HeartbeatService",
                    "Heartbeat response: ${resp.code()}"
                )

            } catch (e: Exception) {

                android.util.Log.e(
                    "HeartbeatService",
                    "Heartbeat failed",
                    e
                )
            }
        }

        handler.postDelayed(
            { sendHeartbeat() },
            HEARTBEAT_INTERVAL_MS
        )
    }

    fun stopHeartbeats() {

        isRunning = false

        handler.removeCallbacksAndMessages(null)

        if (::repo.isInitialized) {
            repo.resetSequence()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)

        stopSelf()
    }

    override fun onDestroy() {

        stopHeartbeats()

        super.onDestroy()
    }
}