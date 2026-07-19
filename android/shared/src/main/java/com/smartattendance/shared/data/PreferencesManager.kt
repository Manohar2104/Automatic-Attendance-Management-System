package com.smartattendance.shared.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {

        val SERVER_URL = stringPreferencesKey("server_url")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val IS_REGISTERED = booleanPreferencesKey("is_registered")
        val SESSION_ID = stringPreferencesKey("session_id")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val IS_SCANNING = booleanPreferencesKey("is_scanning")

        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val TEACHER_ID = stringPreferencesKey("teacher_id")
        val TEACHER_EMAIL = stringPreferencesKey("teacher_email")
        val ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")

        const val DEFAULT_SERVER_URL = "http://192.168.137.1:8000"
    }

    val serverUrl: Flow<String> =
        context.dataStore.data.map {
            it[SERVER_URL] ?: DEFAULT_SERVER_URL
        }

    val deviceId: Flow<String> =
        context.dataStore.data.map {
            it[DEVICE_ID] ?: ""
        }

    val isRegistered: Flow<Boolean> =
        context.dataStore.data.map {
            it[IS_REGISTERED] ?: false
        }

    val sessionId: Flow<String> =
        context.dataStore.data.map {
            it[SESSION_ID] ?: ""
        }

    val accessToken: Flow<String> =
        context.dataStore.data.map {
            it[ACCESS_TOKEN] ?: ""
        }

    val refreshToken: Flow<String> =
        context.dataStore.data.map {
            it[REFRESH_TOKEN] ?: ""
        }

    val teacherId: Flow<String> =
        context.dataStore.data.map {
            it[TEACHER_ID] ?: ""
        }

    val teacherEmail: Flow<String> =
        context.dataStore.data.map {
            it[TEACHER_EMAIL] ?: ""
        }

    val activeSessionId: Flow<String> =
        context.dataStore.data.map {
            it[ACTIVE_SESSION_ID] ?: ""
        }

    val lastSyncTime: Flow<Long> =
        context.dataStore.data.map {
            it[LAST_SYNC_TIME] ?: 0L
        }

    val isScanning: Flow<Boolean> =
        context.dataStore.data.map {
            it[IS_SCANNING] ?: false
        }

    suspend fun saveRefreshToken(token: String) {
        context.dataStore.edit {
            it[REFRESH_TOKEN] = token
        }
    }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit {
            it[SERVER_URL] = url
        }
    }

    suspend fun saveDeviceId(id: String) {
        context.dataStore.edit {
            it[DEVICE_ID] = id
        }
    }

    suspend fun setRegistered(registered: Boolean) {
        context.dataStore.edit {
            it[IS_REGISTERED] = registered
        }
    }

    suspend fun saveSessionId(sessionId: String) {
        context.dataStore.edit {
            it[SESSION_ID] = sessionId
        }
    }

    suspend fun saveAccessToken(token: String) {
        context.dataStore.edit {
            it[ACCESS_TOKEN] = token
        }
    }

    suspend fun saveTeacherId(teacherId: String) {
        context.dataStore.edit {
            it[TEACHER_ID] = teacherId
        }
    }

    suspend fun saveTeacherEmail(email: String) {
        context.dataStore.edit {
            it[TEACHER_EMAIL] = email
        }
    }

    suspend fun saveActiveSessionId(sessionId: String) {
        context.dataStore.edit {
            it[ACTIVE_SESSION_ID] = sessionId
        }
    }

    suspend fun updateLastSyncTime() {
        context.dataStore.edit {
            it[LAST_SYNC_TIME] = System.currentTimeMillis()
        }
    }

    suspend fun setScanning(scanning: Boolean) {
        context.dataStore.edit {
            it[IS_SCANNING] = scanning
        }
    }

    suspend fun clearTeacherSession() {
        context.dataStore.edit {
            it.remove(ACCESS_TOKEN)
            it.remove(REFRESH_TOKEN)
            it.remove(TEACHER_ID)
            it.remove(TEACHER_EMAIL)
            it.remove(ACTIVE_SESSION_ID)
        }
    }
}