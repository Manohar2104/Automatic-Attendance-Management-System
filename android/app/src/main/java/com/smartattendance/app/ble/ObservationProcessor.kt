package com.smartattendance.app.ble

import android.bluetooth.le.ScanResult
import java.security.MessageDigest

class ObservationProcessor(
    serverUrl: String,
    authToken: String,
    teacherId: String,
    sessionId: String,
    private val scanParser: ScanParser = ScanParser(),
    private val registeredDeviceFilter: RegisteredDeviceFilter = RegisteredDeviceFilter(),
    private val presenceMonitor: PresenceMonitor = PresenceMonitor(),
) {
    private val observationUploader = ObservationUploader(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO),
        serverUrl = serverUrl,
        authToken = authToken,
        teacherId = teacherId,
        sessionId = sessionId,
    )

    fun replaceRegisteredDevices(devices: Collection<RegisteredDeviceEntry>) {
        registeredDeviceFilter.replaceAll(devices)
    }

    suspend fun start() {
        observationUploader.start()
    }

    suspend fun stop() {
        observationUploader.stop()
    }

    suspend fun processScanResult(result: ScanResult) {
        val parsedPacket = scanParser.parse(result) ?: return
        val studentId = registeredDeviceFilter.resolveStudentId(parsedPacket.anonymousDeviceIdHex) ?: return

        presenceMonitor.recordObservation(
            studentId = studentId,
            rssi = parsedPacket.rssi,
            lastSeenTimestampMillis = parsedPacket.observedAtMillis,
        )

        observationUploader.enqueueObservation(
            studentId = studentId,
            rssi = parsedPacket.rssi,
            lastSeenMillis = parsedPacket.observedAtMillis,
            rollingTokenHex = parsedPacket.rollingTokenHex,
            timestampMillis = parsedPacket.observedAtMillis,
            hmacHex = sha256Hex(parsedPacket.manufacturerData),
        )
    }

    private fun sha256Hex(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        val output = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            output.append(HEX_CHARS[value ushr 4])
            output.append(HEX_CHARS[value and 0x0F])
        }
        return output.toString()
    }

    companion object {
        private const val HEX_CHARS = "0123456789abcdef"
    }
}
