package com.smartattendance.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

class SmartAttendanceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            Log.e(TAG, "CRASH: ${throwable.message}\n${sw}")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val wifiChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "WiFi Scanning",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when attendance scanning is active"
            }
            val bleChannel = NotificationChannel(
                BLE_NOTIFICATION_CHANNEL_ID,
                "BLE Advertising",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when BLE advertising is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(wifiChannel)
            manager.createNotificationChannel(bleChannel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "wifi_scan"
        const val BLE_NOTIFICATION_CHANNEL_ID = "ble_advertising"
        private const val TAG = "SmartAttendance"
    }
}
