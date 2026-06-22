package com.automatic.attendance.student.wifi

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.automatic.attendance.student.repository.WifiFingerprint
import android.net.wifi.ScanResult
import java.util.concurrent.TimeUnit

class WifiScanManager(private val context: Context) {
    private val wifiManager: WifiManager? = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var lastNonEmptyFingerprint: List<WifiFingerprint> = emptyList()

    private fun hasLocationServicesEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            @Suppress("DEPRECATION")
            (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true)
        }
    }

    private fun hasFineLocationPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    private fun hasCoarseLocationPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    private fun hasNearbyWifiPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) {
                false
            }
        } else {
            true
        }
    }

    private fun hasAccessWifiStatePermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_WIFI_STATE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    private fun readCachedScanResults(): List<ScanResult> {
    return try {
        if (!hasAccessWifiStatePermission()) {
            android.util.Log.w(
                "WifiScan",
                "ACCESS_WIFI_STATE not granted; returning cached fingerprint only"
            )
            emptyList()
        } else {
            wifiManager?.scanResults.orEmpty()
        }
    } catch (e: Exception) {
        android.util.Log.d(
            "WifiScan",
            "scanResults read failed: ${e.message}",
            e
        )
        emptyList()
    }
}

    private fun readCachedFingerprint(): List<WifiFingerprint> {
        val results = readCachedScanResults()
        android.util.Log.d(
            "WifiScan",
            "scanResults.size=${results.size}"
        )

        results.firstOrNull()?.let { first ->
            android.util.Log.d(
                "WifiScan",
                "firstResult BSSID=${first.BSSID}, SSID=${first.SSID}, RSSI=${first.level}"
            )
        }

        return results.map { result ->
            WifiFingerprint(
                bssid = result.BSSID ?: "",
                ssid = result.SSID?.takeIf { it.isNotEmpty() },
                rssi = result.level
            )
        }
    }

    private fun latestScanTimestamp(results: List<ScanResult>): Long {
        return results.maxOfOrNull { it.timestamp } ?: 0L
    }

    private fun refreshFingerprintWithTimeout(timeoutMs: Long = 1500L): List<WifiFingerprint> {
        val baselineResults = readCachedScanResults()
        val baselineTimestamp = latestScanTimestamp(baselineResults)

        val started = try {
            wifiManager?.startScan()
        } catch (e: Exception) {
            android.util.Log.d("WifiScan", "startScan() failed: ${e.message}", e)
            false
        }

        android.util.Log.d("WifiScan", "startScan() returned=$started")

        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        var results = emptyList<WifiFingerprint>()

        while (System.nanoTime() < deadline) {
            val rawResults = readCachedScanResults()
            if (rawResults.isNotEmpty() && latestScanTimestamp(rawResults) > baselineTimestamp) {
                results = rawResults.map { result ->
                    WifiFingerprint(
                        bssid = result.BSSID ?: "",
                        ssid = result.SSID?.takeIf { it.isNotEmpty() },
                        rssi = result.level
                    )
                }
                break
            }

            try {
                Thread.sleep(150)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }

        return results
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun getWifiFingerprint(): List<WifiFingerprint> {
        if (wifiManager == null) return lastNonEmptyFingerprint

        val fineGranted = hasFineLocationPermission()
        val coarseGranted = hasCoarseLocationPermission()
        val nearbyGranted = hasNearbyWifiPermission()
        val locationServicesEnabled = hasLocationServicesEnabled()

        android.util.Log.d(
            "WifiScan",
            "locationServicesEnabled=$locationServicesEnabled, ACCESS_FINE_LOCATION=$fineGranted, ACCESS_COARSE_LOCATION=$coarseGranted, NEARBY_WIFI_DEVICES=$nearbyGranted"
        )

        return try {
            val freshFingerprint = if (
    locationServicesEnabled &&
    fineGranted &&
    hasAccessWifiStatePermission() &&
    nearbyGranted
) {
                refreshFingerprintWithTimeout()
            } else {
                android.util.Log.w(
                    "WifiScan",
                    "Skipping fresh scan because location services or required permissions are unavailable"
                )
                emptyList()
            }

            val cachedFingerprint = if (freshFingerprint.isNotEmpty()) {
                freshFingerprint
            } else {
                readCachedFingerprint().ifEmpty { lastNonEmptyFingerprint }
            }

            if (cachedFingerprint.isNotEmpty()) {
                lastNonEmptyFingerprint = cachedFingerprint
            }

            cachedFingerprint
        } catch (e: Exception) {
            android.util.Log.d(
                "WifiScan",
                "scanResults read failed: ${e.message}",
                e
            )
            lastNonEmptyFingerprint
        }
    }
}
