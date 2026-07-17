package com.smartattendance.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object BiometricKeyManager {

    private const val KEY_NAME = "smart_attendance_biometric_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /**
     * Initializes and returns a Cipher using our biometric-bound key.
     * Returns null if a new fingerprint was registered, which invalidates the key.
     */
    fun getCipher(): Cipher? {
        return try {
            val cipher = Cipher.getInstance(
                "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"
            )
            val key = getOrCreateSecretKey()
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Thrown if a new fingerprint was registered on the device!
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_NAME)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                KEY_NAME,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)

            // Invalidate the key automatically if a new fingerprint is enrolled
            builder.setInvalidatedByBiometricEnrollment(true)

            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        }

        return keyStore.getKey(KEY_NAME, null) as SecretKey
    }

    /**
     * Delete the key (useful on logout)
     */
    fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_NAME)) {
                keyStore.deleteEntry(KEY_NAME)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
