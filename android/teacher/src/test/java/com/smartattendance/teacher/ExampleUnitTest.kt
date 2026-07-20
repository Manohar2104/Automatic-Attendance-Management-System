package com.smartattendance.teacher

import com.smartattendance.shared.data.PreferencesManager
import com.smartattendance.shared.network.BleObservationUploadResponse
import com.smartattendance.teacher.ble.ObservationUploader
import com.smartattendance.teacher.ble.ParsedObservation
import com.smartattendance.teacher.repository.TeacherRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
    @Test
    fun pendingObservationsAreClearedOnSessionChange() = runTest {
        val repository = mockk<TeacherRepository>()
        val preferences = mockk<PreferencesManager>()

        every { preferences.accessToken } returns flowOf("token-123")
        every { preferences.teacherId } returns flowOf("teacher-1")
        every { preferences.activeSessionId } returns flowOf("session-1")
        coEvery { repository.uploadObservation(any()) } returns Result.success(
            BleObservationUploadResponse(
                session_id = "session-1",
                received = 1,
                accepted = 1,
                rejected = 0,
                processed_at = "2026-07-20T00:00:00Z",
            )
        )

        val uploader = ObservationUploader(this, repository, preferences)
        uploader.enqueue(
            ParsedObservation(
                studentId = "student-1",
                anonymousBleDeviceId = "aa11bb22cc33dd44",
                rollingToken = "bbbbbbbbbbbbbbbb",
                rssi = -55,
                timestampMillis = 1_000L,
                sessionId = "session-1",
            )
        )

        uploader.clearPendingObservations()
        uploader.flushPendingObservations()

        coVerify(exactly = 0) { repository.uploadObservation(any()) }
        assertEquals(1, 1)
    }

    @Test
    fun permanentUploadFailuresAreNotRequeued() = runTest {
        val repository = mockk<TeacherRepository>()
        val preferences = mockk<PreferencesManager>()

        every { preferences.accessToken } returns flowOf("token-123")
        every { preferences.teacherId } returns flowOf("teacher-1")
        every { preferences.activeSessionId } returns flowOf("session-1")
        coEvery { repository.uploadObservation(any()) } returns Result.failure(RuntimeException("bad request"))

        val uploader = ObservationUploader(this, repository, preferences)
        uploader.enqueue(
            ParsedObservation(
                studentId = "student-1",
                anonymousBleDeviceId = "aa11bb22cc33dd44",
                rollingToken = "bbbbbbbbbbbbbbbb",
                rssi = -55,
                timestampMillis = 1_000L,
                sessionId = "session-1",
            )
        )

        uploader.flushPendingObservations()
        uploader.flushPendingObservations()

        coVerify(exactly = 1) { repository.uploadObservation(any()) }
    }
}