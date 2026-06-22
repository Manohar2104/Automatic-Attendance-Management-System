package com.automatic.attendance.student.repository

import com.automatic.attendance.student.network.AttendanceApi
import com.automatic.attendance.student.network.AttendanceHistoryItem
import com.automatic.attendance.student.network.AttendanceHistoryResponse
import com.automatic.attendance.student.network.CurrentAttendanceResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AttendanceRepositoryUnitTest {
    private val api = mockk<AttendanceApi>()
    private lateinit var repo: AttendanceRepository

    @Before
    fun setup() {
        repo = AttendanceRepository(api)
    }

    @Test
    fun `attendance history maps backend response`() = runBlocking {
        coEvery { api.history(any()) } returns Response.success(
            AttendanceHistoryResponse(
                records = listOf(
                    AttendanceHistoryItem(
                        courseName = "Mathematics",
                        sessionId = "session-1",
                        status = "PRESENT",
                        joinScore = 100,
                        attendanceTimestamp = "2026-06-16T10:00:00.000Z",
                        lastHeartbeatTimestamp = "2026-06-16T10:45:00.000Z",
                        totalAcceptedHeartbeats = 4,
                        lastHeartbeatSequenceNumber = 4,
                        currentHeartbeatStatus = "ACCEPTED"
                    )
                )
            )
        )

        val records = repo.getAttendanceHistory("student-1")

        assertEquals(1, records.size)
        assertEquals("Mathematics", records[0].courseName)
        assertEquals("Available", records[0].wifiFingerprintStatus)
        assertEquals(4, records[0].totalAcceptedHeartbeats)
    }

    @Test
    fun `current attendance passes session id through`() = runBlocking {
        coEvery { api.current("session-1") } returns Response.success(
            CurrentAttendanceResponse(
                record = AttendanceHistoryItem(
                    courseName = "Mathematics",
                    sessionId = "session-1",
                    status = "PRESENT",
                    joinScore = 100,
                    attendanceTimestamp = "2026-06-16T10:00:00.000Z",
                    lastHeartbeatTimestamp = "2026-06-16T10:45:00.000Z",
                    totalAcceptedHeartbeats = 3,
                    lastHeartbeatSequenceNumber = 3,
                    currentHeartbeatStatus = "ACCEPTED"
                )
            )
        )

        val response = repo.getCurrentAttendance("session-1", "student-1")

        assertEquals(true, response.isSuccessful)
        assertEquals("session-1", response.body()?.record?.sessionId)
    }
}