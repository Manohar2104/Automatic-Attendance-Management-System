package com.smartattendance.app.ble

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

    fun resolveStudentId(anonymousDeviceIdHex: String): String? {
        return registeredDevices[normalize(anonymousDeviceIdHex)]?.studentId
    }

    fun isRegistered(anonymousDeviceIdHex: String): Boolean {
        return resolveStudentId(anonymousDeviceIdHex) != null
    }

    fun size(): Int = registeredDevices.size

    fun snapshot(): List<RegisteredDeviceEntry> = registeredDevices.values.sortedBy { it.studentId }

    private fun normalize(value: String): String {
        return value.trim().lowercase()
    }
}
