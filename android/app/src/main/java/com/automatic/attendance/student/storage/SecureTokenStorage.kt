package com.automatic.attendance.student.storage

interface SecureTokenStorage {
    fun saveAccessToken(token: String)
    fun saveRefreshToken(token: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clear()
}

// Platform specific implementation should use EncryptedSharedPreferences or Android Keystore.
