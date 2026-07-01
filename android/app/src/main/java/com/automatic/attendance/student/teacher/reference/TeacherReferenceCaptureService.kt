package com.automatic.attendance.student.teacher.reference

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.automatic.attendance.student.network.RetrofitClient
import com.automatic.attendance.student.storage.SecureTokenStorageImpl
import com.automatic.attendance.student.wifi.WifiScanManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TeacherReferenceCaptureService : Service() {

    private val binder = TeacherReferenceBinder()

    private lateinit var wifiScanManager: WifiScanManager
    private lateinit var collector: TeacherReferenceCollector
    private lateinit var repository: TeacherReferenceRepository
    private val scope = CoroutineScope(Dispatchers.IO)

    private var isCapturing = false
    private var sessionId: String? = null

    inner class TeacherReferenceBinder : Binder() {
        fun getService(): TeacherReferenceCaptureService = this@TeacherReferenceCaptureService
    }

    override fun onCreate() {
        super.onCreate()

        wifiScanManager = WifiScanManager(this)

        val storage = SecureTokenStorageImpl(this)
        val retrofit = RetrofitClient.create(
            "http://192.168.137.1:3000/",
            storage
        )

        collector = TeacherReferenceCollector().apply {
            registerProvider(WifiReferenceCollector(wifiScanManager))
            registerProvider(BLEReferenceCollector())
        }

        repository = TeacherReferenceRepository(retrofit)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sessionId = intent?.getStringExtra("sessionId")

        if (sessionId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        captureTeacherReferenceOnce(sessionId!!)
        return START_NOT_STICKY
    }

    private fun captureTeacherReferenceOnce(sessionId: String) {
        if (isCapturing) return

        isCapturing = true
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, "teacher_reference_capture_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Teacher Reference Capture")
            .setContentText("Capturing lecture Wi-Fi environment...")
            .setOngoing(true)
            .build()

        startForeground(2, notification)

        scope.launch {
            try {
                val collectionResult = collector.collectReferenceFingerprint()
                val fingerprintData = collectionResult.fingerprintData

                if (!collectionResult.success || fingerprintData.isEmpty()) {
                    android.util.Log.w(
                        "TeacherReferenceCaptureService",
                        "No Wi-Fi fingerprints collected for session=$sessionId"
                    )
                    return@launch
                }

                val response = repository.storeTeacherReferenceFingerprint(sessionId, fingerprintData)

                android.util.Log.d(
                    "TeacherReferenceCaptureService",
                    "Teacher reference fingerprint response=${response.code()}"
                )
            } catch (error: Exception) {
                android.util.Log.e(
                    "TeacherReferenceCaptureService",
                    "Teacher reference capture failed",
                    error
                )
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isCapturing = false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "teacher_reference_capture_channel",
                "Teacher Reference Capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "One-shot Wi-Fi capture for lecture activation"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}