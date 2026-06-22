package com.automatic.attendance.student.viewmodel

import com.automatic.attendance.student.repository.AuthRepository
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

class AuthViewModelUnitTest {
    private val repo = mockk<AuthRepository>()
    private lateinit var vm: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        vm = AuthViewModel(repo)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful login updates state and saves tokens`() = runBlocking {
        val tokenResp = com.automatic.attendance.student.network.TokenResponse("a","r")
        coEvery { repo.login(any(), any()) } returns Response.success(tokenResp)
        coEvery { repo.saveTokens(any(), any()) } returns Unit

        vm.login("u", "p")

        // allow coroutine to run
        kotlinx.coroutines.delay(50)

        assertTrue(vm.uiState.value is AuthUiState.Success)
        coVerify { repo.saveTokens("a","r") }
    }

    @Test
    fun `failed login sets error`() = runBlocking {
        coEvery { repo.login(any(), any()) } returns Response.error(401, okhttp3.ResponseBody.create(null, ""))

        vm.login("u","p")
        kotlinx.coroutines.delay(50)
        assertTrue(vm.uiState.value is AuthUiState.Error)
    }

    @Test
    fun `restore session calls me when token present`() = runBlocking {
        coEvery { repo.getAccessToken() } returns "token"
        coEvery { repo.me() } returns Response.success(mapOf("id" to "student-1"))

        var result = false
        vm.restoreSession { ok -> result = ok }
        kotlinx.coroutines.delay(50)
        assertTrue(result)
    }
}
