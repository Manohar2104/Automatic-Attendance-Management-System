package com.smartattendance.app.data

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
        val STUDENT_EMAIL = stringPreferencesKey("student_email")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val IS_REGISTERED = booleanPreferencesKey("is_registered")
        val IS_BLE_REGISTERED = booleanPreferencesKey("is_ble_registered")
        val SESSION_ID = stringPreferencesKey("session_id")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val IS_SCANNING = booleanPreferencesKey("is_scanning")

        const val DEFAULT_SERVER_URL = "http://192.168.137.1:8000"
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val deviceId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEVICE_ID] ?: ""
    }

    val studentEmail: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[STUDENT_EMAIL] ?: ""
    }

    val accessToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ACCESS_TOKEN] ?: ""
    }

    val refreshToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[REFRESH_TOKEN] ?: ""
    }

    val isRegistered: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_REGISTERED] ?: false
    }

    val isBleRegistered: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_BLE_REGISTERED] ?: false
    }

    val sessionId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SESSION_ID] ?: ""
    }

    val lastSyncTime: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LAST_SYNC_TIME] ?: 0L
    }

    val isScanning: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_SCANNING] ?: false
    }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { it[SERVER_URL] = url }
    }

    suspend fun saveDeviceId(id: String) {
        context.dataStore.edit { it[DEVICE_ID] = id }
    }

    suspend fun saveStudentEmail(email: String) {
        context.dataStore.edit { it[STUDENT_EMAIL] = email }
    }

    suspend fun saveAccessToken(token: String) {
        context.dataStore.edit { it[ACCESS_TOKEN] = token }
    }

    suspend fun saveRefreshToken(token: String) {
        context.dataStore.edit { it[REFRESH_TOKEN] = token }
    }

    suspend fun setRegistered(registered: Boolean) {
        context.dataStore.edit { it[IS_REGISTERED] = registered }
    }

    suspend fun setBleRegistered(registered: Boolean) {
        context.dataStore.edit { it[IS_BLE_REGISTERED] = registered }
    }

    suspend fun saveSessionId(sessionId: String) {
        context.dataStore.edit { it[SESSION_ID] = sessionId }
    }

    suspend fun updateLastSyncTime() {
        context.dataStore.edit { it[LAST_SYNC_TIME] = System.currentTimeMillis() }
    }

    suspend fun setScanning(scanning: Boolean) {
        context.dataStore.edit { it[IS_SCANNING] = scanning }
    }

    suspend fun clearStudentSession() {
        context.dataStore.edit {
            it.remove(ACCESS_TOKEN)
            it.remove(REFRESH_TOKEN)
            it.remove(SESSION_ID)
            it.remove(IS_SCANNING)
            it.remove(IS_BLE_REGISTERED)
            it.remove(IS_REGISTERED)
        }
    }
}
