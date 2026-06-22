package com.automatic.attendance.student.repository

import com.automatic.attendance.student.network.AuthApi
import com.automatic.attendance.student.network.TokenResponse
import com.automatic.attendance.student.storage.SecureTokenStorage
import retrofit2.Response
import com.automatic.attendance.student.network.LoginRequest
/**
 * AuthRepository handles authentication flows and token persistence.
 */
class AuthRepository(private val api: AuthApi, private val storage: SecureTokenStorage) {
    suspend fun login(username: String, password: String): Response<TokenResponse> {
    return api.login(
        LoginRequest(
            email = username,
            password = password,
            deviceName = android.os.Build.MODEL
        )
    )
}

    suspend fun refresh(refreshToken: String): Response<TokenResponse> {
        return api.refresh(mapOf("refreshToken" to refreshToken))
    }

    suspend fun logout() {
    try {
        api.logout()
    } catch (_: Exception) {
        // Ignore backend logout failure
    }

    // Always clear local tokens
    storage.clear()
    }

    suspend fun me(): Response<Map<String, Any>> {
        val resp = api.me()
        if (resp.code() == 401) {
            // Try to refresh
            val refresh = storage.getRefreshToken() ?: return resp
            val refreshResp = try { api.refresh(mapOf("refreshToken" to refresh)) } catch (e: Exception) { return resp }
            if (refreshResp.isSuccessful && refreshResp.body() != null) {
                val tokens = refreshResp.body()!!
                storage.saveAccessToken(tokens.accessToken)
                storage.saveRefreshToken(tokens.refreshToken)
                // retry me
                return try { api.me() } catch (e: Exception) { resp }
            }
        }
        return resp
    }

    suspend fun saveTokens(access: String, refresh: String) {
        storage.saveAccessToken(access)
        storage.saveRefreshToken(refresh)
    }

    fun getAccessToken(): String? = storage.getAccessToken()
}
