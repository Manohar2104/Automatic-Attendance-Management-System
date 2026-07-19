package com.smartattendance.teacher.ble

import android.bluetooth.le.ScanResult
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import com.smartattendance.shared.ble.BleConstants

private const val HEX_CHARS = "0123456789abcdef"

data class ParsedBleAdvertisement(
    val anonymousDeviceId: ByteArray,
    val anonymousDeviceIdHex: String,
    val rollingToken: ByteArray,
    val rollingTokenHex: String,
    val rssi: Int,
    val timestampMillis: Long,
)

class ScanParser {
    fun parse(result: ScanResult): ParsedBleAdvertisement? {
        val timestamp = Instant.now().toString()
        val record = result.scanRecord
        if (record == null) {
            Log.d(TAG, "Validation result=REJECTED reason=Invalid Advertisement exception=<none> timestamp=$timestamp rawPacketBytes=<null>")
            return null
        }

        val rawBytes = record.bytes
        val rawPacketHex = rawBytes?.let { toHex(it) } ?: "<null>"
        val serviceUuidValues = record.serviceUuids?.map { it.uuid.toString() }.orEmpty()
        val serviceUuids = if (serviceUuidValues.isEmpty()) "<null>" else serviceUuidValues.joinToString()
        val serviceData = record.serviceData?.entries?.joinToString { (uuid, value) ->
            "${uuid.uuid}:${toHex(value)}"
        } ?: "<null>"
        val manufacturerBytes = record.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID)
        val manufacturerHex = manufacturerBytes?.let { toHex(it) } ?: "<null>"

        Log.d(
            TAG,
            "Parse expectation timestamp=$timestamp mac=${result.device?.address ?: "<null>"} expectedServiceUuid=${BleConstants.ADVERTISEMENT_UUID_STRING} actualServiceUuids=$serviceUuids expectedManufacturerId=${BleConstants.MANUFACTURER_ID} actualManufacturerId=${BleConstants.MANUFACTURER_ID} expectedPayloadLength=${BleConstants.ADVERTISEMENT_PAYLOAD_BYTES} actualRawLength=${rawBytes?.size ?: -1}",
        )

        if (serviceUuidValues.isNotEmpty() && serviceUuidValues.none { it.equals(BleConstants.ADVERTISEMENT_UUID_STRING, ignoreCase = true) }) {
            Log.d(
                TAG,
                "Validation result=REJECTED reason=Unknown Service UUID exception=<none> timestamp=$timestamp expected=${BleConstants.ADVERTISEMENT_UUID_STRING} actual=$serviceUuids rawPacketBytes=$rawPacketHex",
            )
            return null
        }

        Log.d(
            TAG,
            "Advertisement parse begin timestamp=$timestamp mac=${result.device?.address ?: "<null>"} deviceHash=${result.device?.hashCode() ?: -1} rssi=${result.rssi} txPower=${result.txPower} parsedUuid=$serviceUuids parsedManufacturerData=$manufacturerHex rawLength=${rawBytes?.size ?: -1} serviceData=$serviceData",
        )

        val manufacturerData = manufacturerBytes
        if (manufacturerData == null) {
            Log.d(TAG, "Validation result=REJECTED reason=Missing Manufacturer Data exception=<none> timestamp=$timestamp expectedManufacturerId=${BleConstants.MANUFACTURER_ID} rawPacketBytes=$rawPacketHex")
            return null
        }

        if (manufacturerData.size != BleConstants.ADVERTISEMENT_PAYLOAD_BYTES) {
            Log.d(
                TAG,
                "Validation result=REJECTED reason=Invalid Advertisement exception=<none> timestamp=$timestamp expectedManufacturerId=${BleConstants.MANUFACTURER_ID} manufacturerLength=${manufacturerData.size} expectedPayloadLength=${BleConstants.ADVERTISEMENT_PAYLOAD_BYTES} rawPacketBytes=$rawPacketHex",
            )
            return null
        }

        return try {
            val buffer = ByteBuffer.wrap(manufacturerData).order(ByteOrder.BIG_ENDIAN)
            val anonymousDeviceId = ByteArray(BleConstants.ANONYMOUS_DEVICE_ID_BYTES)
            buffer.get(anonymousDeviceId)

            val rollingToken = ByteArray(BleConstants.ROLLING_TOKEN_BYTES)
            buffer.get(rollingToken)

            val anonymousIdHex = toHex(anonymousDeviceId)
            val rollingTokenHex = toHex(rollingToken)
            Log.d(
                TAG,
                "Advertisement parsed timestamp=$timestamp anonymousId=$anonymousIdHex rollingToken=$rollingTokenHex sessionId=<not_in_ble_payload> packetVersion=<not_in_ble_payload> parsedManufacturerData=$manufacturerHex parsedUuid=$serviceUuids expectedManufacturerId=${BleConstants.MANUFACTURER_ID} expectedServiceUuid=${BleConstants.ADVERTISEMENT_UUID_STRING} expectedPayloadLength=${BleConstants.ADVERTISEMENT_PAYLOAD_BYTES}",
            )
            Log.d(TAG, "Validation result=ACCEPTED anonymousId=$anonymousIdHex rollingToken=$rollingTokenHex")

            ParsedBleAdvertisement(
                anonymousDeviceId = anonymousDeviceId,
                anonymousDeviceIdHex = anonymousIdHex,
                rollingToken = rollingToken,
                rollingTokenHex = rollingTokenHex,
                rssi = result.rssi,
                timestampMillis = System.currentTimeMillis(),
            )
        } catch (exception: Exception) {
            Log.d(
                TAG,
                "Validation result=REJECTED reason=Invalid Advertisement exception=${exception::class.java.simpleName}:${exception.message} timestamp=$timestamp rawPacketBytes=$rawPacketHex",
                exception,
            )
            null
        }
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

    companion object {
        private const val TAG = "ScanParser"
    }
}
