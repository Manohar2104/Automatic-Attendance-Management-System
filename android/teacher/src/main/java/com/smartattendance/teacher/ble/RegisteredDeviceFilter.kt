package com.smartattendance.teacher.ble

import android.util.Log
import com.smartattendance.shared.ble.BleConstants
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class RegisteredDeviceEntry(
    val studentId: String,
    val anonymousDeviceIdHex: String,
    val publicIdentifier: String? = null,
)

class RegisteredDeviceFilter {
    private val registeredDevices = ConcurrentHashMap<String, RegisteredDeviceEntry>()

    fun replaceAll(devices: Collection<RegisteredDeviceEntry>) {
        registeredDevices.clear()
        devices.forEach { device ->
            registeredDevices[normalize(device.anonymousDeviceIdHex)] = device
        }
    }

    fun register(device: RegisteredDeviceEntry) {
        registeredDevices[normalize(device.anonymousDeviceIdHex)] = device
    }

    fun resolveStudentId(
        anonymousDeviceIdHex: String,
        rollingTokenHex: String? = null,
        deviceAddress: String? = null,
        rssi: Int? = null,
        sessionId: String? = null,
        serviceUuids: String? = null,
        manufacturerId: Int = BleConstants.MANUFACTURER_ID,
    ): String? {
        val normalized = normalize(anonymousDeviceIdHex)
        Log.d(TAG, "Packet Received timestamp=${Instant.now()} studentUuid=<unknown_until_resolved> anonymousId=$normalized rollingToken=${rollingTokenHex ?: "<null>"} deviceAddress=${deviceAddress ?: "<null>"} rssi=${rssi ?: -999} sessionId=${sessionId ?: "<null>"} serviceUuids=${serviceUuids ?: "<null>"} manufacturerId=$manufacturerId")

        if (normalized.isBlank()) {
            Log.d(TAG, "REJECTED reason=Invalid Advertisement detail=Anonymous ID blank")
            return null
        }

        if (rollingTokenHex.isNullOrBlank()) {
            Log.d(TAG, "REJECTED reason=Rolling Token mismatch detail=Rolling token missing")
            return null
        }

        val entry = registeredDevices[normalized]
        if (entry == null) {
            Log.d(TAG, "REJECTED reason=Unknown Device detail=Anonymous ID not found anonymousBleId=$normalized deviceAddress=${deviceAddress ?: "<null>"} serviceUuids=${serviceUuids ?: "<null>"} manufacturerId=$manufacturerId")
            Log.d(TAG, "REJECTED reason=Device not registered detail=No registered-device record for anonymousBleId=$normalized deviceAddress=${deviceAddress ?: "<null>"} serviceUuids=${serviceUuids ?: "<null>"} manufacturerId=$manufacturerId")
            return null
        }
        Log.d(TAG, "ACCEPTED studentUuid=${entry.studentId} anonymousBleId=$normalized rollingToken=$rollingTokenHex deviceAddress=${deviceAddress ?: "<null>"} serviceUuids=${serviceUuids ?: "<null>"} manufacturerId=$manufacturerId")
        return entry.studentId
    }

    fun isRegistered(anonymousDeviceIdHex: String): Boolean {
        return resolveStudentId(anonymousDeviceIdHex) != null
    }

    fun accept(parsedAdvertisement: ParsedBleAdvertisement): Boolean {
        val anonymousDeviceIdHex = parsedAdvertisement.anonymousDeviceIdHex.trim()

        if (anonymousDeviceIdHex.isBlank()) {
            Log.d(TAG, "REJECTED reason=Invalid Advertisement detail=anonymous BLE ID blank")
            return false
        }

        return resolveStudentId(
            anonymousDeviceIdHex = anonymousDeviceIdHex,
            rollingTokenHex = parsedAdvertisement.rollingTokenHex,
            deviceAddress = null,
            rssi = parsedAdvertisement.rssi,
            sessionId = null,
        ) != null
    }

    fun size(): Int = registeredDevices.size

    fun snapshot(): List<RegisteredDeviceEntry> = registeredDevices.values.sortedBy { it.studentId }

    private fun normalize(value: String): String {
        return value.trim().lowercase()
    }

    companion object {
        private const val TAG = "RegisteredDeviceFilter"
    }
}
