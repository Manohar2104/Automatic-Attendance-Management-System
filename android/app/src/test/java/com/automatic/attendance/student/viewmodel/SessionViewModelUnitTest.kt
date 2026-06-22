package com.automatic.attendance.student.viewmodel

import com.automatic.attendance.student.repository.SessionRepository
import com.automatic.attendance.student.network.ActiveSession
import io.mockk.coEvery
import io.mockk.coVerify
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

class SessionViewModelUnitTest {
    private val repo = mockk<SessionRepository>()
    private lateinit var vm: SessionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        vm = SessionViewModel(repo)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetch active sessions success`() = runBlocking {
        val sessions = listOf(ActiveSession("s1","ACTIVE","class-1","Course","2026-06-16T10:00:00Z"))
        coEvery { repo.listActive() } returns Response.success(mapOf("sessions" to sessions))

        vm.fetchActiveSessions()
        kotlinx.coroutines.delay(50)
        assertTrue(vm.state.value is SessionsUiState.Success)
        coVerify { repo.listActive() }
    }

    @Test
    fun `fetch active sessions empty`() = runBlocking {
        coEvery { repo.listActive() } returns Response.success(mapOf("sessions" to emptyList()))
        vm.fetchActiveSessions()
        kotlinx.coroutines.delay(50)
        assertTrue(vm.state.value is SessionsUiState.Success)
    }

    @Test
    fun `fetch active sessions failure`() = runBlocking {
        coEvery { repo.listActive() } returns Response.error(500, okhttp3.ResponseBody.create(null, ""))
        vm.fetchActiveSessions()
        kotlinx.coroutines.delay(50)
        assertTrue(vm.state.value is SessionsUiState.Error)
    }

    @Test
    fun `join success`() = runBlocking {
        coEvery { repo.joinSession(any(), any()) } returns Response.success(mapOf("ok" to true))
        vm.joinSession("s1","student-1")
        kotlinx.coroutines.delay(50)
        assertTrue(vm.joinState.value is JoinUiState.Joined)
    }

    @Test
    fun `join failure`() = runBlocking {
        coEvery { repo.joinSession(any(), any()) } returns Response.error(403, okhttp3.ResponseBody.create(null, ""))
        vm.joinSession("s1","student-1")
        kotlinx.coroutines.delay(50)
        assertTrue(vm.joinState.value is JoinUiState.JoinError)
    }
}
