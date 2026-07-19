package com.smartattendance.app.ble

import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val HEX_CHARS = "0123456789abcdef"

data class ParsedBleAdvertisement(
    val anonymousDeviceId: ByteArray,
    val anonymousDeviceIdHex: String,
    val rollingToken: ByteArray,
    val rollingTokenHex: String,
    val manufacturerData: ByteArray,
    val rssi: Int,
    val observedAtMillis: Long,
)

class ScanParser {
    fun parse(result: ScanResult): ParsedBleAdvertisement? {
        val record = result.scanRecord ?: return null
        val manufacturerData = record.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID) ?: return null

        if (manufacturerData.size != BleConstants.ADVERTISEMENT_PAYLOAD_BYTES) {
            return null
        }

        val buffer = ByteBuffer.wrap(manufacturerData).order(ByteOrder.BIG_ENDIAN)
        val anonymousDeviceId = ByteArray(BleConstants.ANONYMOUS_DEVICE_ID_BYTES)
        buffer.get(anonymousDeviceId)

        val rollingToken = ByteArray(BleConstants.ROLLING_TOKEN_BYTES)
        buffer.get(rollingToken)

        return ParsedBleAdvertisement(
            anonymousDeviceId = anonymousDeviceId,
            anonymousDeviceIdHex = toHex(anonymousDeviceId),
            rollingToken = rollingToken,
            rollingTokenHex = toHex(rollingToken),
            manufacturerData = manufacturerData,
            rssi = result.rssi,
            observedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun toHex(bytes: ByteArray): String {
        val output = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            output.append(HEX_CHARS[value ushr 4])
            output.append(HEX_CHARS[value and 0x0F])
        }
        return output.toString()
    }
}
