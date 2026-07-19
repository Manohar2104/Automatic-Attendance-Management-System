package com.smartattendance.app.ble

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

object BlePermissions {
    fun requiredBlePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
    }

    fun requiredAdvertiserPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }
    }

    fun requiredScannerPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }
    }

    fun requiredPermissions(): Array<String> {
        return requiredBlePermissions()
    }

    fun missingPermissions(context: Context): List<String> {
        return requiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun missingScannerPermissions(context: Context): List<String> {
        return requiredScannerPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        return missingPermissions(context).isEmpty()
    }

    fun hasAdvertiserPermissions(context: Context): Boolean {
        return missingAdvertiserPermissions(context).isEmpty()
    }

    fun missingAdvertiserPermissions(context: Context): List<String> {
        return requiredAdvertiserPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasScannerPermissions(context: Context): Boolean {
        return missingScannerPermissions(context).isEmpty()
    }

    fun permissionReport(context: Context): String {
        return requiredBlePermissions().joinToString(separator = "\n") { permission ->
            "$permission granted=${ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED}"
        }
    }

    fun advertiserPermissionReport(context: Context): String {
        return requiredAdvertiserPermissions().joinToString(separator = "\n") { permission ->
            "$permission granted=${ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED}"
        }
    }

    fun scannerPermissionReport(context: Context): String {
        return requiredScannerPermissions().joinToString(separator = "\n") { permission ->
            "$permission granted=${ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED}"
        }
    }

    fun requestPermissions(
        activity: Activity,
        launcher: ActivityResultLauncher<Array<String>>,
    ) {
        val missing = missingPermissions(activity)
        if (missing.isNotEmpty()) {
            launcher.launch(missing.toTypedArray())
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter
        return adapter?.isEnabled == true
    }
}
