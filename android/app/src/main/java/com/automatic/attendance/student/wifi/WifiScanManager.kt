package com.automatic.attendance.student.wifi

import android.Manifest
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.automatic.attendance.student.repository.WifiFingerprint

class WifiScanManager(private val context: Context) {
    private val wifiManager: WifiManager? = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun getWifiFingerprint(): List<WifiFingerprint> {
        if (wifiManager == null) return emptyList()
        
        return try {
            val results = wifiManager.scanResults

android.util.Log.d(
    "WifiScan",
    "Found ${results?.size ?: 0} networks"
)

results?.forEach {
    android.util.Log.d(
        "WifiScan",
        "SSID=${it.SSID}, BSSID=${it.BSSID}, RSSI=${it.level}"
    )
}
            results?.map { result ->
                WifiFingerprint(
                    bssid = result.BSSID ?: "",
                    ssid = result.SSID?.takeIf { it.isNotEmpty() },
                    rssi = result.level
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun startScan() {
        try {
            wifiManager?.startScan()
        } catch (_: Exception) {
        }
    }
}
