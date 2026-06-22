package com.automatic.attendance.student.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecureTokenStorageImpl(context: Context) : SecureTokenStorage {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs = EncryptedSharedPreferences.create(
        "secure_tokens",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun saveAccessToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
    }

    override fun saveRefreshToken(token: String) {
        prefs.edit().putString("refresh_token", token).apply()
    }

    override fun getAccessToken(): String? = prefs.getString("access_token", null)

    override fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
