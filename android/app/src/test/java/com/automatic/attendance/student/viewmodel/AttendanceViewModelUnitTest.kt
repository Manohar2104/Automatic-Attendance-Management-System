package com.automatic.attendance.student.viewmodel

import com.automatic.attendance.student.repository.AttendanceRepository
import com.automatic.attendance.student.repository.AttendanceRecord
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AttendanceViewModelUnitTest {
    private val repo = mockk<AttendanceRepository>()
    private lateinit var vm: AttendanceViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        vm = AttendanceViewModel(repo)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load attendance history success`() = runBlocking {
        val records = listOf(AttendanceRecord("s1", "Math 101", "CSCI", "PRESENT", 85, 100, 80, "2026-06-16"))
        coEvery { repo.getAttendanceHistory(any()) } returns records

        vm.loadAttendanceHistory("student-1")
        kotlinx.coroutines.delay(50)

        assertTrue(vm.historyState.value is AttendanceHistoryState.Success)
    }

    @Test
    fun `load attendance history empty`() = runBlocking {
        coEvery { repo.getAttendanceHistory(any()) } returns emptyList()

        vm.loadAttendanceHistory("student-1")
        kotlinx.coroutines.delay(50)

        assertTrue(vm.historyState.value is AttendanceHistoryState.Success)
    }

    @Test
    fun `load current attendance success`() = runBlocking {
        val data = mapOf("runningPresenceScore" to 80, "confidenceScore" to 92)
        coEvery { repo.getCurrentAttendance(any(), any()) } returns Response.success(data)

        vm.loadCurrentAttendance("s1", "student-1")
        kotlinx.coroutines.delay(50)

        assertTrue(vm.currentState.value is CurrentAttendanceState.Ready)
    }

    @Test
    fun `load current attendance failure`() = runBlocking {
        coEvery { repo.getCurrentAttendance(any(), any()) } returns Response.error(500, okhttp3.ResponseBody.create(null, ""))

        vm.loadCurrentAttendance("s1", "student-1")
        kotlinx.coroutines.delay(50)

        assertTrue(vm.currentState.value is CurrentAttendanceState.Error)
    }
}
