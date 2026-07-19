package com.smartattendance.teacher.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BleLiveMetrics(
    val packetsReceived: Long = 0L,
    val packetsAccepted: Long = 0L,
    val studentsSeen: Set<String> = emptySet(),
    val lastPacketReceivedAtMillis: Long = 0L,
    val lastAcceptedPacketAtMillis: Long = 0L,
    val lastRssi: Int? = null,
    val lastMacAddress: String? = null,
    val lastStudentId: String? = null,
) {
    val studentsSeenCount: Int
        get() = studentsSeen.size
}

object BleLiveMetricsStore {
    private val _metrics = MutableStateFlow(BleLiveMetrics())
    val metrics: StateFlow<BleLiveMetrics> = _metrics.asStateFlow()

    fun reset() {
        _metrics.value = BleLiveMetrics()
    }

    fun recordPacketReceived(
        timestampMillis: Long,
        macAddress: String?,
        rssi: Int,
    ) {
        val current = _metrics.value
        _metrics.value = current.copy(
            packetsReceived = current.packetsReceived + 1,
            lastPacketReceivedAtMillis = timestampMillis,
            lastRssi = rssi,
            lastMacAddress = macAddress,
        )
    }

    fun recordPacketAccepted(
        timestampMillis: Long,
        studentId: String,
        macAddress: String?,
        rssi: Int,
    ) {
        val current = _metrics.value
        _metrics.value = current.copy(
            packetsAccepted = current.packetsAccepted + 1,
            studentsSeen = current.studentsSeen + studentId,
            lastAcceptedPacketAtMillis = timestampMillis,
            lastRssi = rssi,
            lastMacAddress = macAddress,
            lastStudentId = studentId,
        )
    }
}