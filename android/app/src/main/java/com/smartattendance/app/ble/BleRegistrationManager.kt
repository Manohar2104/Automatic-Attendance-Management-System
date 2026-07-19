package com.smartattendance.app.ble

import android.content.Context
import com.smartattendance.app.data.DeviceManager
import com.smartattendance.app.data.PreferencesManager
import com.smartattendance.app.network.ApiClient
import com.smartattendance.app.network.BleDeviceRegistrationRequest
import retrofit2.HttpException
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.io.IOException

class BleRegistrationManager(
    context: Context,
    private val preferencesManager: PreferencesManager = PreferencesManager(context),
    private val deviceManager: DeviceManager = DeviceManager(context),
) {
    companion object {
        private const val TAG = "BleRegistration"
    }

    suspend fun getRegisteredDeviceId(): String? {
        val storedId = preferencesManager.deviceId.first().trim()
        return storedId.takeIf { it.isNotBlank() }
            ?: deviceManager.getAndroidId().takeIf { it.isNotBlank() }
    }

    suspend fun getAnonymousDeviceId(): ByteArray? {
        val registeredId = getRegisteredDeviceId() ?: return null
        return toFixed16Bytes(registeredId)
    }

    suspend fun registerBleDevice(
        serverUrl: String,
        authToken: String,
        publicIdentifier: String? = null,
    ): Boolean {
        val registeredId = getRegisteredDeviceId() ?: return false
        val anonymousBleIdBytes = toFixed16Bytes(registeredId)
        val anonymousBleId = anonymousBleIdBytes.toHexString()
        val resolvedPublicIdentifier = publicIdentifier?.trim()?.takeIf { it.isNotBlank() }
            ?: preferencesManager.studentEmail.first().trim().takeIf { it.isNotBlank() }
        val deviceHash = registeredId.trim()

        logDebug("BLE registration request -> endpoint=${serverUrl.trim().trimEnd('/')}/api/ble/register")
        logDebug("Authorization header present=true")
        logDebug("anonymous_ble_id=$anonymousBleId")
        logDebug("public_identifier=${resolvedPublicIdentifier ?: "<blank>"}")
        logDebug("device_hash=$deviceHash")

        val request = BleDeviceRegistrationRequest(
            anonymous_ble_id = anonymousBleId,
            public_identifier = resolvedPublicIdentifier,
            device_hash = deviceHash,
        )

        return try {
            val response = ApiClient(serverUrl).api.registerBleDevice("Bearer $authToken", request)
            logDebug(
                "BLE registration HTTP code=200 responseBody={success=${response.success}, student_id=${response.student_id}, anonymous_ble_id=${response.anonymous_ble_id}}",
            )
            if (!response.success) {
                logError("BLE registration returned success=false")
                false
            } else {
                preferencesManager.saveDeviceId(registeredId)
                preferencesManager.setBleRegistered(true)
                true
            }
        } catch (httpException: HttpException) {
            val errorBody = try {
                httpException.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            logError(
                "BLE registration HTTP code=${httpException.code()} errorBody=${errorBody ?: "<empty>"}",
                httpException,
            )
            false
        } catch (ioException: IOException) {
            logError("BLE registration IO failure", ioException)
            false
        } catch (exception: Exception) {
            logError("BLE registration unexpected failure", exception)
            false
        }
    }

    private fun toFixed16Bytes(value: String): ByteArray {
        val normalized = value.trim().removePrefix("0x")
        val hexCandidate = normalized.length % 2 == 0 && normalized.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

        val rawBytes = if (hexCandidate) {
            normalized.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } else {
            MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        }

        return when {
            rawBytes.size >= BleConstants.ANONYMOUS_DEVICE_ID_BYTES -> rawBytes.copyOf(BleConstants.ANONYMOUS_DEVICE_ID_BYTES)
            else -> ByteArray(BleConstants.ANONYMOUS_DEVICE_ID_BYTES).also { target ->
                rawBytes.copyInto(target, endIndex = rawBytes.size)
            }
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun logDebug(message: String) {
        runCatching {
            android.util.Log.d(TAG, message)
        }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable == null) {
                android.util.Log.e(TAG, message)
            } else {
                android.util.Log.e(TAG, message, throwable)
            }
        }
    }
}
