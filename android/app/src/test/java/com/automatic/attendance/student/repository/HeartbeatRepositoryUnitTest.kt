package com.automatic.attendance.student.repository

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit

class HeartbeatRepositoryUnitTest {
    private val retrofit = mockk<Retrofit>()
    private val storage = mockk<com.automatic.attendance.student.storage.SecureTokenStorage>()
    private lateinit var repo: HeartbeatRepository

    @org.junit.Before
    fun setup() {
        repo = HeartbeatRepository(retrofit, storage)
    }

    @Test
    fun `sequence number increments`() {
        assertEquals(1, repo.getNextSequenceNumber())
        assertEquals(2, repo.getNextSequenceNumber())
        assertEquals(3, repo.getNextSequenceNumber())
    }

    @Test
    fun `sequence resets`() {
        repo.getNextSequenceNumber()
        repo.getNextSequenceNumber()
        repo.resetSequence()
        assertEquals(1, repo.getNextSequenceNumber())
    }

    @Test
    fun `wifi fingerprint payload structure`() {
        val payload = HeartbeatPayload(
            sessionId = "session-1",
            wifiFingerprint = listOf(WifiFingerprint("AA:BB:CC:DD:EE:FF", "WiFi", -55)),
            sequenceNumber = 1,
            deviceFingerprint = "device-1",
            timestamp = "2026-06-16T10:00:00Z"
        )
        assertTrue(payload.sessionId.isNotEmpty())
        assertEquals(1, payload.wifiFingerprint.size)
    }

    @Test
    fun `wifi fingerprint with multiple networks`() {
        val fingerprints = listOf(
            WifiFingerprint("AA:BB:CC:DD:EE:FF", "Room1", -55),
            WifiFingerprint("11:22:33:44:55:66", "Room2", -75)
        )
        assertEquals(2, fingerprints.size)
    }
}
