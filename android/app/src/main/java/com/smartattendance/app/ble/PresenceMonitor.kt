package com.smartattendance.app.ble

import java.util.concurrent.ConcurrentHashMap

data class ObservedStudentPresence(
    val studentId: String,
    val rssi: Int,
    val lastSeenTimestampMillis: Long,
    val advertisementCount: Long,
)

class PresenceMonitor {
    private val presenceCache = ConcurrentHashMap<String, ObservedStudentPresence>()

    fun recordObservation(studentId: String, rssi: Int, lastSeenTimestampMillis: Long) {
        val current = presenceCache[studentId]
        val updated = ObservedStudentPresence(
            studentId = studentId,
            rssi = rssi,
            lastSeenTimestampMillis = lastSeenTimestampMillis,
            advertisementCount = (current?.advertisementCount ?: 0L) + 1L,
        )
        presenceCache[studentId] = updated
    }

    fun remove(studentId: String) {
        presenceCache.remove(studentId)
    }

    fun get(studentId: String): ObservedStudentPresence? = presenceCache[studentId]

    fun snapshot(): List<ObservedStudentPresence> {
        return presenceCache.values.sortedBy { it.studentId }
    }

    fun clear() {
        presenceCache.clear()
    }
}
