package com.smartattendance.shared.data

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

class DeviceManager(private val context: Context) {

    fun getAndroidId(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: return ""
        return sha256(androidId)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
