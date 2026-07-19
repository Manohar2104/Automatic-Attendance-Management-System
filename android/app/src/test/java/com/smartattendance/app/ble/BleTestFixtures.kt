package com.smartattendance.app.ble

import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import io.mockk.every
import io.mockk.mockk
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun ByteArray.toHexString(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

fun buildBleAdvertisementPayload(
    anonymousDeviceId: ByteArray = ByteArray(BleConstants.ANONYMOUS_DEVICE_ID_BYTES) { index -> index.toByte() },
    rollingToken: ByteArray = ByteArray(BleConstants.ROLLING_TOKEN_BYTES) { index -> (index + 16).toByte() },
): ByteArray {
    require(anonymousDeviceId.size == BleConstants.ANONYMOUS_DEVICE_ID_BYTES)
    require(rollingToken.size == BleConstants.ROLLING_TOKEN_BYTES)

    return ByteBuffer.allocate(BleConstants.ADVERTISEMENT_PAYLOAD_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .apply {
            put(anonymousDeviceId)
            put(rollingToken)
        }
        .array()
}

fun scanResultWithManufacturerData(
    manufacturerData: ByteArray,
    rssi: Int = -42,
): ScanResult {
    val scanRecord = mockk<ScanRecord>()
    every { scanRecord.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID) } returns manufacturerData

    val scanResult = mockk<ScanResult>()
    every { scanResult.scanRecord } returns scanRecord
    every { scanResult.rssi } returns rssi
    return scanResult
}
